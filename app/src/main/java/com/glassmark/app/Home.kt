@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.glassmark.app

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.foundation.horizontalScroll

fun domainOf(url: String): String =
    runCatching { Uri.parse(url).host }.getOrNull()?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: url

fun relTime(ts: Long): String {
    val d = System.currentTimeMillis() - ts
    val min = d / 60_000
    return when {
        min < 1 -> "agora"
        min < 60 -> "$min min"
        min < 60 * 24 -> "${min / 60} h"
        min < 60 * 48 -> "ontem"
        min < 60 * 24 * 30 -> "${min / (60 * 24)} d"
        else -> java.text.SimpleDateFormat("dd/MM/yy", java.util.Locale("pt", "BR")).format(java.util.Date(ts))
    }
}

private fun siteColors(domain: String): Pair<Color, Color> {
    val h = (domain.hashCode().absoluteValue % 360).toFloat()
    return Color.hsv(h, .55f, .66f) to Color.hsv((h + 40) % 360, .6f, .36f)
}

/** Superfície "vidro": cor translúcida do tema + contorno fino (se adapta a Claro, Escuro e AMOLED). */
@Composable
fun GlassSurface(p: Palette, shape: Shape, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(color = p.glass, shape = shape, border = BorderStroke(1.dp, p.stroke), tonalElevation = 0.dp, shadowElevation = 0.dp, modifier = modifier, content = content)
}

fun onAccentOf(p: Palette) = if (p.dark) Color(0xFF0B1026) else Color.White

@Composable
fun Favicon(domain: String, size: Dp) {
    val letter: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(domain.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        }
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(Color(0xFF8A94A6).copy(alpha = .55f))) {
        SubcomposeAsyncImage(
            model = "https://www.google.com/s2/favicons?domain=$domain&sz=64", contentDescription = null, modifier = Modifier.fillMaxSize(),
            loading = { letter() }, error = { letter() },
            success = { val sc = this; Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .93f)).padding(size * 0.2f)) { sc.SubcomposeAsyncImageContent() } }
        )
    }
}

@Composable
fun GlassChip(
    label: String, count: Int?, selected: Boolean, p: Palette, fs: (Int) -> TextUnit, icon: ImageVector? = null,
    onClick: () -> Unit, onLongClick: (() -> Unit)? = null
) {
    val bg by animateColorAsState(if (selected) p.accent else p.glass, label = "chipBg")
    val fg by animateColorAsState(if (selected) onAccentOf(p) else p.text, label = "chipFg")
    var pressed by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (pressed) 0.95f else 1f, spring(stiffness = 500f), label = "chipPress")
    val click by rememberUpdatedState(onClick)
    val long by rememberUpdatedState(onLongClick)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(
        Modifier.graphicsLayer { scaleX = sc; scaleY = sc }.clip(RoundedCornerShape(18.dp)).background(bg)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { click() },
                    onLongPress = { long?.let { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); it() } }
                )
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(15.dp))
        Text(label, color = fg, fontSize = fs(13), fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (count != null) Text("$count", color = fg.copy(alpha = .7f), fontSize = fs(11), fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ClipBanner(url: String, p: Palette, fs: (Int) -> TextUnit, onSave: () -> Unit, onDismiss: () -> Unit) {
    GlassSurface(p, RoundedCornerShape(20.dp), Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ContentPaste, null, tint = p.accent)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Copiou um link?", color = p.text, fontSize = fs(13), fontWeight = FontWeight.Bold)
                Text(url, color = p.muted, fontSize = fs(11), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.clip(RoundedCornerShape(14.dp)).background(p.accent).clickable(onClick = onSave).padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text("Salvar", color = onAccentOf(p), fontSize = fs(12), fontWeight = FontWeight.ExtraBold)
            }
            Icon(Icons.Default.Close, "Dispensar", tint = p.muted, modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(8.dp).size(18.dp))
        }
    }
}

