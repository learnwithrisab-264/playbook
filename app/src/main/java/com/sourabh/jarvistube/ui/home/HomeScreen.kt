package com.sourabh.jarvistube.ui.home

import com.sourabh.jarvistube.ui.common.Avatar
import com.sourabh.jarvistube.ui.common.feedItems
import com.sourabh.jarvistube.ui.common.HomeTopBar
import com.sourabh.jarvistube.ui.common.LoadMoreEffect
import com.sourabh.jarvistube.ui.common.TopicChip
import com.sourabh.jarvistube.ui.common.TrackSeen
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.TubeViewModel
import com.sourabh.jarvistube.data.StudyChannels
import androidx.compose.material.icons.rounded.Add
import androidx.compose.foundation.layout.width

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
    // The saved list comes back; only a change to the study channels rebuilds it.
    val channelKey = channels.joinToString("|") { it.url }
    LaunchedEffect(channelKey) {
        if (vm.channelFilter != null && channels.none { it.url == vm.channelFilter }) vm.channelFilter = null
        vm.ensureStudyFeed()
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
        TrackSeen(listState, com.sourabh.jarvistube.data.Feed.MyFeed)
        LoadMoreEffect(listState, shown.items.size, vm::loadMoreStudy)
    }
}

@Composable
internal fun StudyChannelsEmpty(onPick: () -> Unit) {
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
internal fun StudyChannelsRow(
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
