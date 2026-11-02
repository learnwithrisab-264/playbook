package com.sourabh.jarvistube.ui

import androidx.compose.foundation.layout.Arrangement
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
    Column(Modifier.fillMaxWidth().pressScale(onClick)) {
        Thumb(video.thumb, Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(12.dp)))
        Spacer(Modifier.height(6.dp))
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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

/** The Shorts tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortsScreen(vm: TubeViewModel, state: LazyGridState, bottomPadding: Dp, onProfile: () -> Unit) {
    LaunchedEffect(Unit) { vm.ensureShorts() }
    val feed = vm.shorts
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = feed.loading && feed.items.isNotEmpty(),
        onRefresh = vm::loadShorts,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            HomeTopBar(onSearch = vm::openSearch, onProfile = onProfile)
            ShortsGrid(
                feed, state, bottomPadding,
                onRetry = vm::loadShorts, onPlay = vm::play, onMore = vm::loadMoreShorts,
                emptyBody = "With the entertainment filter on, only learning Shorts show here. Pull down to refresh.",
                modifier = Modifier.fillMaxSize().padding(top = 4.dp)
            )
        }
    }
}
