package com.sourabh.jarvistube.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.LibTab
import com.sourabh.jarvistube.LibraryViewModel
import com.sourabh.jarvistube.Page
import com.sourabh.jarvistube.TubeViewModel
import com.sourabh.jarvistube.data.ChannelPage
import com.sourabh.jarvistube.data.ChannelRef
import com.sourabh.jarvistube.data.ChannelRepo
import com.sourabh.jarvistube.data.ChannelTab
import com.sourabh.jarvistube.data.Entry
import com.sourabh.jarvistube.data.LibChannel
import com.sourabh.jarvistube.data.PlaylistPage
import com.sourabh.jarvistube.data.StudyChannels
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page as NpPage

/** Shows the top page of [TubeViewModel.pages] above the tabs (Back pops it). */
@Composable
fun PageHost(vm: TubeViewModel, lib: LibraryViewModel, bottomPadding: Dp, visible: Boolean) {
    val top = if (visible) vm.pages.lastOrNull() else null
    AnimatedContent(
        targetState = top,
        transitionSpec = { (slideInHorizontally(tween(200)) { it / 8 } + fadeIn(tween(160))) togetherWith fadeOut(tween(120)) },
        label = "pages"
    ) { page ->
        when (page) {
            null -> Unit
            is Page.Channel -> ChannelScreen(vm, page, bottomPadding)
            is Page.Playlist -> PlaylistScreen(vm, page, bottomPadding)
            Page.StudyChannels -> StudyChannelsScreen(vm, lib, bottomPadding)
        }
    }
}

private data class TabState(
    val items: List<Entry> = emptyList(),
    val next: NpPage? = null,
    val loading: Boolean = false,
    val error: String? = null
)

