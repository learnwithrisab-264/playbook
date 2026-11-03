package com.sourabh.jarvistube.ui.player

import com.sourabh.jarvistube.ui.theme.Corners
import com.sourabh.jarvistube.ui.theme.OnVideo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.text.Cue
import com.sourabh.jarvistube.AppSettings

/** Compose font for a caption font key from AppSettings.captionFonts. */
fun captionFontFamily(key: String): FontFamily = when (key) {
    "serif" -> FontFamily.Serif
    "mono" -> FontFamily.Monospace
    "cursive" -> FontFamily.Cursive
    "casual" -> FontFamily(android.graphics.Typeface.create("casual", android.graphics.Typeface.NORMAL))
    "condensed" -> FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL))
    else -> FontFamily.SansSerif
}

/**
 * YouTube-style captions: always centred at the bottom of the video, [bottomInset] above the
 * bottom edge (so they sit just above the controls when those are showing). The caption file's
 * own positioning is ignored on purpose (YouTube's TTML places lines near the top).
 * Font, size and colour come from Settings › Captions and apply instantly.
 */
@Composable
fun CaptionOverlay(cues: List<Cue>, bottomInset: Dp, modifier: Modifier = Modifier) {
    val text = cues.mapNotNull { it.text?.toString()?.trim()?.takeIf(String::isNotEmpty) }.joinToString("\n")
    if (text.isEmpty()) return
    BoxWithConstraints(modifier) {
        // Scale with the player like YouTube: small in the portrait player, bigger in fullscreen.
        val base = (maxHeight.value * 0.055f).coerceIn(11f, 28f)
        val maxLineWidth = maxWidth * 0.9f
        Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = bottomInset)) {
            CaptionText(
                text,
                fontSizeSp = base * AppSettings.captionScale,
                modifier = Modifier.align(Alignment.Center).widthIn(max = maxLineWidth)
            )
        }
    }
}

/** One caption block in the user's style; also used for the preview in Settings. */
@Composable
fun CaptionText(text: String, fontSizeSp: Float, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color(AppSettings.captionColor),
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * 1.25f).sp,
        fontFamily = captionFontFamily(AppSettings.captionFont),
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(OnVideo.scrim, Corners.tiny)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
