package com.sourabh.jarvistube.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionOff
import com.sourabh.jarvistube.AppSettings
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.QueueMusic
import android.widget.Toast
import com.sourabh.jarvistube.data.Downloads
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.R
import com.sourabh.jarvistube.TubeViewModel
import com.sourabh.jarvistube.data.bestUrl
import com.sourabh.jarvistube.player.NowPlaying
import kotlinx.coroutines.delay

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun PlayerScreen(vm: TubeViewModel, ui: PlayerUi, inPip: Boolean) {
    val controller = Graph.player
    val now by controller.now.collectAsStateWithLifecycle()
    val cur = now ?: return
    val activity = LocalContext.current.findActivity()
    var fullscreen by remember { mutableStateOf(false) }
    val fs = fullscreen || inPip

    BackHandler(enabled = fullscreen) { fullscreen = false }

    // Lock Study Session: stay fullscreen on this video; Back only reminds you of the time left.
    val locked = Graph.study.lockActive
    LaunchedEffect(locked) { if (locked) fullscreen = true }
    val lockContext = LocalContext.current
    BackHandler(enabled = locked) {
        Toast.makeText(lockContext, "Study session locked: ${formatLeft(Graph.study.lockRemainingMs)} left", Toast.LENGTH_SHORT).show()
    }

    // Orientation, YouTube-style: turning the phone sideways enters fullscreen; leaving fullscreen
    // snaps back to portrait and holds it until the phone is physically upright again, so the
    // portrait layout never shows stretched across a landscape screen.
    val orientationContext = LocalContext.current
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var holdPortrait by remember { mutableStateOf(false) }
    var wasFullscreen by remember { mutableStateOf(false) }
    LaunchedEffect(landscape) { if (landscape && !holdPortrait && !inPip) fullscreen = true }

    DisposableEffect(fullscreen) {
        val window = activity?.window
        val ctl = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        if (fullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl?.hide(WindowInsetsCompat.Type.systemBars())
        } else if (wasFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            holdPortrait = true
        }
        wasFullscreen = fullscreen
        onDispose { ctl?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    DisposableEffect(holdPortrait) {
        if (!holdPortrait) {
            if (!fullscreen) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            return@DisposableEffect onDispose { }
        }
        val listener = object : android.view.OrientationEventListener(orientationContext) {
            override fun onOrientationChanged(deg: Int) {
                if (deg != ORIENTATION_UNKNOWN && (deg <= 25 || deg >= 335)) holdPortrait = false
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }
    // Leaving the player (minimise) always gives rotation back to the system.
    DisposableEffect(Unit) {
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        StudyLockDialogHost(cur)
        PlayerMoreSheetHost(cur, ui)
        if (!fs) {
            Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Color.Black))
        }
        VideoBox(
            modifier = if (fs) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            now = cur,
            ui = ui,
            fullscreen = fs,
            showControls = !inPip,
            onToggleFullscreen = { if (!locked) fullscreen = !fullscreen },
            onCollapse = { if (!locked) vm.playerExpanded = false },
            // Swipe up on the video = fullscreen; swipe down = leave fullscreen, or minimise to the mini-player.
            onSwipe = { dir -> if (!locked)
                when {
                    dir < 0 && !fullscreen -> fullscreen = true
                    dir > 0 && fullscreen -> fullscreen = false
                    dir > 0 -> vm.playerExpanded = false
                }
            }
        )
        if (!fs) {
            Details(vm, cur, ui, Modifier.weight(1f))
        }
    }
}

@Composable
private fun VideoBox(
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

    Box(
        modifier
            .background(Color.Black)
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
                // Fullscreen needs a long, deliberate pull (a quarter of the screen) to exit, and a
                // drag that starts at the very top edge is the notification shade, not an exit.
                val upThreshold = 56.dp.toPx()
                val downThreshold = if (fullscreen) size.height * 0.25f else 56.dp.toPx()
                detectVerticalDragGestures(
                    onDragStart = { start ->
                        total = 0f
                        ignore = fullscreen && start.y < size.height * 0.15f
                    },
                    onDragEnd = {
                        if (!ignore) {
                            if (total < -upThreshold) currentOnSwipe(-1) else if (total > downThreshold) currentOnSwipe(1)
                        }
                        total = 0f
                    },
                    onDragCancel = { total = 0f },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        total += dy
                    }
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
            update = { if (it.player !== player) it.player = player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )

        // The thumbnail fills the frame the moment you tap, until the first video frame is drawn.
        val firstFrame by Graph.player.firstFrameUrl.collectAsStateWithLifecycle()
        if (firstFrame != now.video.url && !Graph.player.audioOnly && now.video.thumb != null) {
            Thumb(now.video.thumb, Modifier.matchParentSize())
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
                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }

        if (Graph.player.audioOnly) {
            Box(Modifier.matchParentSize().background(Color.Black), contentAlignment = Alignment.Center) {
                // The Listen player: thumbnail instead of video, with the way back to the picture.
                Thumb(now.video.thumb, Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.55f)))
                // Low in the frame, between the time and the full-screen button: the centre is for Play.
                TextButton(
                    onClick = { Graph.player.updateAudioOnly(false) },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp)
                ) {
                    Icon(Icons.Rounded.Headphones, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Watch video", color = Color.White, fontWeight = FontWeight.Bold)
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
                animationSpec = tween(180, delayMillis = if (controlsUp) 0 else 320),
                label = "captionLift"
            )
            CaptionOverlay(ui.cues, bottomInset = lift, modifier = Modifier.matchParentSize())
        }
        AnimatedVisibility(
            visible = boosting,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp)
        ) {
            Text(
                "2x  ▶▶",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
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
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
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
                    fontSize = 13.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                if (!now.error.startsWith("Blocked")) TextButton(onClick = { Graph.player.retry() }) { Text("Retry") }
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
    }
}

@Composable
private fun Controls(
    now: NowPlaying,
    ui: PlayerUi,
    fullscreen: Boolean,
    onDragging: (Boolean) -> Unit,
    onToggleFullscreen: () -> Unit,
    onCollapse: () -> Unit,
    onBottomHeight: (Int) -> Unit
) {
    val player = Graph.player.player
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    val synced = Graph.player.loadedUrl.collectAsStateWithLifecycle().value == now.video.url

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(Color.Black.copy(0.55f), Color.Transparent, Color.Transparent, Color.Black.copy(0.65f))
            )
        )
    ) {
        // Top bar
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!Graph.study.lockActive) IconButton(onClick = if (fullscreen) onToggleFullscreen else onCollapse) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Minimize", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            if (fullscreen) {
                Text(
                    now.info?.name ?: now.video.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else Spacer(Modifier.weight(1f))
            // CC button only when YouTube actually offers captions for this video.
            if (now.subtitles.isNotEmpty()) {
                Box {
                    IconButton(onClick = { PlayerPopups.ccMenu = true }) {
                        Icon(
                            if (now.subtitleId != null) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionOff,
                            "Captions", tint = Color.White
                        )
                    }
                    CaptionsMenu(now, PlayerPopups.ccMenu) { PlayerPopups.ccMenu = false }
                }
            }
            // Quality on the player itself, so it is reachable in fullscreen and during a lock.
            if (now.heights.isNotEmpty()) {
                Box {
                    IconButton(onClick = { PlayerPopups.qualityMenu = true }) {
                        Icon(Icons.Rounded.HighQuality, "Quality", tint = Color.White)
                    }
                    QualityMenu(now, PlayerPopups.qualityMenu) { PlayerPopups.qualityMenu = false }
                }
            }
            StudyLockButton(now)
        }

        // Center transport (hidden while scrubbing, so the preview has the screen).
        if (scrubMs == null) Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(if (fullscreen) 120.dp else 56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) }) {
                Icon(Icons.Rounded.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Box(
                Modifier.size(60.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))
                    .clickable { Graph.player.togglePlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (ui.isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
            IconButton(onClick = { player.seekTo(player.currentPosition + 10_000L) }) {
                Icon(Icons.Rounded.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(34.dp))
            }
        }

        // Bottom: "2:14 / 17:04" + fullscreen, then the seek bar hugging the video's bottom edge.
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().onSizeChanged { onBottomHeight(it.height) }) {
            // While dragging, the frame preview replaces the time row and sits right on top of the
            // seek bar; it grows upward over the video without moving the bar.
            val ms = scrubMs
            if (ms != null) {
                Box(Modifier.fillMaxWidth().height(48.dp)) {
                    Box(
                        Modifier.align(Alignment.BottomStart)
                            .wrapContentHeight(align = Alignment.Bottom, unbounded = true)
                    ) { SeekPreview(now.info, ms, if (synced) ui.duration else 0L, small = !fullscreen) }
                }
            } else Row(
                Modifier.padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val shown = scrubMs ?: if (synced) ui.position else 0L
                Text(
                    "${formatClock(shown)} / ${formatClock(if (synced) ui.duration else 0L)}",
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                if (!Graph.study.lockActive) IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        "Fullscreen", tint = Color.White
                    )
                }
            }
            YtSeekBar(
                position = if (synced) ui.position else 0L,
                duration = if (synced) ui.duration else 0L,
                buffered = if (synced) ui.buffered else 0L,
                interactive = true,
                onSeek = { player.seekTo(it) },
                onScrub = { scrubMs = it; onDragging(it != null) }
            )
            Spacer(Modifier.height(if (fullscreen) 10.dp else 0.dp))
        }
    }
}

