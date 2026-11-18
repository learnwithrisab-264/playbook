package com.sourabh.jarvistube.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

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
    surfaceContainerHighest = Color(0xFF2C2C2C),
    surfaceContainerLow = Color(0xFF161616),
    surfaceContainerLowest = Color(0xFF0B0B0B),
    surfaceBright = Color(0xFF2C2C2C),
    surfaceDim = Color(0xFF0F0F0F),
    surfaceTint = Color(0xFF0F0F0F),
    secondaryContainer = Color(0xFF272727),
    onSecondaryContainer = Color(0xFFF1F1F1),
    tertiaryContainer = Color(0xFF272727),
    onTertiaryContainer = Color(0xFFF1F1F1)
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
    surfaceContainerHighest = Color(0xFFEBEBEB),
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainerLowest = Color.White,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE6E6E6),
    surfaceTint = Color.White,
    secondaryContainer = Color(0xFFF2F2F2),
    onSecondaryContainer = Color(0xFF0F0F0F),
    tertiaryContainer = Color(0xFFF2F2F2),
    onTertiaryContainer = Color(0xFF0F0F0F)
)

/**
 * One type scale for the whole app: big, tight page titles; calm, readable body text; and
 * labels that never shout. Negative tracking on large sizes keeps headings crisp.
 */
private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-1).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.7).sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.35).sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = (-0.1).sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.1).sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp, letterSpacing = 0.1.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    // Small labels on top of video and in badges (time, speed, duration, notices).
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp)
)

/** Menus, cards, sheets and dialogs share these softer corners. */
private val AppShapes = androidx.compose.material3.Shapes(
    extraSmall = RoundedCornerShape(10.dp), // dropdown menus
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp) // dialogs, sheets
)

/** The only spacing sizes screens use. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** The only corner shapes screens use. */
object Corners {
    val tiny = RoundedCornerShape(4.dp) // duration and LIVE badges
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(14.dp) // thumbnails, cards
    val large = RoundedCornerShape(20.dp) // sheets, big cards, mini player
    val xl = RoundedCornerShape(26.dp) // hero cards
    val pill = CircleShape // chips, round buttons, avatars
}

object IconSize {
    val small = 18.dp // inside chips and rows of text
    val medium = 24.dp // normal buttons
    val large = 34.dp // big player buttons, empty-screen icons
}

/** Animation lengths in milliseconds, scaled by the Animation style in Settings (0 when off). */
object Motion {
    private val scale: Float get() = MotionStyle.of(com.sourabh.jarvistube.AppSettings.motionStyle).scale
    val FAST: Int get() = (150 * scale).toInt()
    val NORMAL: Int get() = (220 * scale).toInt()
}

/** PlayBook's own colours that don't change between light and dark mode. */
object Brand {
    val orange = Color(0xFFFF6D00)
    val live = Color(0xFFD63B3B)
}

/** Colours drawn on top of video, the same in light and dark mode. */
object OnVideo {
    val content = Color.White
    val scrimLight = Color.Black.copy(alpha = 0.4f)
    val scrim = Color.Black.copy(alpha = 0.6f)
    val scrimStrong = Color.Black.copy(alpha = 0.8f)
    val track = Color.White.copy(alpha = 0.3f)
}

/** Dark unless the Appearance setting says Light, or says follow the phone and the phone is light. */
@Composable
fun appIsDark(): Boolean = when (com.sourabh.jarvistube.AppSettings.appearance) {
    "light" -> false
    "system" -> isSystemInDarkTheme()
    else -> true
}

@Composable
fun JarvisTubeTheme(dark: Boolean = appIsDark(), content: @Composable () -> Unit) {
    val theme = EpicTheme.of(com.sourabh.jarvistube.AppSettings.themeKey)
    val target = theme.scheme(dark, Night, Day)
    // Switching themes glides between colours instead of flashing.
    val scheme = animateScheme(target)
    @Suppress("NAME_SHADOWING") val dark = dark && !theme.lightOnly
    // Status and navigation bars: dark bars get light icons, light bars get dark icons.
    val view = androidx.compose.ui.platform.LocalView.current
    // Read here (not inside SideEffect) so opening or closing the player recomposes this.
    val overVideo = SystemBars.overVideo
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                // Over the full player the status bar sits on black, so its icons stay white.
                isAppearanceLightStatusBars = !dark && !overVideo
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes) {
        // Screens drawn outside a Scaffold/Surface (player, search, login) would otherwise fall
        // back to black text and icons. Give the whole app the theme's content colour.
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}


@Composable
private fun animateScheme(t: androidx.compose.material3.ColorScheme): androidx.compose.material3.ColorScheme {
    val spec = androidx.compose.animation.core.tween<Color>(maxOf(Motion.NORMAL * 2, 1))
    @Composable fun a(c: Color) = androidx.compose.animation.animateColorAsState(c, spec, label = "scheme").value
    return t.copy(
        primary = a(t.primary), onPrimary = a(t.onPrimary),
        primaryContainer = a(t.primaryContainer), onPrimaryContainer = a(t.onPrimaryContainer),
        secondary = a(t.secondary), background = a(t.background), onBackground = a(t.onBackground),
        surface = a(t.surface), onSurface = a(t.onSurface), surfaceVariant = a(t.surfaceVariant),
        onSurfaceVariant = a(t.onSurfaceVariant), outline = a(t.outline), outlineVariant = a(t.outlineVariant),
        surfaceContainer = a(t.surfaceContainer), surfaceContainerHigh = a(t.surfaceContainerHigh),
        surfaceContainerHighest = a(t.surfaceContainerHighest), surfaceContainerLow = a(t.surfaceContainerLow),
        surfaceContainerLowest = a(t.surfaceContainerLowest), surfaceBright = a(t.surfaceBright),
        surfaceDim = a(t.surfaceDim), surfaceTint = a(t.surfaceTint),
        secondaryContainer = a(t.secondaryContainer), onSecondaryContainer = a(t.onSecondaryContainer)
    )
}


/** Lets a screen that draws black under the status bar (the full player) ask for white icons. */
object SystemBars {
    var overVideo by androidx.compose.runtime.mutableStateOf(false)
}
