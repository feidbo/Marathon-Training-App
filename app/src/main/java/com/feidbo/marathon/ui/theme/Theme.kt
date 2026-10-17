package com.feidbo.marathon.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Track Town green and gold.
val Green = Color(0xFF0E6B4D)
val GreenDark = Color(0xFF0A4A36)
val GreenLight = Color(0xFF8FD3B6)
val Gold = Color(0xFFFFC72C)
val GoldDeep = Color(0xFFE0A000)
val Coral = Color(0xFFE5674F)
val Sky = Color(0xFF3B82C4)

object StatusColors {
    val done = Color(0xFF1FA463)
    val partial = Color(0xFFF2A93B)
    val missed = Color(0xFFB0B7BF)
    val warn = Color(0xFFD9534F)
}

private val Light = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F0E3),
    onPrimaryContainer = GreenDark,
    secondary = GoldDeep,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFFFFF1C7),
    onSecondaryContainer = Color(0xFF4A3800),
    tertiary = Sky,
    background = Color(0xFFF7F3EE),
    onBackground = Color(0xFF1C1F1E),
    surface = Color.White,
    onSurface = Color(0xFF1C1F1E),
    surfaceVariant = Color(0xFFEFEAE3),
    onSurfaceVariant = Color(0xFF5A605D),
    error = StatusColors.warn,
)

private val Dark = darkColorScheme(
    primary = GreenLight,
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF0F4E3A),
    onPrimaryContainer = Color(0xFFD5F0E3),
    secondary = Gold,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF4A3800),
    onSecondaryContainer = Color(0xFFFFF1C7),
    tertiary = Color(0xFF8CC3F2),
    background = Color(0xFF111413),
    onBackground = Color(0xFFE3E7E4),
    surface = Color(0xFF1B1F1D),
    onSurface = Color(0xFFE3E7E4),
    surfaceVariant = Color(0xFF26302B),
    onSurfaceVariant = Color(0xFFB8C2BD),
    error = Color(0xFFFF8A80),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Black),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

val BigNumber = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)

@Composable
fun MarathonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        content = content,
    )
}
