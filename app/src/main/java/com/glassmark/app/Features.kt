package com.glassmark.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Escopo do processo para tarefas que sobrevivem à tela (compartilhar, widget, importação). */
val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

fun prefsOf(ctx: Context) = ctx.getSharedPreferences("glassmark", Context.MODE_PRIVATE)
fun currentUid(ctx: Context): String = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

/** Abre no navegador do app (Custom Tabs) ou no navegador externo, conforme o ajuste. */
fun openUrl(ctx: Context, url: String) {
    val uri = Uri.parse(url)
    val inApp = prefsOf(ctx).getBoolean("in_app_browser", true)
    runCatching {
        if (inApp) CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(ctx, uri)
        else ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

// ---------- Lembrete "ler depois" ----------
object Reminders {
    private const val CH = "read_later"
    fun schedule(ctx: Context, link: LinkEntity, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx, link)
        am.cancel(pi)
        if (at <= System.currentTimeMillis()) return
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }
    fun cancel(ctx: Context, link: LinkEntity) = ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, link))
    private fun pending(ctx: Context, link: LinkEntity): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).putExtra("docId", link.docId).putExtra("url", link.url).putExtra("title", link.title)
        return PendingIntent.getBroadcast(ctx, link.docId.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    fun notify(ctx: Context, docId: String, url: String, title: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CH, "Ler depois", NotificationManager.IMPORTANCE_DEFAULT))
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(ctx, docId.hashCode(), Intent(Intent.ACTION_VIEW, Uri.parse(url)), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CH).setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Hora de ler").setContentText(title.ifBlank { domainOf(url) })
            .setContentIntent(open).setAutoCancel(true).build()
        nm.notify(docId.hashCode(), n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val r = goAsync()
            appScope.launch {
                runCatching { AppDb.get(ctx).linkDao().all(currentUid(ctx)).filter { it.remindAt > System.currentTimeMillis() }.forEach { Reminders.schedule(ctx, it, it.remindAt) } }
                r.finish()
            }
            return
        }
        Reminders.notify(ctx, intent.getStringExtra("docId").orEmpty(), intent.getStringExtra("url").orEmpty(), intent.getStringExtra("title").orEmpty())
    }
}

// ---------- Backup ----------
object Backup {
    fun toJson(links: List<LinkEntity>): String = JSONArray().apply {
        links.forEach { l ->
            put(JSONObject().apply {
                put("url", l.url); put("title", l.title); put("collection", l.collection); put("savedAt", l.savedAt)
                put("isFavorite", l.isFavorite); put("hidden", l.hidden); put("summary", l.summary); put("tags", l.tags); put("isRead", l.isRead)
            })
        }
    }.toString(2)

    fun fromJson(s: String, uid: String): List<LinkEntity> {
        val arr = runCatching { JSONArray(s) }.getOrElse { JSONObject(s).getJSONArray("links") }
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("url").takeIf { it.startsWith("http") } ?: return@mapNotNull null
            LinkEntity(url = url, title = o.optString("title"), collection = o.optString("collection").ifBlank { "Inbox" },
                savedAt = o.optLong("savedAt", System.currentTimeMillis()), isFavorite = o.optBoolean("isFavorite"), hidden = o.optBoolean("hidden"),
                summary = o.optString("summary"), tags = o.optString("tags"), isRead = o.optBoolean("isRead"), ownerUid = uid)
        }
    }

    /** Favoritos do Chrome/Netscape (HTML): pastas viram coleções. */
    fun fromBookmarksHtml(html: String, uid: String): List<LinkEntity> {
        val out = ArrayList<LinkEntity>()
        val stack = ArrayList<String>()
        var pendingFolder: String? = null
        val tok = Regex("(?is)<h3[^>]*>(.*?)</h3>|<dl>|</dl>|<a\\s+([^>]*)>(.*?)</a>")
        fun unesc(t: String) = android.text.Html.fromHtml(t, 0).toString().trim()
        for (m in tok.findAll(html)) {
            val v = m.value.lowercase()
            when {
                v.startsWith("<h3") -> pendingFolder = unesc(m.groupValues[1])
                v == "<dl>" -> { stack += pendingFolder ?: ""; pendingFolder = null }
                v == "</dl>" -> if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                else -> {
                    val attrs = m.groupValues[2]
                    val href = Regex("(?i)href=\"([^\"]+)\"").find(attrs)?.groupValues?.get(1) ?: continue
                    if (!href.startsWith("http")) continue
                    val added = Regex("(?i)add_date=\"(\\d+)\"").find(attrs)?.groupValues?.get(1)?.toLongOrNull()?.let { it * 1000 }
                    val folder = stack.lastOrNull { it.isNotBlank() && it.lowercase() !in setOf("barra de favoritos", "bookmarks bar", "favoritos", "bookmarks", "outros favoritos", "other bookmarks") }
                    out += LinkEntity(url = unesc(href), title = unesc(m.groupValues[3]), collection = folder ?: "Inbox", savedAt = added ?: System.currentTimeMillis(), ownerUid = uid)
                }
            }
        }
        return out
    }
}

// ---------- Bloqueio biométrico ----------
fun biometricUnlock(ctx: Context, title: String, onOk: () -> Unit) {
    val act = ctx as? androidx.fragment.app.FragmentActivity ?: run { onOk(); return }
    val B = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
    val D = androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val auths = if (Build.VERSION.SDK_INT >= 30) B or D else B
    if (androidx.biometric.BiometricManager.from(ctx).canAuthenticate(auths) != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
        android.widget.Toast.makeText(ctx, "Biometria indisponível neste aparelho", android.widget.Toast.LENGTH_SHORT).show(); onOk(); return
    }
    val prompt = androidx.biometric.BiometricPrompt(act, ContextCompat.getMainExecutor(ctx), object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) { onOk() }
    })
    val info = androidx.biometric.BiometricPrompt.PromptInfo.Builder().setTitle(title).setAllowedAuthenticators(auths)
        .apply { if (Build.VERSION.SDK_INT < 30) setNegativeButtonText("Cancelar") }.build()
    prompt.authenticate(info)
}
