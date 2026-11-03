@file:OptIn(ExperimentalFoundationApi::class)

package com.glassmark.app

import android.content.Intent
import android.content.Context
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.layout.RowScope
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.draw.rotate
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
            val amoledDark by vm.amoledInDark.collectAsState()
            val font by vm.appFont.collectAsState()
            GlassmarkTheme(mode, amoledDark, font) {
                AnimatedContent(
                    targetState = user == null && !guest,
                    transitionSpec = { (fadeIn(tween(300)) + scaleIn(initialScale = 0.96f, animationSpec = tween(300))) togetherWith fadeOut(tween(180)) },
                    label = "nav"
                ) { showAuth -> if (showAuth) AuthScreen(vm) else GlassmarkScreen(vm) }
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
    var chipMenu by remember { mutableStateOf<String?>(null) }
    var headerMenu by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var editLinkTarget by remember { mutableStateOf<LinkEntity?>(null) }
    var editUrl by remember { mutableStateOf("") }
    var editTitle by remember { mutableStateOf("") }
    var editCollection by remember { mutableStateOf("") }
    var deleteLinkTarget by remember { mutableStateOf<LinkEntity?>(null) }
    var manualUrl by remember { mutableStateOf("") }
    var manualTitle by remember { mutableStateOf("") }
    var collection by remember { mutableStateOf("Inbox") }
    var collectionFilter by remember { mutableStateOf("Todos") }
    val theme by vm.themeMode.collectAsState()
    val scale by vm.textScale.collectAsState()
    val amoledDark by vm.amoledInDark.collectAsState()
    val appFont by vm.appFont.collectAsState()
    val gridView by vm.gridView.collectAsState()
    val cardSize by vm.cardSize.collectAsState()
    val hiddenCols by vm.hiddenCollections.collectAsState()
    val summarizing by vm.summarizing.collectAsState()
    val aiMsg by vm.aiMessage.collectAsState()
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
    val collections = listOf("Todos", "Favoritos") + allCollections.filter { it !in hiddenCols } + (if (collectionFilter == "Ocultos") listOf("Ocultos") else emptyList())
    val snack = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()
    val searchFocus = remember { FocusRequester() }
    var opening by remember { mutableStateOf<String?>(null) }
    val openFade by animateFloatAsState(if (opening != null) 1f else 0f, tween(220), label = "openFade")
    val deleteWithUndo: (LinkEntity) -> Unit = { l ->
        vm.deleteLink(l)
        uiScope.launch {
            snack.currentSnackbarData?.dismiss()
            if (snack.showSnackbar("Link excluído", actionLabel = "Desfazer", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.updateLink(l)
        }
    }
    val hideWithUndo: (LinkEntity) -> Unit = { l ->
        val toHide = !l.hidden
        vm.setLinkHidden(l, toHide)
        uiScope.launch {
            snack.currentSnackbarData?.dismiss()
            if (snack.showSnackbar(if (toHide) "Link ocultado" else "Link visível de novo", actionLabel = "Desfazer", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.setLinkHidden(l, !toHide)
        }
    }
    LaunchedEffect(aiMsg) { aiMsg?.let { snack.showSnackbar(it); vm.aiMessage.value = null } }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumeTick++ }
        lifecycleOwner.lifecycle.addObserver(o); onDispose { lifecycleOwner.lifecycle.removeObserver(o) }
    }
    var clipUrl by remember { mutableStateOf<String?>(null) }
    var clipDismissed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(resumeTick) {
        delay(400)
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val t = runCatching { cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim() }.getOrNull()
        clipUrl = t?.takeIf { Regex("^https?://\\S+$").matches(it) && it != clipDismissed }
    }
    LaunchedEffect(opening) {
        val id = opening ?: return@LaunchedEffect
        delay(240)
        links.firstOrNull { it.docId == id }?.let { l -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(l.url))) } }
        delay(600)
        opening = null
    }
    val pagerState = rememberPagerState { collections.size }
    val chipsState = rememberLazyListState()
    LaunchedEffect(collectionFilter, collections.size) {
        val idx = collections.indexOf(collectionFilter)
        if (idx >= 0) {
            if (pagerState.currentPage != idx) pagerState.animateScrollToPage(idx)
            runCatching { chipsState.animateScrollToItem(idx) }
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { pg -> collections.getOrNull(pg)?.let { if (it != collectionFilter) collectionFilter = it } }
    }
    var barVisible by remember { mutableStateOf(true) }
    val barConn = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (available.y < -6f) barVisible = false else if (available.y > 6f) barVisible = true
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }
    val builtIns = setOf("Todos", "Favoritos", "Inbox")
    val filteredFor: (String) -> List<LinkEntity> = { filter ->
        links.filter {
            (if (filter == "Ocultos") it.hidden else !it.hidden && (filter == "Todos" || (filter == "Favoritos" && it.isFavorite) || it.collection == filter)) &&
            (it.url.contains(search, true) || it.title.contains(search, true))
        }
    }
    val hiddenLinksCount = links.count { it.hidden }

    Box(Modifier.fillMaxSize()) {
        val blurRadius by animateDpAsState(if (showAdd || showSettings || renameTarget != null || deleteTarget != null || editLinkTarget != null || deleteLinkTarget != null) 16.dp else 0.dp, tween(250), label = "blur")
        Box(Modifier.fillMaxSize().blur(blurRadius).background(Brush.verticalGradient(p.gradient))) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp).nestedScroll(barConn)) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("$greeting${firstName?.let { ", $it" } ?: ""} 👋", color = muted, fontSize = font(13), fontWeight = FontWeight.SemiBold)
                        Text("O que vamos guardar hoje?", color = primaryText, fontSize = font(22), fontWeight = FontWeight.ExtraBold)
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
                Spacer(Modifier.height(4.dp))
                GlassSurface(p, RoundedCornerShape(26.dp), Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar", tint = muted)
                        TextField(value = search, onValueChange = { search = it }, placeholder = { Text("Buscar links, sites, coleções…", color = muted, fontSize = font(14)) },
                            modifier = Modifier.weight(1f).focusRequester(searchFocus), colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = primaryText, unfocusedTextColor = primaryText), singleLine = true)
                        if (search.isNotEmpty()) Icon(Icons.Default.Close, contentDescription = "Limpar", tint = muted, modifier = Modifier.clip(CircleShape).clickable { search = "" }.padding(6.dp))
                    }
                }
                AnimatedVisibility(visible = clipUrl != null && links.none { it.url == clipUrl }) {
                    ClipBanner(clipUrl.orEmpty(), p, font, onSave = {
                        vm.addLink(clipUrl.orEmpty(), "", if (collectionFilter in allCollections) collectionFilter else "Inbox"); clipDismissed = clipUrl; clipUrl = null
                    }, onDismiss = { clipDismissed = clipUrl; clipUrl = null })
                }
                Spacer(Modifier.height(12.dp))
                if (collectionFilter != "Todos" && collectionFilter !in collections) collectionFilter = "Todos"
                LazyRow(state = chipsState, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(collections, key = { it }) { item ->
                        val editable = item !in builtIns && item != "Ocultos"
                        val cnt = when (item) { "Todos" -> links.count { !it.hidden }; "Favoritos" -> links.count { !it.hidden && it.isFavorite }; "Ocultos" -> hiddenLinksCount; else -> links.count { !it.hidden && it.collection == item } }
                        Box(Modifier.animateItem()) {
                            GlassChip(item, cnt, collectionFilter == item, p, font, icon = if (item == "Favoritos") Icons.Default.Favorite else null,
                                onClick = { if (collectionFilter == item && editable) chipMenu = item else collectionFilter = item }, onLongClick = if (editable) ({ chipMenu = item }) else null)
                            DropdownMenu(expanded = chipMenu == item, onDismissRequest = { chipMenu = null }, containerColor = dialogSurface) {
                                DropdownMenuItem(text = { Text("Editar", color = primaryText) }, leadingIcon = { Icon(Icons.Default.Edit, null, tint = muted) },
                                    onClick = { chipMenu = null; renameText = item; renameTarget = item })
                                DropdownMenuItem(text = { Text("Ocultar", color = primaryText) }, leadingIcon = { Icon(Icons.Default.VisibilityOff, null, tint = muted) },
                                    onClick = { chipMenu = null; vm.hideCollection(item); if (collectionFilter == item) collectionFilter = "Todos" })
                                DropdownMenuItem(text = { Text("Apagar", color = p.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = p.error) },
                                    onClick = { chipMenu = null; deleteTarget = item })
                            }
                        }
                    }
                    item(key = "__new") {
                        Box(Modifier.animateItem()) { GlassChip("Nova", null, false, p, font, icon = Icons.Default.Add, onClick = { showNewCollection = true }) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                HorizontalPager(
                    state = pagerState, modifier = Modifier.weight(1f), key = { collections.getOrNull(it) ?: it }, beyondViewportPageCount = 0
                ) { page ->
                    val filter = collections.getOrNull(page) ?: "Todos"
                    val shown = filteredFor(filter)
                    val favs = if (filter == "Todos" && search.isBlank()) links.filter { it.isFavorite } else emptyList()
                    if (shown.isEmpty()) {
                        Column(Modifier.fillMaxSize().padding(bottom = 90.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(if (filter == "Favoritos") Icons.Default.Favorite else Icons.Default.Bookmark, contentDescription = null, tint = muted.copy(alpha = .8f), modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(when (filter) { "Todos" -> "Sua biblioteca está vazia"; "Favoritos" -> "Nenhum favorito ainda"; "Ocultos" -> "Nada oculto"; else -> "Nada por aqui ainda" },
                                color = primaryText, fontSize = font(15), fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(Modifier.height(4.dp))
                            Text(when (filter) { "Todos" -> "Compartilhe um link com o Link ou toque em + para salvar."; "Favoritos" -> "Toque no ♥ de um link para fixá-lo aqui."; "Ocultos" -> "Links ocultados aparecem aqui."; else -> "Salve links nesta coleção com o botão +." },
                                color = muted, fontSize = font(12), textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(if (gridView) 2 else 1), contentPadding = PaddingValues(bottom = 130.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (favs.isNotEmpty()) {
                                item(key = "__favs", span = { GridItemSpan(maxLineSpan) }) {
                                    Column {
                                        Text("Favoritos", color = primaryText, fontSize = font(15), fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 8.dp))
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            items(favs, key = { it.docId }) { l -> Box(Modifier.animateItem()) { FavCard(l, p, font) { opening = l.docId } } }
                                        }
                                    }
                                }
                            }
                            item(key = "__hdr", span = { GridItemSpan(maxLineSpan) }) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = if (favs.isNotEmpty()) 6.dp else 0.dp)) {
                                    Text(if (filter == "Todos") "Recentes" else filter, color = primaryText, fontSize = font(17), fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                                    Text("${shown.size} itens", color = muted, fontSize = font(12))
                                    Icon(if (gridView) Icons.Default.ViewAgenda else Icons.Default.GridView, contentDescription = "Alternar lista e grade", tint = accent,
                                        modifier = Modifier.clip(CircleShape).clickable { vm.setGridView(!gridView) }.padding(6.dp))
                                    if (filter !in builtIns && filter != "Ocultos") {
                                        Box {
                                            Icon(Icons.Default.MoreVert, contentDescription = "Opções da coleção", tint = muted,
                                                modifier = Modifier.clip(CircleShape).clickable { headerMenu = true }.padding(6.dp))
                                            DropdownMenu(expanded = headerMenu, onDismissRequest = { headerMenu = false }, containerColor = dialogSurface) {
                                                DropdownMenuItem(text = { Text("Editar", color = primaryText) }, leadingIcon = { Icon(Icons.Default.Edit, null, tint = muted) },
                                                    onClick = { headerMenu = false; renameText = filter; renameTarget = filter })
                                                DropdownMenuItem(text = { Text("Ocultar", color = primaryText) }, leadingIcon = { Icon(Icons.Default.VisibilityOff, null, tint = muted) },
                                                    onClick = { headerMenu = false; vm.hideCollection(filter); collectionFilter = "Todos" })
                                                DropdownMenuItem(text = { Text("Apagar", color = p.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = p.error) },
                                                    onClick = { headerMenu = false; deleteTarget = filter })
                                            }
                                        }
                                    }
                                }
                            }
                            items(shown, key = { it.docId }) { link ->
                                LaunchedEffect(link.docId) { vm.fetchOg(link) }
                                SwipeLinkItem(
                                    link, p, font, opening == link.docId, gridView, cardSize, link.docId in summarizing, filter == "Ocultos", Modifier.animateItem(),
                                    onOpen = { opening = link.docId }, onToggleFav = { vm.toggleFavorite(link) }, onDelete = { deleteWithUndo(link) },
                                    onEdit = { editLinkTarget = link; editUrl = link.url; editTitle = link.title; editCollection = link.collection },
                                    onHide = { hideWithUndo(link) }, onSummarize = { vm.summarize(link) }
                                )
                            }
                        }
                    }
                }
            }
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 92.dp, start = 16.dp, end = 16.dp))
            AnimatedVisibility(
                visible = barVisible, modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(240)) { it } + fadeIn(tween(240)), exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160))
            ) { GlassBar(
                p, font, collectionFilter, Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp),
                onHome = { collectionFilter = "Todos"; search = "" }, onFavs = { collectionFilter = "Favoritos" },
                onSearch = { runCatching { searchFocus.requestFocus() } }, onSettings = { showSettings = true }
            ) }
            val fabScale = remember { MutableTransitionState(false) }
            LaunchedEffect(barVisible) { fabScale.targetState = barVisible }
            AnimatedVisibility(
                visibleState = fabScale, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp),
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(),
                exit = scaleOut(tween(180)) + fadeOut(tween(160))
            ) {
                val fabSrc = remember { MutableInteractionSource() }
                val rot by animateFloatAsState(if (showAdd) 45f else 0f, spring(stiffness = Spring.StiffnessMediumLow), label = "fab")
                FloatingActionButton(onClick = { collection = if (collectionFilter in allCollections) collectionFilter else "Inbox"; showAdd = true },
                    containerColor = accent, contentColor = onAccentOf(p), interactionSource = fabSrc,
                    modifier = Modifier.pressScale(fabSrc, 0.92f), shape = RoundedCornerShape(22.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Adicionar link", modifier = Modifier.rotate(rot))
                }
            }
        }
        if (openFade > 0.01f) Box(Modifier.fillMaxSize().graphicsLayer { alpha = openFade }.background(canvas))
        renameTarget?.let { old ->
            val reserved = renameText.trim() in setOf("Todos", "Favoritos")
            AlertDialog(onDismissRequest = { renameTarget = null }, containerColor = dialogSurface,
                title = { Text("Renomear coleção", color = primaryText) },
                text = { Column {
                    OutlinedTextField(value = renameText, onValueChange = { renameText = it }, label = { Text("Nome") }, singleLine = true, isError = reserved, modifier = Modifier.fillMaxWidth())
                    if (reserved) Text("Esse nome é reservado.", color = p.error, fontSize = font(12), modifier = Modifier.padding(top = 4.dp))
                } },
                confirmButton = { TextButton(enabled = renameText.isNotBlank() && !reserved, onClick = {
                    val n = renameText.trim()
                    vm.renameCollection(old, n)
                    if (collectionFilter == old) collectionFilter = n
                    renameTarget = null
                }) { Text("Salvar") } },
                dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancelar") } })
        }
        deleteTarget?.let { name ->
            val n = links.count { it.collection == name }
            AlertDialog(onDismissRequest = { deleteTarget = null }, containerColor = dialogSurface,
                title = { Text("Excluir coleção?", color = primaryText) },
                text = { Text(
                    "A coleção \"$name\" será excluída. " + when (n) {
                        0 -> "Ela não tem links."
                        1 -> "O link dentro dela não será apagado: ele será movido para a Inbox (continua em Todos)."
                        else -> "Os $n links dentro dela não serão apagados: eles serão movidos para a Inbox (continuam em Todos)."
                    }, color = primaryText) },
                confirmButton = { TextButton(onClick = {
                    vm.deleteCollection(name)
                    if (collectionFilter == name) collectionFilter = "Todos"
                    deleteTarget = null
                }) { Text("Excluir", color = p.error) } },
                dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } })
        }
        editLinkTarget?.let { target ->
            AlertDialog(onDismissRequest = { editLinkTarget = null }, containerColor = dialogSurface,
                title = { Text("Editar link", color = primaryText) },
                text = { Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = editUrl, onValueChange = { editUrl = it }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = editTitle, onValueChange = { editTitle = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = editCollection, onValueChange = { editCollection = it }, label = { Text("Coleção") }, modifier = Modifier.fillMaxWidth())
                } },
                confirmButton = { TextButton(onClick = {
                    val url = editUrl.trim()
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        vm.updateLink(target.copy(url = url, title = editTitle.trim(), collection = editCollection.trim().ifBlank { "Inbox" }))
                        editLinkTarget = null
                    }
                }) { Text("Salvar") } },
                dismissButton = { TextButton(onClick = { editLinkTarget = null }) { Text("Cancelar") } })
        }
        deleteLinkTarget?.let { link ->
            AlertDialog(onDismissRequest = { deleteLinkTarget = null }, containerColor = dialogSurface,
                title = { Text("Excluir link?", color = primaryText) },
                text = { Text("\"${link.title.ifBlank { Uri.parse(link.url).host ?: link.url }}\" será removido da sua biblioteca.", color = primaryText) },
                confirmButton = { TextButton(onClick = { vm.deleteLink(link); deleteLinkTarget = null }) { Text("Excluir", color = p.error) } },
                dismissButton = { TextButton(onClick = { deleteLinkTarget = null }) { Text("Cancelar") } })
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
                p = p, theme = theme, scale = scale, amoledInDark = amoledDark, font = appFont, gridView = gridView, cardSize = cardSize, hiddenCols = hiddenCols, hiddenLinksCount = hiddenLinksCount, user = user, hasCustomAvatar = customAvatarUri != null,
                onTheme = vm::setThemeMode, onScale = vm::setTextScale, onAmoledInDark = vm::setAmoledInDark, onFont = vm::setAppFont, onGrid = vm::setGridView, onCardSize = vm::setCardSize, onUnhideCol = vm::unhideCollection,
                onShowHiddenLinks = { showSettings = false; collectionFilter = "Ocultos" },
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
    p: Palette, theme: ThemeMode, scale: TextScale, amoledInDark: Boolean, font: AppFont, gridView: Boolean, cardSize: CardSize, hiddenCols: Set<String>, hiddenLinksCount: Int, user: com.google.firebase.auth.FirebaseUser?, hasCustomAvatar: Boolean,
    onTheme: (ThemeMode) -> Unit, onScale: (TextScale) -> Unit, onAmoledInDark: (Boolean) -> Unit, onFont: (AppFont) -> Unit, onGrid: (Boolean) -> Unit, onCardSize: (CardSize) -> Unit, onUnhideCol: (String) -> Unit, onShowHiddenLinks: () -> Unit,
    onPickAvatar: () -> Unit, onResetAvatar: () -> Unit, onLogin: () -> Unit, onLogout: () -> Unit, onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose) {
        PopIn {
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
                    if (theme == ThemeMode.SYSTEM) {
                        SettingRow(label = "Usar preto AMOLED no modo escuro", subtitle = "Quando o sistema estiver escuro", p = p, onClick = { onAmoledInDark(!amoledInDark) }) {
                            Switch(
                                checked = amoledInDark, onCheckedChange = null,
                                colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = if (p.dark) Color(0xFF0B1026) else Color.White, uncheckedTrackColor = Color.Transparent, uncheckedThumbColor = p.muted, uncheckedBorderColor = p.muted)
                            )
                        }
                    }
                    Text("Fonte", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
                    AppFont.entries.forEach { f ->
                        SettingRow(label = f.label, p = p, onClick = { onFont(f) }, labelFamily = f.family ?: androidx.compose.ui.text.font.FontFamily.Default, subtitle = "Aa Bb Cc 123 — Seus links", subtitleFamily = f.family ?: androidx.compose.ui.text.font.FontFamily.Default) {
                            RadioButton(selected = font == f, onClick = null, colors = radioColors(p))
                        }
                    }
                    Text("Visualização", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
                    listOf(false to "Lista", true to "Grade (2 colunas)").forEach { (g, l) ->
                        SettingRow(label = l, p = p, onClick = { onGrid(g) }) { RadioButton(selected = gridView == g, onClick = null, colors = radioColors(p)) }
                    }
                    Text("Tamanho dos cartões", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
                    CardSize.entries.forEach { cs ->
                        SettingRow(label = cs.label, p = p, onClick = { onCardSize(cs) }) { RadioButton(selected = cardSize == cs, onClick = null, colors = radioColors(p)) }
                    }
                    Text("Tamanho da fonte", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
                    TextScale.entries.forEach { sc ->
                        SettingRow(label = sc.label, p = p, onClick = { onScale(sc) }) { RadioButton(selected = scale == sc, onClick = null, colors = radioColors(p)) }
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = p.divider)
                    SectionHeader("OCULTOS", p)
                    SettingRow(label = "Links ocultos", subtitle = "$hiddenLinksCount ${if (hiddenLinksCount == 1) "link" else "links"}", p = p) {
                        TextButton(enabled = hiddenLinksCount > 0, onClick = onShowHiddenLinks) { Text("Ver") }
                    }
                    if (hiddenCols.isEmpty()) SettingRow(label = "Abas ocultas", subtitle = "Nenhuma", p = p) {}
                    hiddenCols.sorted().forEach { c -> SettingRow(label = c, subtitle = "Aba oculta", p = p) { TextButton(onClick = { onUnhideCol(c) }) { Text("Mostrar") } } }
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
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = p.divider)
                    SectionHeader("SOBRE", p)
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val versionName = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
                    SettingRow(label = "Versão do app", p = p) { Text(versionName, color = p.muted, fontSize = 14.sp) }
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
    labelFamily: androidx.compose.ui.text.font.FontFamily? = null, subtitleFamily: androidx.compose.ui.text.font.FontFamily? = null,
    trailing: @Composable () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(label, color = p.text, fontSize = 15.sp, maxLines = 2, fontFamily = labelFamily)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = p.muted, fontSize = 12.sp, maxLines = 1, fontFamily = subtitleFamily, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun radioColors(p: Palette) = RadioButtonDefaults.colors(selectedColor = p.accent, unselectedColor = p.muted)
