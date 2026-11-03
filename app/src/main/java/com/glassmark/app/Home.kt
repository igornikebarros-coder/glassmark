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
    val fg by animateColorAsState(if (selected) p.text else p.muted, label = "tabFg")
    val line by animateColorAsState(if (selected) p.accent else Color.Transparent, label = "tabLine")
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
private fun SummaryBlock(link: LinkEntity, p: Palette, fs: (Int) -> TextUnit, busy: Boolean, onSummarize: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    if (link.summary.isNotBlank()) {
        Row(
            Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { expanded = !expanded }.animateContentSize()
        ) {
            Icon(Icons.Default.AutoAwesome, null, tint = p.accent, modifier = Modifier.size(13.dp).padding(top = 2.dp))
            Text(link.summary, color = p.text.copy(alpha = .78f), fontSize = fs(12), lineHeight = fs(17), maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
        }
    } else if (busy) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp, color = p.accent)
            Text("Resumindo…", color = p.muted, fontSize = fs(11), modifier = Modifier.padding(start = 8.dp))
        }
    } else {
        Row(Modifier.padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onSummarize).padding(vertical = 4.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, null, tint = p.accent, modifier = Modifier.size(13.dp))
            Text("Resumir com IA", color = p.accent, fontSize = fs(11), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
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
    ) { LinkCard(link, p, fs, opening, grid, size, summarizing, hiddenMode, onOpen, onToggleFav, onDelete, onEdit, onHide, onSummarize) }
}

@Composable
private fun LinkCard(
    link: LinkEntity, p: Palette, fs: (Int) -> TextUnit, opening: Boolean, grid: Boolean, size: CardSize, summarizing: Boolean, hiddenMode: Boolean,
    onOpen: () -> Unit, onToggleFav: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit, onHide: () -> Unit, onSummarize: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val sc by animateFloatAsState(if (opening) 1.04f else 1f, spring(stiffness = 400f), label = "open")
    var menu by remember { mutableStateOf(false) }
    val d = domainOf(link.url)
    val hasOg = link.ogImage.isNotBlank() && link.ogImage != "-"
    val banner = hasOg && (grid || size == CardSize.LARGE)
    val sideThumb = hasOg && !banner
    val shape = RoundedCornerShape(18.dp)
    val title = link.title.ifBlank { d }
    val meta = "$d · ${relTime(link.savedAt)}"
    val moreMenu: @Composable () -> Unit = {
        Box {
            Icon(Icons.Default.MoreVert, "Mais opções", tint = p.muted, modifier = Modifier.clip(CircleShape).clickable { menu = true }.padding(6.dp))
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = p.dialog) {
                if (link.summary.isBlank()) DropdownMenuItem(text = { Text("Resumir com IA", color = p.text) }, leadingIcon = { Icon(Icons.Default.AutoAwesome, null, tint = p.accent) }, onClick = { menu = false; onSummarize() })
                DropdownMenuItem(text = { Text("Editar", color = p.text) }, leadingIcon = { Icon(Icons.Default.Edit, null, tint = p.muted) }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text(if (hiddenMode) "Mostrar" else "Ocultar", color = p.text) }, leadingIcon = { Icon(if (hiddenMode) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = p.muted) }, onClick = { menu = false; onHide() })
                DropdownMenuItem(text = { Text("Apagar", color = p.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = p.error) }, onClick = { menu = false; onDelete() })
            }
        }
    }
    GlassSurface(
        p, shape,
        Modifier.fillMaxWidth().graphicsLayer { scaleX = sc; scaleY = sc }.pressScale(src, 0.98f).clip(shape)
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onOpen)
    ) {
        Column {
            if (banner) {
                val h = if (grid) when (size) { CardSize.SMALL -> 70.dp; CardSize.MEDIUM -> 96.dp; CardSize.LARGE -> 130.dp } else 150.dp
                Thumb(link, Modifier.fillMaxWidth().height(h).padding(8.dp))
            }
            if (grid) {
                Column(Modifier.padding(start = 12.dp, end = 4.dp, top = if (banner) 0.dp else 12.dp, bottom = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Favicon(d, 14.dp)
                        Text(meta, color = p.muted, fontSize = fs(11), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp, end = 8.dp))
                    }
                    Text(title, color = p.text, fontSize = fs(13), fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, end = 8.dp))
                    if (link.collection != "Inbox") Text(link.collection, color = p.accent, fontSize = fs(11), modifier = Modifier.padding(top = 2.dp))
                    Box(Modifier.padding(end = 8.dp)) { SummaryBlock(link, p, fs, summarizing, onSummarize) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        AnimatedHeart(link.isFavorite, p.muted, onToggleFav); moreMenu()
                    }
                }
            } else {
                Row(Modifier.padding(start = 12.dp, top = if (banner) 2.dp else 12.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
                    if (sideThumb) Thumb(link, Modifier.size(if (size == CardSize.SMALL) 56.dp else 72.dp)) else Box(Modifier.padding(top = 2.dp)) { Favicon(d, 30.dp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, color = p.text, fontSize = fs(15), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (sideThumb) { Favicon(d, 14.dp); Spacer(Modifier.width(6.dp)) }
                            Text(meta, color = p.muted, fontSize = fs(11), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (link.collection != "Inbox") Text(link.collection, color = p.accent, fontSize = fs(11), modifier = Modifier.padding(top = 2.dp))
                        SummaryBlock(link, p, fs, summarizing, onSummarize)
                    }
                    AnimatedHeart(link.isFavorite, p.muted, onToggleFav); moreMenu()
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