@Composable
private fun Details(vm: TubeViewModel, now: NowPlaying, ui: PlayerUi, modifier: Modifier) {
    val context = LocalContext.current
    val info = now.info
    var expanded by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }
    var audioMenu by remember { mutableStateOf(false) }
    var captionsMenu by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var downloadMenu by remember { mutableStateOf(false) }
    val desc = remember(info) {
        androidx.core.text.HtmlCompat.fromHtml(
            info?.description?.content.orEmpty(),
            androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
        ).toString().trim()
    }

    LazyColumn(modifier.navigationBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                Text(
                    info?.name ?: now.video.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { expanded = !expanded }
                )
                Spacer(Modifier.height(10.dp))
                // YouTube's grey description card: views + date, a preview, "…more".
                val meta = listOfNotNull(
                    (info?.viewCount ?: now.video.views).takeIf { it >= 0 }?.let { "${String.format(java.util.Locale.US, "%,d", it)} views" },
                    prettyDate(info?.textualUploadDate ?: now.video.uploaded)
                ).joinToString("  ")
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { expanded = !expanded }
                        .padding(12.dp)
                ) {
                    Text(meta, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    if (desc.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            desc,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (expanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (expanded) "Show less" else "…more",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                val channelUrl = info?.uploaderUrl?.takeIf { it.isNotBlank() } ?: now.video.channelUrl
                Row(
                    // Tap the channel to open its page (the player drops to the mini-player).
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = channelUrl != null && !Graph.study.lockActive) {
                            channelUrl?.let { vm.openChannel(it, info?.uploaderName ?: now.video.channel) }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(info?.uploaderName ?: now.video.channel, 40, info?.uploaderAvatars?.bestUrl() ?: now.video.avatar)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            info?.uploaderName ?: now.video.channel,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        info?.uploaderSubscriberCount?.takeIf { it >= 0 }?.let {
                            Text(
                                "${compact(it)} subscribers",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (!Graph.study.lockActive) SubscribeButton(channelUrl, info?.uploaderName ?: now.video.channel)
                }
            }
        }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box {
                    PillChip(
                        selected = ui.speed != 1f,
                        onClick = { speedMenu = true },
                        label = { Text(AppSettings.speedText(ui.speed)) },
                        leadingIcon = { Icon(Icons.Rounded.Speed, null, Modifier.size(18.dp)) }
                    )
                    // The speeds chosen in Settings › Playback speed (up to 4x).
                    DropdownMenu(speedMenu, { speedMenu = false }) {
                        AppSettings.speeds.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(AppSettings.speedText(s)) },
                                trailingIcon = { if (s == ui.speed) Icon(Icons.Rounded.Check, null) },
                                onClick = { Graph.player.player.setPlaybackSpeed(s); speedMenu = false }
                            )
                        }
                    }
                }
                if (now.heights.isNotEmpty()) {
                    Box {
                        val playing = now.playingHeight.takeIf { it > 0 }
                        PillChip(
                            selected = false,
                            onClick = { qualityMenu = true },
                            label = {
                                Text(
                                    if (now.autoQuality) "Auto" + (playing?.let { " (${it}p)" } ?: "")
                                    else AppSettings.qualityLabel(playing ?: now.quality)
                                )
                            },
                            leadingIcon = { Icon(Icons.Rounded.HighQuality, null, Modifier.size(18.dp)) }
                        )
                        QualityMenu(now, qualityMenu) { qualityMenu = false }
                    }
                }
                if (now.subtitles.isNotEmpty()) {
                    Box {
                        val current = now.subtitles.firstOrNull { it.id == now.subtitleId }
                        PillChip(
                            selected = current != null,
                            onClick = { captionsMenu = true },
                            label = { Text(current?.label?.substringBefore(" (") ?: "Captions") },
                            leadingIcon = { Icon(Icons.Rounded.ClosedCaption, null, Modifier.size(18.dp)) }
                        )
                        CaptionsMenu(now, captionsMenu) { captionsMenu = false }
                    }
                }
                if (now.audioTracks.size > 1) {
                    // Always starts on the original language; other dubs are an opt-in choice.
                    Box {
                        val current = now.audioTracks.firstOrNull { it.id == now.audioTrackId }
                        PillChip(
                            selected = current?.original == false,
                            onClick = { audioMenu = true },
                            label = { Text(current?.label ?: "Audio") },
                            leadingIcon = { Icon(Icons.Rounded.Language, null, Modifier.size(18.dp)) }
                        )
                        DropdownMenu(audioMenu, { audioMenu = false }) {
                            now.audioTracks.forEach { t ->
                                DropdownMenuItem(
                                    text = {
                                        val tag = t.original && !t.label.contains("original", ignoreCase = true)
                                        Text(if (tag) "${t.label} (original)" else t.label)
                                    },
                                    trailingIcon = { if (t.id == now.audioTrackId) Icon(Icons.Rounded.Check, null) },
                                    onClick = { Graph.player.setAudioTrack(t.id); audioMenu = false }
                                )
                            }
                        }
                    }
                }
                PillChip(
                    selected = ui.loop,
                    onClick = { ui.toggleLoop() },
                    label = { Text("Loop") },
                    leadingIcon = { Icon(Icons.Rounded.Repeat, null, Modifier.size(18.dp)) }
                )
                PillChip(
                    selected = Graph.player.queue.isNotEmpty(),
                    onClick = { showQueue = true },
                    label = { Text("Queue (${Graph.player.queue.size})") },
                    leadingIcon = { Icon(Icons.Rounded.QueueMusic, null, Modifier.size(18.dp)) }
                )
                if (showQueue) QueueSheet(onDismiss = { showQueue = false })
                if (!Graph.study.lockActive) {
                    PillChip(
                        selected = false,
                        onClick = { PlayerPopups.lockPicker = true },
                        label = { Text("Lock Study Session") },
                        leadingIcon = { Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp)) }
                    )
                }
                PillChip(
                    selected = Graph.player.sponsorBlock,
                    onClick = { Graph.player.updateSponsorBlock(!Graph.player.sponsorBlock) },
                    label = { Text("SponsorBlock") },
                    leadingIcon = { Icon(Icons.Rounded.SkipNext, null, Modifier.size(18.dp)) }
                )
                PillChip(
                    selected = Graph.player.audioOnly,
                    onClick = { Graph.player.updateAudioOnly(!Graph.player.audioOnly) },
                    label = { Text("Listen") }, // audio only: no video download, saves data and battery
                    leadingIcon = { Icon(Icons.Rounded.Headphones, null, Modifier.size(18.dp)) }
                )
                Box {
                    PillChip(
                        selected = Graph.player.sleepEndAt > 0,
                        onClick = { sleepMenu = true },
                        label = { Text(if (Graph.player.sleepEndAt > 0) "Sleep on" else "Sleep") },
                        leadingIcon = { Icon(Icons.Rounded.Bedtime, null, Modifier.size(18.dp)) }
                    )
                    DropdownMenu(sleepMenu, { sleepMenu = false }) {
                        listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 45 to "45 min", 60 to "60 min").forEach { (m, l) ->
                            DropdownMenuItem(
                                text = { Text(l) },
                                onClick = { Graph.player.setSleepTimer(m); sleepMenu = false }
                            )
                        }
                    }
                }
                if (info != null) {
                    PillChip(
                        selected = false,
                        onClick = { DownloadSheet.open(now.video, info) },
                        label = { Text("Download") },
                        leadingIcon = { Icon(Icons.Rounded.Download, null, Modifier.size(18.dp)) }
                    )
                }
                PillChip(
                    selected = false,
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, now.video.url)
                        }
                        context.startActivity(Intent.createChooser(send, "Share"))
                    },
                    label = { Text("Share") },
                    leadingIcon = { Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)) }
                )
            }
        }
        item {
            val related = info?.relatedItems.orEmpty()
                .filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
                .filter { it.duration !in 1L..60L }
            val locked = Graph.study.active
            if (locked) {
                Text(
                    "Related videos are off during a study session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else if (!vm.showRelated) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { vm.showRelated = true }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Related videos hidden", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Distraction-free mode. Tap to reveal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column {
                    // Same control, now a real ON/OFF: tap to hide the feed again.
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { vm.showRelated = false }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Related videos", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Tap to hide them again.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Rounded.ExpandLess, "Hide related videos", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    related.forEach { r ->
                        val v = com.sourabh.jarvistube.data.VideoItem(
                            url = r.url, title = r.name, channel = r.uploaderName.orEmpty(),
                            thumb = r.thumbnails.bestUrl(),
                            durationSec = r.duration, views = r.viewCount, uploaded = r.textualUploadDate,
                            isLive = r.streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM,
                            avatar = runCatching { r.uploaderAvatars.bestUrl() }.getOrNull()
                        )
                        if (!com.sourabh.jarvistube.data.ContentFilter.hides(v)) VideoCard(v, onClick = { vm.play(v) })
                    }
                }
            }
        }
    }
}


