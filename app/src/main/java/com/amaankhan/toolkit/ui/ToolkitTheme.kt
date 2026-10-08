@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.amaankhan.toolkit.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.amaankhan.toolkit.R
import com.amaankhan.toolkit.data.ThemeChoice

private val Inter = FontFamily(
    Font(R.font.inter, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.inter, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)
// Approved semantic palettes; appearance remains independent of the palette preference.
fun toolkitColors(palette: com.amaankhan.toolkit.data.Palette, dark: Boolean): androidx.compose.material3.ColorScheme {
    val colors = when (palette) {
        com.amaankhan.toolkit.data.Palette.PLATINUM -> if (dark)
            listOf(0xFF101113,0xFF191B1E,0xFFF3F3F1,0xFFB0B2B5,0xFF34373C,0xFFE1E2DE,0xFF2B2E32,0xFF17191C)
            else listOf(0xFFF2F2EF,0xFFFFFFFF,0xFF1B1D20,0xFF606368,0xFFD7D9D5,0xFF24292F,0xFFE5E7E3,0xFFFFFFFF)
        com.amaankhan.toolkit.data.Palette.CHAMPAGNE -> if (dark)
            listOf(0xFF141210,0xFF201D19,0xFFF4EEE4,0xFFBEB4A5,0xFF433B30,0xFFD9BC89,0xFF342B1F,0xFF211A12)
            else listOf(0xFFF4F0E8,0xFFFFFDFA,0xFF29231C,0xFF706352,0xFFDED4C4,0xFF755319,0xFFEEE2CB,0xFFFFFFFF)
        com.amaankhan.toolkit.data.Palette.JADE -> if (dark)
            listOf(0xFF0D1514,0xFF152220,0xFFEAF3EF,0xFFA8BDB5,0xFF30473F,0xFF9CCCB7,0xFF223C32,0xFF11241B)
            else listOf(0xFFEFF4F0,0xFFFFFFFF,0xFF1B3026,0xFF526C5E,0xFFCDDCD1,0xFF285D43,0xFFDCECDF,0xFFFFFFFF)
    }.map { Color(it) }
    val (bg, surface, text, muted, line) = colors
    val accent = colors[5]; val tint = colors[6]; val onAccent = colors[7]
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(primary = accent, onPrimary = onAccent, primaryContainer = tint, onPrimaryContainer = text,
        secondary = accent, onSecondary = onAccent, secondaryContainer = tint, onSecondaryContainer = text,
        tertiary = accent, onTertiary = onAccent, tertiaryContainer = tint, onTertiaryContainer = text,
        background = bg, onBackground = text, surface = surface, onSurface = text,
        surfaceVariant = tint, onSurfaceVariant = muted, outline = muted, outlineVariant = line,
        surfaceTint = Color.Transparent, inverseSurface = text, inverseOnSurface = bg, inversePrimary = onAccent,
        surfaceContainerLowest = bg, surfaceContainerLow = surface, surfaceContainer = surface,
        surfaceContainerHigh = tint, surfaceContainerHighest = tint, surfaceBright = surface, surfaceDim = bg)
}
private val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp),
)
@Composable fun isDarkTheme(choice: ThemeChoice) = choice == ThemeChoice.DARK || choice == ThemeChoice.SYSTEM && isSystemInDarkTheme()
@Composable fun ToolkitTheme(choice: ThemeChoice, palette: com.amaankhan.toolkit.data.Palette = com.amaankhan.toolkit.data.Palette.PLATINUM, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = toolkitColors(palette, isDarkTheme(choice)), typography = AppTypography) {
        InputFocusArea(Modifier.fillMaxSize(), content)
    }
}
