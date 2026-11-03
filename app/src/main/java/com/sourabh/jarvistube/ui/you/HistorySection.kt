package com.sourabh.jarvistube.ui.you

import com.sourabh.jarvistube.ui.theme.Corners
import com.sourabh.jarvistube.ui.common.pressScale
import com.sourabh.jarvistube.ui.common.Thumb
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.TubeViewModel
import com.sourabh.jarvistube.data.HistoryDay
import com.sourabh.jarvistube.data.WatchHistory
import com.sourabh.jarvistube.data.WatchedVideo
import com.sourabh.jarvistube.data.historyDayOf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault())

private fun whenWatched(item: WatchedVideo, group: HistoryDay, zone: ZoneId): String {
    val at = Instant.ofEpochMilli(item.watchedAt).atZone(zone)
    return if (group == HistoryDay.Older) at.format(dateFormat) else at.format(timeFormat)
}

/** History saved on the phone: search, Today / Yesterday / Older groups, delete one, clear all. */
fun LazyListScope.historySection(
    vm: TubeViewModel,
    query: String,
    onQuery: (String) -> Unit,
    onClearAll: () -> Unit
) {
    val all = WatchHistory.items
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val shown = if (query.isBlank()) all.toList()
    else all.filter { it.video.title.contains(query.trim(), true) || it.video.channel.contains(query.trim(), true) }

    item(key = "history-tools") {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                placeholder = { Text("Search history") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                modifier = Modifier.weight(1f)
            )
            if (all.isNotEmpty()) TextButton(onClick = onClearAll) { Text("Clear all") }
        }
    }
    if (shown.isEmpty()) {
        item(key = "history-empty") {
            Text(
                if (all.isEmpty()) "Videos you watch will appear here." else "Nothing in your history matches that.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
            )
        }
    }
    val groups = shown.groupBy { historyDayOf(it.watchedAt, now, zone) }
    HistoryDay.entries.forEach { day ->
        val list = groups[day] ?: return@forEach
        item(key = "history-day-${day.name}") {
            Text(
                day.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp)
            )
        }
        items(list, key = { "h:" + it.video.url }) { item ->
            Row(
                Modifier.fillMaxWidth().pressScale({ vm.play(item.video) })
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Thumb(item.video.thumb, Modifier.width(120.dp).height(68.dp).clip(Corners.medium))
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.video.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(item.video.channel, whenWatched(item, day, zone)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { WatchHistory.remove(item.video.url) }) {
                    Icon(Icons.Rounded.Close, "Delete from history", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Asks first: clearing history can't be undone. */
@Composable
fun ClearHistoryDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear all history?") },
        text = { Text("This removes the history saved for this account on this phone. It can't be undone.") },
        confirmButton = {
            TextButton(onClick = { WatchHistory.clear(); onDismiss() }) { Text("Clear all") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
