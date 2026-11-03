package com.sourabh.jarvistube.ui.shorts

import com.sourabh.jarvistube.R
import androidx.compose.ui.res.stringResource
import com.sourabh.jarvistube.ui.theme.Corners
import com.sourabh.jarvistube.ui.common.EmptyState
import com.sourabh.jarvistube.ui.common.ErrorBox
import com.sourabh.jarvistube.ui.common.HomeTopBar
import com.sourabh.jarvistube.ui.common.pressScale
import com.sourabh.jarvistube.ui.common.Thumb
import com.sourabh.jarvistube.ui.common.TopicChip
import com.sourabh.jarvistube.ui.common.TrackSeen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sourabh.jarvistube.data.TrustedClock
import com.sourabh.jarvistube.data.nextMidnightAfter
import com.sourabh.jarvistube.data.timeLeftText
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.FeedState
import com.sourabh.jarvistube.TubeViewModel
import com.sourabh.jarvistube.data.VideoItem

/** A tall Shorts card: vertical thumbnail with the title and channel under it. */
@Composable
fun ShortCard(video: VideoItem, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().pressScale(onClick, onPress = { com.sourabh.jarvistube.data.Repo.prefetchShort(video.url) })) {
        Thumb(video.thumb, Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(Corners.medium))
        Spacer(Modifier.height(8.dp))
        Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            video.channel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

/** Shorts in a two-column grid, with the same loading, error and empty states as the other feeds. */
@Composable
fun ShortsGrid(
    feed: FeedState,
    state: LazyGridState,
    bottomPadding: Dp,
    onRetry: () -> Unit,
    onPlay: (VideoItem) -> Unit,
    onMore: () -> Unit,
    emptyBody: String,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = state,
        modifier = modifier,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (header != null) item(span = { GridItemSpan(maxLineSpan) }) { header() }
        when {
            feed.loading && feed.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
                }
            }
            feed.error != null && feed.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                ErrorBox(feed.error, onRetry)
            }
            feed.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(Icons.Rounded.PlayCircle, "No Shorts here", emptyBody)
            }
            else -> {
                items(feed.items, key = { it.url }) { v -> ShortCard(v, onClick = { onPlay(v) }) }
                if (feed.loadingMore) item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                }
            }
        }
    }
    val nearEnd by remember(state) {
        derivedStateOf {
            val total = state.layoutInfo.totalItemsCount
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 6
        }
    }
    LaunchedEffect(nearEnd, feed.items.size) { if (nearEnd) onMore() }
}

/** Milliseconds left on the Block Shorts timer, updated every second; 0 when not blocked. */
@Composable
fun rememberShortsBlockLeft(): Long {
    val until = com.sourabh.jarvistube.Graph.study.shortsBlockUntil
    var left by remember { mutableLongStateOf(0L) }
    LaunchedEffect(until) {
        while (true) {
            left = (until - TrustedClock.now()).coerceAtLeast(0L)
            if (left == 0L) break
            delay(1000)
        }
    }
    return left
}

/** Shown instead of Shorts while the timer runs. */
@Composable
fun ShortsLocked(leftMs: Long) {
    EmptyState(
        Icons.Rounded.Lock,
        "Shorts are blocked",
        "${timeLeftText(leftMs)} left. You set this in the You tab."
    )
}

/** Block Shorts in the You tab: pick a time; it can be made longer but not ended early. */
@Composable
fun ShortsBlockCard() {
    val study = com.sourabh.jarvistube.Graph.study
    val left = rememberShortsBlockLeft()
    var asking by remember { mutableStateOf<Pair<String, Long>?>(null) }
    fun untilIn(min: Long) = TrustedClock.now() + min * 60_000L
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(Corners.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.block_shorts), style = MaterialTheme.typography.titleMedium)
        }
        Text(
            if (left > 0) "Blocked: ${timeLeftText(left)} left. You can make it longer, not shorter."
            else "Lock the Shorts tab for a while. It stays locked even if you close the app or restart the phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { TopicChip("15 min", false) { asking = "15 minutes" to untilIn(15) } }
            item { TopicChip("30 min", false) { asking = "30 minutes" to untilIn(30) } }
            item { TopicChip("1 hour", false) { asking = "1 hour" to untilIn(60) } }
            item {
                TopicChip("Until tomorrow", false) {
                    asking = "the rest of today" to nextMidnightAfter(TrustedClock.now(), java.time.ZoneId.systemDefault())
                }
            }
        }
    }
    asking?.let { (label, until) ->
        val shorter = until <= study.shortsBlockUntil
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(if (shorter) "Already blocked for longer" else "Block Shorts?") },
            text = {
                Text(
                    if (shorter) "The timer already runs past that, and it can't be shortened."
                    else "Shorts stay locked for $label. You can't turn this off early."
                )
            },
            confirmButton = {
                TextButton(onClick = { if (!shorter) study.blockShortsUntil(until); asking = null }) {
                    Text(if (shorter) "OK" else "Block")
                }
            },
            dismissButton = { if (!shorter) TextButton(onClick = { asking = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

/** The Shorts tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortsScreen(vm: TubeViewModel, state: LazyGridState, bottomPadding: Dp, onProfile: () -> Unit) {
    val study = com.sourabh.jarvistube.Graph.study
    val left = rememberShortsBlockLeft()
    LaunchedEffect(left > 0, com.sourabh.jarvistube.data.Session.dataKey) { if (left <= 0) vm.ensureShorts() }
    val feed = vm.shorts
    TrackSeen(state, com.sourabh.jarvistube.data.Feed.Shorts)
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = feed.loading && feed.items.isNotEmpty(),
        onRefresh = vm::loadShorts,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            HomeTopBar(onSearch = vm::openSearch, onProfile = onProfile)
            if (left > 0) {
                ShortsLocked(left)
                return@Column
            }
            ShortsGrid(
                feed, state, bottomPadding,
                onRetry = vm::loadShorts, onMore = vm::loadMoreShorts,
                onPlay = { v -> vm.openShorts({ vm.shorts.items }, v, vm::loadMoreShorts) },
                emptyBody = "With the entertainment filter on, only learning Shorts show here. Pull down to refresh.",
                modifier = Modifier.fillMaxSize().padding(top = 4.dp)
            )
        }
    }
}
