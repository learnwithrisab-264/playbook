package com.sourabh.jarvistube.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlin.math.abs

const val MIN_ZOOM = 1f
const val MAX_ZOOM = 3f

/** Zoom limited to 1x..[max] (3x by default; more when filling the screen needs it). */
fun clampZoom(zoom: Float, max: Float = MAX_ZOOM): Float = zoom.coerceIn(MIN_ZOOM, max)

/**
 * How much a fitted video must grow to fill the whole frame (crop the black bars): 1 when the
 * shapes already match. [videoAspect] is width / height.
 */
fun fillScale(frameW: Float, frameH: Float, videoAspect: Float): Float {
    if (frameW <= 0f || frameH <= 0f || videoAspect <= 0f) return 1f
    val frameAspect = frameW / frameH
    return if (videoAspect > frameAspect) videoAspect / frameAspect else frameAspect / videoAspect
}

/** Where a pinch settles, like YouTube: Original (1x), Zoomed to fill, or a free zoom beyond fill. */
fun snapZoom(zoom: Float, fill: Float): Float = when {
    fill <= 1.02f -> if (zoom < 1.1f) 1f else zoom
    zoom <= 1f + (fill - 1f) / 2f -> 1f
    zoom <= fill * 1.15f -> fill
    else -> zoom
}

/** Keeps the zoomed picture covering the frame: you can't pan past its edges. */
fun clampPan(pan: Offset, zoom: Float, width: Float, height: Float): Offset {
    val maxX = (zoom - 1f) * width / 2f
    val maxY = (zoom - 1f) * height / 2f
    return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
}

/**
 * Two-finger gestures on the video: pinch to zoom, move both fingers to pan, and a quick
 * two-finger double tap to reset. Events with only one finger are left alone, so taps, double-tap
 * seek and the swipes keep working. Two-finger events are consumed so they don't seek or swipe.
 */
suspend fun PointerInputScope.detectVideoZoom(
    onTransform: (zoom: Float, pan: Offset) -> Unit,
    onReset: () -> Unit,
    onEnd: () -> Unit = {}
) {
    var lastTwoFingerTap = 0L
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var twoFingers = false
        var moved = false
        var startedAt = 0L
        var endedAt = 0L
        do {
            val event = awaitPointerEvent()
            val down = event.changes.count { it.pressed }
            if (down >= 2) {
                if (!twoFingers) {
                    twoFingers = true
                    startedAt = event.changes.first().uptimeMillis
                }
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                if (abs(zoom - 1f) > 0.02f || pan.getDistance() > 6f) moved = true
                onTransform(zoom, pan)
            }
            if (twoFingers) event.changes.forEach { it.consume() }
            endedAt = event.changes.first().uptimeMillis
        } while (event.changes.any { it.pressed })
        if (twoFingers && moved) onEnd()
        if (twoFingers && !moved && endedAt - startedAt < 300) {
            if (endedAt - lastTwoFingerTap < 450) {
                onReset()
                lastTwoFingerTap = 0L
            } else {
                lastTwoFingerTap = endedAt
            }
        }
    }
}
