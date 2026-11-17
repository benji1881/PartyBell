package com.benji.partybell.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.benji.partybell.R

@Immutable
data class Palette(
    val background: Color,
    val card: Color,
    val sage: Color,
    val onSage: Color,
    val text: Color,
    val muted: Color,
    val line: Color,
    val yellow: Color,
    val red: Color,
)

private val Light = Palette(
    background = Color(0xFFF3EFE6),
    card = Color(0xFFFBF9F4),
    sage = Color(0xFF5E7F63),
    onSage = Color.White,
    text = Color(0xFF2B2F2A),
    muted = Color(0xFF7A7F76),
    line = Color(0xFFE4DFD3),
    yellow = Color(0xFFE8C468),
    red = Color(0xFFD9776F),
)

private val Dark = Palette(
    background = Color(0xFF171A17),
    card = Color(0xFF222622),
    sage = Color(0xFF5E7F63),
    onSage = Color(0xFFF4F1EA),
    text = Color(0xFFECE8DF),
    muted = Color(0xFF9AA096),
    line = Color(0xFF2F342F),
    yellow = Color(0xFFD9B65A),
    red = Color(0xFFD9776F),
)

private val LocalPalette = staticCompositionLocalOf { Light }

val AppColors: Palette
    @Composable
    @ReadOnlyComposable
    get() = LocalPalette.current

val Cream = Color(0xFFF7F3EA)

private const val ROLE_TANK = 1
private const val ROLE_MELEE = 2
private const val ROLE_RANGED = 3
private const val ROLE_HEALER = 4

fun roleColor(role: Int): Color = when (role) {
    ROLE_TANK -> Color(0xFF5B7DA8)
    ROLE_HEALER -> Color(0xFF5E8F62)
    ROLE_MELEE, ROLE_RANGED -> Color(0xFFB5615C)
    else -> Color(0xFF8A857C)
}

private val RoleOrder = listOf(ROLE_TANK, ROLE_HEALER, ROLE_MELEE, ROLE_RANGED)

fun roleRank(role: Int): Int = RoleOrder.indexOf(role).let { if (it < 0) RoleOrder.size else it }

@OptIn(ExperimentalTextApi::class)
private fun variable(resource: Int, weight: Int) =
    Font(resource, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Jakarta = FontFamily(listOf(400, 500, 600, 700, 800).map { variable(R.font.plus_jakarta_sans, it) })

val Fraunces = FontFamily(variable(R.font.fraunces, 600))

private fun Typography.withFont(font: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = font),
    displayMedium = displayMedium.copy(fontFamily = font),
    displaySmall = displaySmall.copy(fontFamily = font),
    headlineLarge = headlineLarge.copy(fontFamily = font),
    headlineMedium = headlineMedium.copy(fontFamily = font),
    headlineSmall = headlineSmall.copy(fontFamily = font),
    titleLarge = titleLarge.copy(fontFamily = font),
    titleMedium = titleMedium.copy(fontFamily = font),
    titleSmall = titleSmall.copy(fontFamily = font),
    bodyLarge = bodyLarge.copy(fontFamily = font),
    bodyMedium = bodyMedium.copy(fontFamily = font),
    bodySmall = bodySmall.copy(fontFamily = font),
    labelLarge = labelLarge.copy(fontFamily = font),
    labelMedium = labelMedium.copy(fontFamily = font),
    labelSmall = labelSmall.copy(fontFamily = font),
)

@Composable
fun PartyBellTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val palette = if (dark) Dark else Light
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = palette.sage,
        onPrimary = palette.onSage,
        background = palette.background,
        onBackground = palette.text,
        surface = palette.card,
        onSurface = palette.text,
        surfaceVariant = palette.line,
        onSurfaceVariant = palette.muted,
        surfaceContainerHighest = palette.line,
        outline = palette.line,
        primaryContainer = palette.sage,
        onPrimaryContainer = palette.onSage,
    )
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = Typography().withFont(Jakarta), content = content)
    }
}