@Composable
fun FavCard(link: LinkEntity, p: Palette, fs: (Int) -> TextUnit, onOpen: () -> Unit) {
    val d = domainOf(link.url)
    val src = remember { MutableInteractionSource() }
    val hasOg = link.ogImage.isNotBlank() && link.ogImage != "-"
    GlassSurface(p, RoundedCornerShape(18.dp), Modifier.width(150.dp).pressScale(src).clip(RoundedCornerShape(18.dp))
        .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onOpen)) {
        Column {
            if (hasOg) Thumb(link, Modifier.fillMaxWidth().height(64.dp).padding(6.dp), corner = 12.dp)
            Column(Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Favicon(d, 18.dp); Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF729A), modifier = Modifier.size(14.dp))
                }
                Text(link.title.ifBlank { d }, color = p.text, fontSize = fs(11), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

enum class CardSize(val key: String, val label: String) {
    SMALL("small", "Pequeno"), MEDIUM("medium", "Médio"), LARGE("large", "Grande");
    companion object { fun fromKey(k: String?) = entries.firstOrNull { it.key == k } ?: MEDIUM }
}

/** Miniatura só quando existe imagem de prévia (og:image); fundo neutro, sem gradiente colorido. */
@Composable
fun Thumb(link: LinkEntity, modifier: Modifier, corner: Dp = 12.dp) {
    Box(modifier.clip(RoundedCornerShape(corner)).background(Color(0xFF8A94A6).copy(alpha = .18f))) {
        AsyncImage(model = link.ogImage, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** Agrupa a linha do tempo: Hoje / Ontem / Esta semana / Antes. */
fun timelineGroup(ts: Long): String {
    val c = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }
    val today = c.timeInMillis
    val day = 86_400_000L
    return when {
        ts >= today -> "Hoje"
        ts >= today - day -> "Ontem"
        ts >= today - 6 * day -> "Esta semana"
        else -> "Antes"
    }
}

@Composable
fun TimelineHeader(label: String, p: Palette, fs: (Int) -> TextUnit) {
    Row(Modifier.padding(top = 16.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (label == "Hoje") p.accent else p.muted.copy(alpha = .6f)))
        Text(label.uppercase(), color = p.muted, fontSize = fs(11), fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Aba minimalista: texto + contagem, sublinhado na selecionada. Toque longo abre o menu da aba. */
@Composable
fun MinimalTab(
    label: String, count: Int?, selected: Boolean, p: Palette, fs: (Int) -> TextUnit, icon: ImageVector? = null,
    onClick: () -> Unit, onLongClick: (() -> Unit)? = null
) {
    val fg by animateColorAsState(if (selected) p.text else p.muted, androidx.compose.animation.core.tween(110), label = "tabFg")
    val line by animateColorAsState(if (selected) p.accent else Color.Transparent, androidx.compose.animation.core.tween(110), label = "tabLine")
    val click by rememberUpdatedState(onClick)
    val long by rememberUpdatedState(onLongClick)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Column(
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = { click() },
                onLongPress = { long?.let { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); it() } }
            )
        }
    ) {
        Row(Modifier.padding(top = 8.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
            Text(label, color = fg, fontSize = fs(16), fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold, maxLines = 1)
            if (count != null) Text("$count", color = p.muted, fontSize = fs(12), fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.height(2.dp).fillMaxWidth().clip(RoundedCornerShape(1.dp)).background(line))
    }
}

@Composable
private fun SummaryBlock(link: LinkEntity, p: Palette, fs: (Int) -> TextUnit, busy: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    if (link.summary.isNotBlank()) {
        Text(
            link.summary, color = p.text.copy(alpha = .78f), fontSize = fs(12), lineHeight = fs(17), maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { expanded = !expanded }.animateContentSize()
        )
    } else if (busy) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp, color = p.accent)
            Text("Resumindo…", color = p.muted, fontSize = fs(11), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
fun SwipeLinkItem(
    link: LinkEntity, p: Palette, fs: (Int) -> TextUnit, opening: Boolean, grid: Boolean, size: CardSize, summarizing: Boolean, hiddenMode: Boolean,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit, onToggleFav: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit, onHide: () -> Unit, onSummarize: () -> Unit
) {
    val hide by rememberUpdatedState(onHide)
    val del by rememberUpdatedState(onDelete)
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        when (v) {
            SwipeToDismissBoxValue.StartToEnd -> { hide(); true }
            SwipeToDismissBoxValue.EndToStart -> { del(); true }
            else -> false
        }
    })
    SwipeToDismissBox(
        state = state, modifier = modifier,
        backgroundContent = {
            val dir = state.dismissDirection
            val c by animateColorAsState(
                when (dir) { SwipeToDismissBoxValue.StartToEnd -> Color(0xFF5B8DEF); SwipeToDismissBoxValue.EndToStart -> Color(0xFFFF4D6A); else -> Color.Transparent }, label = "swipeBg"
            )
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(c).padding(horizontal = 22.dp),
                contentAlignment = if (dir == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (dir == SwipeToDismissBoxValue.StartToEnd) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (hiddenMode) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = Color.White)
                    Text(if (hiddenMode) "Mostrar" else "Ocultar", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                } else if (dir == SwipeToDismissBoxValue.EndToStart) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, null, tint = Color.White)
                    Text("Excluir", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    ) {
        var bounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
        var menu by remember { mutableStateOf(false) }
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        Box(Modifier.onGloballyPositioned { bounds = it.boundsInWindow() }.graphicsLayer { alpha = if (menu) 0f else 1f }) {
            LinkCard(link, p, fs, opening, grid, size, summarizing, hiddenMode, onOpen,
                onLongPress = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); menu = true })
        }
        if (menu) CardContextMenu(link, p, fs, bounds, hiddenMode, onDismiss = { menu = false },
            card = { LinkCard(link, p, fs, false, grid, size, summarizing, hiddenMode, onOpen = {}, onLongPress = null) },
            onOpen = onOpen, onToggleFav = onToggleFav, onDelete = onDelete, onEdit = onEdit, onHide = onHide, onSummarize = onSummarize)
    }
}

private class MenuAction(val icon: ImageVector, val label: String, val danger: Boolean = false, val run: () -> Unit)

/** Menu de contexto estilo iOS: fundo desfocado/escurecido, card ampliado no lugar e bolha flutuante com ações. */
@Composable
private fun CardContextMenu(
    link: LinkEntity, p: Palette, fs: (Int) -> TextUnit, bounds: androidx.compose.ui.geometry.Rect, hiddenMode: Boolean,
    onDismiss: () -> Unit, card: @Composable () -> Unit,
    onOpen: () -> Unit, onToggleFav: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit, onHide: () -> Unit, onSummarize: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scope = rememberCoroutineScope()
    val prog = remember { androidx.compose.animation.core.Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { prog.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 420f)) }
    fun close(after: () -> Unit = {}) {
        if (closing) return
        closing = true
        scope.launch { prog.animateTo(0f, androidx.compose.animation.core.tween(170)); onDismiss(); after() }
    }
    val actions = buildList {
        add(MenuAction(Icons.Default.OpenInNew, "Abrir") { onOpen() })
        add(MenuAction(Icons.Default.Share, "Compartilhar") {
            val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, link.url)
                if (link.title.isNotBlank()) putExtra(android.content.Intent.EXTRA_SUBJECT, link.title)
            }
            ctx.startActivity(android.content.Intent.createChooser(i, "Compartilhar link"))
        })
        add(MenuAction(if (link.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (link.isFavorite) "Desfavoritar" else "Favoritar") { onToggleFav() })
        if (link.summary.isBlank()) add(MenuAction(Icons.Default.AutoAwesome, "Resumir") { onSummarize() })
        add(MenuAction(Icons.Default.Edit, "Editar") { onEdit() })
        add(MenuAction(if (hiddenMode) Icons.Default.Visibility else Icons.Default.VisibilityOff, if (hiddenMode) "Mostrar" else "Ocultar") { onHide() })
        add(MenuAction(Icons.Default.Delete, "Apagar", danger = true) { onDelete() })
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { close() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val window = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        SideEffect {
            window?.let { w ->
                w.setDimAmount(0f)
                w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                w.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                w.setWindowAnimations(0)
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    w.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    w.attributes = w.attributes.apply { blurBehindRadius = 48 }
                }
            }
        }
        val blurOk = android.os.Build.VERSION.SDK_INT >= 31
        BoxWithConstraints(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = prog.value.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = if (blurOk) (if (p.dark) .45f else .28f) else (if (p.dark) .7f else .5f)))
                .pointerInput(Unit) { detectTapGestures { close() } }
        ) {
            val screenH = with(density) { maxHeight.toPx() }
            val screenW = with(density) { maxWidth.toPx() }
            val pad = with(density) { 12.dp.toPx() }
            val bubbleH = with(density) { 84.dp.toPx() }
            val topSafe = with(density) { 40.dp.toPx() }
            val grow = 1.04f
            val extra = bounds.height * (grow - 1f) / 2f
            val above = bounds.top - extra - pad - bubbleH > topSafe
            val bubbleY = (if (above) bounds.top - extra - pad - bubbleH else bounds.bottom + extra + pad)
                .coerceIn(topSafe, (screenH - bubbleH - pad).coerceAtLeast(topSafe))
            val t = prog.value
            // Card em foco, ampliado no mesmo lugar
            Box(
                Modifier.offset { androidx.compose.ui.unit.IntOffset(bounds.left.toInt(), bounds.top.toInt()) }
                    .width(with(density) { bounds.width.toDp() })
                    .graphicsLayer { val s = 1f + (grow - 1f) * t; scaleX = s; scaleY = s; shadowElevation = 24f * t; shape = RoundedCornerShape(18.dp); clip = true }
                    .pointerInput(Unit) { detectTapGestures { close(onOpen) } }
            ) { card() }
            // Bolha de ações
            Surface(
                color = p.dialog, shape = RoundedCornerShape(26.dp), border = BorderStroke(1.dp, p.stroke), shadowElevation = 12.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { androidx.compose.ui.unit.IntOffset(0, bubbleY.toInt()) }
                    .widthIn(max = maxWidth - 24.dp)
                    .graphicsLayer {
                        val s = .6f + .4f * t; scaleX = s; scaleY = s
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, if (above) 1f else 0f)
                    }
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                Row(
                    Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    actions.forEach { a ->
                        val tint = if (a.danger) p.error else p.text
                        Column(
                            Modifier.width(58.dp).clip(RoundedCornerShape(14.dp)).clickable { close(a.run) }.padding(vertical = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(Modifier.size(42.dp).clip(CircleShape).background(if (a.danger) p.error.copy(alpha = .14f) else p.text.copy(alpha = .08f)), contentAlignment = Alignment.Center) {
                                Icon(a.icon, a.label, tint = tint, modifier = Modifier.size(20.dp))
                            }
                            Text(a.label, color = tint, fontSize = fs(10), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkCard(
    link: LinkEntity, p: Palette, fs: (Int) -> TextUnit, opening: Boolean, grid: Boolean, size: CardSize, summarizing: Boolean, hiddenMode: Boolean,
    onOpen: () -> Unit, onLongPress: (() -> Unit)?, cardModifier: Modifier = Modifier
) {
    val src = remember { MutableInteractionSource() }
    val sc by animateFloatAsState(if (opening) 1.04f else 1f, spring(stiffness = 400f), label = "open")
    val d = domainOf(link.url)
    val hasOg = link.ogImage.isNotBlank() && link.ogImage != "-"
    val banner = hasOg && (grid || size == CardSize.LARGE)
    val sideThumb = hasOg && !banner
    val shape = RoundedCornerShape(18.dp)
    val title = link.title.ifBlank { d }
    val meta = "$d · ${relTime(link.savedAt)}"
    val pink = Color(0xFFFF729A)
    GlassSurface(
        p, shape,
        cardModifier.fillMaxWidth().graphicsLayer { scaleX = sc; scaleY = sc }.pressScale(src, 0.98f).clip(shape)
            .combinedClickable(
                interactionSource = src, indication = LocalIndication.current, onClick = onOpen,
                onLongClick = onLongPress
            )
    ) {
        Column {
            if (banner) {
                val h = if (grid) when (size) { CardSize.SMALL -> 70.dp; CardSize.MEDIUM -> 96.dp; CardSize.LARGE -> 130.dp } else 150.dp
                Thumb(link, Modifier.fillMaxWidth().height(h).padding(8.dp))
            }
            Row(Modifier.padding(start = 14.dp, end = 14.dp, top = if (banner) 4.dp else 14.dp, bottom = 14.dp), verticalAlignment = Alignment.Top) {
                if (sideThumb) {
                    Thumb(link, Modifier.size(if (size == CardSize.SMALL) 56.dp else 72.dp))
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, color = p.text, fontSize = fs(if (grid) 13 else 15), fontWeight = FontWeight.Bold, maxLines = if (grid) 3 else 2, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(meta, color = p.muted, fontSize = fs(11), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (link.isFavorite) Text(" · Favorito", color = pink, fontSize = fs(11), maxLines = 1)
                    }
                    if (link.collection != "Inbox") Text(link.collection, color = p.accent, fontSize = fs(11), modifier = Modifier.padding(top = 2.dp))
                    SummaryBlock(link, p, fs, summarizing)
                }
            }
        }
    }
}

@Composable
fun GlassBar(
    p: Palette, fs: (Int) -> TextUnit, selected: String, modifier: Modifier = Modifier,
    onHome: () -> Unit, onFavs: () -> Unit, onSearch: () -> Unit, onSettings: () -> Unit
) {
    GlassSurface(p, RoundedCornerShape(32.dp), modifier.fillMaxWidth().height(64.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            @Composable fun RowScope.Item(icon: ImageVector, label: String, on: Boolean, click: () -> Unit) {
                val c by animateColorAsState(if (on) p.accent else p.muted, label = "navItem")
                Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable(onClick = click).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icon, label, tint = c, modifier = Modifier.size(22.dp))
                    Text(label, color = c, fontSize = fs(10), fontWeight = FontWeight.SemiBold)
                }
            }
            Item(Icons.Default.Home, "Início", selected == "Todos", onHome)
            Item(Icons.Default.Favorite, "Favoritos", selected == "Favoritos", onFavs)
            Spacer(Modifier.width(76.dp))
            Item(Icons.Default.Search, "Buscar", false, onSearch)
            Item(Icons.Default.Settings, "Ajustes", false, onSettings)
        }
    }
}
