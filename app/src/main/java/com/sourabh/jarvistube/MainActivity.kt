package com.sourabh.jarvistube

import com.sourabh.jarvistube.ui.theme.Motion
import android.Manifest
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.sourabh.jarvistube.ui.home.HomeScreen
import com.sourabh.jarvistube.ui.common.LocalTabActive
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import com.sourabh.jarvistube.ui.home.RecommendedScreen
import com.sourabh.jarvistube.ui.shorts.ShortsScreen
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.rounded.Explore
import com.sourabh.jarvistube.ui.you.YouScreen
import com.sourabh.jarvistube.ui.channel.PageHost
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch
import com.sourabh.jarvistube.ui.common.LocalOpenChannel
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import com.sourabh.jarvistube.ui.search.SearchOverlay
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sourabh.jarvistube.ui.you.LoginScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sourabh.jarvistube.ui.player.MiniPlayer
import com.sourabh.jarvistube.ui.player.FloatingPlayer
import androidx.compose.foundation.layout.statusBars
import com.sourabh.jarvistube.ui.player.PlayerScreen
import com.sourabh.jarvistube.ui.study.StudyScreen
import com.sourabh.jarvistube.ui.player.rememberPlayerUi
import com.sourabh.jarvistube.ui.theme.JarvisTubeTheme

private enum class Tab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    Home("Home", Icons.Rounded.Home, Icons.Outlined.Home),
    Study("Study", Icons.Rounded.School, Icons.Outlined.School),
    Recommended("Recommended", Icons.Rounded.Explore, Icons.Outlined.Explore),
    Shorts("Shorts", Icons.Rounded.PlayCircle, Icons.Outlined.PlayCircle),
    You("You", Icons.Rounded.AccountCircle, Icons.Outlined.AccountCircle)
}

/**
 * A tab kept alive in the background: still laid out (so showing it again needs no new layout),
 * but invisible, unreachable by touch and hidden from accessibility. The visible tab sits above
 * it (zIndex 1).
 */
private fun Modifier.notShown(): Modifier = this
    .graphicsLayer { alpha = 0f }
    .clearAndSetSemantics { }
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    .changes.forEach { it.consume() }
            }
        }
    }

/**
 * YouTube's own bottom bar: equal items, outlined icon when idle and filled when active,
 * a small label, no pills or tints. Just a hairline on top to separate it from the content.
 */
@Composable
private fun BottomBar(tabs: List<Tab>, current: Tab, onSelect: (Tab) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val touch = com.sourabh.jarvistube.ui.theme.rememberTouch()
    Column(Modifier.fillMaxWidth().background(cs.background)) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(cs.outlineVariant.copy(alpha = 0.35f)))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { t ->
                val selected = t == current
                val tint by animateColorAsState(
                    if (selected) cs.onSurface else cs.onSurfaceVariant, tween(Motion.FAST), label = "tab"
                )
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                // The diya: the tab you are on is lit by a small flickering flame.
                androidx.compose.animation.AnimatedVisibility(
                    visible = selected,
                    enter = androidx.compose.animation.fadeIn(tween(Motion.NORMAL)) + androidx.compose.animation.scaleIn(tween(Motion.NORMAL), 0.6f),
                    exit = androidx.compose.animation.fadeOut(tween(Motion.FAST))
                ) { com.sourabh.jarvistube.ui.theme.DiyaGlow(Modifier.size(64.dp, 52.dp)) }
                Column(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { if (!selected) touch(); onSelect(t) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        if (selected) t.selectedIcon else t.unselectedIcon, t.label,
                        tint = tint, modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        t.label, fontSize = 10.sp, color = tint, maxLines = 1, softWrap = false,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
                }
            }
        }
    }
}

private const val PIP_ACTION = "com.sourabh.jarvistube.PIP_BUTTON"

class MainActivity : ComponentActivity() {
    override fun onStop() {
        super.onStop()
        // The home-screen icon follows the theme; switched only while PlayBook is in the background.
        com.sourabh.jarvistube.ui.theme.AppIcon.apply(this)
    }

