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
    val (a, b) = siteColors(domain)
    val letter: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(domain.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        }
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(Brush.linearGradient(listOf(a, b)))) {
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
    val src = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) p.accent else p.glass, label = "chipBg")
    val fg by animateColorAsState(if (selected) onAccentOf(p) else p.text, label = "chipFg")
    Row(
        Modifier.pressScale(src).clip(RoundedCornerShape(18.dp)).background(bg)
            .then(if (selected) Modifier else Modifier.background(Color.Transparent))
            .combinedClickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick, onLongClick = onLongClick)
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
    val (a, b) = siteColors(d)
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier.width(148.dp).height(98.dp).pressScale(src).clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(a, b)))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onOpen)
    ) {
        Box(Modifier.padding(10.dp)) { Favicon(d, 26.dp) }
        Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF729A), modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(16.dp))
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000)))).padding(10.dp, 16.dp, 10.dp, 8.dp)) {
            Text(link.title.ifBlank { d }, color = Color.White, fontSize = fs(11), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = fs(13))
        }
    }
}

enum class CardSize(val key: String, val label: String) {
    SMALL("small", "Pequeno"), MEDIUM("medium", "Médio"), LARGE("large", "Grande");
    companion object { fun fromKey(k: String?) = entries.firstOrNull { it.key == k } ?: MEDIUM }
}

/** Miniatura: imagem og:image (Coil) sobre um gradiente com o favicon, que serve de reserva. */
@Composable
fun Thumb(link: LinkEntity, modifier: Modifier, iconSize: Dp = 34.dp, corner: Dp = 16.dp) {
    val d = domainOf(link.url)
    val (a, b) = siteColors(d)
    Box(modifier.clip(RoundedCornerShape(corner)).background(Brush.linearGradient(listOf(a, b))), contentAlignment = Alignment.Center) {
        Favicon(d, iconSize)
        if (link.ogImage.isNotBlank() && link.ogImage != "-")
            AsyncImage(model = link.ogImage, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun SummaryBlock(link: LinkEntity, p: Palette, fs: (Int) -> TextUnit, busy: Boolean, onSummarize: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    if (link.summary.isNotBlank()) {
        Row(
            Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.accent.copy(alpha = .12f))
                .clickable { expanded = !expanded }.padding(8.dp).animateContentSize()
        ) {
            Icon(Icons.Default.AutoAwesome, null, tint = p.accent, modifier = Modifier.size(14.dp).padding(top = 2.dp))
            Text(link.summary, color = p.text, fontSize = fs(12), lineHeight = fs(16), maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
        }
    } else if (busy) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = p.accent)
            Text("Resumindo…", color = p.muted, fontSize = fs(11), modifier = Modifier.padding(start = 8.dp))
        }
    } else {
        Row(Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onSummarize).padding(vertical = 4.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, null, tint = p.accent, modifier = Modifier.size(14.dp))
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
                when (dir) { SwipeToDismissBoxValue.StartToEnd -> Color(0xFF5B8DEF); SwipeToDismissBoxValue.EndToStart -> Color(0xFFE5484D); else -> Color.Transparent }, label = "swipeBg"
            )
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(c).padding(horizontal = 24.dp),
                contentAlignment = if (dir == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (dir == SwipeToDismissBoxValue.StartToEnd) Icon(if (hiddenMode) Icons.Default.Visibility else Icons.Default.VisibilityOff, "Ocultar", tint = Color.White)
                else if (dir == SwipeToDismissBoxValue.EndToStart) Icon(Icons.Default.Delete, "Apagar", tint = Color.White)
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
    val sc by animateFloatAsState(if (opening) 1.05f else 1f, spring(stiffness = 400f), label = "open")
    var menu by remember { mutableStateOf(false) }
    val d = domainOf(link.url)
    val banner = grid || size == CardSize.LARGE
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
        p, RoundedCornerShape(22.dp),
        Modifier.fillMaxWidth().graphicsLayer { scaleX = sc; scaleY = sc }.pressScale(src, 0.98f).clip(RoundedCornerShape(22.dp))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onOpen)
    ) {
        Column {
            if (banner) {
                val h = if (grid) when (size) { CardSize.SMALL -> 70.dp; CardSize.MEDIUM -> 100.dp; CardSize.LARGE -> 140.dp } else 160.dp
                Thumb(link, Modifier.fillMaxWidth().height(h).padding(8.dp), iconSize = if (h < 90.dp) 26.dp else 40.dp, corner = 16.dp)
            }
            Row(Modifier.padding(start = 12.dp, top = if (banner) 2.dp else 12.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
                if (!banner) {
                    val s = if (size == CardSize.SMALL) 52.dp else 84.dp
                    Thumb(link, Modifier.size(s), iconSize = if (size == CardSize.SMALL) 26.dp else 36.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("$d · ${relTime(link.savedAt)}", color = p.muted, fontSize = fs(11), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(link.title.ifBlank { d }, color = p.text, fontSize = fs(if (grid) 13 else 15), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    if (link.collection != "Inbox") Text(link.collection, color = p.accent, fontSize = fs(11), modifier = Modifier.padding(top = 2.dp))
                    SummaryBlock(link, p, fs, summarizing, onSummarize)
                    if (grid) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        AnimatedHeart(link.isFavorite, p.muted, onToggleFav); moreMenu()
                    }
                }
                if (!grid) { AnimatedHeart(link.isFavorite, p.muted, onToggleFav); moreMenu() }
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
