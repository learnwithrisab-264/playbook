package com.sourabh.jarvistube.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.AppSettings
import kotlin.math.cos
import kotlin.math.sin

/**
 * PlayBook's themes, each a place from the Ramayana and the Mahabharata. The colours carry the
 * place's meaning, and the name and one-line meaning show in Settings › Look & feel.
 */
enum class EpicTheme(
    val key: String,
    val title: String,
    val meaning: String,
    val primary: Color,
    val secondary: Color,
    /** Dark background and raised surface. */
    val night: Color,
    val raised: Color,
    /** Designed as a light theme (Ayodhya): always light, whatever Appearance says. */
    val lightOnly: Boolean = false
) {
    Agni("agni", "Agni", "The sacred flame. Learning ignites.", Color(0xFFFF8C00), Color(0xFFFFC107), Color(0xFF0F0F0F), Color(0xFF272727)),
    Kurukshetra("kurukshetra", "Kurukshetra", "Arjuna's focus: one target, nothing else.", Color(0xFFFF5A5F), Color(0xFFD4A373), Color(0xFF120C0C), Color(0xFF2A1D1D)),
    Dwarka("dwarka", "Dwarka", "Krishna's city on the sea. Calm depth.", Color(0xFF2EC4C6), Color(0xFFE9C46A), Color(0xFF071417), Color(0xFF14282C)),
    Vrindavan("vrindavan", "Vrindavan", "The peacock feather and the flute. Joyful curiosity.", Color(0xFF4D8DFF), Color(0xFF16C79A), Color(0xFF0A1020), Color(0xFF18213A)),
    Lanka("lanka", "Lanka", "The golden city. Every moment precious.", Color(0xFFE6B422), Color(0xFFF4D58D), Color(0xFF0B0A07), Color(0xFF241F17)),
    Panchavati("panchavati", "Panchavati", "Rama's forest years. Simple and steady.", Color(0xFF7BC67E), Color(0xFFC8A27A), Color(0xFF0C120C), Color(0xFF1D291D)),
    Ayodhya("ayodhya", "Ayodhya", "Dharma and order. Clear as morning.", Color(0xFFC2410C), Color(0xFF8B1E3F), Color(0xFFFBF6EC), Color(0xFFF1E7D6), lightOnly = true);

    companion object {
        fun of(key: String): EpicTheme = entries.firstOrNull { it.key == key } ?: Agni
    }
}

private fun onColor(c: Color) = if (c.luminance() > 0.45f) Color(0xFF14110C) else Color.White

/** A full Material colour scheme from a theme's few colours. Agni keeps PlayBook's original look. */
fun EpicTheme.scheme(dark: Boolean, agniNight: ColorScheme, agniDay: ColorScheme): ColorScheme {
    if (this == EpicTheme.Agni) return if (dark) agniNight else agniDay
    val useDark = dark && !lightOnly
    return if (useDark) darkColorScheme(
        primary = primary,
        onPrimary = onColor(primary),
        primaryContainer = lerp(night, primary, 0.35f),
        onPrimaryContainer = lerp(Color.White, primary, 0.15f),
        secondary = secondary,
        onSecondary = onColor(secondary),
        tertiary = secondary,
        background = night,
        onBackground = Color(0xFFF1EFEA),
        surface = night,
        onSurface = Color(0xFFF1EFEA),
        surfaceVariant = raised,
        onSurfaceVariant = lerp(Color(0xFFAAAAAA), primary, 0.12f),
        outline = lerp(raised, Color.White, 0.12f),
        outlineVariant = lerp(night, raised, 0.7f),
        error = Color(0xFFFF6B6B),
        surfaceContainer = lerp(night, raised, 0.45f),
        surfaceContainerHigh = lerp(night, raised, 0.75f),
        surfaceContainerHighest = lerp(raised, Color.White, 0.05f)
    ) else {
        // Light: a paper tinted by the theme, and a deeper accent so white text on it stays readable.
        val accent = if (lightOnly) primary else lerp(primary, Color.Black, 0.28f)
        val paper = if (lightOnly) night else lerp(Color.White, primary, 0.035f)
        val card = if (lightOnly) raised else lerp(Color.White, primary, 0.08f)
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = lerp(paper, primary, 0.2f),
            onPrimaryContainer = lerp(Color.Black, accent, 0.4f),
            secondary = if (lightOnly) secondary else lerp(secondary, Color.Black, 0.25f),
            onSecondary = Color.White,
            tertiary = secondary,
            background = paper,
            onBackground = Color(0xFF14110C),
            surface = paper,
            onSurface = Color(0xFF14110C),
            surfaceVariant = card,
            onSurfaceVariant = Color(0xFF5E5A54),
            outline = lerp(card, Color.Black, 0.18f),
            outlineVariant = lerp(paper, card, 0.8f),
            error = Color(0xFFD63B3B),
            surfaceContainer = lerp(paper, card, 0.5f),
            surfaceContainerHigh = card,
            surfaceContainerHighest = lerp(card, Color.Black, 0.04f)
        )
    }
}

