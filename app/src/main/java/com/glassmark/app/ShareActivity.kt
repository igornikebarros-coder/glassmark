package com.glassmark.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Recebe links compartilhados: salva na hora (título e prévia em segundo plano) e oferece escolher a aba, sem abrir o app. */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val url = Regex("https?://\\S+").find(text)?.value
        if (url.isNullOrBlank()) { Toast.makeText(this, "Nenhum link encontrado", Toast.LENGTH_SHORT).show(); finish(); return }
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        val uid = currentUid(this)
        val db = AppDb.get(this)
        val repo = Repository(db)
        val prefs = prefsOf(this)
        val mode = ThemeMode.fromKey(prefs.getString("theme_mode", null))
        val amoled = prefs.getBoolean("amoled_in_dark", false)
        val font = AppFont.fromKey(prefs.getString("app_font", null))
        setContent {
            GlassmarkTheme(mode, amoled, font) {
                val p = LocalPalette.current
                var dup by remember { mutableStateOf<LinkEntity?>(null) }
                var saved by remember { mutableStateOf<LinkEntity?>(null) }
                var cols by remember { mutableStateOf(listOf("Inbox")) }
                var chosen by remember { mutableStateOf("Inbox") }
                var checked by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                fun save() = scope.launch {
                    val l = repo.addLink(uid, url, subject, "Inbox") {}
                    saved = l
                    appScope.launch {
                        val info = PageFetcher.fetch(url)
                        if (info != null) {
                            if (subject.isBlank() && info.title.isNotBlank()) repo.setField(uid, l.docId, "title", info.title) {}
                            repo.setField(uid, l.docId, "ogImage", info.ogImage ?: "-") {}
                        }
                        LinkWidget.refresh(applicationContext)
                    }
                }
                LaunchedEffect(Unit) {
                    val all = db.linkDao().all(uid)
                    cols = (listOf("Inbox") + db.collectionDao().all(uid).map { it.name } + all.map { it.collection }).distinct()
                    val n = normalizeUrl(url)
                    dup = all.firstOrNull { normalizeUrl(it.url) == n }
                    checked = true
                    if (dup == null) save()
                }
                LaunchedEffect(saved, chosen) { if (saved != null) { delay(4500); finish() } }
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .35f)).clickable { finish() }, contentAlignment = Alignment.BottomCenter) {
                    if (checked) Surface(color = p.dialog, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp), modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}) {
                        Column(Modifier.navigationBarsPadding().padding(20.dp)) {
                            val d = dup
                            if (d != null && saved == null) {
                                Text("Esse link já está salvo", color = p.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                                Text(d.title.ifBlank { domainOf(d.url) }, color = p.muted, fontSize = 13.sp, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
                                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { save() }) { Text("Salvar mesmo assim") }
                                    Button(onClick = { openUrl(this@ShareActivity, d.url); finish() }, colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = onAccentOf(p))) { Text("Abrir") }
                                }
                            } else {
                                Text("Link salvo", color = p.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                                Text("Escolha a aba:", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(cols) { c ->
                                        val sel = c == chosen
                                        Text(c, color = if (sel) onAccentOf(p) else p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (sel) p.accent else p.glass)
                                                .clickable {
                                                    chosen = c
                                                    saved?.let { l -> scope.launch { repo.setField(uid, l.docId, "collection", c) {}; if (uid.isNotEmpty()) repo.addCollection(uid, c) {} } }
                                                }.padding(horizontal = 14.dp, vertical = 8.dp))
                                    }
                                }
                                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { finish() }) { Text("Pronto") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
