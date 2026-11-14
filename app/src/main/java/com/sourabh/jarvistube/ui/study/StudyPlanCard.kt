package com.sourabh.jarvistube.ui.study

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.data.PlanItem
import com.sourabh.jarvistube.data.StudyPlan
import com.sourabh.jarvistube.data.VideoItem
import com.sourabh.jarvistube.ui.common.Thumb
import com.sourabh.jarvistube.ui.common.TopicChip
import com.sourabh.jarvistube.ui.common.formatDuration
import com.sourabh.jarvistube.ui.theme.Corners
import com.sourabh.jarvistube.ui.theme.rememberTouch

/**
 * Top of the Study tab: what you chose to watch today and this week, with progress. Add videos
 * from any video's ⋮ menu or the player's settings; they tick off when watched to the end.
 */
@Composable
internal fun StudyPlanCard(onPlay: (VideoItem) -> Unit) {
    LaunchedEffect(Unit) { StudyPlan.rollOver() }
    var week by remember { mutableStateOf(false) }
    val today = StudyPlan.today()
    val thisWeek = StudyPlan.week()
    val list = if (week) thisWeek else today
    val touch = rememberTouch()
    SectionCard("Your plan") {
        Column(Modifier.animateContentSize()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TopicChip("Today · ${today.count { it.done }}/${today.size}", !week) { week = false }
                TopicChip("This week · ${thisWeek.count { it.done }}/${thisWeek.size}", week) { week = true }
            }
            Spacer(Modifier.height(12.dp))
            if (list.isEmpty()) {
                Text(
                    if (week) "Nothing planned for this week yet." else "Nothing planned for today yet.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Tap ⋮ on any video, or the ⚙ in the player, and choose Add to Today or Add to this week.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val done = list.count { it.done }
                LinearProgressIndicator(
                    progress = { done.toFloat() / list.size },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                )
                Text(
                    if (done == list.size) "All done. Well studied." else "$done of ${list.size} watched",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                )
                // Unwatched first, in the order you added them.
                list.sortedBy { it.done }.forEach { p ->
                    PlanRow(p, onPlay = { onPlay(p.video) }, onToggle = { touch(); StudyPlan.setDone(p.video.url, !p.done) }, onRemove = { StudyPlan.remove(p.video.url) })
                }
            }
        }
    }
}

@Composable
private fun PlanRow(p: PlanItem, onPlay: () -> Unit, onToggle: () -> Unit, onRemove: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().clip(Corners.medium).clickable(onClick = onPlay).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
            Icon(
                if (p.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                if (p.done) "Mark as not watched" else "Mark as watched",
                tint = if (p.done) MaterialTheme.colorScheme.primary else muted
            )
        }
        Box(Modifier.width(96.dp).height(54.dp).clip(Corners.small).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Thumb(p.video.thumb, Modifier.matchParentSize())
            formatDuration(p.video.durationSec).takeIf { it.isNotEmpty() }?.let {
                Text(
                    it, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).clip(Corners.tiny)
                        .background(com.sourabh.jarvistube.ui.theme.OnVideo.scrimStrong).padding(horizontal = 3.dp)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                p.video.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                textDecoration = if (p.done) TextDecoration.LineThrough else null,
                color = if (p.done) muted else MaterialTheme.colorScheme.onSurface
            )
            Text(p.video.channel, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1)
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.Close, "Remove from plan", Modifier.size(18.dp), tint = muted)
        }
    }
}
