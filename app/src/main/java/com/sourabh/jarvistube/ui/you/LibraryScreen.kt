package com.sourabh.jarvistube.ui.you

import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.Refresh
import com.sourabh.jarvistube.R
import androidx.compose.ui.res.stringResource
import com.sourabh.jarvistube.ui.theme.Corners
import com.sourabh.jarvistube.ui.common.Avatar
import com.sourabh.jarvistube.ui.common.EmptyState
import com.sourabh.jarvistube.ui.common.ErrorBox
import com.sourabh.jarvistube.ui.common.LoadMoreEffect
import com.sourabh.jarvistube.ui.common.LocalTabActive
import com.sourabh.jarvistube.ui.common.pressScale
import com.sourabh.jarvistube.ui.common.Thumb
import com.sourabh.jarvistube.ui.common.TopicChip
import com.sourabh.jarvistube.ui.common.VideoCard
import com.sourabh.jarvistube.ui.common.VideoCardSkeleton
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.LibDetail
import com.sourabh.jarvistube.LibFeed
import com.sourabh.jarvistube.LibTab
import com.sourabh.jarvistube.LibraryViewModel
import com.sourabh.jarvistube.TubeViewModel
import com.sourabh.jarvistube.data.LibChannel
import com.sourabh.jarvistube.data.LibPlaylist
import com.sourabh.jarvistube.data.LibVideo
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    vm: TubeViewModel,
    lib: LibraryViewModel,
    bottomPadding: Dp,
    onSignIn: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val tabs = LibTab.entries.toList()
    if (!lib.signedIn) {
        SignedOut(vm, bottomPadding, onSignIn, onOpenDownloads, onOpenSettings)
        return
    }
    val detail = lib.detail
    // Player collapses first; and only while the You tab is on screen.
    BackHandler(enabled = detail != null && !vm.playerExpanded && LocalTabActive.current) { lib.closeDetail() }
    LaunchedEffect(lib.tab, lib.signedIn) { lib.ensure(lib.tab) }

    val loader = if (detail != null) lib.detailLoader else lib.loader(lib.tab)
    val state = loader?.state ?: LibFeed(loading = true)
    val listState = rememberLazyListState()
    LaunchedEffect(lib.tab, detail) { listState.scrollToItem(0) }
    var accountSheet by remember { mutableStateOf(false) }
    var newPlaylist by remember { mutableStateOf(false) }
    var historyQuery by remember { mutableStateOf("") }
    var clearHistory by remember { mutableStateOf(false) }
    /** "YT History": only the YouTube account's history. */
    var ytOnly by remember { mutableStateOf(false) }
    if (clearHistory) ClearHistoryDialog(onDismiss = { clearHistory = false })
    if (accountSheet) {
        AccountSheet(lib, onAddAccount = { lib.prepareAddAccount(); onSignIn() }, onDismiss = { accountSheet = false })
    }
    if (newPlaylist) NewPlaylistDialog(onCreate = { lib.createPlaylist(it) }, onDismiss = { newPlaylist = false })

    // Refresh (pull down, or the button next to the tabs): History re-reads this app's list,
    // YT History / Playlists / Subscriptions load again from YouTube. No relaunch needed.
    val refreshScope = androidx.compose.runtime.rememberCoroutineScope()
    var appRefreshing by remember { mutableStateOf(false) }
    var ytRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(state.loading) { if (!state.loading) ytRefreshing = false }
    val onRefresh: () -> Unit = {
        if (detail == null && lib.tab == LibTab.History && !ytOnly) {
            appRefreshing = true
            com.sourabh.jarvistube.data.WatchHistory.reload()
            refreshScope.launch { kotlinx.coroutines.delay(500); appRefreshing = false }
        } else {
            ytRefreshing = true
            loader?.refresh()
        }
    }

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = appRefreshing || (ytRefreshing && state.loading),
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding(),
        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp)
    ) {
        if (detail == null) {
            item { YouTopBar(lib, onSwitch = { accountSheet = true }, onOpenDownloads, onOpenSettings) }
            stickyHeader {
                Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    val segments = listOf("History", "YT History", "Playlists", "Subscriptions")
                    val chosen = when {
                        lib.tab == LibTab.History && ytOnly -> 1
                        lib.tab == LibTab.History -> 0
                        lib.tab == LibTab.Playlists -> 2
                        else -> 3
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            Segmented(segments, chosen) { i ->
                                ytOnly = i == 1
                                lib.selectTab(listOf(LibTab.History, LibTab.History, LibTab.Playlists, LibTab.Channels)[i])
                            }
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(androidx.compose.material.icons.Icons.Rounded.Refresh, "Refresh")
                        }
                    }
                }
            }
        } else {
            item {
                Row(
                    Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { lib.closeDetail() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                    Text(
                        detail.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    val videos = state.items.filterIsInstance<LibVideo>().map { it.video }
                    if (videos.isNotEmpty()) {
                        TextButton(onClick = {
                            com.sourabh.jarvistube.Graph.player.clearQueue()
                            com.sourabh.jarvistube.Graph.player.enqueueAll(videos.drop(1))
                            vm.play(videos.first())
                        }) { Text(stringResource(R.string.play_all)) }
                    }
                }
            }
        }
        if (detail == null && lib.tab == LibTab.Playlists) {
            item {
                Row(
                    Modifier.fillMaxWidth().clickable { newPlaylist = true }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.width(120.dp).height(68.dp).clip(Corners.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Add, null) }
                    Spacer(Modifier.size(16.dp))
                    Text(stringResource(R.string.new_playlist), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        val historyShown = detail == null && lib.tab == LibTab.History && !ytOnly
        // History: only what you watched in this app. The YouTube account's list is in YT History.
        if (historyShown) historySection(vm, historyQuery, { historyQuery = it }, onClearAll = { clearHistory = true })
        // YT History: just the YouTube account's list, each with "Remove from watch history".
        else libItems(state, vm, lib, inHistory = detail == null && lib.tab == LibTab.History, inPlaylist = detail != null, onRetry = { loader?.refresh() })
    }
    }
    LoadMoreEffect(listState, state.items.size) { loader?.more() }
}

private fun LazyListScope.libItems(
    state: LibFeed,
    vm: TubeViewModel,
    lib: LibraryViewModel,
    inHistory: Boolean,
    inPlaylist: Boolean,
    onRetry: () -> Unit
) {
    when {
        state.loading && state.items.isEmpty() -> items(4) { VideoCardSkeleton() }
        state.error != null && state.items.isEmpty() -> item {
            ErrorBox(
                state.error, onRetry,
                extraLabel = if (state.sessionProblem) "Sign in again" else null,
                onExtra = { lib.signOut() }
            )
        }
        state.items.isEmpty() -> item {
            Text(
                "Nothing here yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(48.dp)
            )
        }
        else -> {
            items(state.items, key = { it.key }) { item ->
                when (item) {
                    is LibVideo -> if (!com.sourabh.jarvistube.data.ContentFilter.hides(item.video)) {
                        val actions = when {
                            inHistory -> listOf("Remove from watch history" to { lib.removeFromHistory(item) })
                            inPlaylist -> listOf("Remove from playlist" to { lib.removeFromPlaylist(item) })
                            else -> emptyList()
                        }
                        VideoCard(item.video, onClick = { vm.play(item.video) }, extraActions = actions)
                    }
                    is LibPlaylist -> RowCard(
                        thumb = item.thumb, circle = false, title = item.title, sub = item.sub,
                        onClick = { lib.openDetail(LibDetail(item.title, "VL" + item.id)) },
                        // Watch later / Liked videos are system lists and can't be deleted.
                        action = if (item.id == "WL" || item.id == "LL") null else "Delete playlist" to { lib.deletePlaylist(item) },
                        confirm = "Delete \"${item.title}\"? This can't be undone."
                    )
                    is LibChannel -> RowCard(
                        thumb = item.thumb, circle = true, title = item.name, sub = item.sub,
                        // Real channel page (Home/Videos/Live/Playlists) instead of a bare video list.
                        onClick = { vm.openChannel("https://www.youtube.com/channel/${item.id}", item.name) },
                        action = "Unsubscribe" to { lib.unsubscribe(item) },
                        confirm = "Unsubscribe from ${item.name}?"
                    )
                }
            }
            if (state.loadingMore) {
                item {
                    Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
                        com.sourabh.jarvistube.ui.theme.ChakraSpinner(30.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowCard(
    thumb: String?,
    circle: Boolean,
    title: String,
    sub: String,
    onClick: () -> Unit,
    action: Pair<String, () -> Unit>? = null,
    confirm: String? = null
) {
    var menu by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    if (asking && action != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { asking = false },
            text = { Text(confirm ?: "${action.first}?") },
            confirmButton = { TextButton(onClick = { asking = false; action.second() }) { Text(action.first) } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    Row(
        Modifier.fillMaxWidth().pressScale(onClick).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumb(
            thumb,
            Modifier
                .then(if (circle) Modifier.size(56.dp) else Modifier.width(120.dp).height(68.dp))
                .clip(if (circle) CircleShape else Corners.medium)
        )
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (action != null) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                androidx.compose.material3.DropdownMenu(menu, { menu = false }) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(action.first) },
                        onClick = { menu = false; asking = true }
                    )
                }
            }
        }
    }
}

/**
 * Who is signed in, like YouTube's You page: photo, name and @handle, with a
 * "Switch account" link to the account sheet (add, switch, sign out).
 */
/**
 * The top of the You tab in one row: your photo and name (tap to switch account), then Downloads
 * and Settings. Signed out, it just says "You".
 */
@Composable
private fun YouTopBar(lib: LibraryViewModel?, onSwitch: () -> Unit, onOpenDownloads: () -> Unit, onOpenSettings: () -> Unit) {
    val a = lib?.account
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (a != null) {
            Row(
                Modifier.weight(1f).clip(com.sourabh.jarvistube.ui.theme.Corners.pill).clickable(onClick = onSwitch)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Brush.sweepGradient(listOf(cs.primary, cs.secondary, cs.primary))),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(cs.background), contentAlignment = Alignment.Center) {
                        Avatar(a.name ?: a.email ?: "?", 40, a.photo)
                    }
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name ?: "Your account", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // The Gmail address stays private here; Settings shows it.
                    Text(
                        (a.handle?.let { "$it · " } ?: "") + "Switch account",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            Text(stringResource(R.string.you), style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
        }
        IconButton(onClick = onOpenDownloads) { Icon(Icons.Rounded.Download, "Downloads") }
        IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, "Settings") }
    }
}

/** A row of equal segments with a pill that glides to the chosen one. */
@Composable
internal fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val touch = com.sourabh.jarvistube.ui.theme.rememberTouch()
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxWidth().height(44.dp).clip(CircleShape).background(cs.surfaceVariant).padding(4.dp)
    ) {
        val w = maxWidth / labels.size
        val x by androidx.compose.animation.core.animateDpAsState(
            w * selected,
            if (com.sourabh.jarvistube.ui.theme.Motion.NORMAL == 0) androidx.compose.animation.core.snap()
            else androidx.compose.animation.core.spring(dampingRatio = 0.75f, stiffness = 500f),
            label = "segment"
        )
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(CircleShape).background(cs.primary))
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                val fg by androidx.compose.animation.animateColorAsState(if (on) cs.onPrimary else cs.onSurfaceVariant, label = "segFg")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                        .clickable { if (!on) { touch(); onSelect(i) } },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label, color = fg, maxLines = 1, softWrap = false,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun NewPlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_playlist)) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = title, onValueChange = { title = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.title)) }, modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = { onCreate(title); onDismiss() }) { Text(stringResource(R.string.create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SignedOut(
    vm: TubeViewModel,
    bottomPadding: Dp,
    onSignIn: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var clearHistory by remember { mutableStateOf(false) }
    if (clearHistory) ClearHistoryDialog(onDismiss = { clearHistory = false })
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().imePadding(),
        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp)
    ) {
        item { YouTopBar(null, onSwitch = {}, onOpenDownloads, onOpenSettings) }
        item { SignedOutBody(onSignIn) }
        item {
            Text(
                "History", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
            )
        }
        historySection(vm, query, { query = it }, onClearAll = { clearHistory = true })
    }
}

@Composable
private fun SignedOutBody(onSignIn: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(Corners.large).background(cs.surfaceContainer).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.VideoLibrary, null, tint = cs.primary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Your playlists and subscriptions", style = MaterialTheme.typography.titleSmall)
            Text(
                "Unofficial sign-in. A secondary Google account is safest.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )
        }
        Spacer(Modifier.size(8.dp))
        Button(onClick = onSignIn) { Text("Sign in") }
    }
}
