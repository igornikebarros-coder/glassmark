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
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.positionInWindow

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
    val line by animateColorAsState(if (selected) p.accent else Color.Transparent, motionSpring(stiffness = 900f), label = "tabLine")
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
    onOpen: () -> Unit, onToggleFav: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit, onHide: () -> Unit, onSummarize: () -> Unit,
    onToggleRead: () -> Unit = {}, onRemind: () -> Unit = {}, onTag: (String) -> Unit = {}
) {
    val hide by rememberUpdatedState(onHide)
    val del by rememberUpdatedState(onDelete)
    val swipeHaptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val state = rememberSwipeToDismissBoxState(positionalThreshold = { it * 0.35f }, confirmValueChange = { v ->
        when (v) {
            SwipeToDismissBoxValue.StartToEnd -> { swipeHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); hide(); true }
            SwipeToDismissBoxValue.EndToStart -> { swipeHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); del(); true }
            else -> false
        }
    })
    LaunchedEffect(state) {
        snapshotFlow { state.targetValue }.collect { if (it != SwipeToDismissBoxValue.Settled) swipeHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) }
    }
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
        LaunchedEffect(Unit) { snapshotFlow { CardMenu.req }.collect { if (it == null) menu = false } }
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        Box(Modifier.onGloballyPositioned { bounds = it.boundsInWindow() }.graphicsLayer { alpha = if (menu && CardMenu.req != null) 0f else 1f }) {
            LinkCard(link, p, fs, opening, grid, size, summarizing, hiddenMode, onOpen, onTag = onTag,
                onLongPress = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); menu = true })
        }
        if (menu) {
            CardMenu.req = CardMenuReq(link, bounds, hiddenMode,
                card = { LinkCard(link, p, fs, false, grid, size, summarizing, hiddenMode, onOpen = {}, onLongPress = null) },
                onOpen = onOpen, onToggleFav = onToggleFav, onDelete = onDelete, onEdit = onEdit, onHide = onHide, onSummarize = onSummarize, onToggleRead = onToggleRead, onRemind = onRemind,
                onClosed = { menu = false })
        }
    }
}

private class MenuAction(val icon: ImageVector, val label: String, val danger: Boolean = false, val run: () -> Unit)

class CardMenuReq(
    val link: LinkEntity, val bounds: androidx.compose.ui.geometry.Rect, val hiddenMode: Boolean, val card: @Composable () -> Unit,
    val onOpen: () -> Unit, val onToggleFav: () -> Unit, val onDelete: () -> Unit, val onEdit: () -> Unit, val onHide: () -> Unit, val onSummarize: () -> Unit, val onToggleRead: () -> Unit, val onRemind: () -> Unit,
    val onClosed: () -> Unit
)

object CardMenu { var req by mutableStateOf<CardMenuReq?>(null) }

