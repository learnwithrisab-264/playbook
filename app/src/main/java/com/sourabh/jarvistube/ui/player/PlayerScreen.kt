package com.sourabh.jarvistube.ui.player

import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.AppSettings

import kotlinx.coroutines.launch

import androidx.compose.ui.graphics.graphicsLayer

import com.sourabh.jarvistube.ui.study.formatLeft
import com.sourabh.jarvistube.ui.study.StudyLockDialogHost
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import com.sourabh.jarvistube.data.bestUrl
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Fullscreen
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.TubeViewModel

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
    // Swiping up while holding the phone upright gives fullscreen that stays upright (the video
    // sits in the middle, full width; pinch zoom can fill the screen). The button, a lock and
    // turning the phone sideways give the normal landscape fullscreen.
    var portraitFullscreen by remember { mutableStateOf(false) }
    var wasPortraitFullscreen by remember { mutableStateOf(false) }
    LaunchedEffect(landscape) {
        if (landscape) portraitFullscreen = false
        if (landscape && !holdPortrait && !inPip) fullscreen = true
    }
    LaunchedEffect(locked) { if (locked) portraitFullscreen = false }

    DisposableEffect(fullscreen, portraitFullscreen) {
        val window = activity?.window
        val ctl = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        if (fullscreen) {
            // Upright fullscreen doesn't force a rotation, so turning the phone still switches to landscape.
            activity?.requestedOrientation = if (portraitFullscreen) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl?.hide(WindowInsetsCompat.Type.systemBars())
        } else if (wasFullscreen && !wasPortraitFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            holdPortrait = true
        }
        wasFullscreen = fullscreen
        wasPortraitFullscreen = fullscreen && portraitFullscreen
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

    // The status bar sits on black above the video: keep its icons white in every theme.
    androidx.compose.runtime.DisposableEffect(Unit) {
        com.sourabh.jarvistube.ui.theme.SystemBars.overVideo = true
        onDispose { com.sourabh.jarvistube.ui.theme.SystemBars.overVideo = false }
    }
    // YouTube's pull-to-minimise: the player follows the finger down and shrinks; let go past the
    // threshold and it drops into the mini player, otherwise it springs back.
    val pull = remember { androidx.compose.animation.core.Animatable(0f) }
    val pullScope = androidx.compose.runtime.rememberCoroutineScope()
    // Ambient mode: the video's colours glow softly behind the player, like YouTube's.
    val ambientOn = AppSettings.ambient && !fs && android.os.Build.VERSION.SDK_INT >= 31
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    if (ambientOn) {
        val thumb = cur.video.thumb ?: cur.info?.thumbnails?.let { it.bestUrl() }
        coil3.compose.AsyncImage(
            model = thumb,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(520.dp)
                .graphicsLayer { alpha = 0.75f }
                .blur(90.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded)
        )
        Box(
            Modifier.fillMaxWidth().height(540.dp).background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to MaterialTheme.colorScheme.background.copy(alpha = 0.2f),
                    0.55f to MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                    1f to MaterialTheme.colorScheme.background
                )
            )
        )
    }
    Column(
        Modifier.fillMaxSize()
            .graphicsLayer {
                val p = (pull.value / (size.height * 0.6f)).coerceIn(0f, 1f)
                translationY = pull.value
                scaleX = 1f - 0.25f * p
                scaleY = 1f - 0.25f * p
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                alpha = 1f - 0.35f * p
            }
            .then(if (ambientOn) Modifier else Modifier.background(MaterialTheme.colorScheme.background))
    ) {
        StudyLockDialogHost(cur)
        PlayerMoreSheetHost(cur, ui)
        if (!fs) {
            Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Color.Black))
        }
        // Fullscreen comments sit beside the video (it keeps playing on the left), as on YouTube.
        val comments = rememberComments(cur.video.url)
        val links = rememberLinkActions(vm, cur.video.url)
        var sideComments by remember(cur.video.url) { mutableStateOf(false) }
        LaunchedEffect(fullscreen, landscape) { if (!fullscreen || !landscape) sideComments = false }
        androidx.compose.foundation.layout.Row(if (fs) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
        VideoBox(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            onComments = if (fullscreen && landscape && !inPip) ({ sideComments = true }) else null,
            now = cur,
            ui = ui,
            fullscreen = fs,
            showControls = !inPip,
            onToggleFullscreen = { if (!locked) { portraitFullscreen = false; fullscreen = !fullscreen } },
            onCollapse = { if (!locked) vm.playerExpanded = false },
            onPlay = vm::play,
            onPull = { y -> if (!locked && !fs) pullScope.launch { pull.snapTo(y.coerceAtLeast(0f)) } },
            onPullEnd = { minimising ->
                if (!minimising || locked) pullScope.launch { pull.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.8f)) }
            },
            // Swipe up on the video = fullscreen; swipe down = leave fullscreen, or minimise to the mini-player.
            onSwipe = { dir -> if (!locked)
                when {
                    dir < 0 && !fullscreen -> { portraitFullscreen = !landscape; fullscreen = true }
                    dir > 0 && fullscreen -> fullscreen = false
                    dir > 0 -> vm.playerExpanded = false
                }
            }
        )
        if (sideComments && fullscreen) {
            CommentsPanel(
                comments, links,
                channelName = cur.info?.uploaderName ?: cur.video.channel,
                channelAvatar = cur.info?.uploaderAvatars?.let { it.bestUrl() } ?: cur.video.avatar,
                onClose = { sideComments = false },
                modifier = Modifier.fillMaxHeight().fillMaxWidth(0.42f)
            )
        }
        }
        if (!fs) {
            Details(vm, cur, ui, Modifier.weight(1f))
        }
    }
    }
}