    private val vm: TubeViewModel by viewModels()
    private var inPip by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handlePlayExtra(intent)
        // App pinning is no longer used for the study lock: it also blocked alarms, calls, the
        // dialer and quick settings. StudyGuardService enforces the lock instead and leaves those alone.
        // Unpin in case an older build left the app pinned.
        unpinIfPinned()
        val filter = android.content.IntentFilter(PIP_ACTION)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pipButtons, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(pipButtons, filter)
        Graph.player.player.addListener(pipState)
        setContent {
            JarvisTubeTheme {
                val askNotifications = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                AppRoot(vm, inPip)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handlePlayExtra(intent)
    }

    /** Opens a video from a new-upload notification, which carries its link as play_url. */
    private fun handlePlayExtra(intent: android.content.Intent?) {
        val url = intent?.getStringExtra("play_url") ?: return
        vm.play(com.sourabh.jarvistube.data.VideoItem(url, "Video", "", null, 0, -1, null, false))
    }

    private fun unpinIfPinned() {
        val am = getSystemService(android.app.ActivityManager::class.java) ?: return
        if (am.lockTaskModeState != android.app.ActivityManager.LOCK_TASK_MODE_NONE) runCatching { stopLockTask() }
    }

    override fun onDestroy() {
        unregisterReceiver(pipButtons)
        Graph.player.player.removeListener(pipState)
        super.onDestroy()
    }

    /** The buttons on the PiP window. */
    private val pipButtons = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            when (intent.getStringExtra("button")) {
                "previous" -> Graph.player.skipPrevious()
                "toggle" -> Graph.player.togglePlay()
                "next" -> Graph.player.skipNext()
            }
        }
    }

    /** Keeps the PiP Play/Pause icon matching the player, whatever changed it. */
    private val pipState = object : androidx.media3.common.Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (isInPictureInPictureMode) setPictureInPictureParams(pipParams())
        }
    }

    private fun pipAction(button: String, icon: Int, label: String, code: Int): android.app.RemoteAction {
        val intent = android.content.Intent(PIP_ACTION).setPackage(packageName).putExtra("button", button)
        val pending = android.app.PendingIntent.getBroadcast(
            this, code, intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        return android.app.RemoteAction(android.graphics.drawable.Icon.createWithResource(this, icon), label, label, pending)
    }

    private fun pipParams(): PictureInPictureParams {
        val playing = Graph.player.player.playWhenReady
        val actions = listOf(
            pipAction("previous", R.drawable.ic_media_previous, "Previous", 1),
            if (playing) pipAction("toggle", R.drawable.ic_media_pause, "Pause", 2)
            else pipAction("toggle", R.drawable.ic_media_play, "Play", 2),
            pipAction("next", R.drawable.ic_media_next, "Next", 3)
        )
        return PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).setActions(actions).build()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // No picture-in-picture during a study lock: the locked video stays full-screen. A video
        // in the in-app floating window carries on in system PiP when you leave the app.
        // In Listen mode there is no picture to float: the sound simply carries on in the background.
        val showingVideo = !Graph.player.audioOnly && vm.shortsViewer == null
        if (showingVideo && Graph.player.player.isPlaying && !Graph.study.lockActive) {
            enterPictureInPictureMode(
                pipParams()
            )
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        if (!isInPictureInPictureMode) {
            // Expanding returns to the same video and position in the full player. Dismissing
            // the window stops the Activity: that means stop watching, so playback pauses.
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) vm.playerExpanded = Graph.player.now.value != null
            else Graph.player.player.pause()
        }
    }
}

