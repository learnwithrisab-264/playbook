package com.sourabh.jarvistube.ui.home

import com.sourabh.jarvistube.R
import androidx.compose.ui.res.stringResource
import com.sourabh.jarvistube.ui.theme.Corners
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
import androidx.compose.runtime.collectAsState
import com.sourabh.jarvistube.ui.common.pressScale
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.foundation.border
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Close

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
            item { ContinueWatching(vm) }
            if (channels.isEmpty()) {
                item { StudyChannelsEmpty(onPick = vm::openStudyChannels) }
            } else {
                item {
                    StudyChannelsRow(
                        channels,
                        selected = filter,
                        onSelect = { c ->
                            // Tap a channel to see only its videos; tap it again to open its page.
                            when {
                                c == null -> vm.channelFilter = null
                                vm.channelFilter == c.url -> vm.openChannel(c.url, c.name)
                                else -> vm.channelFilter = c.url
                            }
                        },
                        onManage = vm::openStudyChannels
                    )
                }
                if (filter != null) item {
                    val c = channels.firstOrNull { it.url == filter }
                    if (c != null) FilterBanner(c.name, onOpen = { vm.openChannel(c.url, c.name) }, onClear = { vm.channelFilter = null })
                }
                item { Spacer(Modifier.height(6.dp)) }
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
            .padding(horizontal = 24.dp, vertical = 36.dp)
            .clip(Corners.large)
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
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.build_your_feed), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Home shows videos only from the channels you choose. Add your favorite channels to get started.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        androidx.compose.material3.Button(
            onClick = onPick,
            shape = CircleShape
        ) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.my_channels), fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Story-style channel strip that doubles as the feed's filter: "All" first, then each channel.
 * The chosen one wears a ring in the theme's colour.
 */
@Composable
internal fun StudyChannelsRow(
    channels: List<com.sourabh.jarvistube.data.ChannelRef>,
    selected: String?,
    onSelect: (com.sourabh.jarvistube.data.ChannelRef?) -> Unit,
    onManage: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "My Channels",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            androidx.compose.material3.TextButton(onClick = onManage) {
                Text(stringResource(R.string.manage), fontWeight = FontWeight.SemiBold)
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "all") {
                ChannelBubble("All", selected == null, onClick = { onSelect(null) }) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(
                            androidx.compose.ui.graphics.Brush.linearGradient(listOf(cs.primary, cs.secondary))
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Rounded.GridView, null, tint = cs.onPrimary, modifier = Modifier.size(24.dp))
                    }
                }
            }
            items(channels.toList(), key = { it.url }) { c ->
                ChannelBubble(c.name, selected == c.url, onClick = { onSelect(c) }) { Avatar(c.name, 56, c.avatar) }
            }
            item(key = "add") {
                ChannelBubble(stringResource(R.string.add), false, onClick = onManage) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(cs.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Add, "Add channels", tint = cs.primary) }
                }
            }
        }
    }
}

@Composable
private fun ChannelBubble(name: String, selected: Boolean, onClick: () -> Unit, picture: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val touch = com.sourabh.jarvistube.ui.theme.rememberTouch()
    val ring by androidx.compose.animation.animateColorAsState(
        if (selected) cs.primary else androidx.compose.ui.graphics.Color.Transparent, label = "ring"
    )
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (selected) 1f else 0.94f, androidx.compose.animation.core.spring(dampingRatio = 0.6f), label = "bubble"
    )
    Column(
        Modifier.width(66.dp).clip(Corners.medium).clickable { if (!selected) touch(); onClick() }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(64.dp).scale(scale).border(2.5.dp, ring, CircleShape),
            contentAlignment = Alignment.Center
        ) { picture() }
        Spacer(Modifier.height(6.dp))
        Text(
            name, style = MaterialTheme.typography.bodySmall, maxLines = 1,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) cs.onSurface else cs.onSurfaceVariant,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

/** Shown while the feed is narrowed to one channel: open its page, or go back to all. */
@Composable
private fun FilterBanner(name: String, onOpen: () -> Unit, onClear: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp)
            .clip(Corners.pill).background(cs.primary.copy(alpha = 0.10f))
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Only $name", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        androidx.compose.material3.TextButton(onClick = onOpen) { Text("Open channel", fontWeight = FontWeight.SemiBold) }
        androidx.compose.material3.IconButton(onClick = onClear, modifier = Modifier.size(40.dp)) {
            Icon(androidx.compose.material.icons.Icons.Rounded.Close, "Show all channels", tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * The one video you left halfway, right at the top of Home: one tap carries on from where you
 * stopped. Hidden when nothing is unfinished, or when that video is already playing.
 */
@Composable
internal fun ContinueWatching(vm: TubeViewModel) {
    val playingUrl = com.sourabh.jarvistube.Graph.player.now.collectAsState().value?.video?.url
    val pick = androidx.compose.runtime.remember(com.sourabh.jarvistube.data.WatchHistory.items.firstOrNull(), playingUrl) {
        com.sourabh.jarvistube.data.WatchHistory.items.asSequence().take(8)
            .map { it.video }
            .filter { !it.isShort && it.url != playingUrl && it.durationSec > 0 && !com.sourabh.jarvistube.data.ContentFilter.hides(it) }
            .map { it to com.sourabh.jarvistube.Graph.player.resumePositionMs(it.url) }
            .firstOrNull { it.second > 0 }
    } ?: return
    val (v, at) = pick
    val cs = MaterialTheme.colorScheme
    val fraction = (at / 1000f / v.durationSec).coerceIn(0.03f, 1f)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(Corners.large)
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(cs.primary.copy(alpha = 0.16f), cs.surfaceContainer)
                )
            )
            .pressScale({ vm.play(v) })
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(128.dp, 72.dp).clip(Corners.medium)) {
            com.sourabh.jarvistube.ui.common.Thumb(v.thumb, Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.25f)))
            Box(
                Modifier.align(Alignment.Center).size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) { Icon(androidx.compose.material.icons.Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.35f))) {
                Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(cs.primary))
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "CONTINUE WATCHING", style = MaterialTheme.typography.labelSmall, color = cs.primary,
                letterSpacing = androidx.compose.ui.unit.TextUnit(0.8f, androidx.compose.ui.unit.TextUnitType.Sp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                v.title, style = MaterialTheme.typography.titleSmall, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${com.sourabh.jarvistube.ui.common.formatDuration(at / 1000)} of ${com.sourabh.jarvistube.ui.common.formatDuration(v.durationSec)} · ${v.channel}",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
