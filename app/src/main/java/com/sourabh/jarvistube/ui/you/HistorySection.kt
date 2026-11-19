package com.sourabh.jarvistube.ui.you

import com.sourabh.jarvistube.R
import androidx.compose.ui.res.stringResource
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

/** One row of the merged history: watched in this app (exact time) or on YouTube (day only). */
private class HistoryRow(
    val video: com.sourabh.jarvistube.data.VideoItem,
    val at: Long,
    val local: WatchedVideo?,
    val youtube: com.sourabh.jarvistube.data.LibVideo?
)

/**
 * Your watch history as one list: what you watched in this app and your YouTube account's history,
 * merged by day (Today / Yesterday / Older), a video only once. Search, delete one, clear all.
 * YouTube only says which day a video was watched, so those sit at the top of their day in
 * YouTube's own order, next to this app's videos with their exact times.
 */
fun LazyListScope.historySection(
    vm: TubeViewModel,
    query: String,
    onQuery: (String) -> Unit,
    onClearAll: () -> Unit,
    youtube: List<com.sourabh.jarvistube.data.LibVideo> = emptyList(),
    onRemoveYoutube: (com.sourabh.jarvistube.data.LibVideo) -> Unit = {}
) {
    val all = WatchHistory.items
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val today = java.time.LocalDate.now(zone)
    val startToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val startYesterday = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val localByUrl = all.associateBy { it.video.url }
    val ytByUrl = youtube.associateBy { it.video.url }
    // Where a YouTube-only video goes: by its day heading; without one, next to the app video
    // YouTube listed just before it.
    var anchor = now
    val ytOnly = youtube.mapIndexedNotNull { i, lv ->
        localByUrl[lv.video.url]?.let { anchor = it.watchedAt; return@mapIndexedNotNull null }
        val g = lv.group?.trim()?.lowercase()
        val at = when {
            g == null -> anchor - 1
            g == "today" -> now - i
            g == "yesterday" -> startToday - 1 - i
            else -> startYesterday - 1 - i
        }
        anchor = at
        HistoryRow(lv.video, at, null, lv)
    }
    val rows = (all.map { HistoryRow(it.video, it.watchedAt, it, ytByUrl[it.video.url]) } + ytOnly)
        .sortedByDescending { it.at }
    val q = query.trim()
    val shown = if (q.isEmpty()) rows else rows.filter { it.video.title.contains(q, true) || it.video.channel.contains(q, true) }

    item(key = "history-tools") {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                placeholder = { Text(stringResource(R.string.search_history)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                modifier = Modifier.weight(1f),
                // A soft filled pill instead of a boxed outline.
                shape = androidx.compose.foundation.shape.CircleShape,
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                )
            )
            if (all.isNotEmpty()) TextButton(onClick = onClearAll) { Text(stringResource(R.string.clear_all)) }
        }
    }
    if (shown.isEmpty()) {
        item(key = "history-empty") {
            Text(
                if (rows.isEmpty()) "Videos you watch will appear here." else "Nothing in your history matches that.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
            )
        }
    }
    val groups = shown.groupBy { historyDayOf(it.at, now, zone) }
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
        items(list, key = { "h:" + it.video.url }) { row ->
            Row(
                Modifier.fillMaxWidth().pressScale({ vm.play(row.video) })
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Thumb(row.video.thumb, Modifier.width(120.dp).height(68.dp).clip(Corners.medium))
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.video.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val whenText = row.local?.let { whenWatched(it, day, zone) } ?: "YouTube"
                    Text(
                        listOf(row.video.channel, whenText).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = {
                    // Gone from both lists, so it doesn't come back from the other one.
                    row.local?.let { WatchHistory.remove(it.video.url) }
                    row.youtube?.let(onRemoveYoutube)
                }) {
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
        title = { Text(stringResource(R.string.clear_all_history)) },
        text = { Text(stringResource(R.string.this_removes_the_history_saved_for_this)) },
        confirmButton = {
            TextButton(onClick = { WatchHistory.clear(); onDismiss() }) { Text(stringResource(R.string.clear_all)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
