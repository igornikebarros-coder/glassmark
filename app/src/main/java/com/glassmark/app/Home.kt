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

@Composable
fun SwipeLinkItem(
    link: LinkEntity, p: Palette, fs: (Int) -> TextUnit, opening: Boolean, modifier: Modifier = Modifier,
    onOpen: () -> Unit, onToggleFav: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit
) {
    val fav by rememberUpdatedState(onToggleFav)
    val del by rememberUpdatedState(onDelete)
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        when (v) {
            SwipeToDismissBoxValue.StartToEnd -> { fav(); false }
            SwipeToDismissBoxValue.EndToStart -> { del(); true }
            else -> false
        }
    })
    SwipeToDismissBox(
        state = state, modifier = modifier,
        backgroundContent = {
            val dir = state.dismissDirection
            val c by animateColorAsState(
                when (dir) { SwipeToDismissBoxValue.StartToEnd -> Color(0xFFFF729A); SwipeToDismissBoxValue.EndToStart -> Color(0xFFE5484D); else -> Color.Transparent }, label = "swipeBg"
            )
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(c).padding(horizontal = 24.dp),
                contentAlignment = if (dir == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (dir == SwipeToDismissBoxValue.StartToEnd) Icon(if (link.isFavorite) Icons.Default.FavoriteBorder else Icons.Default.Favorite, "Favoritar", tint = Color.White)
                else if (dir == SwipeToDismissBoxValue.EndToStart) Icon(Icons.Default.Delete, "Excluir", tint = Color.White)
            }
        }
    ) { LinkCard(link, p, fs, opening, onOpen, onToggleFav, onDelete, onEdit) }
}

@Composable
private fun LinkCard(
    link: LinkEntity, p: Palette, fs: (Int) -> TextUnit, opening: Boolean,
    onOpen: () -> Unit, onToggleFav: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val sc by animateFloatAsState(if (opening) 1.05f else 1f, spring(stiffness = 400f), label = "open")
    var menu by remember { mutableStateOf(false) }
    val d = domainOf(link.url)
    GlassSurface(
        p, RoundedCornerShape(22.dp),
        Modifier.fillMaxWidth().graphicsLayer { scaleX = sc; scaleY = sc }.pressScale(src, 0.98f).clip(RoundedCornerShape(22.dp))
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onOpen)
    ) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Favicon(d, 48.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("$d · ${relTime(link.savedAt)}", color = p.muted, fontSize = fs(11), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(link.title.ifBlank { d }, color = p.text, fontSize = fs(15), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                if (link.collection != "Inbox") Text(link.collection, color = p.accent, fontSize = fs(11), modifier = Modifier.padding(top = 2.dp))
            }
            AnimatedHeart(link.isFavorite, p.muted, onToggleFav)
            Box {
                Icon(Icons.Default.MoreVert, "Mais opções", tint = p.muted, modifier = Modifier.clip(CircleShape).clickable { menu = true }.padding(6.dp))
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = p.dialog) {
                    DropdownMenuItem(text = { Text("Editar", color = p.text) }, leadingIcon = { Icon(Icons.Default.Edit, null, tint = p.muted) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Excluir", color = p.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = p.error) }, onClick = { menu = false; onDelete() })
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
