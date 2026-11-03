package com.glassmark.app

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.HttpURLConnection
import java.net.URL

data class PageInfo(val title: String, val description: String, val ogImage: String?, val text: String)

/** Baixa a página no aparelho e extrai título, descrição, imagem de prévia (og:image) e texto visível. */
object PageFetcher {
    private val metaTag = Regex("(?i)<meta\\b[^>]*>")
    private val keyAttr = Regex("(?i)\\b(?:property|name)\\s*=\\s*[\"']([^\"']+)[\"']")
    private val contentAttr = Regex("(?i)\\bcontent\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")

    private fun decode(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").trim()

    private fun metas(html: String): Map<String, String> {
        val out = HashMap<String, String>()
        for (m in metaTag.findAll(html)) {
            val tag = m.value
            val k = keyAttr.find(tag)?.groupValues?.get(1)?.lowercase() ?: continue
            val c = contentAttr.find(tag)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } } ?: continue
            if (c.isNotBlank() && k !in out) out[k] = decode(c)
        }
        return out
    }

    suspend fun fetch(url: String): PageInfo? = withContext(Dispatchers.IO) {
        try {
            val target = url.replaceFirst("http://", "https://")
            val conn = URL(target).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
            conn.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
            if (conn.responseCode >= 400) return@withContext null
            val bytes = conn.inputStream.use { ins ->
                val buf = java.io.ByteArrayOutputStream(); val tmp = ByteArray(16384); var total = 0
                while (total < 700_000) { val n = ins.read(tmp); if (n < 0) break; buf.write(tmp, 0, n); total += n }
                buf.toByteArray()
            }
            val ct = conn.contentType.orEmpty()
            var cs = Regex("(?i)charset=([\\w-]+)").find(ct)?.groupValues?.get(1)
            val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1)
            if (cs == null) cs = Regex("(?i)<meta[^>]+charset=[\"']?([\\w-]+)").find(head)?.groupValues?.get(1)
            val html = String(bytes, runCatching { charset(cs ?: "UTF-8") }.getOrDefault(Charsets.UTF_8))
            val m = metas(html)
            val title = decode(m["og:title"] ?: Regex("(?is)<title[^>]*>(.*?)</title>").find(html)?.groupValues?.get(1).orEmpty())
            val desc = m["og:description"] ?: m["description"] ?: m["twitter:description"] ?: ""
            val img = (m["og:image"] ?: m["og:image:url"] ?: m["twitter:image"] ?: m["twitter:image:src"])?.let { raw ->
                runCatching { URL(URL(target), raw).toString().replaceFirst("http://", "https://") }.getOrNull()
            }
            val body = html.replace(Regex("(?is)<(script|style|noscript|svg|head)\\b.*?</\\1>"), " ").replace(Regex("(?s)<!--.*?-->"), " ")
                .replace(Regex("(?s)<[^>]+>"), " ")
            val text = decode(body).replace(Regex("\\s+"), " ").take(4000)
            PageInfo(title, desc, img, text)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
    }
}

/** Resumo com Firebase AI Logic (Gemini Developer API). Tenta cada modelo com tempo limite. */
object AiSummary {
    private val MODELS = listOf("gemini-3.5-flash-lite", "gemini-3.5-flash", "gemini-3.8-flash")
    private val models = HashMap<String, GenerativeModel>()
    private fun model(name: String): GenerativeModel = synchronized(models) {
        models.getOrPut(name) {
            Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                modelName = name,
                systemInstruction = content { text("Você resume páginas da web. Responda sempre em português do Brasil, sem markdown, sem introdução e sem aspas.") }
            )
        }
    }

    suspend fun summarize(url: String, info: PageInfo): Result<String> = withContext(Dispatchers.IO) {
        if (info.title.isBlank() && info.description.isBlank() && info.text.length < 80)
            return@withContext Result.failure(IllegalStateException("Não consegui ler o conteúdo dessa página (pode exigir login ou bloquear a leitura)."))
        val prompt = "Resuma em 2 a 3 frases curtas, em português do Brasil, do que se trata esta página.\nURL: $url\nTítulo: ${info.title}\nDescrição: ${info.description}\nTexto: ${info.text}"
        var last: Throwable? = null
        for (name in MODELS) {
            try {
                val t = withTimeout(20_000) { model(name).generateContent(prompt).text }?.trim()?.replace("**", "")
                if (!t.isNullOrEmpty()) return@withContext Result.success(t)
            } catch (e: TimeoutCancellationException) { last = e } catch (e: CancellationException) { throw e } catch (e: Exception) { last = e }
        }
        val msg = last?.message.orEmpty()
        Result.failure(IllegalStateException(when {
            last is TimeoutCancellationException -> "A IA demorou demais para responder. Tente de novo."
            "429" in msg || "quota" in msg.lowercase() -> "O limite gratuito da IA foi atingido. Tente mais tarde."
            "PERMISSION_DENIED" in msg || "has not been used" in msg || "403" in msg -> "A IA ainda não está habilitada para este app."
            else -> "Não foi possível gerar o resumo agora."
        }))
    }
}
