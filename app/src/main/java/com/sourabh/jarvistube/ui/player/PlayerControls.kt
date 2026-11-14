package com.sourabh.jarvistube.ui.player

import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HeadsetOff

import androidx.compose.foundation.layout.width

import androidx.compose.foundation.border

import com.sourabh.jarvistube.R
import androidx.compose.ui.res.stringResource
import com.sourabh.jarvistube.ui.common.formatClock
import com.sourabh.jarvistube.ui.study.PlayerPopups
import com.sourabh.jarvistube.ui.study.StudyLockButton
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionOff
import com.sourabh.jarvistube.AppSettings
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.player.NowPlaying

@Composable
internal fun Controls(
    now: NowPlaying,
    ui: PlayerUi,
    fullscreen: Boolean,
    onDragging: (Boolean) -> Unit,
    onToggleFullscreen: () -> Unit,
    onCollapse: () -> Unit,
    onBottomHeight: (Int) -> Unit,
    onMoreVideos: (() -> Unit)? = null,
    onComments: (() -> Unit)? = null
) {
    val player = Graph.player.player
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    val synced = Graph.player.loadedUrl.collectAsStateWithLifecycle().value == now.video.url

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(Color.Black.copy(0.55f), Color.Transparent, Color.Transparent, Color.Black.copy(0.65f))
            )
        )
    ) {
        // Top bar
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!Graph.study.lockActive) IconButton(onClick = if (fullscreen) onToggleFullscreen else onCollapse) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Minimize", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            if (fullscreen) {
                Column(Modifier.weight(1f)) {
                    Text(
                        now.info?.name ?: now.video.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        now.info?.uploaderName ?: now.video.channel,
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else Spacer(Modifier.weight(1f))
            // CC button only when YouTube actually offers captions for this video.
            if (now.subtitles.isNotEmpty()) {
                Box {
                    IconButton(onClick = { PlayerPopups.ccMenu = true }) {
                        Icon(
                            if (now.subtitleId != null) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionOff,
                            "Captions", tint = Color.White
                        )
                    }
                    CaptionsMenu(now, PlayerPopups.ccMenu) { PlayerPopups.ccMenu = false }
                }
            }
            // Playback speed right next to CC, one tap away (quality lives in the gear sheet).
            Box {
                Text(
                    AppSettings.speedText(ui.speed),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp).clip(CircleShape)
                        .clickable { PlayerPopups.speedMenu = true }
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                )
                DropdownMenu(PlayerPopups.speedMenu, { PlayerPopups.speedMenu = false }) {
                    AppSettings.speeds.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(AppSettings.speedLabel(s)) },
                            trailingIcon = { if (s == ui.speed) Icon(Icons.Rounded.Check, null) },
                            onClick = { player.setPlaybackSpeed(s); PlayerPopups.speedMenu = false }
                        )
                    }
                }
            }
            StudyLockButton(now)
        }

        // Center transport (hidden while scrubbing, so the preview has the screen).
        if (scrubMs == null) Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(if (fullscreen) 120.dp else 56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) }) {
                Icon(Icons.Rounded.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Box(
                Modifier.size(60.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))
                    .clickable { Graph.player.togglePlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (ui.isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
            IconButton(onClick = { player.seekTo(player.currentPosition + 10_000L) }) {
                Icon(Icons.Rounded.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(34.dp))
            }
        }

        // Bottom: "2:14 / 17:04" + fullscreen, then the seek bar hugging the video's bottom edge.
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().onSizeChanged { onBottomHeight(it.height) }) {
            // While dragging, the frame preview replaces the time row and sits right on top of the
            // seek bar; it grows upward over the video without moving the bar.
            val ms = scrubMs
            if (ms != null) {
                Box(Modifier.fillMaxWidth().height(48.dp)) {
                    Box(
                        Modifier.align(Alignment.BottomStart)
                            .wrapContentHeight(align = Alignment.Bottom, unbounded = true)
                    ) { SeekPreview(now.info, ms, if (synced) ui.duration else 0L, small = !fullscreen) }
                }
            } else Row(
                Modifier.padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val shown = scrubMs ?: if (synced) ui.position else 0L
                Text(
                    "${formatClock(shown)} / ${formatClock(if (synced) ui.duration else 0L)}",
                    color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                if (!Graph.study.lockActive) IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        "Fullscreen", tint = Color.White
                    )
                }
            }
            YtSeekBar(
                position = if (synced) ui.position else 0L,
                duration = if (synced) ui.duration else 0L,
                buffered = if (synced) ui.buffered else 0L,
                interactive = true,
                marks = if (synced && Graph.player.sponsorBlock) sponsorMarks(Graph.player.segments) else emptyList(),
                onSeek = { player.seekTo(it) },
                onScrub = { scrubMs = it; onDragging(it != null) }
            )
            if (fullscreen) FullscreenActions(now, onComments, onMoreVideos)
            Spacer(Modifier.height(if (fullscreen) 6.dp else 0.dp))
        }
    }
}

