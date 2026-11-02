package com.sourabh.jarvistube.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Night = darkColorScheme(
    primary = Color(0xFFFF8C00),
    onPrimary = Color(0xFF2B1400),
    primaryContainer = Color(0xFF5A2A00),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFFFC107),
    // Neutral near-black background; the orange accent is PlayBook's.
    background = Color(0xFF0F0F0F),
    onBackground = Color(0xFFF1F1F1),
    surface = Color(0xFF0F0F0F),
    onSurface = Color(0xFFF1F1F1),
    surfaceVariant = Color(0xFF272727),
    onSurfaceVariant = Color(0xFFAAAAAA),
    outline = Color(0xFF3F3F3F),
    outlineVariant = Color(0xFF2A2A2A),
    error = Color(0xFFFF6B6B),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF212121),
    surfaceContainerHighest = Color(0xFF2C2C2C)
)

private val Day = lightColorScheme(
    primary = Color(0xFFD84315), // deeper than the logo orange so white text on it stays readable
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0B2),
    onPrimaryContainer = Color(0xFF3E1F00),
    secondary = Color(0xFFF57C00),
    background = Color.White,
    onBackground = Color(0xFF0F0F0F),
    surface = Color.White,
    onSurface = Color(0xFF0F0F0F),
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF606060),
    outline = Color(0xFFCCCCCC),
    outlineVariant = Color(0xFFE5E5E5),
    error = Color(0xFFD63B3B),
    surfaceContainer = Color(0xFFF7F7F7),
    surfaceContainerHigh = Color(0xFFF2F2F2),
    surfaceContainerHighest = Color(0xFFEBEBEB)
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
)

@Composable
fun JarvisTubeTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (dark) Night else Day
    MaterialTheme(colorScheme = scheme, typography = AppTypography) {
        // Screens drawn outside a Scaffold/Surface (player, search, login) would otherwise fall
        // back to black text and icons. Give the whole app the theme's content colour.
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}
