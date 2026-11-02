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
import androidx.compose.foundation.lazy.LazyRow
import androidx.activity.viewModels
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
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

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            val user by vm.user.collectAsState()
    val customAvatarUri by vm.customAvatarUri.collectAsState()
            val guest by vm.guest.collectAsState()
            if (user == null && !guest) GlassmarkTheme { AuthScreen(vm) } else GlassmarkScreen(vm)
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleShare(intent) }
    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            val url = Regex("https?://\\S+").find(text)?.value
            if (!url.isNullOrBlank()) vm.addLink(url, intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty(), "Inbox")
        }
    }
}

private enum class GlassTheme { LIGHT, DARK }
private enum class TextScale(val multiplier: Float, val label: String) {
    SMALL(.88f, "Pequena"), DEFAULT(1f, "Padrão"), LARGE(1.15f, "Grande"), EXTRA_LARGE(1.3f, "Extra grande")
}

@Composable
private fun GlassmarkScreen(vm: AppViewModel) {
    val links by vm.links.collectAsState()
    val allCollections by vm.collections.collectAsState()
    val user by vm.user.collectAsState()
    val syncError by vm.syncError.collectAsState()
    val offerUpload by vm.offerUpload.collectAsState()
    val guestCount by vm.guestCount.collectAsState()
    var showNewCollection by remember { mutableStateOf(false) }
    var newCollection by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var manualUrl by remember { mutableStateOf("") }
    var manualTitle by remember { mutableStateOf("") }
    var collection by remember { mutableStateOf("Inbox") }
    var collectionFilter by remember { mutableStateOf("Todos") }
    var theme by remember { mutableStateOf(GlassTheme.DARK) }
    var scale by remember { mutableStateOf(TextScale.DEFAULT) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.saveCustomAvatar(uri.toString())
        }
    }
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = when (hour) { in 5..11 -> "Bom dia"; in 12..17 -> "Boa tarde"; else -> "Boa noite" }
    val firstName = user?.displayName?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotBlank() }
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
    val collections = listOf("Todos", "Favoritos") + allCollections
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
                    GlassTile(glass) {
                        Box(Modifier.size(52.dp).clickable { showSettings = true }, contentAlignment = Alignment.Center) {
                            val avatar = customAvatarUri ?: user?.photoUrl?.toString()
                            if (!avatar.isNullOrBlank()) AsyncImage(model = avatar, contentDescription = "Foto do perfil", modifier = Modifier.fillMaxSize().padding(4.dp).clip(CircleShape))
                            else Icon(Icons.Default.AccountCircle, contentDescription = "Conta", tint = if (user != null) accent else primaryText, modifier = Modifier.padding(14.dp))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    GlassTile(glass) { Icon(Icons.Default.Settings, contentDescription = "Configurações", tint = primaryText, modifier = Modifier.clickable { showSettings = true }.padding(14.dp)) }
                }
                Spacer(Modifier.height(12.dp))
                if (user == null) {
                    GlassPanel(glass, RoundedCornerShape(18.dp), Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 14.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Modo visitante: seus links ficam só neste aparelho.", color = primaryText, fontSize = font(12), modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.goToLogin() }) { Text("Entrar") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (syncError != null) {
                    GlassPanel(Color(0x33FF729A), RoundedCornerShape(18.dp), Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 14.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Não foi possível sincronizar com a nuvem. Verifique a conexão ou as regras do Firestore.", color = primaryText, fontSize = font(12), modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.syncError.value = null }) { Text("OK") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(6.dp))
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
                if (collectionFilter != "Todos" && collectionFilter !in collections) collectionFilter = "Todos"
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(collections) { item ->
                        GlassPanel(glass, RoundedCornerShape(16.dp), Modifier.width(84.dp).clickable { collectionFilter = item }) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(if (item == "Favoritos") Icons.Default.Favorite else Icons.Default.Folder, contentDescription = item, tint = if (collectionFilter == item) accent else muted)
                                Text(item, color = primaryText, fontSize = font(10), maxLines = 1)
                            }
                        }
                    }
                    item {
                        GlassPanel(glass, RoundedCornerShape(16.dp), Modifier.width(84.dp).clickable { showNewCollection = true }) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Add, contentDescription = "Nova coleção", tint = muted)
                                Text("Nova", color = primaryText, fontSize = font(10), maxLines = 1)
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
                        items(filtered, key = { it.docId }) { link ->
                            GlassPanel(glass, RoundedCornerShape(22.dp), Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(15.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(link.title.ifBlank { Uri.parse(link.url).host ?: link.url }, color = primaryText, fontSize = font(16), fontWeight = FontWeight.SemiBold, maxLines = 2)
                                            Text(link.collection, color = muted, fontSize = font(11))
                                        }
                                        Icon(Icons.Default.Favorite, contentDescription = "Alternar favorito", tint = if (link.isFavorite) Color(0xFFFF729A) else muted,
                                            modifier = Modifier.clickable { vm.toggleFavorite(link) }.padding(8.dp))
                                        Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = muted,
                                            modifier = Modifier.clickable { vm.deleteLink(link) }.padding(8.dp))
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
            FloatingActionButton(onClick = { collection = if (collectionFilter in allCollections) collectionFilter else "Inbox"; showAdd = true }, containerColor = glass, contentColor = accent,
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
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        vm.addLink(url, manualTitle.trim(), collection.trim().ifBlank { "Inbox" })
                        manualUrl = ""; manualTitle = ""; showAdd = false
                    }
                }) { Text("Salvar") } },
                dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancelar") } })
        }
        if (showNewCollection) {
            AlertDialog(onDismissRequest = { showNewCollection = false }, containerColor = dialogSurface,
                title = { Text("Nova coleção", color = primaryText) },
                text = { OutlinedTextField(value = newCollection, onValueChange = { newCollection = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
                confirmButton = { TextButton(onClick = {
                    val n = newCollection.trim()
                    if (n.isNotEmpty()) { vm.addCollection(n); collectionFilter = n; newCollection = ""; showNewCollection = false }
                }) { Text("Criar") } },
                dismissButton = { TextButton(onClick = { showNewCollection = false }) { Text("Cancelar") } })
        }
        if (offerUpload && user != null) {
            AlertDialog(onDismissRequest = { vm.dismissUpload() }, containerColor = dialogSurface,
                title = { Text("Enviar links locais?", color = primaryText) },
                text = { Text("Você tem $guestCount ${if (guestCount == 1) "link salvo" else "links salvos"} como visitante neste aparelho. Quer enviar para a sua conta?", color = primaryText) },
                confirmButton = { TextButton(onClick = { vm.uploadGuest() }) { Text("Enviar para a conta") } },
                dismissButton = { TextButton(onClick = { vm.dismissUpload() }) { Text("Agora não") } })
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
                    Text("Conta", color = primaryText, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { avatarPicker.launch(arrayOf("image/*")) }) { Text("Escolher foto de perfil") }
                    if (customAvatarUri != null) TextButton(onClick = { vm.saveCustomAvatar(null) }) { Text("Usar foto do Google") }
                    val u = user
                    if (u != null) {
                        Text(u.displayName?.takeIf { it.isNotBlank() } ?: "Conta Glassmark", color = primaryText, fontSize = font(14))
                        Text(u.email.orEmpty(), color = muted, fontSize = font(12))
                        TextButton(onClick = { showSettings = false; vm.logout() }) { Text("Sair da conta") }
                    } else {
                        Text("Modo visitante: links apenas neste aparelho.", color = muted, fontSize = font(12), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        TextButton(onClick = { showSettings = false; vm.goToLogin() }) { Text("Entrar ou criar conta") }
                    }
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
