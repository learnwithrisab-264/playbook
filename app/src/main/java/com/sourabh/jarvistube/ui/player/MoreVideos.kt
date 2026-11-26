package com.sourabh.jarvistube.ui.player

import androidx.compose.foundation.gestures.detectTapGestures

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.mutableIntStateOf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.data.ContentFilter
import com.sourabh.jarvistube.data.VideoItem
import com.sourabh.jarvistube.data.WatchHistory
import com.sourabh.jarvistube.data.bestUrl
import com.sourabh.jarvistube.player.NowPlaying
import com.sourabh.jarvistube.ui.common.Avatar
import com.sourabh.jarvistube.ui.common.Thumb
import com.sourabh.jarvistube.ui.common.compact
import com.sourabh.jarvistube.ui.common.formatDuration
import com.sourabh.jarvistube.ui.common.prettyDate
import com.sourabh.jarvistube.ui.theme.Corners
import com.sourabh.jarvistube.ui.theme.OnVideo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/** Related videos on this one's topic, without Shorts and without what the content filter hides. */
internal fun relatedVideos(now: NowPlaying): List<VideoItem> =
    com.sourabh.jarvistube.data.Related.of(now.info, now.video)

/**
 * "More videos" in fullscreen, as on YouTube: a strip over the bottom of the video with All (related
 * videos) and Watched (your history). The video keeps playing behind it.
 */
@Composable
internal fun MoreVideosPanel(now: NowPlaying, onPlay: (VideoItem) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    /** 0 = All (related), 1 = From this channel, 2 = Watched. */
    var tab by remember { mutableIntStateOf(0) }
    val channelUrl = now.info?.uploaderUrl?.takeIf { it.isNotBlank() } ?: now.video.channelUrl
    val channelName = now.info?.uploaderName ?: now.video.channel
    var fromChannel by remember(channelUrl) { mutableStateOf<List<VideoItem>?>(null) }
    LaunchedEffect(tab, channelUrl) {
        if (tab == 1 && fromChannel == null && channelUrl != null) {
            fromChannel = runCatching { com.sourabh.jarvistube.data.ChannelVideos(channelUrl).load() }.getOrDefault(emptyList())
                .filter { it.url != now.video.url && !it.isShort && !ContentFilter.hides(it) }
        }
    }
    val list = when (tab) {
        2 -> WatchHistory.items.asSequence().map { it.video }
            .filter { it.url != now.video.url && !it.isShort && !ContentFilter.hides(it) }
            .take(50).toList()
        1 -> fromChannel.orEmpty()
        else -> com.sourabh.jarvistube.data.Related.of(now.info, now.video)
    }

    Column(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f), Color.Black.copy(alpha = 0.9f))))
            // Swipes and taps here belong to the strip, not to the video's gestures behind it.
            .pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
            }
            .padding(bottom = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("More videos", color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close more videos", tint = Color.White) }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StripChip("All", tab == 0) { tab = 0 }
            if (channelUrl != null) StripChip(channelName.ifBlank { "This channel" }, tab == 1) { tab = 1 }
            StripChip("Watched", tab == 2) { tab = 2 }
        }
        Spacer(Modifier.height(10.dp))
        if (list.isEmpty()) {
            Text(
                when {
                    tab == 2 -> "Nothing watched yet."
                    tab == 1 && fromChannel == null -> "Loading this channel's videos…"
                    tab == 1 -> "No other videos from this channel."
                    now.info == null -> "Loading…"
                    else -> "No related videos."
                },
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
            )
        } else LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(list, key = { it.url }) { v -> StripCard(v) { onPlay(v) } }
        }
    }
}

@Composable
private fun StripChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.Black else Color.White,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(Corners.small)
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun StripCard(v: VideoItem, onClick: () -> Unit) {
    // Start fetching the moment a finger lands, so the video is ready by the time it lifts.
    val resume = remember(v.url) { com.sourabh.jarvistube.Graph.player.resumePositionMs(v.url) }
    Column(
        Modifier.width(220.dp).clip(Corners.small).pointerInput(v.url) {
            detectTapGestures(
                onPress = { com.sourabh.jarvistube.data.Repo.prefetch(v.url); tryAwaitRelease() },
                onTap = { onClick() }
            )
        }
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(Corners.small)) {
            Thumb(v.thumb, Modifier.matchParentSize())
            // How much of it you've already watched, as YouTube's red line.
            if (resume > 0 && v.durationSec > 0) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.35f)))
                Box(
                    Modifier.align(Alignment.BottomStart).height(3.dp)
                        .fillMaxWidth((resume / 1000f / v.durationSec).coerceIn(0.02f, 1f))
                        .background(com.sourabh.jarvistube.ui.theme.Brand.live)
                )
            }
            val badge = if (v.isLive) "LIVE" else formatDuration(v.durationSec)
            if (badge.isNotEmpty()) {
                Text(
                    badge, color = Color.White, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(Corners.tiny)
                        .background(OnVideo.scrimStrong).padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(24.dp).clip(CircleShape)) { Avatar(v.channel, 24, v.avatar) }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(v.title, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(
                    v.channel.ifBlank { null },
                    v.views.takeIf { it > 0 }?.let { "${compact(it)} views" },
                    prettyDate(v.uploaded)
                ).joinToString(" · ")
                if (resume > 0) {
                    Text(
                        "Resume at " + com.sourabh.jarvistube.ui.common.formatClock(resume),
                        color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1
                    )
                }
                if (meta.isNotEmpty()) {
                    Text(meta, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
