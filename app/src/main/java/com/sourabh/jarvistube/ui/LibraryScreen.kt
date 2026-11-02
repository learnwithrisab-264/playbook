package com.sourabh.jarvistube.ui

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
import androidx.compose.material.icons.automirrored.rounded.Logout
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
import com.sourabh.jarvistube.data.InnerTube
import com.sourabh.jarvistube.data.LibChannel
import com.sourabh.jarvistube.data.LibPlaylist
import com.sourabh.jarvistube.data.LibVideo
import androidx.compose.foundation.layout.imePadding

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
    if (clearHistory) ClearHistoryDialog(onDismiss = { clearHistory = false })
    if (accountSheet) {
        AccountSheet(lib, onAddAccount = { lib.prepareAddAccount(); onSignIn() }, onDismiss = { accountSheet = false })
    }
    if (newPlaylist) NewPlaylistDialog(onCreate = { lib.createPlaylist(it) }, onDismiss = { newPlaylist = false })

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding(),
        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp)
    ) {
        if (detail == null) {
            item { YouHeader(onOpenDownloads, onOpenSettings) }
            item { ProfileHeader(lib, onSwitch = { accountSheet = true }) }
            item { ShortsBlockCard() }
            stickyHeader {
                Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tabs) { t ->
                            TopicChip(t.label, t == lib.tab) { lib.selectTab(t) }
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
                        }) { Text("Play all") }
                    }
                }
            }
        }
        if (detail == null && lib.tab == LibTab.Playlists) {
            item {
                Row(
                    Modifier.fillMaxWidth().clickable { newPlaylist = true }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.width(120.dp).height(68.dp).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Add, null) }
                    Spacer(Modifier.size(14.dp))
                    Text("New playlist", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (detail == null && lib.tab == LibTab.History) {
            historySection(vm, historyQuery, { historyQuery = it }, onClearAll = { clearHistory = true })
            if (state.items.isNotEmpty()) item(key = "youtube-history") {
                Text(
                    "From your YouTube account",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
                )
            }
        }
        libItems(state, vm, lib, inHistory = detail == null && lib.tab == LibTab.History, inPlaylist = detail != null, onRetry = { loader?.refresh() })
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
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
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
            dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancel") } }
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
                .clip(if (circle) CircleShape else RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.size(14.dp))
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
@Composable
private fun ProfileHeader(lib: LibraryViewModel, onSwitch: () -> Unit) {
    val a = lib.account
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSwitch).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(a?.name ?: a?.email ?: "?", 64, a?.photo)
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                a?.name ?: "Your account",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            // The Gmail address stays private here; Settings › Account shows it.
            a?.handle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                if (lib.accounts.size > 1) "Switch account (${lib.accounts.size}) ›" else "Switch or add account ›",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun NewPlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = title, onValueChange = { title = it }, singleLine = true,
                placeholder = { Text("Title") }, modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = { onCreate(title); onDismiss() }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun YouHeader(onOpenDownloads: () -> Unit, onOpenSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("You", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onOpenDownloads) { Icon(Icons.Rounded.Download, "Downloads") }
        IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, "Settings") }
    }
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
        item { YouHeader(onOpenDownloads, onOpenSettings) }
        item { ShortsBlockCard() }
        item { SignedOutBody(onSignIn) }
        item {
            Text(
                "History", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
            )
        }
        historySection(vm, query, { query = it }, onClearAll = { clearHistory = true })
    }
}

@Composable
private fun SignedOutBody(onSignIn: () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EmptyState(
            Icons.Rounded.VideoLibrary,
            "Your library",
            "Sign in to see your subscriptions, watch history, playlists and channels, without the recommendations."
        )
        Button(onClick = onSignIn) { Text("Sign in with Google") }
        Spacer(Modifier.height(12.dp))
        Text(
            "Unofficial sign-in. A secondary Google account is safest.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}