@Composable
private fun AppRoot(vm: TubeViewModel, inPip: Boolean) {
    val controller = Graph.player
    val now by controller.now.collectAsStateWithLifecycle()
    val ui = rememberPlayerUi(controller.player)
    // Keep the display on while a video is actually playing (not for audio-only).
    val window = (androidx.compose.ui.platform.LocalContext.current as? android.app.Activity)?.window
    LaunchedEffect(ui.isPlaying, controller.audioOnly) {
        val flag = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        if (ui.isPlaying && !controller.audioOnly) window?.addFlags(flag) else window?.clearFlags(flag)
    }
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val homeState = rememberLazyListState()
    val expanded = vm.playerExpanded && now != null
    val lib: LibraryViewModel = viewModel()
    var showLogin by rememberSaveable { mutableStateOf(false) }

    val study = Graph.study
    // Recommended is gone while the setting is off and during any session, multi-day lock,
    // scheduled window or timed video lock (the bar stays visible during a video lock).
    val tabs = Tab.entries.filter { (it != Tab.Recommended || vm.recommendedAllowed) && (it != Tab.Shorts || (vm.shortsAllowed && AppSettings.showShorts)) && (it != Tab.Study || AppSettings.studyTabShown) }
    val shownTab = when {
        study.active -> Tab.Study
        tab !in tabs -> Tab.Home
        else -> tab
    }
    val recommendedState = rememberLazyListState()
    val shortsState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    // Don't jump back to Recommended when a lock ends: the user lands on Home instead.
    LaunchedEffect(tabs) { if (tab !in tabs) tab = Tab.Home }

    LaunchedEffect(now == null) { if (now == null) vm.playerExpanded = false }

    // Lock Study Session: (re)open the locked video full-screen, including after a crash or reboot.
    // Runs again if the player ever closes, or anything else hides it, while the session runs.
    LaunchedEffect(study.lockActive, now == null, vm.playerExpanded, vm.pages.size, vm.searchOpen) {
        if (study.lockActive) {
            vm.pages.clear()
            vm.closeSearch()
        }
        if (study.lockActive) study.lockedVideo()?.let { v ->
            if (controller.now.value?.video?.url != v.url) vm.play(v)
            vm.playerExpanded = true
        }
    }
    // A session just started: drop anything that isn't allowed.
    LaunchedEffect(study.active) {
        val v = controller.now.value?.video
        if (study.active && v != null && !study.isAllowed(v.channel, v.channelUrl)) controller.stop()
    }
    // Declared before the player's handler so Back collapses the player first, then closes search.
    BackHandler(enabled = vm.searchOpen) { vm.closeSearch() }
    BackHandler(enabled = vm.pages.isNotEmpty()) { vm.popPage() }
    BackHandler(enabled = expanded && !study.lockActive) { vm.playerExpanded = false }

    // The mini-player lives at the top level (not inside the tab Scaffold) so it stays visible
    // above search and channel pages too. Leaving the full player therefore always leaves a
    // visible, controllable mini-player instead of audio playing with nothing on screen.
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val overlayOpen = vm.searchOpen || vm.pages.isNotEmpty()
    // Collapsed player: video keeps playing in a floating window above every screen; in audio-only
    // mode (nothing to show) the bottom mini-player bar is used instead. Never during a study
    // lock, where the locked video stays full-screen.
    val collapsed = now != null && !expanded && !inPip && !study.lockActive && vm.shortsViewer == null
    val showFloating = collapsed && !controller.audioOnly
    val showMini = collapsed && controller.audioOnly
    val barHeight = if (study.active) 0.dp else 56.5.dp
    val miniPad = if (showMini) 72.dp else 0.dp
    val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    CompositionLocalProvider(
        LocalOpenChannel provides { v: com.sourabh.jarvistube.data.VideoItem ->
            v.channelUrl?.let { vm.openChannel(it, v.channel) }
        }
    ) {
    Box(Modifier.fillMaxSize()) {
        if (!inPip) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0),
                bottomBar = {
                    if (study.active) {
                        Spacer(Modifier.navigationBarsPadding())
                    } else {
                        BottomBar(tabs, current = shownTab, onSelect = { tab = it })
                    }
                }
            ) { padding ->
                val bottom = padding.calculateBottomPadding() + miniPad
                // Tabs you have opened stay composed (just not drawn), so switching back is
                // instant instead of rebuilding the whole screen, like YouTube's own tabs.
                // Plain set (not state): it only changes together with shownTab/tabs, which already
                // recompose this. Recommended is dropped as soon as a lock hides it.
                val opened = remember { LinkedHashSet<Tab>() }
                opened.add(shownTab)
                opened.retainAll { it in tabs || it == shownTab }
                Box(Modifier.fillMaxSize()) {
                    opened.toList().forEach { t ->
                        key(t) {
                            val active = t == shownTab
                            Box(Modifier.fillMaxSize().then(if (active) Modifier.zIndex(1f) else Modifier.notShown())) {
                                CompositionLocalProvider(LocalTabActive provides active) {
                                    when (t) {
                                        Tab.Home -> HomeScreen(vm, homeState, bottom, onProfile = { tab = Tab.You })
                                        Tab.Study -> StudyScreen(vm, lib, bottom)
                                        Tab.Recommended -> RecommendedScreen(vm, recommendedState, bottom, onProfile = { tab = Tab.You })
                                        Tab.Shorts -> ShortsScreen(vm, shortsState, bottom, onProfile = { tab = Tab.You })
                                        Tab.You -> YouScreen(vm, lib, bottom, onSignIn = { showLogin = true })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = vm.searchOpen && !inPip,
            enter = fadeIn(tween(Motion.FAST)) + slideInVertically(tween(Motion.NORMAL)) { it / 12 },
            exit = fadeOut(tween(Motion.FAST))
        ) {
            SearchOverlay(vm, bottomPadding = navInset + miniPad)
        }

        // Channel / playlist / study-channel pages, stacked above the tabs and search.
        PageHost(vm, lib, bottomPadding = navInset + miniPad, visible = !inPip)

        AnimatedVisibility(
            visible = showMini,
            enter = slideInVertically(tween(Motion.NORMAL)) { it } + fadeIn(tween(Motion.FAST)),
            exit = slideOutVertically(tween(Motion.NORMAL)) { it } + fadeOut(tween(Motion.FAST)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (overlayOpen) navInset else navInset + barHeight)
        ) {
            now?.let { MiniPlayer(it, ui, onExpand = { vm.playerExpanded = true }) }
        }

        // In-app picture-in-picture: above the tabs, search and pages; below the full player.
        AnimatedVisibility(
            visible = showFloating,
            enter = fadeIn(tween(Motion.NORMAL)),
            exit = fadeOut(tween(Motion.FAST))
        ) {
            now?.let {
                FloatingPlayer(
                    it, ui,
                    topInset = statusInset,
                    bottomInset = if (overlayOpen) navInset else navInset + barHeight,
                    onExpand = { vm.playerExpanded = true }
                )
            }
        }

        AnimatedVisibility(
            visible = expanded || (inPip && now != null),
            // Short and front-loaded so the player feels instant, like YouTube's.
            enter = slideInVertically(tween(Motion.NORMAL, easing = LinearOutSlowInEasing)) { it / 4 } + fadeIn(tween(Motion.FAST)),
            exit = slideOutVertically(tween(Motion.NORMAL, easing = FastOutLinearInEasing)) { it / 3 } + fadeOut(tween(Motion.FAST))
        ) {
            PlayerScreen(vm, ui, inPip)
        }

        AnimatedVisibility(
            visible = showLogin,
            enter = slideInVertically(tween(Motion.NORMAL)) { it } + fadeIn(),
            exit = slideOutVertically(tween(Motion.NORMAL)) { it } + fadeOut()
        ) {
            LoginScreen(
                onCookie = { lib.onSignedIn(it); showLogin = false },
                onClose = { showLogin = false }
            )
        }
        // Full-screen Shorts sit above every screen.
        vm.shortsViewer?.let { com.sourabh.jarvistube.ui.shorts.ShortsViewerScreen(vm, it, ui) }
        com.sourabh.jarvistube.ui.you.SaveToPlaylistHost()
        com.sourabh.jarvistube.ui.downloads.DownloadSheetHost()
    }
    }
}
