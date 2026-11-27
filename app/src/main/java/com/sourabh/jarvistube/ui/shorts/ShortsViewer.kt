package com.sourabh.jarvistube.ui.shorts

import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionOff
import androidx.compose.material.icons.rounded.Share

import com.sourabh.jarvistube.ui.theme.Brand
import com.sourabh.jarvistube.ui.theme.OnVideo
import com.sourabh.jarvistube.ui.common.Thumb
import com.sourabh.jarvistube.ui.player.PlayerUi
import android.app.Activity
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.R
import com.sourabh.jarvistube.ShortsViewer
import com.sourabh.jarvistube.TubeViewModel
import com.sourabh.jarvistube.data.Repo
import com.sourabh.jarvistube.data.VideoItem

/**
 * YouTube-style Shorts: the video fills the whole screen. Swipe up or down for the next or
 * previous Short, tap to pause, Back to leave. Each Short loops until you swipe. Every Short
 * still goes through vm.playShort, so the filter, study rules and daily limit apply.
 */
@Composable
fun ShortsViewerScreen(vm: TubeViewModel, viewer: ShortsViewer, ui: PlayerUi) {
    val items = viewer.items()
    val pager = rememberPagerState(initialPage = viewer.start) { items.size }
    val player = Graph.player.player
    val activity = LocalContext.current as? Activity
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    BackHandler { vm.closeShorts() }

    // Full screen with no system bars; loop each Short; pause if you leave the app.
    DisposableEffect(Unit) {
        val window = activity?.window
        val bars = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars?.hide(WindowInsetsCompat.Type.systemBars())
        player.repeatMode = Player.REPEAT_MODE_ONE
        val pauseOnLeave = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) player.pause() }
        lifecycle.addObserver(pauseOnLeave)
        onDispose {
            lifecycle.removeObserver(pauseOnLeave)
            player.repeatMode = if (ui.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            bars?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Study sessions and locks close the viewer, like they hide the Shorts tab.
    LaunchedEffect(vm.shortsAllowed) { if (!vm.shortsAllowed) vm.closeShorts() }

    LaunchedEffect(pager.settledPage, items.size) {
        val page = pager.settledPage
        items.getOrNull(page)?.let(vm::playShort)
        // Ready the next ones so a swipe starts quickly, and fetch more near the end.
        items.getOrNull(page + 1)?.let { Repo.prefetchShort(it.url) }
        items.getOrNull(page + 2)?.let { Repo.prefetchShort(it.url) }
        if (page >= items.size - 4) viewer.onMore()
    }

    // Comments open over the reel, which keeps playing behind.
    var commentsFor by remember { mutableStateOf<VideoItem?>(null) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { items[it].url }) { page ->
            ShortPage(
                items[page], ui, playing = page == pager.settledPage,
                onComments = { commentsFor = items[page] },
                onChannel = { url, name -> vm.closeShorts(); vm.openChannel(url, name) }
            )
        }
        commentsFor?.let { v ->
            val state = com.sourabh.jarvistube.ui.player.rememberComments(v.url)
            val links = com.sourabh.jarvistube.ui.player.rememberLinkActions(vm, v.url)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null
            ) { commentsFor = null }) {
                com.sourabh.jarvistube.ui.player.CommentsPanel(
                    state, links,
                    channelName = v.channel,
                    channelAvatar = v.avatar,
                    onClose = { commentsFor = null },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.66f)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                )
            }
        }
        IconButton(
            onClick = vm::closeShorts,
            modifier = Modifier.statusBarsPadding().padding(4.dp)
        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun ShortPage(
    video: VideoItem,
    ui: PlayerUi,
    playing: Boolean,
    onComments: () -> Unit = {},
    onChannel: (String, String) -> Unit = { _, _ -> }
) {
    val controller = Graph.player
    val now by controller.now.collectAsStateWithLifecycle()
    val firstFrame by controller.firstFrameUrl.collectAsStateWithLifecycle()
    val current = now?.takeIf { playing && it.video.url == video.url }
    val showVideo = current != null && !controller.audioOnly

    // Double-tap to pause or play (a single tap does nothing, so scrolling stays calm).
    // Press and hold near the left or right edge: 2x while you hold, like YouTube.
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var fast by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().background(Color.Black).pointerInput(current != null) {
            detectTapGestures(
                onDoubleTap = { if (current != null) controller.togglePlay() },
                onPress = { o ->
                    val edge = o.x < size.width * 0.2f || o.x > size.width * 0.8f
                    if (current == null || !edge) return@detectTapGestures
                    val before = controller.player.playbackParameters.speed
                    val hold = scope.launch {
                        kotlinx.coroutines.delay(350)
                        fast = true
                        controller.player.setPlaybackSpeed(2f)
                        if (!controller.player.playWhenReady) controller.player.play()
                    }
                    tryAwaitRelease()
                    hold.cancel()
                    if (fast) {
                        fast = false
                        controller.player.setPlaybackSpeed(before)
                    }
                }
            )
        }
    ) {
        if (showVideo) {
            // Only the Short on screen holds the video picture.
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.view_player_short, null) as PlayerView).also { view ->
                        view.player = controller.player
                        // Every Short in its own shape: nothing is cropped to fill the screen.
                        view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        val fit = object : Player.Listener {
                            override fun onVideoSizeChanged(size: VideoSize) {
                                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        }
                        view.tag = fit
                        controller.player.addListener(fit)
                    }
                },
                onRelease = {
                    (it.tag as? Player.Listener)?.let(controller.player::removeListener)
                    it.player = null
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        // The thumbnail covers the screen until the first frame of this Short is drawn.
        if (!showVideo || firstFrame != video.url) Thumb(video.thumb, Modifier.fillMaxSize())

        if (current != null && (current.loading || ui.isBuffering)) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(44.dp), color = Color.White, strokeWidth = 3.dp)
        } else if (current != null && !ui.isPlaying) {
            Icon(
                Icons.Rounded.PlayArrow, "Paused", tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(72.dp).clip(CircleShape)
                    .background(OnVideo.scrimLight).padding(12.dp)
            )
        }
        current?.error?.let {
            Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(32.dp))
        }

        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, OnVideo.scrim)))
                .navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 16.dp)
        ) {
            val channel = current?.info?.uploaderName ?: video.channel
            val channelUrl = current?.info?.uploaderUrl?.takeIf { it.isNotBlank() } ?: video.channelUrl
            if (channel.isNotBlank()) {
                // Tap the channel to open it.
                Text(
                    "@" + channel.removePrefix("@"),
                    color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clickable(enabled = channelUrl != null && !Graph.study.lockActive) {
                        channelUrl?.let { onChannel(it, channel) }
                    }
                )
            }
            Text(
                current?.info?.name ?: video.title,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
        // Captions (English / Hindi, one clean line) when turned on with CC.
        if (current != null) com.sourabh.jarvistube.ui.player.CaptionOverlay(bottomInset = 150.dp, modifier = Modifier.matchParentSize())
        // Reels-style actions on the right: comments, captions, share.
        Column(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 8.dp, bottom = 110.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)
        ) {
            ReelAction(Icons.AutoMirrored.Rounded.Comment, "Comments", onComments)
            val ccOn = current?.subtitleId != null
            ReelAction(if (ccOn) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionOff, if (ccOn) "CC on" else "CC") {
                current?.let { cur ->
                    if (ccOn) controller.setSubtitle(null)
                    else cur.subtitles.firstOrNull { it.id == "en" }?.let { controller.setSubtitle(it.id) }
                        ?: cur.subtitles.firstOrNull()?.let { controller.setSubtitle(it.id) }
                }
            }
            val context = LocalContext.current
            ReelAction(Icons.Rounded.Share, "Share") {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, video.url)
                }
                context.startActivity(android.content.Intent.createChooser(send, "Share"))
            }
        }
        if (current != null) ShortProgress(ui, Modifier.align(Alignment.BottomStart))
        // While held: a "2x ▶▶" badge at the top, as YouTube shows it.
        if (fast) Row(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 16.dp)
                .clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("2x", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.size(4.dp))
            Icon(Icons.Rounded.FastForward, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

/** Its own composable so position updates redraw only this line. */
@Composable
private fun ShortProgress(ui: PlayerUi, modifier: Modifier) {
    val progress = if (ui.duration > 0) (ui.position.toFloat() / ui.duration).coerceIn(0f, 1f) else 0f
    Box(modifier.fillMaxWidth(progress).height(2.dp).background(androidx.compose.material3.MaterialTheme.colorScheme.primary))
}


@Composable
private fun ReelAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(CircleShape).clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(30.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}