/** Menu de contexto estilo iOS desenhado na mesma hierarquia: scrim + card em foco ampliado + bolha de ações. */
@Composable
fun CardMenuOverlay(p: Palette, fs: (Int) -> TextUnit, progress: Float, closing: Boolean, onClose: (after: () -> Unit) -> Unit) {
    val r = CardMenu.req ?: return
    val link = r.link
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var showSummary by remember(r) { mutableStateOf(false) }
    androidx.activity.compose.BackHandler { onClose {} }
    val close: (() -> Unit) -> Unit = { if (!closing) onClose(it) }
    val actions = buildList {
        add(MenuAction(Icons.Default.OpenInNew, "Abrir") { r.onOpen() })
        add(MenuAction(Icons.Default.Share, "Compartilhar") {
            val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, link.url)
                if (link.title.isNotBlank()) putExtra(android.content.Intent.EXTRA_SUBJECT, link.title)
            }
            ctx.startActivity(android.content.Intent.createChooser(i, "Compartilhar link"))
        })
        add(MenuAction(if (link.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (link.isFavorite) "Desfavoritar" else "Favoritar") { r.onToggleFav() })
        add(MenuAction(Icons.Default.AutoAwesome, if (link.summary.isBlank()) "Resumir" else "Resumo") { r.onSummarize() })
        add(MenuAction(if (link.isRead) Icons.Default.MarkEmailUnread else Icons.Default.DoneAll, if (link.isRead) "Não lido" else "Lido") { r.onToggleRead() })
        add(MenuAction(Icons.Default.Alarm, if (link.remindAt > System.currentTimeMillis()) "Lembrete" else "Lembrar") { r.onRemind() })
        add(MenuAction(Icons.Default.Edit, "Editar") { r.onEdit() })
        add(MenuAction(if (r.hiddenMode) Icons.Default.Visibility else Icons.Default.VisibilityOff, if (r.hiddenMode) "Mostrar" else "Ocultar") { r.onHide() })
        add(MenuAction(Icons.Default.Delete, "Apagar", danger = true) { r.onDelete() })
    }
    val t = progress
    val scrimA = if (p.dark) .55f else .30f
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
            .background(Color.Black.copy(alpha = scrimA * t.coerceIn(0f, 1f)))
            .pointerInput(r) { detectTapGestures { close {} } }
    ) {
        val screenH = with(density) { maxHeight.toPx() }
        val pad = with(density) { 14.dp.toPx() }
        val bubbleH = with(density) { 84.dp.toPx() }
        val topSafe = with(density) { 44.dp.toPx() }
        val grow = 1.06f
        val left = r.bounds.left - origin.x
        val top = r.bounds.top - origin.y
        val bottom = r.bounds.bottom - origin.y
        val extra = r.bounds.height * (grow - 1f) / 2f
        val above = top - extra - pad - bubbleH > topSafe
        val bubbleY = (if (above) top - extra - pad - bubbleH else bottom + extra + pad)
            .coerceIn(topSafe, (screenH - bubbleH - pad).coerceAtLeast(topSafe))
        Box(
            Modifier.offset { androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()) }
                .width(with(density) { r.bounds.width.toDp() })
                .graphicsLayer { val s = 1f + (grow - 1f) * t; scaleX = s; scaleY = s; shadowElevation = with(density) { 22.dp.toPx() } * t.coerceIn(0f, 1f); shape = RoundedCornerShape(18.dp); clip = false }
                .pointerInput(r) { detectTapGestures { close(r.onOpen) } }
        ) { r.card() }
        Column(
            Modifier.align(Alignment.TopCenter)
                .offset { androidx.compose.ui.unit.IntOffset(0, bubbleY.toInt()) }
                .widthIn(max = maxWidth - 24.dp)
                .graphicsLayer {
                    val s = .6f + .4f * t; scaleX = s; scaleY = s; alpha = t.coerceIn(0f, 1f)
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, if (above) 1f else 0f)
                }
                .pointerInput(r) { detectTapGestures { } }
        ) {
            Surface(color = p.dialog, shape = RoundedCornerShape(26.dp), border = BorderStroke(1.dp, p.stroke), shadowElevation = 12.dp) {
                Row(
                    Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    actions.forEach { a ->
                        val tint = if (a.danger) p.error else p.text
                        val isResumo = a.label == "Resumo"
                        Column(
                            Modifier.width(58.dp).clip(RoundedCornerShape(14.dp))
                                .clickable { if (isResumo) showSummary = !showSummary else close(a.run) }.padding(vertical = 2.dp),
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
            if (showSummary) Surface(color = p.dialog, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, p.stroke), shadowElevation = 12.dp, modifier = Modifier.padding(top = 8.dp)) {
                Text(link.summary, color = p.text, fontSize = fs(13), lineHeight = fs(19),
                    modifier = Modifier.heightIn(max = 260.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(16.dp))
            }
        }
    }
}

@Composable
private fun LinkCard(
    link: LinkEntity, p: Palette, fs: (Int) -> TextUnit, opening: Boolean, grid: Boolean, size: CardSize, summarizing: Boolean, hiddenMode: Boolean,
    onOpen: () -> Unit, onLongPress: (() -> Unit)?, cardModifier: Modifier = Modifier, onTag: (String) -> Unit = {}
) {
    val src = remember { MutableInteractionSource() }
    val sc by animateFloatAsState(if (opening) 1.04f else 1f, motionSpring(stiffness = 400f), label = "open")
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
                    val tags = link.tagList()
                    if (tags.isNotEmpty()) Row(Modifier.padding(top = 6.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.forEach { t ->
                            Text("#$t", color = p.muted, fontSize = fs(11), fontWeight = FontWeight.SemiBold, maxLines = 1,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(p.text.copy(alpha = .07f)).clickable { onTag(t) }.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
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
