package com.glassmark.app

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "Seguir o sistema"),
    LIGHT("light", "Claro"),
    DARK("dark", "Escuro"),
    AMOLED("amoled", "Escuro AMOLED");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class TextScale(val key: String, val multiplier: Float, val label: String) {
    SMALL("small", .88f, "Pequena"),
    DEFAULT("default", 1f, "Padrão"),
    LARGE("large", 1.15f, "Grande"),
    EXTRA_LARGE("xl", 1.3f, "Extra grande");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** Cores de toda a interface (tela principal, login, diálogos). */
data class Palette(
    val dark: Boolean,
    val canvas: Color,
    val gradient: List<Color>,
    val text: Color,
    val muted: Color,
    val glass: Color,
    val dialog: Color,
    val accent: Color,
    val divider: Color,
    val stroke: Color,
    val error: Color = Color(0xFFFF729A)
)

fun paletteFor(mode: ThemeMode): Palette = when (mode) {
    ThemeMode.LIGHT -> Palette(
        dark = false, canvas = Color(0xFFF4F6FA),
        gradient = listOf(Color(0xFFFFFFFF), Color(0xFFE8EDF7), Color(0xFFF4F6FA)),
        text = Color(0xFF171A24), muted = Color(0xFF737B8C), glass = Color(0xBFFFFFFF),
        dialog = Color(0xFFF7F8FC), accent = Color(0xFF5267B8),
        divider = Color(0x1A171A24), stroke = Color(0x22171A24)
    )
    ThemeMode.DARK -> Palette(
        dark = true, canvas = Color(0xFF080A10),
        gradient = listOf(Color(0xFF20283B), Color(0xFF080A10), Color(0xFF050609)),
        text = Color(0xFFF5F6FC), muted = Color(0xFF9DA7BC), glass = Color(0x20FFFFFF),
        dialog = Color(0xFF1B1D25), accent = Color(0xFFBFCBFF),
        divider = Color(0x22FFFFFF), stroke = Color(0x33FFFFFF)
    )
    ThemeMode.AMOLED -> Palette(
        dark = true, canvas = Color(0xFF000000),
        gradient = listOf(Color(0xFF000000), Color(0xFF000000), Color(0xFF000000)),
        text = Color(0xFFF5F6FC), muted = Color(0xFF9AA2B3), glass = Color(0x1AFFFFFF),
        dialog = Color(0xFF000000), accent = Color(0xFFBFCBFF),
        divider = Color(0x29FFFFFF), stroke = Color(0x40FFFFFF)
    )
}

val LocalPalette = staticCompositionLocalOf { paletteFor(ThemeMode.DARK) }

/** Resolve o tema efetivo: "Seguir o sistema" usa claro/escuro do aparelho (e AMOLED no escuro, se ativado). */
@Composable
fun resolveTheme(mode: ThemeMode, amoledInDark: Boolean): ThemeMode {
    if (mode != ThemeMode.SYSTEM) return mode
    return if (isSystemInDarkTheme()) (if (amoledInDark) ThemeMode.AMOLED else ThemeMode.DARK) else ThemeMode.LIGHT
}

@Composable
fun GlassmarkTheme(mode: ThemeMode, amoledInDark: Boolean, font: AppFont, content: @Composable () -> Unit) {
    val p = paletteFor(resolveTheme(mode, amoledInDark))
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !p.dark
                isAppearanceLightNavigationBars = !p.dark
            }
        }
    }
    val scheme = if (p.dark) darkColorScheme(
        background = p.canvas, surface = p.canvas, primary = p.accent, onPrimary = Color(0xFF0B1026),
        onBackground = p.text, onSurface = p.text
    ) else lightColorScheme(
        background = p.canvas, surface = p.canvas, primary = p.accent, onPrimary = Color.White,
        onBackground = p.text, onSurface = p.text
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = Typography().withFont(font.family), content = content)
    }
}
