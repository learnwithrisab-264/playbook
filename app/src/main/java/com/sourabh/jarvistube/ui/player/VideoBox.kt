package com.sourabh.jarvistube.ui.player

import androidx.compose.ui.res.stringResource
import com.sourabh.jarvistube.ui.theme.Motion
import com.sourabh.jarvistube.ui.theme.OnVideo
import com.sourabh.jarvistube.ui.common.Thumb
import com.sourabh.jarvistube.ui.study.PlayerPopups
import android.view.LayoutInflater
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.R
import com.sourabh.jarvistube.player.NowPlaying
import kotlinx.coroutines.delay

@Composable
internal fun VideoBox(
    modifier: Modifier,
    now: NowPlaying,
    ui: PlayerUi,
    fullscreen: Boolean,
    showControls: Boolean,
    onToggleFullscreen: () -> Unit,
    onCollapse: () -> Unit,
    onSwipe: (Int) -> Unit
) {
    val player = Graph.player.player
    var controlsVisible by remember { mutableStateOf(true) }
    val synced = Graph.player.loadedUrl.collectAsStateWithLifecycle().value == now.video.url
    val currentOnSwipe by androidx.compose.runtime.rememberUpdatedState(onSwipe)
    var dragging by remember { mutableStateOf(false) }
    var flash by remember { mutableIntStateOf(0) }
    var flashKey by remember { mutableIntStateOf(0) }
    var boosting by remember { mutableStateOf(false) }
    var speedBefore by remember { mutableFloatStateOf(1f) }
    /** Height of the controls' bottom part (time row + seek bar); captions rise above it. */
    var controlsBottomPx by remember { mutableIntStateOf(0) }

    // Controls stay up while one of their menus (CC, lock) or the lock dialog is open.
    LaunchedEffect(controlsVisible, ui.isPlaying, dragging, flashKey, PlayerPopups.anyOpen) {
        if (controlsVisible && ui.isPlaying && !dragging && !PlayerPopups.anyOpen) {
            delay(3000)
            controlsVisible = false
        }
    }
    LaunchedEffect(flashKey) {
        if (flash != 0) {
            delay(550)
            flash = 0
        }
    }
    // Brightness and sound swipes: what is being changed and its level, shown in a small box.
    val context = androidx.compose.ui.platform.LocalContext.current
    val brightness = remember(context) { WindowBrightness(context) }
    val volume = remember(context) { MediaVolume(context) }
    var adjusting by remember { mutableStateOf<Pair<SwipeZone, Float>?>(null) }
    var adjustDone by remember { mutableIntStateOf(0) }
    LaunchedEffect(adjustDone) {
        delay(700)
        adjusting = null
    }
    // Fit / Fill for upright videos, and two-finger zoom for every video. Both reset for a new video.
    var fill by remember(now.video.url) { mutableStateOf(false) }
    var zoom by remember(now.video.url) { mutableFloatStateOf(MIN_ZOOM) }
    var pan by remember(now.video.url) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var vertical by remember { mutableStateOf(player.videoSize.height > player.videoSize.width) }
    androidx.compose.runtime.DisposableEffect(player) {
        val sizeListener = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                vertical = videoSize.height > videoSize.width
            }
        }
        player.addListener(sizeListener)
        onDispose { player.removeListener(sizeListener) }
    }
    // PlayBook's own brightness lasts only for fullscreen; the phone's setting comes back after.
    androidx.compose.runtime.DisposableEffect(fullscreen) {
        onDispose { brightness.reset() }
    }

    Box(
        modifier
            .background(Color.Black)
            .clipToBounds() // a zoomed picture stays inside the player
            // Tap / double-tap first (outer) ...
            .pointerInput(Unit) {
                // No onDoubleTap: that makes Compose hold every single tap ~300 ms to rule out a
                // second one. Instead a tap acts at once, and a quick second tap on the same side
                // turns into a 10 s skip (and keeps skipping while you keep tapping), like YouTube.
                var lastTapAt = 0L
                var lastSide = 0
                detectTapGestures(
                    // Hold anywhere on the video for 2x; let go to return to the previous speed.
                    onPress = {
                        tryAwaitRelease()
                        if (boosting) {
                            player.setPlaybackSpeed(speedBefore)
                            boosting = false
                        }
                    },
                    onLongPress = {
                        if (!boosting) {
                            speedBefore = player.playbackParameters.speed
                            player.setPlaybackSpeed(2f)
                            boosting = true
                        }
                    },
                    onTap = { offset ->
                        val t = android.os.SystemClock.uptimeMillis()
                        val side = if (offset.x < size.width / 2f) -1 else 1
                        if (t - lastTapAt < 320 && side == lastSide) {
                            player.seekTo((player.currentPosition + side * 10_000L).coerceAtLeast(0L))
                            flash = side * 10
                            flashKey++
                            controlsVisible = false
                        } else {
                            controlsVisible = !controlsVisible
                        }
                        lastTapAt = t
                        lastSide = side
                    }
                )
            }
            // ... then the vertical swipe (inner, so it sees the drag before the tap detector does).
            .pointerInput(fullscreen) {
                var total = 0f
                var ignore = false
                var zone = SwipeZone.Exit
                // Fullscreen needs a long, deliberate pull (a quarter of the screen) to exit, and a
                // drag that starts at the very top edge is the notification shade, not an exit.
                val upThreshold = 56.dp.toPx()
                val downThreshold = if (fullscreen) size.height * 0.25f else 56.dp.toPx()
                detectVerticalDragGestures(
                    onDragStart = { start ->
                        total = 0f
                        ignore = fullscreen && start.y < size.height * 0.15f
                        // In fullscreen the sides change brightness and sound; the middle still exits.
                        zone = if (fullscreen && !ignore) swipeZoneAt(start.x, size.width.toFloat()) else SwipeZone.Exit
                        if (zone == SwipeZone.Brightness) adjusting = SwipeZone.Brightness to brightness.current()
                        if (zone == SwipeZone.Volume) adjusting = SwipeZone.Volume to volume.current()
                    },
                    onDragEnd = {
                        if (!ignore && zone == SwipeZone.Exit) {
                            if (total < -upThreshold) currentOnSwipe(-1) else if (total > downThreshold) currentOnSwipe(1)
                        }
                        total = 0f
                        adjustDone++
                    },
                    onDragCancel = { total = 0f; adjustDone++ },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        total += dy
                        val (kind, level) = adjusting ?: return@detectVerticalDragGestures
                        if (zone == SwipeZone.Exit) return@detectVerticalDragGestures
                        val next = adjustLevel(level, dy, size.height.toFloat())
                        if (kind == SwipeZone.Brightness) brightness.set(next) else volume.set(next)
                        adjusting = kind to next
                    }
                )
            }
            // ... and two-finger zoom innermost, so it claims two-finger touches before the others.
            .pointerInput(Unit) {
                detectVideoZoom(
                    onTransform = { z, p ->
                        zoom = clampZoom(zoom * z)
                        pan = clampPan(pan + p, zoom, size.width.toFloat(), size.height.toFloat())
                    },
                    onReset = { zoom = MIN_ZOOM; pan = androidx.compose.ui.geometry.Offset.Zero }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.view_player_texture, null) as PlayerView).also {
                    it.player = player
                    // Captions are drawn by CaptionOverlay instead: SubtitleView follows the
                    // caption file's positions, and YouTube's TTML puts lines at the top.
                    it.subtitleView?.visibility = android.view.View.GONE
                }
            },
            update = {
                if (it.player !== player) it.player = player
                it.resizeMode = if (fill && vertical) androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                it.scaleX = zoom
                it.scaleY = zoom
                it.translationX = pan.x
                it.translationY = pan.y
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )

        // The thumbnail fills the frame the moment you tap, until the first video frame is drawn.
        val firstFrame by Graph.player.firstFrameUrl.collectAsStateWithLifecycle()
        if (firstFrame != now.video.url && !Graph.player.audioOnly && now.video.thumb != null) {
            Thumb(now.video.thumb, Modifier.matchParentSize())
        }

        adjusting?.let { (kind, level) ->
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 24.dp).clip(CircleShape)
                    .background(OnVideo.scrim).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (kind == SwipeZone.Brightness) Icons.Rounded.BrightnessMedium else Icons.AutoMirrored.Rounded.VolumeUp,
                    if (kind == SwipeZone.Brightness) "Brightness" else "Volume",
                    tint = Color.White, modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(12.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { level },
                    modifier = Modifier.size(width = 110.dp, height = 4.dp),
                    color = Color.White,
                    trackColor = OnVideo.track
                )
                Spacer(Modifier.size(12.dp))
                Text("${(level * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }

        if (ui.isBuffering || now.loading) {
            CircularProgressIndicator(
                Modifier.align(Alignment.Center).size(44.dp),
                color = Color.White,
                strokeWidth = 3.dp
            )
        }

        AnimatedVisibility(
            visible = flash != 0,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(if (flash < 0) Alignment.CenterStart else Alignment.CenterEnd)
        ) {
            Text(
                if (flash < 0) "« 10s" else "10s »",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 36.dp)
                    .clip(CircleShape).background(OnVideo.scrim)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (Graph.player.audioOnly) {
            Box(Modifier.matchParentSize().background(Color.Black), contentAlignment = Alignment.Center) {
                // The Listen player: thumbnail instead of video, with the way back to the picture.
                Thumb(now.video.thumb, Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(OnVideo.scrim))
                // Low in the frame, between the time and the full-screen button: the centre is for Play.
                TextButton(
                    onClick = { Graph.player.updateAudioOnly(false) },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp)
                ) {
                    Icon(Icons.Rounded.Headphones, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.watch_video), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Captions at the bottom; when the controls show they rise to sit just above the time
        // row and seek bar. Drawn before the controls so buttons always stay on top.
        if (now.subtitleId != null) {
            val controlsUp = showControls && controlsVisible && controlsBottomPx > 0
            // Rise at once; drop only after the controls' fade-out (~300 ms) so they never overlap.
            val lift by animateDpAsState(
                if (controlsUp) with(LocalDensity.current) { controlsBottomPx.toDp() } + 4.dp else 10.dp,
                animationSpec = tween(Motion.NORMAL, delayMillis = if (controlsUp) 0 else 320),
                label = "captionLift"
            )
            CaptionOverlay(ui.cues, bottomInset = lift, modifier = Modifier.matchParentSize())
        }
        AnimatedVisibility(
            visible = boosting,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
        ) {
            Text(
                "2x  ▶▶",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(CircleShape).background(OnVideo.scrim)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        val notice by Graph.player.notice.collectAsStateWithLifecycle()
        AnimatedVisibility(
            visible = notice != null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 52.dp)
        ) {
            Text(
                notice.orEmpty(),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        if (now.error != null) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    now.error,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                if (!now.error.startsWith("Blocked")) TextButton(onClick = { Graph.player.retry() }) { Text(stringResource(R.string.retry)) }
            }
        }

        // With the controls hidden, a slim progress line (with the buffered part) stays at the bottom.
        if (!controlsVisible || !showControls) {
            YtSeekBar(
                position = if (synced) ui.position else 0L,
                duration = if (synced) ui.duration else 0L,
                buffered = if (synced) ui.buffered else 0L,
                interactive = false,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (showControls) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.matchParentSize()
            ) {
                Controls(
                    now = now,
                    ui = ui,
                    fullscreen = fullscreen,
                    onDragging = { dragging = it },
                    onToggleFullscreen = onToggleFullscreen,
                    onCollapse = onCollapse,
                    onBottomHeight = { controlsBottomPx = it }
                )
            }
        }

        // Fit / Fill for upright videos, and Reset while zoomed. On top, so the controls can't cover it.
        val zoomed = zoom > MIN_ZOOM
        if (showControls && controlsVisible && (vertical || zoomed)) {
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                if (vertical) VideoPill(if (fill) "Fit" else "Fill") { fill = !fill }
                if (zoomed) VideoPill("Reset zoom") { zoom = MIN_ZOOM; pan = androidx.compose.ui.geometry.Offset.Zero }
            }
        }
    }
}

/** A small round button drawn on the video. */
@Composable
private fun VideoPill(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.clip(CircleShape).background(com.sourabh.jarvistube.ui.theme.OnVideo.scrim)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}
