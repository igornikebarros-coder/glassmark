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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.layout.RowScope
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
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

class MainActivity : androidx.fragment.app.FragmentActivity() {
    private var unlocked by mutableStateOf(false)
    private var stoppedAt = 0L
    private val vm: AppViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Motion.refresh(this)
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
                    transitionSpec = { if (Motion.reduced) fadeIn(tween(150)) togetherWith fadeOut(tween(150)) else (fadeIn(tween(220)) + scaleIn(initialScale = 0.96f, animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))) togetherWith fadeOut(tween(160)) },
                    label = "nav"
                ) { showAuth -> if (showAuth) AuthScreen(vm) else GlassmarkScreen(vm) }
                val lockOn by vm.appLock.collectAsState()
                if (lockOn && !unlocked) {
                    val p = LocalPalette.current
                    val ask = { biometricUnlock(this@MainActivity, "Desbloquear o Link") { unlocked = true } }
                    LaunchedEffect(Unit) { ask() }
                    Column(Modifier.fillMaxSize().background(p.canvas).pointerInput(Unit) { detectTapGestures { } }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Lock, null, tint = p.accent, modifier = Modifier.size(42.dp))
                        Text("Link bloqueado", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 12.dp))
                        TextButton(onClick = ask) { Text("Desbloquear") }
                    }
                }
            }
        }
    }
    override fun onResume() { super.onResume(); Motion.refresh(this); if (stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > 60_000) unlocked = false; stoppedAt = 0 }
    override fun onStop() { super.onStop(); stoppedAt = System.currentTimeMillis() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleShare(intent) }
    private fun handleShare(intent: Intent?) {
        when (intent?.action) { "com.glassmark.link.SAVE" -> vm.pendingAction.value = "save"; "com.glassmark.link.SEARCH" -> vm.pendingAction.value = "search" }
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
    val linksLoaded by vm.linksLoaded.collectAsState()
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
    var showSearch by remember { mutableStateOf(false) }
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
    val inAppBrowser by vm.inAppBrowser.collectAsState()
    val appLock by vm.appLock.collectAsState()
    val lockedTabs by vm.lockedTabs.collectAsState()
    val widgetFavs by vm.widgetFavorites.collectAsState()
    val pendingAction by vm.pendingAction.collectAsState()
    val unlockedTabs = remember { mutableStateListOf<String>() }
    var editTags by remember { mutableStateOf("") }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
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
    val collections = listOf("Todos", "Favoritos", "Não lidos") + allCollections.filter { it !in hiddenCols } + (if (collectionFilter == "Ocultos") listOf("Ocultos") else emptyList())
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
        links.firstOrNull { it.docId == id }?.let { l -> openUrl(context, l.url); vm.markRead(l) }
        delay(600)
        opening = null
    }
    val pagerState = rememberPagerState { collections.size }
    val chipsState = rememberLazyListState()
    LaunchedEffect(collectionFilter, collections.size) {
        val idx = collections.indexOf(collectionFilter)
        if (idx >= 0) {
            if (pagerState.currentPage != idx) pagerState.animateScrollToPage(idx, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
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
    val builtIns = setOf("Todos", "Favoritos", "Inbox", "Não lidos")
    val saveOrWarn: (String, String, String) -> Unit = { url, title, col ->
        val d = vm.findDuplicate(url)
        if (d == null) vm.addLink(url, title, col)
        else uiScope.launch {
            snack.currentSnackbarData?.dismiss()
            if (snack.showSnackbar("Esse link já está salvo", actionLabel = "Abrir", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) opening = d.docId
        }
    }
    val pickReminder: (LinkEntity) -> Unit = { l ->
        if (android.os.Build.VERSION.SDK_INT >= 33) notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        val c = java.util.Calendar.getInstance().apply { if (l.remindAt > System.currentTimeMillis()) timeInMillis = l.remindAt else add(java.util.Calendar.HOUR_OF_DAY, 1) }
        val dp = android.app.DatePickerDialog(context, { _, y, m, d ->
            android.app.TimePickerDialog(context, { _, h, min ->
                c.set(y, m, d, h, min, 0)
                vm.setReminder(l, c.timeInMillis)
                uiScope.launch { snack.showSnackbar("Lembrete: " + java.text.SimpleDateFormat("dd/MM 'às' HH:mm", java.util.Locale("pt", "BR")).format(c.time)) }
            }, c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE), true).show()
        }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH))
        if (l.remindAt > System.currentTimeMillis()) dp.setButton(android.content.DialogInterface.BUTTON_NEUTRAL, "Remover") { _, _ -> vm.setReminder(l, 0) }
        dp.show()
    }
    LaunchedEffect(pendingAction) {
        when (pendingAction) { "save" -> showAdd = true; "search" -> showSearch = true }
        vm.pendingAction.value = null
    }
    val filteredFor: (String) -> List<LinkEntity> = { filter ->
        links.filter {
            (if (filter == "Ocultos") it.hidden else !it.hidden && (filter == "Todos" || (filter == "Favoritos" && it.isFavorite) || (filter == "Não lidos" && !it.isRead) || it.collection == filter)) &&
            (if (search.startsWith("#") && search.length > 1) it.tagList().any { t -> t.equals(search.drop(1).trim(), true) }
             else it.url.contains(search, true) || it.title.contains(search, true) || it.tags.contains(search, true))
        }
    }
    val hiddenLinksCount = links.count { it.hidden }
    val shownFilter = collections.getOrNull(pagerState.targetPage) ?: collectionFilter
    LaunchedEffect(showSearch) { if (showSearch) { delay(80); runCatching { searchFocus.requestFocus() } } }
    val pasteClip: () -> Unit = {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val t = runCatching { cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim() }.getOrNull()
        if (t != null && Regex("^https?://\\S+$").matches(t)) saveOrWarn(t, "", if (collectionFilter in allCollections) collectionFilter else "Inbox")
        else uiScope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar("Copie um link (https://…) primeiro") }
    }

    Box(Modifier.fillMaxSize()) {
        val blurRadius by animateDpAsState(if (showAdd || renameTarget != null || deleteTarget != null || editLinkTarget != null || deleteLinkTarget != null || CardMenu.req != null) 16.dp else 0.dp, tween(250), label = "blur")
        Box(Modifier.fillMaxSize().blur(blurRadius).background(Brush.verticalGradient(p.gradient))) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp).nestedScroll(barConn)) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("$greeting,", color = muted, fontSize = font(15), fontWeight = FontWeight.Medium)
                        Text(firstName ?: (if (user == null) "Visitante" else "Link"), color = primaryText, fontSize = font(32), fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    Box(Modifier.size(42.dp).clip(CircleShape).border(1.dp, p.stroke, CircleShape).clickable { showSearch = !showSearch; if (!showSearch) search = "" }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar", tint = primaryText, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.size(42.dp).clip(CircleShape).background(accent).clickable { showSettings = true }, contentAlignment = Alignment.Center) {
                        val avatar = customAvatarUri ?: user?.photoUrl?.toString()
                        if (!avatar.isNullOrBlank()) AsyncImage(model = avatar, contentDescription = "Foto do perfil", contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                        else if (firstName != null) Text(firstName.first().uppercase(), color = onAccentOf(p), fontWeight = FontWeight.ExtraBold, fontSize = font(16))
                        else Icon(Icons.Default.AccountCircle, contentDescription = "Conta", tint = onAccentOf(p))
                    }
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
                if (showSearch || search.isNotEmpty()) {
                    GlassSurface(p, RoundedCornerShape(22.dp), Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = muted)
                            TextField(value = search, onValueChange = { search = it }, placeholder = { Text("Buscar links, sites, coleções…", color = muted, fontSize = font(14)) },
                                modifier = Modifier.weight(1f).focusRequester(searchFocus), colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = primaryText, unfocusedTextColor = primaryText), singleLine = true)
                            Icon(Icons.Default.Close, contentDescription = "Fechar busca", tint = muted, modifier = Modifier.clip(CircleShape).clickable { search = ""; showSearch = false }.padding(6.dp))
                        }
                    }
                }
                AnimatedVisibility(visible = clipUrl != null && links.none { it.url == clipUrl }) {
                    ClipBanner(clipUrl.orEmpty(), p, font, onSave = {
                        saveOrWarn(clipUrl.orEmpty(), "", if (collectionFilter in allCollections) collectionFilter else "Inbox"); clipDismissed = clipUrl; clipUrl = null
                    }, onDismiss = { clipDismissed = clipUrl; clipUrl = null })
                }
                Spacer(Modifier.height(8.dp))
                if (collectionFilter != "Todos" && collectionFilter !in collections) collectionFilter = "Todos"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LazyRow(state = chipsState, horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.weight(1f)) {
                        items(collections, key = { it }) { item ->
                            val editable = item !in builtIns && item != "Ocultos"
                            val cnt = when (item) { "Todos" -> links.count { !it.hidden }; "Favoritos" -> links.count { !it.hidden && it.isFavorite }; "Não lidos" -> links.count { !it.hidden && !it.isRead }; "Ocultos" -> hiddenLinksCount; else -> links.count { !it.hidden && it.collection == item } }
                            Box(Modifier.animateItem()) {
                                MinimalTab(item, cnt, shownFilter == item, p, font,
                                    onClick = { if (collectionFilter == item && editable) chipMenu = item else collectionFilter = item }, onLongClick = if (editable) ({ chipMenu = item }) else null)
                                DropdownMenu(expanded = chipMenu == item, onDismissRequest = { chipMenu = null }, containerColor = dialogSurface) {
                                    DropdownMenuItem(text = { Text("Editar", color = primaryText) }, leadingIcon = { Icon(Icons.Default.Edit, null, tint = muted) },
                                        onClick = { chipMenu = null; renameText = item; renameTarget = item })
                                    DropdownMenuItem(text = { Text("Ocultar", color = primaryText) }, leadingIcon = { Icon(Icons.Default.VisibilityOff, null, tint = muted) },
                                        onClick = { chipMenu = null; vm.hideCollection(item); if (collectionFilter == item) collectionFilter = "Todos" })
                                    DropdownMenuItem(text = { Text(if (item in lockedTabs) "Desbloquear aba" else "Bloquear aba", color = primaryText) }, leadingIcon = { Icon(if (item in lockedTabs) Icons.Default.LockOpen else Icons.Default.Lock, null, tint = muted) },
                                        onClick = { chipMenu = null; if (item in lockedTabs) biometricUnlock(context, "Remover bloqueio da aba") { vm.toggleTabLock(item) } else vm.toggleTabLock(item) })
                                    DropdownMenuItem(text = { Text("Apagar", color = p.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = p.error) },
                                        onClick = { chipMenu = null; deleteTarget = item })
                                }
                            }
                        }
                        item(key = "__new") {
                            Box(Modifier.animateItem()) { MinimalTab("Nova", null, false, p, font, icon = Icons.Default.Add, onClick = { showNewCollection = true }) }
                        }
                    }
                    Icon(if (gridView) Icons.Default.ViewAgenda else Icons.Default.GridView, contentDescription = "Alternar lista e grade", tint = muted,
                        modifier = Modifier.padding(start = 6.dp).clip(CircleShape).clickable { vm.setGridView(!gridView) }.padding(8.dp).size(20.dp))
                }
                HorizontalDivider(color = p.divider)
                Spacer(Modifier.height(4.dp))
                HorizontalPager(
                    state = pagerState, modifier = Modifier.weight(1f), key = { collections.getOrNull(it) ?: it }, beyondViewportPageCount = 1,
                    verticalAlignment = Alignment.Top,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState, snapPositionalThreshold = 0.2f,
                        snapAnimationSpec = motionSpring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)
                    )
                ) { page ->
                    val filter = collections.getOrNull(page) ?: "Todos"
                    val shown = filteredFor(filter).sortedByDescending { it.savedAt }
                    var epoch by remember { mutableIntStateOf(0) }
                    var armed by remember { mutableStateOf(false) }
                    LaunchedEffect(pagerState.settledPage, linksLoaded) {
                        if (linksLoaded && pagerState.settledPage == page) { armed = true; epoch++; kotlinx.coroutines.delay(700); armed = false }
                    }
                    if (!linksLoaded) {
                        SkeletonList(p, gridView)
                    } else if (filter in lockedTabs && filter !in unlockedTabs) {
                        Column(Modifier.fillMaxSize().padding(bottom = 90.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = muted, modifier = Modifier.size(32.dp))
                            Text("Aba bloqueada", color = primaryText, fontSize = font(15), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                            TextButton(onClick = { biometricUnlock(context, "Desbloquear \"$filter\"") { unlockedTabs.add(filter) } }) { Text("Desbloquear") }
                        }
                    } else if (shown.isEmpty()) {
                        if (filter == "Todos" && search.isBlank()) {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 20.dp, bottom = 110.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(accent.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = accent, modifier = Modifier.size(34.dp))
                                }
                                Spacer(Modifier.height(18.dp))
                                Text("Sua biblioteca está vazia", color = primaryText, fontSize = font(20), fontWeight = FontWeight.ExtraBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Spacer(Modifier.height(4.dp))
                                Text("Salve seu primeiro link em 3 passos", color = muted, fontSize = font(13), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Spacer(Modifier.height(18.dp))
                                listOf("Abra qualquer site ou app", "Toque em Compartilhar", "Escolha \u201cLink\u201d").forEachIndexed { i, s ->
                                    GlassSurface(p, RoundedCornerShape(14.dp), Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(24.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                                                Text("${i + 1}", color = onAccentOf(p), fontSize = font(12), fontWeight = FontWeight.ExtraBold)
                                            }
                                            Text(s, color = primaryText, fontSize = font(14), fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 12.dp))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(accent).clickable(onClick = pasteClip).padding(vertical = 14.dp),
                                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = onAccentOf(p), modifier = Modifier.size(18.dp))
                                    Text("Colar link copiado", color = onAccentOf(p), fontSize = font(15), fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        } else {
                            Column(Modifier.fillMaxSize().padding(bottom = 90.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(if (filter == "Favoritos") Icons.Default.Favorite else Icons.Default.Bookmark, contentDescription = null, tint = muted.copy(alpha = .8f), modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(10.dp))
                                Text(if (search.isNotBlank()) "Nada encontrado" else when (filter) { "Favoritos" -> "Nenhum favorito ainda"; "Ocultos" -> "Nada oculto"; else -> "Nada por aqui ainda" },
                                    color = primaryText, fontSize = font(15), fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Spacer(Modifier.height(4.dp))
                                Text(if (search.isNotBlank()) "Tente outra busca." else when (filter) { "Favoritos" -> "Toque no \u2665 de um link para fix\u00e1-lo aqui."; "Ocultos" -> "Links ocultados aparecem aqui."; else -> "Salve links nesta cole\u00e7\u00e3o com o bot\u00e3o Salvar link." },
                                    color = muted, fontSize = font(12), textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                            }
                        }
                    } else {
                        val groups = shown.groupBy { timelineGroup(it.savedAt) }
                        LazyVerticalGrid(
                            modifier = Modifier.fillMaxSize(),
                            columns = GridCells.Fixed(if (gridView) 2 else 1), contentPadding = PaddingValues(bottom = 110.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            groups.forEach { (label, list) ->
                                item(key = "__h_$label", span = { GridItemSpan(maxLineSpan) }) { TimelineHeader(label, p, font) }
                                itemsIndexed(list, key = { _, it -> it.docId }) { idx, link ->
                                    LaunchedEffect(link.docId) { vm.fetchOg(link) }
                                    val order = remember(epoch) { shown.indexOf(link).coerceAtLeast(idx) }
                                    SwipeLinkItem(
                                        link, p, font, opening == link.docId, gridView, cardSize, link.docId in summarizing, filter == "Ocultos", Modifier.animateItem().staggerIn(order, epoch, armed),
                                        onOpen = { opening = link.docId }, onToggleFav = { vm.toggleFavorite(link) }, onDelete = { deleteWithUndo(link) },
                                        onEdit = { editLinkTarget = link; editUrl = link.url; editTitle = link.title; editCollection = link.collection; editTags = link.tagList().joinToString(", ") },
                                        onHide = { hideWithUndo(link) }, onSummarize = { vm.summarize(link) },
                                        onToggleRead = { vm.markRead(link, !link.isRead) }, onRemind = { pickReminder(link) },
                                        onTag = { t -> search = "#$t"; showSearch = true }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp, start = 16.dp, end = 16.dp))
            ExtendedFloatingActionButton(
                onClick = { collection = if (collectionFilter in allCollections) collectionFilter else "Inbox"; showAdd = true },
                expanded = barVisible,
                icon = { Icon(Icons.Default.Add, contentDescription = "Salvar link") },
                text = { Text("Salvar link", fontWeight = FontWeight.ExtraBold) },
                containerColor = accent, contentColor = onAccentOf(p), shape = RoundedCornerShape(28.dp),
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 20.dp)
            )
        }
        val cmScope = rememberCoroutineScope()
        val cmProg = remember { androidx.compose.animation.core.Animatable(0f) }
        var cmClosing by remember { mutableStateOf(false) }
        val cmReq = CardMenu.req
        LaunchedEffect(cmReq) { if (cmReq != null) { cmClosing = false; cmProg.snapTo(0f); cmProg.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 420f)) } }
        CardMenuOverlay(p, font, cmProg.value, cmClosing) { after ->
            if (!cmClosing) { cmClosing = true
                cmScope.launch { cmProg.animateTo(0f, tween(170)); val r = CardMenu.req; CardMenu.req = null; r?.onClosed?.invoke(); after() } }
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
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = editTags, onValueChange = { editTags = it }, label = { Text("Tags (separadas por vírgula)") }, placeholder = { Text("receita, trabalho") }, modifier = Modifier.fillMaxWidth())
                } },
                confirmButton = { TextButton(onClick = {
                    val url = editUrl.trim()
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        vm.updateLink(target.copy(url = url, title = editTitle.trim(), collection = editCollection.trim().ifBlank { "Inbox" }, tags = editTags.split(',', '#').map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(",")))
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
                        saveOrWarn(url, manualTitle.trim(), collection.trim().ifBlank { "Inbox" })
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
        androidx.activity.compose.BackHandler(enabled = showSettings) { showSettings = false }
        AnimatedVisibility(
            visible = showSettings, modifier = Modifier.fillMaxSize(),
            enter = if (Motion.reduced) fadeIn(tween(150)) else slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it / 3 } + fadeIn(tween(220)),
            exit = if (Motion.reduced) fadeOut(tween(150)) else slideOutHorizontally(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)) { it / 3 } + fadeOut(tween(180))
        ) {
            SettingsScreen(
                p = p, theme = theme, scale = scale, amoledInDark = amoledDark, font = appFont, gridView = gridView, cardSize = cardSize, hiddenCols = hiddenCols, hiddenLinksCount = hiddenLinksCount, user = user,
                avatarUrl = customAvatarUri ?: user?.photoUrl?.toString(), hasCustomAvatar = customAvatarUri != null,
                onTheme = vm::setThemeMode, onScale = vm::setTextScale, onAmoledInDark = vm::setAmoledInDark, onFont = vm::setAppFont, onGrid = vm::setGridView, onCardSize = vm::setCardSize, onUnhideCol = vm::unhideCollection,
                onShowHiddenLinks = { showSettings = false; collectionFilter = "Ocultos" },
                onPickAvatar = { avatarPicker.launch(arrayOf("image/*")) },
                onResetAvatar = { vm.saveCustomAvatar(null) },
                onLogin = { showSettings = false; vm.goToLogin() },
                onLogout = { showSettings = false; vm.logout() },
                onClose = { showSettings = false },
                extra = {
                    val exportL = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                        if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportJson().toByteArray()) } }
                            .onSuccess { uiScope.launch { snack.showSnackbar("Backup exportado (${links.size} links)") } }
                    }
                    val importJ = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                        if (uri != null) runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }.orEmpty() }
                            .mapCatching { Backup.fromJson(it, "") }
                            .onSuccess { vm.importLinks(it) { n -> uiScope.launch { snack.showSnackbar("$n links importados") } } }
                            .onFailure { uiScope.launch { snack.showSnackbar("Arquivo de backup inválido") } }
                    }
                    val importH = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                        if (uri != null) runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }.orEmpty() }
                            .mapCatching { Backup.fromBookmarksHtml(it, "") }
                            .onSuccess { vm.importLinks(it) { n -> uiScope.launch { snack.showSnackbar("$n favoritos importados") } } }
                    }
                    SettingsGroup("Navegador", p) {
                        SettingRow(label = "Abrir links dentro do app", subtitle = if (inAppBrowser) "Navegador do app (Custom Tabs)" else "Navegador externo", p = p, onClick = { vm.setInAppBrowser(!inAppBrowser) }) { SettingSwitch(inAppBrowser, p) }
                    }
                    SettingsGroup("Segurança", p) {
                        SettingRow(label = "Bloquear com biometria", subtitle = "Digital ou rosto ao abrir o app", p = p, onClick = { biometricUnlock(context, "Confirmar") { vm.setAppLock(!appLock) } }) { SettingSwitch(appLock, p) }
                        SettingDivider(p)
                        SettingRow(label = "Abas bloqueadas", subtitle = if (lockedTabs.isEmpty()) "Segure uma aba e escolha Bloquear aba" else lockedTabs.sorted().joinToString(", "), p = p) {}
                    }
                    SettingsGroup("Widget", p) {
                        SettingRow(label = "Mostrar favoritos no widget", subtitle = if (widgetFavs) "Favoritos" else "Recentes", p = p, onClick = { vm.setWidgetFavorites(!widgetFavs) }) { SettingSwitch(widgetFavs, p) }
                    }
                    SettingsGroup("Backup", p) {
                        SettingRow(label = "Exportar backup (JSON)", p = p, onClick = { exportL.launch("link-backup.json") }) { Text("Exportar", color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                        SettingDivider(p)
                        SettingRow(label = "Importar backup (JSON)", p = p, onClick = { importJ.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Importar", color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                        SettingDivider(p)
                        SettingRow(label = "Importar favoritos do Chrome", subtitle = "Arquivo HTML exportado do navegador", p = p, onClick = { importH.launch(arrayOf("text/html", "*/*")) }) { Text("Importar", color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
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
private fun SettingsGroup(title: String, p: Palette, content: @Composable ColumnScope.() -> Unit) {
    Text(title.uppercase(), color = p.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.padding(start = 6.dp, top = 24.dp, bottom = 8.dp))
    GlassSurface(p, RoundedCornerShape(18.dp), Modifier.fillMaxWidth()) { Column(content = content) }
}

@Composable
private fun SettingDivider(p: Palette) { HorizontalDivider(color = p.divider, modifier = Modifier.padding(start = 16.dp)) }

@Composable
private fun SettingsScreen(
    p: Palette, theme: ThemeMode, scale: TextScale, amoledInDark: Boolean, font: AppFont, gridView: Boolean, cardSize: CardSize, hiddenCols: Set<String>, hiddenLinksCount: Int, user: com.google.firebase.auth.FirebaseUser?,
    avatarUrl: String?, hasCustomAvatar: Boolean,
    onTheme: (ThemeMode) -> Unit, onScale: (TextScale) -> Unit, onAmoledInDark: (Boolean) -> Unit, onFont: (AppFont) -> Unit, onGrid: (Boolean) -> Unit, onCardSize: (CardSize) -> Unit, onUnhideCol: (String) -> Unit, onShowHiddenLinks: () -> Unit,
    onPickAvatar: () -> Unit, onResetAvatar: () -> Unit, onLogin: () -> Unit, onLogout: () -> Unit, onClose: () -> Unit, extra: @Composable () -> Unit = {}
) {
    Column(Modifier.fillMaxSize().background(p.canvas).pointerInput(Unit) { detectTapGestures { } }.statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = p.text, modifier = Modifier.clip(CircleShape).clickable(onClick = onClose).padding(12.dp))
            Text("Ajustes", color = p.text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 4.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            SettingsGroup("Conta", p) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(p.accent), contentAlignment = Alignment.Center) {
                        val initial = (user?.displayName?.trim()?.firstOrNull() ?: user?.email?.firstOrNull())?.uppercase()
                        if (!avatarUrl.isNullOrBlank()) AsyncImage(model = avatarUrl, contentDescription = "Foto do perfil", contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                        else if (initial != null) Text(initial, color = onAccentOf(p), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        else Icon(Icons.Default.AccountCircle, null, tint = onAccentOf(p))
                    }
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(if (user != null) (user.displayName?.takeIf { it.isNotBlank() } ?: "Conta Link") else "Modo visitante", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(if (user != null) (user.email ?: "") else "Links apenas neste aparelho", color = p.muted, fontSize = 13.sp)
                    }
                }
                SettingDivider(p)
                SettingRow(label = "Foto de perfil", p = p, onClick = onPickAvatar) { Text("Escolher", color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                if (hasCustomAvatar) {
                    SettingDivider(p)
                    SettingRow(label = "Usar foto do Google", p = p, onClick = onResetAvatar) { Text("Restaurar", color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                }
                SettingDivider(p)
                if (user != null) SettingRow(label = "Sair da conta", p = p, onClick = onLogout) { Text("Sair", color = p.error, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                else SettingRow(label = "Entrar ou criar conta", subtitle = "Sincronize seus links na nuvem", p = p, onClick = onLogin) { Text("Entrar", color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }

            SettingsGroup("Aparência · Tema", p) {
                ThemeMode.entries.forEachIndexed { i, m ->
                    if (i > 0) SettingDivider(p)
                    SettingRow(label = m.label, p = p, onClick = { onTheme(m) }) { RadioButton(selected = theme == m, onClick = null, colors = radioColors(p)) }
                }
                if (theme == ThemeMode.SYSTEM) {
                    SettingDivider(p)
                    SettingRow(label = "Usar preto AMOLED no escuro", subtitle = "Quando o sistema estiver escuro", p = p, onClick = { onAmoledInDark(!amoledInDark) }) {
                        Switch(
                            checked = amoledInDark, onCheckedChange = null,
                            colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = onAccentOf(p), uncheckedTrackColor = Color.Transparent, uncheckedThumbColor = p.muted, uncheckedBorderColor = p.muted)
                        )
                    }
                }
            }
            SettingsGroup("Aparência · Fonte", p) {
                AppFont.entries.forEachIndexed { i, f ->
                    if (i > 0) SettingDivider(p)
                    SettingRow(label = f.label, p = p, onClick = { onFont(f) }, labelFamily = f.family ?: androidx.compose.ui.text.font.FontFamily.Default, subtitle = "Aa Bb Cc 123 — Seus links", subtitleFamily = f.family ?: androidx.compose.ui.text.font.FontFamily.Default) {
                        RadioButton(selected = font == f, onClick = null, colors = radioColors(p))
                    }
                }
            }
            SettingsGroup("Aparência · Tamanho do texto", p) {
                TextScale.entries.forEachIndexed { i, sc ->
                    if (i > 0) SettingDivider(p)
                    SettingRow(label = sc.label, p = p, onClick = { onScale(sc) }) { RadioButton(selected = scale == sc, onClick = null, colors = radioColors(p)) }
                }
            }

            SettingsGroup("Visualização", p) {
                listOf(false to "Lista", true to "Grade (2 colunas)").forEachIndexed { i, (g, l) ->
                    if (i > 0) SettingDivider(p)
                    SettingRow(label = l, p = p, onClick = { onGrid(g) }) { RadioButton(selected = gridView == g, onClick = null, colors = radioColors(p)) }
                }
            }
            SettingsGroup("Tamanho dos cards", p) {
                CardSize.entries.forEachIndexed { i, cs ->
                    if (i > 0) SettingDivider(p)
                    SettingRow(label = cs.label, p = p, onClick = { onCardSize(cs) }) { RadioButton(selected = cardSize == cs, onClick = null, colors = radioColors(p)) }
                }
            }

            SettingsGroup("Ocultos", p) {
                SettingRow(label = "Links ocultos", subtitle = "$hiddenLinksCount ${if (hiddenLinksCount == 1) "link" else "links"}", p = p, onClick = if (hiddenLinksCount > 0) onShowHiddenLinks else null) {
                    Text("Ver", color = if (hiddenLinksCount > 0) p.accent else p.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                SettingDivider(p)
                if (hiddenCols.isEmpty()) SettingRow(label = "Abas ocultas", subtitle = "Nenhuma", p = p) {}
                hiddenCols.sorted().forEachIndexed { i, c ->
                    if (i > 0) SettingDivider(p)
                    SettingRow(label = c, subtitle = "Aba oculta", p = p, onClick = { onUnhideCol(c) }) { Text("Mostrar", color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                }
            }

            extra()
            SettingsGroup("Sobre", p) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val versionName = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
                SettingRow(label = "Versão do app", p = p) { Text(versionName, color = p.muted, fontSize = 14.sp) }
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
        Modifier.fillMaxWidth().heightIn(min = 54.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp),
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
private fun SettingSwitch(v: Boolean, p: Palette) = Switch(checked = v, onCheckedChange = null,
    colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = onAccentOf(p), uncheckedTrackColor = Color.Transparent, uncheckedThumbColor = p.muted, uncheckedBorderColor = p.muted))

@Composable
private fun radioColors(p: Palette) = RadioButtonDefaults.colors(selectedColor = p.accent, unselectedColor = p.muted)