/** Caption picker shared by the CC button and the Captions pill: Off + every language offered. */
@Composable
internal fun QualityMenu(now: NowPlaying, expanded: Boolean, onDismiss: () -> Unit) {
    // Only resolutions this video really offers AND this phone can decode smoothly.
    DropdownMenu(expanded, onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.auto)) },
            trailingIcon = { if (now.autoQuality) Icon(Icons.Rounded.Check, null) },
            onClick = { Graph.player.setQuality(0); onDismiss() }
        )
        now.heights.forEach { h ->
            DropdownMenuItem(
                text = { Text(AppSettings.qualityLabel(h)) },
                trailingIcon = { if (!now.autoQuality && h == now.playingHeight) Icon(Icons.Rounded.Check, null) },
                onClick = { Graph.player.setQuality(h); onDismiss() }
            )
        }
    }
}

@Composable
internal fun CaptionsMenu(now: NowPlaying, expanded: Boolean, onDismiss: () -> Unit) {
    DropdownMenu(expanded, onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.off)) },
            trailingIcon = { if (now.subtitleId == null) Icon(Icons.Rounded.Check, null) },
            onClick = { Graph.player.setSubtitle(null); onDismiss() }
        )
        now.subtitles.forEach { s ->
            DropdownMenuItem(
                text = { Text(s.label) },
                trailingIcon = { if (s.id == now.subtitleId) Icon(Icons.Rounded.Check, null) },
                onClick = { Graph.player.setSubtitle(s.id); onDismiss() }
            )
        }
    }
}


/**
 * YouTube's fullscreen action row: everything you'd leave the video for, without leaving it.
 * Comments open beside the video; More videos shows a peek of what's next.
 */
@Composable
private fun FullscreenActions(now: NowPlaying, onComments: (() -> Unit)?, onMoreVideos: (() -> Unit)?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val locked = Graph.study.lockActive
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onComments != null) ActionIcon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.Comment, "Comments", onComments)
        if (!locked) {
            if (com.sourabh.jarvistube.data.Session.isSignedIn) {
                ActionIcon(androidx.compose.material.icons.Icons.Rounded.BookmarkBorder, "Save to playlist") {
                    com.sourabh.jarvistube.ui.you.SaveToPlaylist.open(now.video)
                }
            }
            ActionIcon(androidx.compose.material.icons.Icons.Rounded.Share, "Share") {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, now.video.url)
                }
                context.startActivity(android.content.Intent.createChooser(send, "Share"))
            }
            now.info?.let { info ->
                ActionIcon(androidx.compose.material.icons.Icons.Rounded.Download, "Download") {
                    com.sourabh.jarvistube.ui.downloads.DownloadSheet.open(now.video, info)
                }
            }
        }
        ActionIcon(
            if (Graph.player.audioOnly) androidx.compose.material.icons.Icons.Rounded.Headphones else androidx.compose.material.icons.Icons.Rounded.HeadsetOff,
            if (Graph.player.audioOnly) "Back to video" else "Listen (audio only)"
        ) { Graph.player.updateAudioOnly(!Graph.player.audioOnly) }
        Spacer(Modifier.weight(1f))
        if (onMoreVideos != null) {
            val peek = remember(now.info) { relatedVideos(now).firstOrNull()?.thumb }
            Row(
                Modifier.clip(CircleShape).clickable(onClick = onMoreVideos).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("More videos", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (peek != null) {
                    Spacer(Modifier.width(10.dp))
                    com.sourabh.jarvistube.ui.common.Thumb(
                        peek,
                        Modifier.width(56.dp).height(32.dp).clip(com.sourabh.jarvistube.ui.theme.Corners.small)
                            .border(1.5.dp, Color.White, com.sourabh.jarvistube.ui.theme.Corners.small)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp)) }
}
