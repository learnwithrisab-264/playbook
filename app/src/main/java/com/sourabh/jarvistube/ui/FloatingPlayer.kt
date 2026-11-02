package com.sourabh.jarvistube.ui

import android.view.LayoutInflater
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.sourabh.jarvistube.AppSettings
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.R
import com.sourabh.jarvistube.player.NowPlaying
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Where the window's top-left may sit, in px: the four corner positions. */
private data class FloatBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun corner(c: Int) = Offset(if (c % 2 == 0) left else right, if (c < 2) top else bottom)
}

/**
 * In-app picture-in-picture: the playing video in a small window that floats above every screen
 * (Home, search, channel and playlist pages, Settings) while you browse. Drag it anywhere; on
 * release it snaps to the nearest corner, which is remembered. Tap it to open the full player.
 * [topInset]/[bottomInset] keep it clear of the status bar and the bottom bar.
 */
@Composable
fun FloatingPlayer(now: NowPlaying, ui: PlayerUi, topInset: Dp, bottomInset: Dp, onExpand: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val landscape = maxWidth > maxHeight
        val margin = 12.dp
        // Size: pinch the window or drag its resize grip. Kept between a small 140 dp and the
        // largest window that still fits between the bars; remembered across launches.
        val minW = 140.dp
        val maxW = minOf(maxWidth - margin * 2, (maxHeight - topInset - bottomInset - margin * 2) * (16f / 9f))
            .coerceAtLeast(minW)
        val defaultW = (if (landscape) maxWidth * 0.32f else maxWidth * 0.52f).coerceIn(160.dp, 320.dp)
        var widthDp by remember { mutableFloatStateOf(AppSettings.floatWidthDp.takeIf { it > 0f } ?: defaultW.value) }
        var resizing by remember { mutableStateOf(false) }
        // Plain holder (not state): updated on every layout without causing recomposition.
        val grip = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
        val w = widthDp.dp.coerceIn(minW, maxW)
        val h = w * 9f / 16f
        val limits by rememberUpdatedState(minW.value to maxW.value)
        // Read state (not `w`) so the long-lived gesture handlers always see the current size.
        fun currentW() = widthDp.coerceIn(limits.first, limits.second)
        fun resizeBy(dpDelta: Float) {
            widthDp = (currentW() + dpDelta).coerceIn(limits.first, limits.second)
        }
        fun endResize() {
            resizing = false
            widthDp = currentW()
            AppSettings.updateFloatWidth(widthDp)
        }
        val bounds = with(density) {
            val top = (topInset + margin).toPx()
            FloatBounds(
                left = margin.toPx(),
                top = top,
                right = (maxWidth - w - margin).toPx(),
                bottom = (maxHeight - bottomInset - h - margin).toPx().coerceAtLeast(top)
            )
        }
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val wPx = with(density) { w.toPx() }
        val hPx = with(density) { h.toPx() }
        val pos = remember { Animatable(bounds.corner(AppSettings.floatCorner), Offset.VectorConverter) }
        var dragging by remember { mutableStateOf(false) }

        // Re-seat on its corner when the space around it changes (bottom bar shown or hidden,
        // rotation, search opened).
        // While resizing it follows instantly, so the window grows from its anchored corner.
        LaunchedEffect(bounds) {
            val target = bounds.corner(AppSettings.floatCorner)
            when {
                resizing -> pos.snapTo(target)
                !dragging -> pos.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow))
            }
        }
        val rightAnchored = AppSettings.floatCorner % 2 == 1

        fun settle() {
            dragging = false
            val p = pos.value
            val corner = (if (p.x + wPx / 2 > widthPx / 2) 1 else 0) + (if (p.y + hPx / 2 > heightPx / 2) 2 else 0)
            AppSettings.updateFloatCorner(corner)
            scope.launch { pos.animateTo(bounds.corner(corner), spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) }
        }

        // Small radius: the video SurfaceView itself can't be clipped to rounded corners.
        val shape = RoundedCornerShape(6.dp)
        Box(
            Modifier
                .offset { IntOffset(pos.value.x.roundToInt(), pos.value.y.roundToInt()) }
                .size(w, h)
                .shadow(10.dp, shape)
                .clip(shape)
                .background(Color.Black)
                .pointerInput(bounds) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { settle() },
                        onDragCancel = { settle() }
                    ) { change, drag ->
                        change.consume()
                        val next = Offset(
                            (pos.value.x + drag.x).coerceIn(0f, widthPx - wPx),
                            (pos.value.y + drag.y).coerceIn(0f, heightPx - hPx)
                        )
                        scope.launch { pos.snapTo(next) }
                    }
                }
                // Two-finger pinch resizes (runs before the drag above and takes the gesture).
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) {
                                val zoom = event.calculateZoom()
                                if (zoom != 1f) {
                                    resizing = true
                                    resizeBy(currentW() * (zoom - 1f))
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        if (resizing) endResize()
                    }
                }
                .clickable(onClick = onExpand)
        ) {
            // Its own PlayerView (SurfaceView, for smooth frame timing): ExoPlayer moves its
            // picture here from the full player's surface.
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.view_player_float, null) as PlayerView).also {
                        it.player = Graph.player.player
                        it.subtitleView?.visibility = android.view.View.GONE
                    }
                },
                update = { if (it.player !== Graph.player.player) it.player = Graph.player.player },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize()
            )
            val firstFrame by Graph.player.firstFrameUrl.collectAsStateWithLifecycle()
            if (firstFrame != now.video.url && now.video.thumb != null) {
                Thumb(now.video.thumb, Modifier.fillMaxSize())
            }
            // Soft scrim so the white buttons read on bright frames.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.5f))
                    )
                )
            )
            if (ui.isBuffering || now.loading) {
                CircularProgressIndicator(
                    Modifier.align(Alignment.Center).size(28.dp),
                    color = Color.White,
                    strokeWidth = 2.5.dp
                )
            }
            // ✕ on the outer top corner; the resize grip on the inner one (towards the middle of
            // the screen), so dragging the grip outwards grows the window from its corner.
            IconButton(
                onClick = { Graph.player.stop() },
                modifier = Modifier.align(if (rightAnchored) Alignment.TopEnd else Alignment.TopStart).size(36.dp)
            ) { Icon(Icons.Rounded.Close, "Close player", tint = Color.White) }
            Box(
                Modifier
                    .align(if (rightAnchored) Alignment.TopStart else Alignment.TopEnd)
                    .size(40.dp)
                    .onGloballyPositioned { grip[0] = it }
                    .pointerInput(rightAnchored) {
                        // The grip moves as the window grows, so track the finger in screen
                        // coordinates: the window edge then stays under the finger.
                        var startX = 0f
                        var startW = 0f
                        detectDragGestures(
                            onDragStart = { at ->
                                resizing = true
                                startX = grip[0]?.localToRoot(at)?.x ?: 0f
                                startW = currentW()
                            },
                            onDragEnd = { endResize() },
                            onDragCancel = { endResize() }
                        ) { change, _ ->
                            change.consume()
                            val x = grip[0]?.localToRoot(change.position)?.x ?: return@detectDragGestures
                            // Moving away from the anchored side grows the window.
                            val outward = if (rightAnchored) startX - x else x - startX
                            resizeBy(startW + outward / density.density - currentW())
                        }
                    }
                    .semantics { contentDescription = "Resize player" },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.OpenInFull, null, tint = Color.White,
                    modifier = Modifier.size(18.dp).graphicsLayer { scaleX = if (rightAnchored) -1f else 1f }
                )
            }
            androidx.compose.foundation.layout.Row(
                Modifier.align(Alignment.BottomStart).padding(bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { Graph.player.skipPrevious() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous", tint = Color.White)
                }
                IconButton(onClick = { Graph.player.togglePlay() }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (ui.isPlaying) "Pause" else "Play",
                        tint = Color.White
                    )
                }
                IconButton(onClick = { Graph.player.skipNext() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Next", tint = Color.White)
                }
            }
            FloatProgress(ui, Modifier.align(Alignment.BottomStart))
        }
    }
}

/**
 * The red progress line. Its own composable so the 4-per-second position updates redraw only
 * this line, not the whole window around the video surface.
 */
@Composable
private fun FloatProgress(ui: PlayerUi, modifier: Modifier) {
    val progress = if (ui.duration > 0) (ui.position.toFloat() / ui.duration).coerceIn(0f, 1f) else 0f
    Box(
        modifier.fillMaxWidth(progress).height(2.dp)
            .background(Color(0xFFFF6D00)) // PlayBook orange, like the seek bar
    )
}
