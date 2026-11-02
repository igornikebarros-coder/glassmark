package com.glassmark.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.ui.draw.blur
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.room.Room
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var db: GlassmarkDatabase
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Room.databaseBuilder(applicationContext, GlassmarkDatabase::class.java, "glassmark.db").build()
        handleShare(intent)
        setContent { GlassmarkScreen(db.linkDao()) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleShare(intent) }
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

private enum class GlassTheme { LIGHT, DARK }
private enum class TextScale(val multiplier: Float, val label: String) {
    SMALL(.88f, "Pequena"), DEFAULT(1f, "Padrão"), LARGE(1.15f, "Grande"), EXTRA_LARGE(1.3f, "Extra grande")
}

@Composable
private fun GlassmarkScreen(dao: LinkDao) {
    val links by dao.observeAll().collectAsState(initial = emptyList())
    var search by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var manualUrl by remember { mutableStateOf("") }
    var manualTitle by remember { mutableStateOf("") }
    var collection by remember { mutableStateOf("Inbox") }
    var collectionFilter by remember { mutableStateOf("Todos") }
    var theme by remember { mutableStateOf(GlassTheme.DARK) }
    var scale by remember { mutableStateOf(TextScale.DEFAULT) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val dark = theme == GlassTheme.DARK
    val canvas = if (dark) Color(0xFF080A10) else Color(0xFFF4F6FA)
    val primaryText = if (dark) Color(0xFFF5F6FC) else Color(0xFF171A24)
    val muted = if (dark) Color(0xFF9DA7BC) else Color(0xFF737B8C)
    val glass = if (dark) Color(0x20FFFFFF) else Color(0xBFFFFFFF)
    // Dialog surfaces are deliberately more opaque for legibility in both themes.
    val dialogSurface = if (dark) Color(0xF51B1D25) else Color(0xF5F7F8FC)
    val accent = if (dark) Color(0xFFBFCBFF) else Color(0xFF5267B8)
    val font: (Int) -> TextUnit = { (it * scale.multiplier).sp }
    val collections = listOf("Todos", "Inbox", "Favoritos") + links.map { it.collection }.distinct().filter { it !in listOf("Inbox") }
    val filtered = links.filter {
        (collectionFilter == "Todos" || (collectionFilter == "Favoritos" && it.isFavorite) || it.collection == collectionFilter) &&
        (it.url.contains(search, true) || it.title.contains(search, true))
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme(background = canvas, surface = canvas, primary = accent, onSurface = primaryText)
        else lightColorScheme(background = canvas, surface = canvas, primary = accent, onSurface = primaryText)) {
        Box(Modifier.fillMaxSize().blur(if (showAdd || showSettings) 16.dp else 0.dp).background(Brush.verticalGradient(if (dark)
            listOf(Color(0xFF20283B), canvas, Color(0xFF050609)) else listOf(Color(0xFFFFFFFF), Color(0xFFE8EDF7), canvas)))) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("GLASSMARK", color = muted, fontSize = font(11), fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text("Sua biblioteca", color = primaryText, fontSize = font(28), fontWeight = FontWeight.SemiBold)
                        Text("Links e ideias, em um só lugar", color = muted, fontSize = font(13))
                    }
                    GlassTile(glass) { Icon(Icons.Default.Bookmark, contentDescription = "Glassmark", tint = primaryText, modifier = Modifier.padding(14.dp)) }
                    Spacer(Modifier.width(8.dp))
                    GlassTile(glass) { Icon(Icons.Default.Settings, contentDescription = "Configurações", tint = primaryText, modifier = Modifier.clickable { showSettings = true }.padding(14.dp)) }
                }
                Spacer(Modifier.height(18.dp))
                GlassPanel(glass, RoundedCornerShape(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar", tint = muted)
                        TextField(value = search, onValueChange = { search = it }, placeholder = { Text("Buscar links...", color = muted, fontSize = font(14)) },
                            modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = primaryText, unfocusedTextColor = primaryText), singleLine = true)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("COLEÇÕES", color = muted, fontSize = font(11), fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    collections.distinct().take(4).forEach { item ->
                        GlassPanel(glass, RoundedCornerShape(16.dp), Modifier.weight(1f).clickable { collectionFilter = item }) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(if (item == "Favoritos") Icons.Default.Favorite else Icons.Default.Folder, contentDescription = item, tint = if (collectionFilter == item) accent else muted)
                                Text(item, color = primaryText, fontSize = font(10), maxLines = 1)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (collectionFilter == "Todos") "Salvos recentemente" else collectionFilter, color = primaryText, fontSize = font(17), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${filtered.size} itens", color = muted, fontSize = font(12))
                }
                Spacer(Modifier.height(10.dp))
                if (filtered.isEmpty()) {
                    GlassPanel(glass, RoundedCornerShape(26.dp), Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Bookmark, contentDescription = null, tint = muted, modifier = Modifier.size(34.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Sua biblioteca está vazia",
                                color = primaryText,
                                fontSize = font(16),
                                fontWeight = FontWeight.Medium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Compartilhe um link com o Glassmark para salvá-lo.",
                                color = muted,
                                fontSize = font(13),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(filtered, key = { it.id }) { link ->
                            GlassPanel(glass, RoundedCornerShape(22.dp), Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(15.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(link.title.ifBlank { Uri.parse(link.url).host ?: link.url }, color = primaryText, fontSize = font(16), fontWeight = FontWeight.SemiBold, maxLines = 2)
                                            Text(link.collection, color = muted, fontSize = font(11))
                                        }
                                        Icon(Icons.Default.Favorite, contentDescription = "Alternar favorito", tint = if (link.isFavorite) Color(0xFFFF729A) else muted,
                                            modifier = Modifier.clickable { scope.launch { dao.toggleFavorite(link.id) } }.padding(8.dp))
                                    }
                                    Spacer(Modifier.height(7.dp))
                                    Text(link.url, color = accent, fontSize = font(12), maxLines = 2,
                                        modifier = Modifier.clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url))) } })
                                }
                            }
                        }
                    }
                }
            }
            FloatingActionButton(onClick = { showAdd = true }, containerColor = glass, contentColor = accent,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(22.dp), shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar link")
            }
        }
        if (showAdd) {
            AlertDialog(onDismissRequest = { showAdd = false }, containerColor = dialogSurface,
                title = { Text("Salvar link", color = primaryText) },
                text = { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = manualUrl, onValueChange = { manualUrl = it }, label = { Text("URL") }, placeholder = { Text("https://...") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = manualTitle, onValueChange = { manualTitle = it }, label = { Text("Título (opcional)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = collection, onValueChange = { collection = it }, label = { Text("Coleção") }, modifier = Modifier.fillMaxWidth())
                } },
                confirmButton = { TextButton(onClick = {
                    val url = manualUrl.trim()
                    if (url.startsWith("http://") || url.startsWith("https://")) scope.launch {
                        dao.insert(LinkEntity(url = url, title = manualTitle.trim(), collection = collection.trim().ifBlank { "Inbox" }))
                        manualUrl = ""; manualTitle = ""; showAdd = false
                    }
                }) { Text("Salvar") } },
                dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancelar") } })
        }
        if (showSettings) {
            AlertDialog(onDismissRequest = { showSettings = false }, containerColor = dialogSurface,
                title = { Text("Personalização", color = primaryText) },
                text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Aparência", color = primaryText, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { theme = GlassTheme.LIGHT }) { Text("Modo claro") }
                        TextButton(onClick = { theme = GlassTheme.DARK }) { Text("Modo escuro") }
                    }
                    Text("Tamanho da fonte", color = primaryText, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    TextScale.entries.forEach { option -> TextButton(onClick = { scale = option }) {
                        Text((if (scale == option) "✓ " else "") + option.label, color = primaryText, fontSize = (14 * option.multiplier).sp)
                    } }
                    Text("Login Google e sincronização em nuvem ainda requerem configuração de Firebase/OAuth.", color = muted, fontSize = font(12), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                } },
                confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Fechar") } })
        }
    }
}

@Composable
private fun GlassPanel(color: Color, shape: RoundedCornerShape, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = color, shape = shape, modifier = modifier, tonalElevation = 0.dp, shadowElevation = 0.dp) { Column(content = content) }
}
@Composable
private fun GlassTile(color: Color, content: @Composable BoxScope.() -> Unit) {
    Surface(color = color, shape = RoundedCornerShape(18.dp), tonalElevation = 0.dp) { Box(content = content) }
}
