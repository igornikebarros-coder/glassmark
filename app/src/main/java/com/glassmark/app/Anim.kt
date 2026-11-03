package com.glassmark.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Escala sutil enquanto o item está pressionado (usa o mesmo interactionSource do clique). */
fun Modifier.pressScale(source: MutableInteractionSource, pressed: Float = 0.97f): Modifier = composed {
    val isPressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (isPressed) pressed else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "press")
    graphicsLayer { scaleX = s; scaleY = s }
}

/** Coração com "pulo" ao favoritar e cor animada. */
@Composable
fun AnimatedHeart(favorite: Boolean, idle: Color, onClick: () -> Unit) {
    val scale = remember { Animatable(1f) }
    var first = remember { true }
    LaunchedEffect(favorite) {
        if (first) { first = false; return@LaunchedEffect }
        if (favorite) {
            scale.animateTo(1.4f, tween(110))
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        } else {
            scale.animateTo(0.8f, tween(90))
            scale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
        }
    }
    val tint by animateColorAsState(if (favorite) Color(0xFFFF729A) else idle, tween(220), label = "heart")
    Icon(
        if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
        contentDescription = if (favorite) "Remover dos favoritos" else "Adicionar aos favoritos", tint = tint,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp).scale(scale.value)
    )
}

/** Entrada suave (fade + zoom leve) para o conteúdo de diálogos próprios. */
@Composable
fun PopIn(content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220))
    ) { content() }
}