/** How things move: Leela (playful), Shanti (calm), Vayu (swift) or Off. */
enum class MotionStyle(val key: String, val title: String, val meaning: String, val scale: Float) {
    Leela("leela", "Leela", "Playful, like Krishna's play. Springs and glows.", 1f),
    Shanti("shanti", "Shanti", "Calm and unhurried. Slow, soft fades.", 1.6f),
    Vayu("vayu", "Vayu", "Swift as the wind god. Quick and light.", 0.6f),
    Still("off", "Off", "No animation. Everything just appears.", 0f);

    companion object {
        fun of(key: String): MotionStyle = entries.firstOrNull { it.key == key } ?: Leela
    }
}

/** A light tap under the finger for choices, when Haptic touches are on in Settings. */
@Composable
fun rememberTouch(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return { if (AppSettings.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
}

/**
 * The loading mark: the Sudarshan chakra turning, the wheel of time, while something is fetched.
 * Eight spokes and a hub in the theme's colour; it stops spinning when animations are off.
 */
@Composable
fun ChakraSpinner(size: Dp = 32.dp, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val style = MotionStyle.of(AppSettings.motionStyle)
    val spin = rememberInfiniteTransition(label = "chakra")
    val angle by spin.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(((1400 * style.scale).toInt()).coerceAtLeast(700), easing = LinearEasing)),
        label = "chakra-angle"
    )
    val glow by spin.animateFloat(0.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "chakra-glow")
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = center
        rotate(if (style == MotionStyle.Still) 0f else angle, c) {
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.22f * glow), Color.Transparent), c, r), r, c)
            drawCircle(color, r * 0.86f, c, style = Stroke(r * 0.12f))
            // The serrated rim: small teeth around the wheel.
            for (i in 0 until 16) {
                val a = Math.toRadians(i * 22.5).toFloat()
                val tip = Offset(c.x + cos(a) * r, c.y + sin(a) * r)
                val base = Offset(c.x + cos(a) * r * 0.82f, c.y + sin(a) * r * 0.82f)
                drawLine(color.copy(alpha = 0.8f), base, tip, r * 0.09f)
            }
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0 + 22.5).toFloat()
                drawLine(color, Offset(c.x + cos(a) * r * 0.2f, c.y + sin(a) * r * 0.2f), Offset(c.x + cos(a) * r * 0.78f, c.y + sin(a) * r * 0.78f), r * 0.08f)
            }
            drawCircle(color, r * 0.2f, c)
        }
    }
}

/**
 * The diya: a small flickering flame and its glow, drawn behind the selected tab.
 * Where you stand is lit.
 */
@Composable
fun DiyaGlow(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val still = MotionStyle.of(AppSettings.motionStyle) == MotionStyle.Still
    // The flame catches when the tab is chosen, flickers a few times, then burns steady (a
    // never-ending flicker would redraw the bar every frame and cost battery).
    val f = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(if (still) 1f else 0.4f) }
    androidx.compose.runtime.LaunchedEffect(still) {
        if (still) return@LaunchedEffect
        f.animateTo(1f, tween(320))
        repeat(3) {
            f.animateTo(0.78f, tween(260))
            f.animateTo(1f, tween(300))
        }
    }
    val flame = f.value
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height * 0.42f)
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.26f * flame), Color.Transparent), c, size.minDimension * 0.7f), size.minDimension * 0.7f, c)
        // The flame itself: a small teardrop under the label.
        val w = size.width * 0.06f
        val h = size.height * 0.16f * flame
        val baseY = size.height * 0.98f
        val path = Path().apply {
            moveTo(size.width / 2f, baseY - h)
            quadraticTo(size.width / 2f + w, baseY - h * 0.35f, size.width / 2f, baseY)
            quadraticTo(size.width / 2f - w, baseY - h * 0.35f, size.width / 2f, baseY - h)
            close()
        }
        drawPath(path, Brush.verticalGradient(listOf(Color(0xFFFFE08A), color), baseY - h, baseY))
    }
}
