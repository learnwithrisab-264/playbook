package com.sourabh.jarvistube.ui.player

import com.sourabh.jarvistube.ui.common.pressScale
import com.sourabh.jarvistube.ui.common.Thumb
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.player.NowPlaying

@Composable
fun MiniPlayer(now: NowPlaying, ui: PlayerUi, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp
    ) {
        Column {
            Row(
                Modifier.pressScale(onExpand).padding(start = 8.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Thumb(
                    now.video.thumb,
                    Modifier.width(56.dp).size(width = 56.dp, height = 40.dp).clip(RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        now.info?.name ?: now.video.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        now.info?.uploaderName ?: now.video.channel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                // Back to the picture, at the same position.
                IconButton(onClick = { Graph.player.updateAudioOnly(false) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Videocam, "Watch video")
                }
                IconButton(onClick = { Graph.player.skipPrevious() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous")
                }
                IconButton(onClick = { Graph.player.togglePlay() }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (ui.isPlaying) "Pause" else "Play"
                    )
                }
                IconButton(onClick = { Graph.player.skipNext() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Next")
                }
                IconButton(onClick = { Graph.player.stop() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Close, "Close player")
                }
            }
            val progress = if (ui.duration > 0) (ui.position.toFloat() / ui.duration).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = androidx.compose.ui.graphics.Color(0xFFFF6D00), // PlayBook orange, like the seek bar
                    trackColor = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}
