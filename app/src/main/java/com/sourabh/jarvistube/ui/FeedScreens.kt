package com.sourabh.jarvistube.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.FeedState
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.TubeViewModel
import com.sourabh.jarvistube.data.VideoItem
import com.sourabh.jarvistube.data.StudyChannels
import androidx.compose.material.icons.rounded.Add
import androidx.compose.foundation.layout.width

/** YouTube-style chip: grey when idle, inverted when selected. */
@Composable
internal fun TopicChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) cs.onSurface else cs.surfaceVariant, label = "chipBg")
    val fg by animateColorAsState(if (selected) cs.surface else cs.onSurfaceVariant, label = "chipFg")
    Text(
        label,
        color = fg,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

@Composable
internal fun HomeTopBar(onSearch: () -> Unit, onProfile: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LogoMark()
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search", Modifier.size(26.dp)) }
        IconButton(onClick = onProfile) {
            val me = com.sourabh.jarvistube.data.Session.active
            if (me?.photo != null) Avatar(me.name ?: "?", 30, me.photo)
            else Icon(Icons.Rounded.AccountCircle, "You", Modifier.size(28.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: TubeViewModel,
    listState: LazyListState,
    bottomPadding: Dp,
    onProfile: () -> Unit
) {
    val channels = StudyChannels.list
    val feed = vm.studyFeed
    // Rebuild the feed whenever the set of study channels changes.
    val channelKey = channels.joinToString("|") { it.url }
    LaunchedEffect(channelKey) {
        if (vm.channelFilter != null && channels.none { it.url == vm.channelFilter }) vm.channelFilter = null
        vm.loadStudyFeed()
    }
    val filter = vm.channelFilter
    val shown = if (filter == null) feed else {
        val name = channels.firstOrNull { it.url == filter }?.name
        feed.copy(items = feed.items.filter { it.channelUrl == filter || (name != null && it.channel.equals(name, true)) })
    }

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = feed.loading && feed.items.isNotEmpty(),
        onRefresh = vm::loadStudyFeed,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            // The list itself starts below the status bar, so the pinned chip row never slides under it.
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = bottomPadding + 16.dp)
        ) {
            item { HomeTopBar(onSearch = vm::openSearch, onProfile = onProfile) }
            if (channels.isEmpty()) {
                item { StudyChannelsEmpty(onPick = vm::openStudyChannels) }
            } else {
                item {
                    StudyChannelsRow(
                        channels,
                        onOpen = { vm.openChannel(it.url, it.name) },
                        onManage = vm::openStudyChannels
                    )
                }
                stickyHeader {
                    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item { TopicChip("All", filter == null) { vm.channelFilter = null } }
                            items(channels.toList(), key = { it.url }) { c ->
                                TopicChip(c.name, filter == c.url) { vm.channelFilter = c.url }
                            }
                        }
                    }
                }
                feedItems(shown, onRetry = vm::loadStudyFeed, onPlay = vm::play)
            }
        }
    }
}