// ------------------------------------------------------------------ channel

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelScreen(vm: TubeViewModel, page: Page.Channel, bottomPadding: Dp) {
    val scope = rememberCoroutineScope()
    var channel by remember(page.url) { mutableStateOf<ChannelPage?>(null) }
    var error by remember(page.url) { mutableStateOf<String?>(null) }
    var selected by rememberSaveable(page.url) { mutableStateOf("home") }
    val tabStates = remember(page.url) { mutableStateMapOf<String, TabState>() }
    val listState = rememberLazyListState()

    fun loadTab(c: ChannelPage, tab: ChannelTab, more: Boolean) {
        val cur = tabStates[tab.key] ?: TabState()
        if (cur.loading || (more && cur.next == null)) return
        tabStates[tab.key] = cur.copy(loading = true, error = null)
        scope.launch {
            tabStates[tab.key] = try {
                val p = if (more) ChannelRepo.tabMore(c, tab, cur.next!!) else ChannelRepo.tab(c, tab)
                TabState((cur.items + p.items).distinctBy { it.key }, p.next)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                cur.copy(loading = false, error = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    LaunchedEffect(page.url) {
        try {
            channel = ChannelRepo.page(page.url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
    }
    val c = channel
    // "Home" shows the About text plus the latest uploads (the Videos tab's first page).
    val activeTab: ChannelTab? = c?.let { ch ->
        if (selected == "home") ch.tabs.firstOrNull { it.key == "videos" } else ch.tabs.firstOrNull { it.key == selected }
    }
    LaunchedEffect(c, activeTab?.key) {
        if (c != null && activeTab != null && tabStates[activeTab.key] == null) loadTab(c, activeTab, more = false)
    }
    val state = activeTab?.let { tabStates[it.key] } ?: TabState(loading = c == null && error == null)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp)
    ) {
        item { PageTopBar(c?.ref?.name ?: page.name, vm::popPage) }
        if (error != null && c == null) {
            item { ErrorBox(error!!, onRetry = { error = null; scope.launch { runCatching { channel = ChannelRepo.page(page.url) }.onFailure { error = it.message } } }) }
            return@LazyColumn
        }
        if (c != null) {
            item { ChannelHeader(c) }
            stickyHeader {
                Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { TopicChip("Home", selected == "home") { selected = "home" } }
                        items(c.tabs, key = { it.key }) { t -> TopicChip(t.label, selected == t.key) { selected = t.key } }
                    }
                }
            }
            if (selected == "home" && c.description.isNotBlank()) {
                item {
                    Text(
                        c.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
        if (c != null && activeTab == null) {
            item { EmptyHint("This channel has nothing here.") }
        } else {
            entryItems(vm, state, onRetry = { if (c != null && activeTab != null) loadTab(c, activeTab, more = false) })
        }
    }
    if (c != null && activeTab != null && selected != "home") {
        LoadMoreEffect(listState, state.items.size) { loadTab(c, activeTab, more = true) }
    }
}

@Composable
private fun ChannelHeader(c: ChannelPage) {
    val inStudy = StudyChannels.contains(c.ref.url)
    Column {
        if (c.banner != null) {
            Thumb(c.banner, Modifier.fillMaxWidth().aspectRatio(6.2f).padding(horizontal = 12.dp).clip(RoundedCornerShape(12.dp)))
        }
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(c.ref.name, 72, c.ref.avatar)
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(c.ref.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (c.subscribers >= 0) {
                    Text(
                        "${compact(c.subscribers)} subscribers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            UploadBell(c.ref)
            SubscribeButton(c.ref.url, c.ref.name)
        }
        Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
            if (inStudy) {
                FilledTonalButton(onClick = { StudyChannels.toggle(c.ref) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("In my channels")
                }
            } else {
                Button(onClick = { StudyChannels.toggle(c.ref) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Add to my channels")
                }
            }
        }
    }
}

// ------------------------------------------------------------------ playlist

@Composable
private fun PlaylistScreen(vm: TubeViewModel, page: Page.Playlist, bottomPadding: Dp) {
    val scope = rememberCoroutineScope()
    var info by remember(page.url) { mutableStateOf<PlaylistPage?>(null) }
    var state by remember(page.url) { mutableStateOf(TabState(loading = true)) }
    val listState = rememberLazyListState()

    fun load(more: Boolean) {
        val cur = state
        if (more && (cur.loading || cur.next == null)) return
        state = cur.copy(loading = true, error = null)
        scope.launch {
            state = try {
                if (more) {
                    val p = ChannelRepo.playlistMore(page.url, cur.next!!)
                    TabState((cur.items + p.items).distinctBy { it.key }, p.next)
                } else {
                    val p = ChannelRepo.playlist(page.url)
                    info = p
                    TabState(p.first.items, p.first.next)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                cur.copy(loading = false, error = e.message ?: e.javaClass.simpleName)
            }
        }
    }
    LaunchedEffect(page.url) { load(more = false) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp)
    ) {
        item { PageTopBar(info?.title ?: page.title, vm::popPage) }
        info?.let { p ->
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(p.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOf(p.owner, if (p.count >= 0) "${p.count} videos" else "").filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    val videos = state.items.filterIsInstance<Entry.Video>().map { it.video }
                    Button(
                        onClick = {
                            Graph.player.clearQueue()
                            Graph.player.enqueueAll(videos.drop(1))
                            videos.firstOrNull()?.let(vm::play)
                        },
                        enabled = videos.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.size(8.dp))
                        Text("Play all")
                    }
                }
            }
        }
        entryItems(vm, state, onRetry = { load(more = false) })
    }
    LoadMoreEffect(listState, state.items.size) { load(more = true) }
}

// ------------------------------------------------------------------ study channels picker

@Composable
private fun StudyChannelsScreen(vm: TubeViewModel, lib: LibraryViewModel, bottomPadding: Dp) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ChannelRef>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(lib.signedIn) { if (lib.signedIn) lib.ensure(LibTab.Channels) }
    val subs = if (lib.signedIn) {
        lib.loader(LibTab.Channels).state.items.filterIsInstance<LibChannel>()
            .map { ChannelRef("https://www.youtube.com/channel/${it.id}", it.name, it.thumb) }
    } else emptyList()

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        focus.clearFocus()
        searching = true
        searchError = null
        scope.launch {
            try {
                results = ChannelRepo.searchChannels(q)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                searchError = e.message ?: e.javaClass.simpleName
            } finally {
                searching = false
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp)
    ) {
        item { PageTopBar("My Channels", vm::popPage) }
        item {
            Text(
                "Home shows new videos only from these channels, and they're always allowed in Study mode.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        item {
            TextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search channels, e.g. JEE Wallah") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        if (searching) item { Spinner() }
        searchError?.let { e -> item { ErrorBox(e, onRetry = { search() }) } }
        if (results.isNotEmpty()) {
            item { SectionTitle("Search results") }
            items(results, key = { "r:" + it.url }) { c -> ChannelPickRow(vm, c) }
        }
        if (StudyChannels.list.isNotEmpty()) {
            item { SectionTitle("Your channels") }
            items(StudyChannels.list.toList(), key = { "s:" + it.url }) { c -> ChannelPickRow(vm, c) }
        }
        if (subs.isNotEmpty()) {
            item { SectionTitle("From your subscriptions") }
            items(subs, key = { "y:" + it.url }) { c -> ChannelPickRow(vm, c) }
        }
    }
}

@Composable
private fun ChannelPickRow(vm: TubeViewModel, c: ChannelRef) {
    val added = StudyChannels.contains(c.url)
    Row(
        Modifier.fillMaxWidth().clickable { vm.openChannel(c.url, c.name) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(c.name, 44, c.avatar)
        Spacer(Modifier.size(14.dp))
        Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (added) {
            FilledTonalButton(onClick = { StudyChannels.toggle(c) }) { Text("Added") }
        } else {
            OutlinedButton(onClick = { StudyChannels.toggle(c) }) { Text("Add") }
        }
    }
}

// ------------------------------------------------------------------ shared bits

@Composable
private fun PageTopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun Spinner() {
    Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(32.dp)
    )
}

private fun LazyListScope.entryItems(vm: TubeViewModel, state: TabState, onRetry: () -> Unit) {
    when {
        state.loading && state.items.isEmpty() -> items(3) { VideoCardSkeleton() }
        state.error != null && state.items.isEmpty() -> item { ErrorBox(state.error, onRetry = onRetry) }
        state.items.isEmpty() -> item { EmptyHint("Nothing here yet.") }
        else -> {
            items(state.items, key = { it.key }) { e ->
                when (e) {
                    is Entry.Video -> VideoCard(e.video, onClick = { vm.play(e.video) })
                    is Entry.Playlist -> Row(
                        Modifier.fillMaxWidth().clickable { vm.openPlaylist(e.url, e.title) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            Thumb(e.thumb, Modifier.width(150.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)))
                            if (e.count >= 0) {
                                Text(
                                    "${e.count} videos",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                                        .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.8f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Spacer(Modifier.size(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(e.owner, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    is Entry.Channel -> ChannelPickRow(vm, e.ref)
                }
            }
            if (state.loading) item { Spinner() }
        }
    }
}
