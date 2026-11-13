package com.sourabh.jarvistube.ui.player

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

/** YouTube's related videos for this one, without Shorts and without what the content filter hides. */
internal fun relatedVideos(now: NowPlaying): List<VideoItem> =
    now.info?.relatedItems.orEmpty()
        .filterIsInstance<StreamInfoItem>()
        .filter { it.duration !in 1L..60L }
        .map { r ->
            VideoItem(
                url = r.url, title = r.name, channel = r.uploaderName.orEmpty(),
                thumb = r.thumbnails.bestUrl(),
                durationSec = r.duration, views = r.viewCount, uploaded = r.textualUploadDate,
                isLive = r.streamType == StreamType.LIVE_STREAM,
                avatar = runCatching { r.uploaderAvatars.bestUrl() }.getOrNull()
            )
        }
        .filterNot(ContentFilter::hides)

/**
 * "More videos" in fullscreen, as on YouTube: a strip over the bottom of the video with All (related
 * videos) and Watched (your history). The video keeps playing behind it.
 */
@Composable
internal fun MoreVideosPanel(now: NowPlaying, onPlay: (VideoItem) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var watched by remember { mutableStateOf(false) }
    val list = if (watched) {
        WatchHistory.items.asSequence().map { it.video }
            .filter { it.url != now.video.url && !it.isShort && !ContentFilter.hides(it) }
            .take(50).toList()
    } else remember(now.info) { relatedVideos(now) }

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
            StripChip("All", !watched) { watched = false }
            StripChip("Watched", watched) { watched = true }
        }
        Spacer(Modifier.height(10.dp))
        if (list.isEmpty()) {
            Text(
                if (watched) "Nothing watched yet." else if (now.info == null) "Loading…" else "No related videos.",
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
    Column(Modifier.width(220.dp).clip(Corners.small).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(Corners.small)) {
            Thumb(v.thumb, Modifier.matchParentSize())
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
                if (meta.isNotEmpty()) {
                    Text(meta, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