/**
 * Recommended tab: the signed-in account's YouTube home feed, or Trending for the region when
 * signed out. Everything still passes ContentFilter, and taps go through vm.play() (study gate +
 * daily free-time limit).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RecommendedScreen(
    vm: TubeViewModel,
    listState: LazyListState,
    bottomPadding: Dp,
    onProfile: () -> Unit
) {
    // First open, and again after switching account or region.
    LaunchedEffect(com.sourabh.jarvistube.data.Session.activeId, com.sourabh.jarvistube.AppSettings.region) {
        vm.ensureRecommended()
    }
    val feed = vm.recommended
    // Re-applied on screen so turning the filter on (or a lock forcing it) hides items already loaded.
    val filterOn = Graph.study.entertainmentBlocked
    // Last check before the list is drawn: no Shorts here, whichever source the videos came from.
    val shown = remember(feed, filterOn) {
        feed.copy(items = com.sourabh.jarvistube.data.ContentFilter.filter(feed.items).filterNot { it.isShort || it.durationSec in 1L..60L })
    }

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = feed.loading && feed.items.isNotEmpty(),
        onRefresh = vm::loadRecommended,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = bottomPadding + 16.dp)
        ) {
            item { HomeTopBar(onSearch = vm::openSearch, onProfile = onProfile) }
            vm.recommendedSource?.takeIf { !feed.loading || feed.items.isNotEmpty() }?.let { source ->
                item {
                    Text(
                        source,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp)
                    )
                }
            }
            feedItems(
                shown,
                onRetry = vm::loadRecommended,
                onPlay = vm::play,
                emptyTitle = "Nothing to show",
                emptyBody = if (filterOn) "Recommended videos that look like entertainment are filtered out. Pull down to refresh."
                else "No recommendations right now. Pull down to refresh."
            )
        }
        LoadMoreEffect(listState, feed.items.size, vm::loadMoreRecommended)
    }
}

@Composable
private fun StudyChannelsEmpty(onPick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 36.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.School, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(18.dp))
        Text("Build your feed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Home shows videos only from the channels you choose. Add your favorite channels to get started.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        androidx.compose.material3.Button(
            onClick = onPick,
            shape = CircleShape
        ) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("My Channels", fontWeight = FontWeight.SemiBold)
        }
    }
}

/** YouTube-subscriptions-style avatar strip; the last bubble manages the list. */
@Composable
private fun StudyChannelsRow(
    channels: List<com.sourabh.jarvistube.data.ChannelRef>,
    onOpen: (com.sourabh.jarvistube.data.ChannelRef) -> Unit,
    onManage: () -> Unit
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "My Channels",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            androidx.compose.material3.TextButton(onClick = onManage) {
                Text("Manage", fontWeight = FontWeight.Medium)
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(channels.toList(), key = { it.url }) { c ->
                Column(
                    Modifier.width(64.dp).clickable { onOpen(c) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Avatar(c.name, 56, c.avatar)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        c.name, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            item {
                Column(
                    Modifier.width(64.dp).clickable(onClick = onManage),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Add, "Add channels", tint = MaterialTheme.colorScheme.primary) }
                    Spacer(Modifier.height(6.dp))
                    Text("Add", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** Full-screen search like YouTube's: back arrow + field, then suggestions, then results. */
@Composable
fun SearchOverlay(vm: TubeViewModel, bottomPadding: Dp = 0.dp) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val requester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val submitted = vm.submittedQuery
    val showResults = submitted != null && vm.query.trim() == submitted

    LaunchedEffect(Unit) { requester.requestFocus() }

    fun submit(text: String) {
        focus.clearFocus()
        keyboard?.hide()
        vm.submitSearch(text)
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::closeSearch) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            TextField(
                value = vm.query,
                onValueChange = { vm.query = it; vm.updateSuggestions(it) },
                singleLine = true,
                placeholder = { Text("Search PlayBook") },
                trailingIcon = {
                    if (vm.query.isNotEmpty()) {
                        IconButton(onClick = { vm.clearSearch(); vm.suggestions.clear(); requester.requestFocus() }) {
                            Icon(Icons.Rounded.Close, "Clear")
                        }
                    }
                },
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit(vm.query) }),
                modifier = Modifier.weight(1f).focusRequester(requester)
            )
        }

        if (showResults) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding + 24.dp)) {
                feedItems(vm.results, onRetry = { vm.submitSearch(submitted!!) }, onPlay = vm::play)
            }
            LoadMoreEffect(listState, vm.results.items.size, vm::loadMoreSearch)
        } else {
            val rows: List<Pair<String, Boolean>> = if (vm.query.isBlank()) {
                vm.recents.map { it to true }
            } else {
                vm.suggestions.map { it to false }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding + 24.dp)) {
                items(rows, key = { (text, recent) -> "$recent:$text" }) { (text, recent) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { submit(text) }.padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (recent) Icons.Rounded.History else Icons.Rounded.Search, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.size(20.dp))
                        Text(text, Modifier.weight(1f).padding(vertical = 14.dp), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        IconButton(onClick = { vm.query = text; vm.updateSuggestions(text) }) {
                            Icon(Icons.Rounded.NorthWest, "Use as search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (recent) {
                            IconButton(onClick = { vm.removeRecent(text) }) {
                                Icon(Icons.Rounded.Close, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.feedItems(
    feed: FeedState,
    onRetry: () -> Unit,
    onPlay: (VideoItem) -> Unit,
    emptyTitle: String = "No study videos here",
    emptyBody: String = "PlayBook only shows learning content. Music, films and entertainment are filtered out."
) {
    when {
        feed.loading && feed.items.isEmpty() -> items(4) { VideoCardSkeleton() }
        feed.error != null && feed.items.isEmpty() -> item { ErrorBox(feed.error, onRetry) }
        feed.items.isEmpty() -> item { EmptyState(Icons.Rounded.School, emptyTitle, emptyBody) }
        else -> {
            items(feed.items, key = { it.url }) { v -> VideoCard(v, onClick = { onPlay(v) }) }
            if (feed.loadingMore) {
                item {
                    Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                }
            }
        }
    }
}
