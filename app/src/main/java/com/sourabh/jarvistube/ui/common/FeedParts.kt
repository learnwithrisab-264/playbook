package com.sourabh.jarvistube.ui.common

import com.sourabh.jarvistube.ui.theme.Corners
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.FeedState
import com.sourabh.jarvistube.data.VideoItem

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
            .clip(Corners.medium)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp)
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

internal fun LazyListScope.feedItems(
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

/**
 * One dot per loaded page; the page on screen is the big highlighted one. Tapping a dot shows that
 * page again from memory; "+" loads a new page. The row scrolls sideways past about 7 dots.
 */
@Composable
internal fun PageDots(count: Int, current: Int, loading: Boolean, onPick: (Int) -> Unit, onNext: () -> Unit) {
    val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(current, count) {
        rowState.animateScrollToItem((current - 3).coerceAtLeast(0))
    }
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Page ${current + 1} of $count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        androidx.compose.foundation.lazy.LazyRow(
            state = rowState,
            modifier = Modifier.padding(top = 4.dp).widthIn(max = 36.dp * 8),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(count) { i ->
                val on = i == current
                Box(
                    Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable { if (!on) onPick(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.size(if (on) 14.dp else 8.dp).clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
                    )
                }
            }
            item {
                Box(
                    Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable(enabled = !loading) { onNext() },
                    contentAlignment = Alignment.Center
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Add, "Next page", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}


/** When another page is picked, start it from the top (not when coming back to the tab). */
@Composable
internal fun ScrollTopOnPageChange(state: androidx.compose.foundation.lazy.LazyListState, page: Int) {
    var shownPage by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(page) }
    androidx.compose.runtime.LaunchedEffect(page) {
        if (page != shownPage) {
            shownPage = page
            state.scrollToItem(0)
        }
    }
}
