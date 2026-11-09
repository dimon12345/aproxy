package com.aproxy.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object AProxyPalette {
    val Background = Color(0xFF070C17)
    val BackgroundTop = Color(0xFF0B1424)
    val Surface = Color(0xFF111B2E)
    val SurfaceVariant = Color(0xFF1A2740)
    val Outline = Color(0xFF273A59)

    val Cyan = Color(0xFF38BDF8)
    val Violet = Color(0xFFA78BFA)
    val Mint = Color(0xFF34D399)
    val Amber = Color(0xFFFBBF24)
    val Rose = Color(0xFFFB7185)

    val Text = Color(0xFFE8EFFA)
    val TextMuted = Color(0xFF93A6C2)
}

private val DarkColors = darkColorScheme(
    primary = AProxyPalette.Cyan,
    onPrimary = Color(0xFF04121F),
    primaryContainer = Color(0xFF0B3A52),
    onPrimaryContainer = AProxyPalette.Text,
    secondary = AProxyPalette.Violet,
    onSecondary = Color(0xFF160B2E),
    tertiary = AProxyPalette.Mint,
    background = AProxyPalette.Background,
    onBackground = AProxyPalette.Text,
    surface = AProxyPalette.Surface,
    onSurface = AProxyPalette.Text,
    surfaceVariant = AProxyPalette.SurfaceVariant,
    onSurfaceVariant = AProxyPalette.TextMuted,
    outline = AProxyPalette.Outline,
    error = AProxyPalette.Rose,
    onError = Color(0xFF1F0710),
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp,
    ),
    titleMedium = TextStyle(
        fontSize = 17.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodySmall = TextStyle(
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
    ),
)

@Composable
fun AProxyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        content = content,
    )
}
