package com.sourabh.jarvistube.ui.shorts

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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { items[it].url }) { page ->
            ShortPage(items[page], ui, playing = page == pager.settledPage)
        }
        IconButton(
            onClick = vm::closeShorts,
            modifier = Modifier.statusBarsPadding().padding(4.dp)
        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun ShortPage(video: VideoItem, ui: PlayerUi, playing: Boolean) {
    val controller = Graph.player
    val now by controller.now.collectAsStateWithLifecycle()
    val firstFrame by controller.firstFrameUrl.collectAsStateWithLifecycle()
    val current = now?.takeIf { playing && it.video.url == video.url }
    val showVideo = current != null && !controller.audioOnly

    Box(
        Modifier.fillMaxSize().background(Color.Black).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { if (current != null) controller.togglePlay() }
        )
    ) {
        if (showVideo) {
            // Only the Short on screen holds the video picture.
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.view_player_short, null) as PlayerView).also { view ->
                        view.player = controller.player
                        // Tall videos fill the screen; a wide clip is shown whole instead of cropped.
                        val fit = object : Player.Listener {
                            override fun onVideoSizeChanged(size: VideoSize) {
                                view.resizeMode = if (size.width > size.height) AspectRatioFrameLayout.RESIZE_MODE_FIT
                                else AspectRatioFrameLayout.RESIZE_MODE_ZOOM
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
            if (channel.isNotBlank()) {
                Text(channel, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                current?.info?.name ?: video.title,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
        if (current != null) ShortProgress(ui, Modifier.align(Alignment.BottomStart))
    }
}

/** Its own composable so position updates redraw only this line. */
@Composable
private fun ShortProgress(ui: PlayerUi, modifier: Modifier) {
    val progress = if (ui.duration > 0) (ui.position.toFloat() / ui.duration).coerceIn(0f, 1f) else 0f
    Box(modifier.fillMaxWidth(progress).height(2.dp).background(Brand.orange))
}
