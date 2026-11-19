package com.sourabh.jarvistube.ui.player

import com.sourabh.jarvistube.ui.theme.Brand
import com.sourabh.jarvistube.ui.theme.Motion
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** A coloured stretch on the seek bar, in milliseconds. */
data class SeekMark(val startMs: Long, val endMs: Long, val color: Color)

/** SponsorBlock segments of the loaded video as seek-bar marks, in each category's colour. */
fun sponsorMarks(segments: List<com.sourabh.jarvistube.data.SkipSegment>): List<SeekMark> = segments.mapNotNull { s ->
    val c = com.sourabh.jarvistube.data.SPONSOR_CATEGORIES.firstOrNull { it.key == s.category } ?: return@mapNotNull null
    SeekMark((s.start * 1000).toLong(), (s.end * 1000).toLong(), Color(c.color))
}

/**
 * YouTube-style progress bar: a thin track with the buffered part in lighter grey, the played
 * part in the accent colour and a round thumb. [interactive] = false draws the slim, thumbless
 * line that stays at the bottom of the video while the controls are hidden.
 *
 * Positions are in milliseconds. [onSeek] fires on tap and when a drag ends; [onScrub] reports
 * the position while dragging (so the time label can follow the finger).
 */
@Composable
fun YtSeekBar(
    position: Long,
    duration: Long,
    buffered: Long,
    interactive: Boolean,
    modifier: Modifier = Modifier,
    marks: List<SeekMark> = emptyList(),
    onSeek: (Long) -> Unit = {},
    onScrub: (Long?) -> Unit = {}
) {
    val accent = androidx.compose.material3.MaterialTheme.colorScheme.primary // the theme's colour
    var dragFraction by remember { mutableFloatStateOf(-1f) } // -1 = not dragging
    val dragging = dragFraction >= 0f
    // Until the player knows the duration it reports 0; treat that as "no progress yet" instead of
    // dividing by ~1, which drew a full bar (the jump-to-end glitch) for resumed videos.
    val known = duration > 0
    val dur = if (known) duration.toFloat() else 1f
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnScrub by rememberUpdatedState(onScrub)

    val trackHeight by animateDpAsState(
        targetValue = when {
            !interactive -> 2.5.dp
            dragging -> 5.dp
            else -> 3.dp
        },
        animationSpec = tween(Motion.FAST), label = "trackH"
    )
    val thumbRadius by animateDpAsState(
        targetValue = if (dragging) 9.dp else 6.dp, animationSpec = tween(Motion.FAST), label = "thumb"
    )

    var gestures: Modifier = Modifier
    if (interactive) {
        gestures = Modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val f = (offset.x / size.width).coerceIn(0f, 1f)
                    currentOnSeek((f * dur).toLong())
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        currentOnScrub((dragFraction * dur).toLong())
                    },
                    onDragEnd = {
                        currentOnSeek((dragFraction * dur).toLong())
                        dragFraction = -1f
                        currentOnScrub(null)
                    },
                    onDragCancel = {
                        dragFraction = -1f
                        currentOnScrub(null)
                    },
                    onHorizontalDrag = { change, _ ->
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        currentOnScrub((dragFraction * dur).toLong())
                    }
                )
            }
    }

    // A tall touch target around a thin visual track.
    Canvas(modifier.fillMaxWidth().height(if (interactive) 28.dp else 6.dp).then(gestures)) {
        val inset = if (interactive) 9.dp.toPx() else 0f
        val left = inset
        val width = size.width - inset * 2
        val h = trackHeight.toPx()
        val top = if (interactive) size.height / 2f - h / 2f else size.height - h
        val r = CornerRadius(h / 2f, h / 2f)

        fun bar(fraction: Float, color: Color) {
            val w = width * fraction.coerceIn(0f, 1f)
            if (w > 0f) drawRoundRect(color, Offset(left, top), Size(w, h), r)
        }

        bar(1f, Color.White.copy(alpha = 0.28f))
        if (known) bar(buffered.coerceAtMost(duration) / dur, Color.White.copy(alpha = 0.55f))
        val playedFraction = when {
            dragging -> dragFraction
            known -> position.coerceIn(0L, duration) / dur
            else -> 0f
        }
        bar(playedFraction, accent)
        // SponsorBlock segments, drawn over the track like SponsorBlock does.
        if (known) for (m in marks) {
            val x = left + width * (m.startMs / dur).coerceIn(0f, 1f)
            val w = width * ((m.endMs - m.startMs) / dur).coerceIn(0f, 1f)
            if (w > 0f) drawRect(m.color, Offset(x, top), Size(w.coerceAtMost(left + width - x), h))
        }

        if (interactive) {
            drawCircle(accent, thumbRadius.toPx(), Offset(left + width * playedFraction.coerceIn(0f, 1f), size.height / 2f))
        }
    }
}
