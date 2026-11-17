package com.benji.partybell.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.benji.partybell.R
import kotlin.math.abs

val Itim = FontFamily(Font(R.font.itim))

object AppColors {
    val Background = Color(0xFF2E2925)
    val Panel = Color(0xFF221E1B)
    val Bubble = Color(0xFFCDC6BD)
    val Ring = Color.White
    val AvatarBackground = Color(0xFF8C8279)
    val Text = Color.White
    val Muted = Color(0xFFB8AFA6)
    val Shadow = Color(0x5C3C281E)
}

private val Palette = listOf(
    Color(0xFFE06A70),
    Color(0xFF5B87C1),
    Color(0xFFE39A55),
    Color(0xFF6FAE7F),
    Color(0xFFA07CC5),
    Color(0xFFD27AA6),
    Color(0xFF4FA6A8),
    Color(0xFFC98F5F),
)

private const val ROLE_TANK = 1
private const val ROLE_MELEE = 2
private const val ROLE_RANGED = 3
private const val ROLE_HEALER = 4

fun nameColor(name: String): Color = Palette[(abs(name.hashCode().toLong()) % Palette.size).toInt()]

fun roleColor(role: Int): Color = when (role) {
    ROLE_TANK -> Palette[1]
    ROLE_HEALER -> Palette[3]
    ROLE_MELEE, ROLE_RANGED -> Palette[0]
    else -> AppColors.AvatarBackground
}

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
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette[0],
            onPrimary = Color.White,
            background = AppColors.Background,
            surface = AppColors.Background,
            onBackground = AppColors.Text,
            onSurface = AppColors.Text,
        ),
        typography = Typography().withFont(Itim),
        content = content,
    )
}
