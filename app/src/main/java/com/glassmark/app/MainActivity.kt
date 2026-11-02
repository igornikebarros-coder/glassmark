package com.glassmark.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.room.Room
import kotlinx.coroutines.launch

private val Ink = Color(0xFF090B12)
private val Glass = Color(0x24FFFFFF)
private val TextMain = Color(0xFFF4F5FA)
private val Muted = Color(0xFF9CA3B5)

class MainActivity : ComponentActivity() {
    private lateinit var db: GlassmarkDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Room.databaseBuilder(applicationContext, GlassmarkDatabase::class.java, "glassmark.db").build()
        handleShare(intent)
        setContent { GlassmarkScreen(db.linkDao()) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            val url = Regex("https?://\\S+").find(text)?.value
            if (!url.isNullOrBlank()) {
                val title = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
                kotlinx.coroutines.MainScope().launch { db.linkDao().insert(LinkEntity(url = url, title = title)) }
            }
        }
    }
}

@Composable
private fun GlassmarkScreen(dao: LinkDao) {
    val links by dao.observeAll().collectAsState(initial = emptyList())
    var search by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var manualUrl by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val filtered = links.filter { it.url.contains(search, true) || it.title.contains(search, true) }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Ink, surface = Color(0xFF111521), primary = Color(0xFFB9C7FF),
        onBackground = TextMain, onSurface = TextMain
    )) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF20283B), Ink, Color(0xFF050609))))) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("GLASSMARK", color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("Sua biblioteca", color = TextMain, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Surface(color = Glass, shape = RoundedCornerShape(18.dp)) {
                        Icon(Icons.Default.Bookmark, contentDescription = null, tint = TextMain, modifier = Modifier.padding(14.dp))
                    }
                }
                Spacer(Modifier.height(20.dp))
                Surface(color = Glass, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp)) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar", tint = Muted)
                        Spacer(Modifier.width(10.dp))
                        TextField(value = search, onValueChange = { search = it }, placeholder = { Text("Buscar links...", color = Muted) },
                            modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = TextMain, unfocusedTextColor = TextMain
                            ), singleLine = true)
                    }
                }
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Salvos recentemente", color = TextMain, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${filtered.size} itens", color = Muted, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) {
                    Surface(color = Glass, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Bookmark, contentDescription = null, tint = Muted, modifier = Modifier.size(34.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Sua biblioteca está vazia", color = TextMain, fontWeight = FontWeight.Medium)
                            Text("Compartilhe um link com o Glassmark para salvá-lo.", color = Muted)
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(filtered, key = { it.id }) { link ->
                            Surface(color = Glass, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(link.title.ifBlank { Uri.parse(link.url).host ?: link.url }, color = TextMain, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                            Text(link.collection, color = Muted, style = MaterialTheme.typography.labelSmall)
                                        }
                                        Icon(Icons.Default.Favorite, contentDescription = "Favorito", tint = if (link.isFavorite) Color(0xFFFF8BA7) else Muted,
                                            modifier = Modifier.clickable { scope.launch { dao.toggleFavorite(link.id) } }.padding(8.dp))
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(link.url, color = Color(0xFFB9C7FF), style = MaterialTheme.typography.bodySmall, maxLines = 2,
                                        modifier = Modifier.clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url))) } })
                                }
                            }
                        }
                    }
                }
            }
            FloatingActionButton(onClick = { showAdd = true }, containerColor = Color(0xFFCFD7FF), contentColor = Ink,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(22.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar link")
            }
        }
        if (showAdd) {
            AlertDialog(onDismissRequest = { showAdd = false }, title = { Text("Salvar link") },
                text = { TextField(value = manualUrl, onValueChange = { manualUrl = it }, placeholder = { Text("https://...") }) },
                confirmButton = { TextButton(onClick = {
                    val url = manualUrl.trim()
                    if (url.startsWith("http")) scope.launch { dao.insert(LinkEntity(url = url)); manualUrl = ""; showAdd = false }
                }) { Text("Salvar") } },
                dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancelar") } })
        }
    }
}
