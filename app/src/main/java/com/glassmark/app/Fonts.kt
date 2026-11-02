@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.glassmark.app

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/** Fontes embutidas (licença OFL, funcionam offline). SYSTEM usa a fonte padrão do aparelho. */
enum class AppFont(val key: String, val label: String) {
    SYSTEM("system", "Padrão do sistema"),
    INTER("inter", "Inter"),
    ROBOTO("roboto", "Roboto"),
    POPPINS("poppins", "Poppins"),
    NUNITO("nunito", "Nunito"),
    MONTSERRAT("montserrat", "Montserrat"),
    LORA("lora", "Lora (serifada)");

    val family: FontFamily? by lazy {
        when (this) {
            SYSTEM -> null
            INTER -> variable(R.font.inter)
            ROBOTO -> variable(R.font.roboto)
            NUNITO -> variable(R.font.nunito)
            MONTSERRAT -> variable(R.font.montserrat)
            LORA -> variable(R.font.lora)
            POPPINS -> FontFamily(
                Font(R.font.poppins_regular, FontWeight.Normal),
                Font(R.font.poppins_medium, FontWeight.Medium),
                Font(R.font.poppins_semibold, FontWeight.SemiBold),
                Font(R.font.poppins_bold, FontWeight.Bold)
            )
        }
    }

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

private fun variable(res: Int) = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

/** Aplica a família a todos os estilos de texto do Material. */
fun Typography.withFont(f: FontFamily?): Typography {
    if (f == null) return this
    fun TextStyle.w() = copy(fontFamily = f)
    return copy(
        displayLarge = displayLarge.w(), displayMedium = displayMedium.w(), displaySmall = displaySmall.w(),
        headlineLarge = headlineLarge.w(), headlineMedium = headlineMedium.w(), headlineSmall = headlineSmall.w(),
        titleLarge = titleLarge.w(), titleMedium = titleMedium.w(), titleSmall = titleSmall.w(),
        bodyLarge = bodyLarge.w(), bodyMedium = bodyMedium.w(), bodySmall = bodySmall.w(),
        labelLarge = labelLarge.w(), labelMedium = labelMedium.w(), labelSmall = labelSmall.w()
    )
}
