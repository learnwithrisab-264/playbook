package com.sourabh.jarvistube.ui.player

import com.sourabh.jarvistube.ui.common.Thumb
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.Graph

/** "Up next": only what you queued. Nothing is added automatically. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(onDismiss: () -> Unit) {
    val queue = Graph.player.queue
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Up next", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (queue.isNotEmpty()) TextButton(onClick = { Graph.player.clearQueue() }) { Text("Clear") }
            }
            if (queue.isEmpty()) {
                Text(
                    "Nothing queued. Use ⋮ › Play next or Add to queue on any video (or long-press it), or Play all on a playlist. With an empty queue, Next picks a related video or the channel's next upload.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
                )
            } else {
                LazyColumn(Modifier.height(420.dp)) {
                    itemsIndexed(queue.toList(), key = { _, v -> v.url }) { i, v ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Thumb(v.thumb, Modifier.width(96.dp).height(54.dp).clip(RoundedCornerShape(10.dp)))
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(v.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    v.channel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            // Reorder: move up / down one place.
                            IconButton(onClick = { Graph.player.moveInQueue(i, i - 1) }, enabled = i > 0, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Rounded.KeyboardArrowUp, "Move up")
                            }
                            IconButton(onClick = { Graph.player.moveInQueue(i, i + 1) }, enabled = i < queue.lastIndex, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Rounded.KeyboardArrowDown, "Move down")
                            }
                            IconButton(onClick = { Graph.player.removeFromQueue(i) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Rounded.Close, "Remove from queue")
                            }
                        }
                    }
                }
            }
        }
    }
}
