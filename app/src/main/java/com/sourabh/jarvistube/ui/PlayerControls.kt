package com.sourabh.jarvistube.ui

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
    onBottomHeight: (Int) -> Unit
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
                Text(
                    now.info?.name ?: now.video.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
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
            // Quality on the player itself, so it is reachable in fullscreen and during a lock.
            if (now.heights.isNotEmpty()) {
                Box {
                    IconButton(onClick = { PlayerPopups.qualityMenu = true }) {
                        Icon(Icons.Rounded.HighQuality, "Quality", tint = Color.White)
                    }
                    QualityMenu(now, PlayerPopups.qualityMenu) { PlayerPopups.qualityMenu = false }
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
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium
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
                onSeek = { player.seekTo(it) },
                onScrub = { scrubMs = it; onDragging(it != null) }
            )
            Spacer(Modifier.height(if (fullscreen) 10.dp else 0.dp))
        }
    }
}

/** Caption picker shared by the CC button and the Captions pill: Off + every language offered. */
@Composable
internal fun QualityMenu(now: NowPlaying, expanded: Boolean, onDismiss: () -> Unit) {
    // Only resolutions this video really offers AND this phone can decode smoothly.
    DropdownMenu(expanded, onDismiss) {
        DropdownMenuItem(
            text = { Text("Auto") },
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
            text = { Text("Off") },
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
