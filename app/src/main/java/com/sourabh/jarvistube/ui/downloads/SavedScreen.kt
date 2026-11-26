package com.sourabh.jarvistube.ui.downloads

import com.sourabh.jarvistube.ui.theme.Corners
import com.sourabh.jarvistube.ui.common.EmptyState
import com.sourabh.jarvistube.ui.common.pressScale
import com.sourabh.jarvistube.ui.common.SubPageHeader
import com.sourabh.jarvistube.ui.common.Thumb
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.TubeViewModel
import com.sourabh.jarvistube.data.Downloads
import com.sourabh.jarvistube.data.SavedItem
import java.util.Locale

@Composable
fun SavedScreen(vm: TubeViewModel, bottomPadding: Dp, onBack: () -> Unit) {
    val saved = Downloads.saved
    val active = Downloads.active.entries.toList()

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(), // rows never scroll under the status bar
        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp)
    ) {
        item { SubPageHeader("Downloads", onBack, large = true) }
        // Storage at a glance: what downloads take and what is left on the phone.
        item {
            Text(
                "Using ${formatBytes(Downloads.usedBytes())} · ${formatBytes(Downloads.freeBytes())} free · Saved in Gallery › Movies/PlayBook",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        items(active, key = { it.key }) { (key, p) ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(p.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        p.stage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (p.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    if (!p.failed) {
                        Spacer(Modifier.height(8.dp))
                        if (p.fraction >= 0) {
                            // Glides to each new value instead of jumping.
                            val shown by androidx.compose.animation.core.animateFloatAsState(p.fraction, label = "dl")
                            LinearProgressIndicator(progress = { shown }, Modifier.fillMaxWidth())
                        }
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                // Pause / Resume, then Cancel.
                if (!p.failed) IconButton(onClick = { if (p.paused) Downloads.resume(key) else Downloads.pause(key) }) {
                    Icon(
                        if (p.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        if (p.paused) "Resume" else "Pause",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { Downloads.cancel(key) }) { Icon(Icons.Rounded.Close, "Cancel") }
            }
        }
        if (saved.isEmpty() && active.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.Download,
                    "No downloads yet",
                    "Open any video and tap Download to watch it offline."
                )
            }
        }
        items(saved.toList(), key = { it.id }) { s -> SavedRow(s, onPlay = { vm.playLocal(s) }) }
    }
}

@Composable
private fun SavedRow(item: SavedItem, onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressScale(onPlay).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumb(item.thumb, Modifier.width(120.dp).height(68.dp).clip(Corners.medium))
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val mb = String.format(Locale.US, "%.0f MB", item.sizeBytes / 1_048_576.0)
            Text(
                listOf(item.channel, if (item.audioOnly) "Audio" else "Video", mb)
                    .filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { Downloads.delete(item) }) {
            Icon(Icons.Rounded.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