/** Caption picker shared by the CC button and the Captions pill: Off + every language offered. */
@Composable
private fun QualityMenu(now: NowPlaying, expanded: Boolean, onDismiss: () -> Unit) {
    // Only resolutions this video really offers AND this phone can decode smoothly.
    DropdownMenu(expanded, onDismiss) {
        DropdownMenuItem(
            text = { Text("Auto") },
            trailingIcon = { if (now.autoQuality) Icon(Icons.Rounded.Check, null) },
            onClick = { Graph.player.setQuality(0); onDismiss() }
        )
        now.heights.forEach { h ->
            DropdownMenuItem(
                text = { Text(AppSettings.qualityLabel(h)) },
                trailingIcon = { if (!now.autoQuality && h == now.playingHeight) Icon(Icons.Rounded.Check, null) },
                onClick = { Graph.player.setQuality(h); onDismiss() }
            )
        }
    }
}

@Composable
private fun CaptionsMenu(now: NowPlaying, expanded: Boolean, onDismiss: () -> Unit) {
    DropdownMenu(expanded, onDismiss) {
        DropdownMenuItem(
            text = { Text("Off") },
            trailingIcon = { if (now.subtitleId == null) Icon(Icons.Rounded.Check, null) },
            onClick = { Graph.player.setSubtitle(null); onDismiss() }
        )
        now.subtitles.forEach { s ->
            DropdownMenuItem(
                text = { Text(s.label) },
                trailingIcon = { if (s.id == now.subtitleId) Icon(Icons.Rounded.Check, null) },
                onClick = { Graph.player.setSubtitle(s.id); onDismiss() }
            )
        }
    }
}
