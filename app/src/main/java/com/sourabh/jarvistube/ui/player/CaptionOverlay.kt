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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.text.withStyle
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
 * YouTube-style captions: one clean line, centred at the bottom of the video, [bottomInset] above
 * the bottom edge (just above the controls when those show). Lines come from PlayBook's own
 * caption engine (see player/Captions), checked every frame against the playback position, and
 * the word being spoken is lit up when YouTube gives word timings (Settings › Captions).
 */
@Composable
fun CaptionOverlay(bottomInset: Dp, modifier: Modifier = Modifier) {
    val lines by com.sourabh.jarvistube.Graph.player.captionLines.collectAsState()
    if (lines.isEmpty()) return
    val player = com.sourabh.jarvistube.Graph.player.player
    var line by remember(lines) { mutableStateOf<com.sourabh.jarvistube.player.CaptionLine?>(null) }
    var word by remember(lines) { mutableIntStateOf(-1) }
    LaunchedEffect(lines) {
        while (true) {
            withFrameMillis { }
            val pos = player.currentPosition
            val l = com.sourabh.jarvistube.player.Captions.lineAt(lines, pos)
            if (l !== line) line = l
            val w = if (l != null && AppSettings.captionHighlight) com.sourabh.jarvistube.player.Captions.wordAt(l, pos) else -1
            if (w != word) word = w
        }
    }
    val current = line ?: return
    val accent = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val text = remember(current, word, accent) {
        androidx.compose.ui.text.buildAnnotatedString {
            if (word < 0) append(current.text)
            else {
                // Trim the first word's leading space so the line stays centred.
                current.words.forEachIndexed { i, w ->
                    val t = if (i == 0) w.text.trimStart() else w.text
                    if (i == word) {
                        val lead = t.takeWhile { it == ' ' }
                        append(lead)
                        withStyle(androidx.compose.ui.text.SpanStyle(background = accent.copy(alpha = 0.85f), color = Color.White)) {
                            append(t.drop(lead.length))
                        }
                    } else append(t)
                }
            }
        }
    }
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
fun CaptionText(text: String, fontSizeSp: Float, modifier: Modifier = Modifier) =
    CaptionText(androidx.compose.ui.text.AnnotatedString(text), fontSizeSp, modifier)

@Composable
fun CaptionText(text: androidx.compose.ui.text.AnnotatedString, fontSizeSp: Float, modifier: Modifier = Modifier) {
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
