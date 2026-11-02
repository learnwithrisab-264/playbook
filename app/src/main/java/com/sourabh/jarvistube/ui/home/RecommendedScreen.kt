package com.sourabh.jarvistube.ui.home

import com.sourabh.jarvistube.ui.common.feedItems
import com.sourabh.jarvistube.ui.common.HomeTopBar
import com.sourabh.jarvistube.ui.common.LoadMoreEffect
import com.sourabh.jarvistube.ui.common.TrackSeen
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.TubeViewModel

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
    LaunchedEffect(com.sourabh.jarvistube.data.Session.dataKey, com.sourabh.jarvistube.AppSettings.region) {
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
        TrackSeen(listState, com.sourabh.jarvistube.data.Feed.Recommended)
        LoadMoreEffect(listState, feed.items.size, vm::loadMoreRecommended)
    }
}
