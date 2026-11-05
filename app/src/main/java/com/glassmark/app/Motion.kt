package com.glassmark.app

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Preferência de movimento reduzido do sistema (escala de animação 0 / "Remover animações"). */
object Motion {
    var reduced by mutableStateOf(false)
    fun refresh(ctx: Context) {
        val r = ctx.contentResolver
        fun g(k: String) = runCatching { Settings.Global.getFloat(r, k, 1f) }.getOrDefault(1f)
        reduced = g(Settings.Global.ANIMATOR_DURATION_SCALE) == 0f || g(Settings.Global.TRANSITION_ANIMATION_SCALE) == 0f
    }
}

/** Mola padrão do app; vira "snap" com movimento reduzido. */
fun <T> motionSpring(damping: Float = 0.82f, stiffness: Float = Spring.StiffnessMediumLow): FiniteAnimationSpec<T> =
    if (Motion.reduced) snap() else spring(dampingRatio = damping, stiffness = stiffness)

/** Entrada escalonada (fade + subida + escala) só quando [epoch] muda (primeira carga/troca de aba). */
@Composable
fun Modifier.staggerIn(index: Int, epoch: Int, armed: Boolean): Modifier {
    val anim = armed && !Motion.reduced && index < 14
    val a = remember(epoch) { Animatable(if (anim) 0f else 1f) }
    LaunchedEffect(epoch) {
        if (anim) { delay(index * 35L); a.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) }
    }
    return graphicsLayer {
        val v = a.value
        alpha = v.coerceIn(0f, 1f); translationY = (1f - v) * 28.dp.toPx(); val s = .96f + .04f * v; scaleX = s; scaleY = s
    }
}

/** Placeholder com brilho enquanto os dados carregam. */
@Composable
fun SkeletonList(p: Palette, grid: Boolean, modifier: Modifier = Modifier) {
    val x = if (Motion.reduced) remember { mutableFloatStateOf(0.5f) } else rememberInfiniteTransition(label = "shimmer")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "sh")
    val base = p.text.copy(alpha = .07f); val hi = p.text.copy(alpha = .14f)
    val brush = Brush.linearGradient(listOf(base, hi, base), start = Offset(x.value * 1600f - 600f, 0f), end = Offset(x.value * 1600f, 300f))
    Column(modifier.fillMaxSize().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(6) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(if (grid) 2 else 1) {
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(base).padding(14.dp)) {
                        Box(Modifier.fillMaxWidth(.8f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(brush))
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth(.45f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(brush))
                    }
                }
            }
        }
    }
}
