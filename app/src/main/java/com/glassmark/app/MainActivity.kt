package com.glassmark.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
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
            val guest by vm.guest.collectAsState()
            val mode by vm.themeMode.collectAsState()
            GlassmarkTheme(mode) {
                if (user == null && !guest) AuthScreen(vm) else GlassmarkScreen(vm)
            }
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

@Composable
private fun GlassmarkScreen(vm: AppViewModel) {
    val links by vm.links.collectAsState()
    val allCollections by vm.collections.collectAsState()
    val user by vm.user.collectAsState()
    val customAvatarUri by vm.customAvatarUri.collectAsState()
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
    val theme by vm.themeMode.collectAsState()
    val scale by vm.textScale.collectAsState()
    val context = LocalContext.current
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.saveCustomAvatar(uri.toString())
        }
    }
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = when (hour) { in 5..11 -> "Bom dia"; in 12..17 -> "Boa tarde"; else -> "Boa noite" }
    val firstName = user?.displayName?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotBlank() }
    val p = LocalPalette.current
    val canvas = p.canvas
    val primaryText = p.text
    val muted = p.muted
    val glass = p.glass
    // Diálogos usam superfícies opacas para manter a legibilidade em todos os temas.
    val dialogSurface = p.dialog
    val accent = p.accent
    val font: (Int) -> TextUnit = { (it * scale.multiplier).sp }
    val collections = listOf("Todos", "Favoritos") + allCollections
    val filtered = links.filter {
        (collectionFilter == "Todos" || (collectionFilter == "Favoritos" && it.isFavorite) || it.collection == collectionFilter) &&
        (it.url.contains(search, true) || it.title.contains(search, true))
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().blur(if (showAdd || showSettings) 16.dp else 0.dp).background(Brush.verticalGradient(p.gradient))) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("$greeting${firstName?.let { ", $it" } ?: ""} 👋", color = primaryText, fontSize = font(28), fontWeight = FontWeight.SemiBold)
                        Text("O que vamos guardar hoje?", color = muted, fontSize = font(13))
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
                                "Compartilhe um link com o Link para salvá-lo.",
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
            SettingsDialog(
                p = p, theme = theme, scale = scale, user = user, hasCustomAvatar = customAvatarUri != null,
                onTheme = vm::setThemeMode, onScale = vm::setTextScale,
                onPickAvatar = { avatarPicker.launch(arrayOf("image/*")) },
                onResetAvatar = { vm.saveCustomAvatar(null) },
                onLogin = { showSettings = false; vm.goToLogin() },
                onLogout = { showSettings = false; vm.logout() },
                onClose = { showSettings = false }
            )
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

// ---------- Configurações ----------

@Composable
private fun SettingsDialog(
    p: Palette, theme: ThemeMode, scale: TextScale, user: com.google.firebase.auth.FirebaseUser?, hasCustomAvatar: Boolean,
    onTheme: (ThemeMode) -> Unit, onScale: (TextScale) -> Unit,
    onPickAvatar: () -> Unit, onResetAvatar: () -> Unit, onLogin: () -> Unit, onLogout: () -> Unit, onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose) {
        Surface(
            color = p.dialog, shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, p.stroke), tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp)) {
                Text(
                    "Configurações", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(Modifier.height(8.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                    SectionHeader("APARÊNCIA", p)
                    Text("Tema", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                    ThemeMode.entries.forEach { m ->
                        SettingRow(label = m.label, p = p, onClick = { onTheme(m) }) { RadioButton(selected = theme == m, onClick = null, colors = radioColors(p)) }
                    }
                    Text("Tamanho da fonte", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
                    TextScale.entries.forEach { sc ->
                        SettingRow(label = sc.label, p = p, onClick = { onScale(sc) }) { RadioButton(selected = scale == sc, onClick = null, colors = radioColors(p)) }
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = p.divider)
                    SectionHeader("CONTA", p)
                    if (user != null) {
                        SettingRow(
                            label = user.displayName?.takeIf { it.isNotBlank() } ?: "Conta Link",
                            subtitle = user.email, p = p
                        ) {}
                    } else {
                        SettingRow(label = "Modo visitante", subtitle = "Links apenas neste aparelho", p = p) {
                            TextButton(onClick = onLogin) { Text("Entrar") }
                        }
                    }
                    SettingRow(label = "Foto de perfil", p = p) { TextButton(onClick = onPickAvatar) { Text("Escolher") } }
                    if (hasCustomAvatar) {
                        SettingRow(label = "Usar foto do Google", p = p) { TextButton(onClick = onResetAvatar) { Text("Restaurar") } }
                    }
                    if (user != null) {
                        SettingRow(label = "Sair da conta", p = p) { TextButton(onClick = onLogout) { Text("Sair", color = p.error) } }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = p.divider)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text("Fechar") }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, p: Palette) {
    Text(
        text, color = p.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

/** Linha padrão: rótulo à esquerda, controle alinhado ao final, altura mínima uniforme. */
@Composable
private fun SettingRow(
    label: String, p: Palette, subtitle: String? = null, onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(label, color = p.text, fontSize = 15.sp, maxLines = 2)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun radioColors(p: Palette) = RadioButtonDefaults.colors(selectedColor = p.accent, unselectedColor = p.muted)
