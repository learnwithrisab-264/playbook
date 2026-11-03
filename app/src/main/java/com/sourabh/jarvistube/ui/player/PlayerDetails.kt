package com.sourabh.jarvistube.ui.player

import com.sourabh.jarvistube.ui.theme.Corners
import com.sourabh.jarvistube.ui.common.Avatar
import com.sourabh.jarvistube.ui.common.compact
import com.sourabh.jarvistube.ui.common.PillChip
import com.sourabh.jarvistube.ui.common.prettyDate
import com.sourabh.jarvistube.ui.common.VideoCard
import com.sourabh.jarvistube.ui.downloads.DownloadSheet
import com.sourabh.jarvistube.ui.study.PlayerPopups
import com.sourabh.jarvistube.ui.you.SubscribeButton
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ClosedCaption
import com.sourabh.jarvistube.AppSettings
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.R
import com.sourabh.jarvistube.TubeViewModel
import com.sourabh.jarvistube.data.bestUrl
import com.sourabh.jarvistube.player.NowPlaying

@Composable
internal fun Details(vm: TubeViewModel, now: NowPlaying, ui: PlayerUi, modifier: Modifier) {
    val context = LocalContext.current
    val info = now.info
    var expanded by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }
    var audioMenu by remember { mutableStateOf(false) }
    var captionsMenu by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    val desc = remember(info) {
        androidx.core.text.HtmlCompat.fromHtml(
            info?.description?.content.orEmpty(),
            androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
        ).toString().trim()
    }

    LazyColumn(modifier.navigationBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                Text(
                    info?.name ?: now.video.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { expanded = !expanded }
                )
                Spacer(Modifier.height(12.dp))
                // YouTube's grey description card: views + date, a preview, "…more".
                val meta = listOfNotNull(
                    (info?.viewCount ?: now.video.views).takeIf { it >= 0 }?.let { "${String.format(java.util.Locale.US, "%,d", it)} views" },
                    prettyDate(info?.textualUploadDate ?: now.video.uploaded)
                ).joinToString("  ")
                Column(
                    Modifier.fillMaxWidth()
                        .clip(Corners.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { expanded = !expanded }
                        .padding(12.dp)
                ) {
                    Text(meta, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    if (desc.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            desc,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (expanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (expanded) "Show less" else "…more",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                val channelUrl = info?.uploaderUrl?.takeIf { it.isNotBlank() } ?: now.video.channelUrl
                Row(
                    // Tap the channel to open its page (the player drops to the mini-player).
                    Modifier.fillMaxWidth().clip(Corners.medium)
                        .clickable(enabled = channelUrl != null && !Graph.study.lockActive) {
                            channelUrl?.let { vm.openChannel(it, info?.uploaderName ?: now.video.channel) }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(info?.uploaderName ?: now.video.channel, 40, info?.uploaderAvatars?.bestUrl() ?: now.video.avatar)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            info?.uploaderName ?: now.video.channel,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        info?.uploaderSubscriberCount?.takeIf { it >= 0 }?.let {
                            Text(
                                "${compact(it)} subscribers",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (!Graph.study.lockActive) SubscribeButton(channelUrl, info?.uploaderName ?: now.video.channel)
                }
            }
        }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box {
                    PillChip(
                        selected = ui.speed != 1f,
                        onClick = { speedMenu = true },
                        label = { Text(AppSettings.speedText(ui.speed)) },
                        leadingIcon = { Icon(Icons.Rounded.Speed, null, Modifier.size(18.dp)) }
                    )
                    // The speeds chosen in Settings › Playback speed (up to 4x).
                    DropdownMenu(speedMenu, { speedMenu = false }) {
                        AppSettings.speeds.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(AppSettings.speedText(s)) },
                                trailingIcon = { if (s == ui.speed) Icon(Icons.Rounded.Check, null) },
                                onClick = { Graph.player.player.setPlaybackSpeed(s); speedMenu = false }
                            )
                        }
                    }
                }
                if (now.heights.isNotEmpty()) {
                    Box {
                        val playing = now.playingHeight.takeIf { it > 0 }
                        PillChip(
                            selected = false,
                            onClick = { qualityMenu = true },
                            label = {
                                Text(
                                    if (now.autoQuality) "Auto" + (playing?.let { " (${it}p)" } ?: "")
                                    else AppSettings.qualityLabel(playing ?: now.quality)
                                )
                            },
                            leadingIcon = { Icon(Icons.Rounded.HighQuality, null, Modifier.size(18.dp)) }
                        )
                        QualityMenu(now, qualityMenu) { qualityMenu = false }
                    }
                }
                if (now.subtitles.isNotEmpty()) {
                    Box {
                        val current = now.subtitles.firstOrNull { it.id == now.subtitleId }
                        PillChip(
                            selected = current != null,
                            onClick = { captionsMenu = true },
                            label = { Text(current?.label?.substringBefore(" (") ?: "Captions") },
                            leadingIcon = { Icon(Icons.Rounded.ClosedCaption, null, Modifier.size(18.dp)) }
                        )
                        CaptionsMenu(now, captionsMenu) { captionsMenu = false }
                    }
                }
                if (now.audioTracks.size > 1) {
                    // Always starts on the original language; other dubs are an opt-in choice.
                    Box {
                        val current = now.audioTracks.firstOrNull { it.id == now.audioTrackId }
                        PillChip(
                            selected = current?.original == false,
                            onClick = { audioMenu = true },
                            label = { Text(current?.label ?: "Audio") },
                            leadingIcon = { Icon(Icons.Rounded.Language, null, Modifier.size(18.dp)) }
                        )
                        DropdownMenu(audioMenu, { audioMenu = false }) {
                            now.audioTracks.forEach { t ->
                                DropdownMenuItem(
                                    text = {
                                        val tag = t.original && !t.label.contains("original", ignoreCase = true)
                                        Text(if (tag) "${t.label} (original)" else t.label)
                                    },
                                    trailingIcon = { if (t.id == now.audioTrackId) Icon(Icons.Rounded.Check, null) },
                                    onClick = { Graph.player.setAudioTrack(t.id); audioMenu = false }
                                )
                            }
                        }
                    }
                }
                PillChip(
                    selected = ui.loop,
                    onClick = { ui.toggleLoop() },
                    label = { Text("Loop") },
                    leadingIcon = { Icon(Icons.Rounded.Repeat, null, Modifier.size(18.dp)) }
                )
                PillChip(
                    selected = Graph.player.queue.isNotEmpty(),
                    onClick = { showQueue = true },
                    label = { Text("Queue (${Graph.player.queue.size})") },
                    leadingIcon = { Icon(Icons.Rounded.QueueMusic, null, Modifier.size(18.dp)) }
                )
                if (showQueue) QueueSheet(onDismiss = { showQueue = false })
                if (!Graph.study.lockActive) {
                    PillChip(
                        selected = false,
                        onClick = { PlayerPopups.lockPicker = true },
                        label = { Text("Lock Study Session") },
                        leadingIcon = { Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp)) }
                    )
                }
                PillChip(
                    selected = Graph.player.sponsorBlock,
                    onClick = { Graph.player.updateSponsorBlock(!Graph.player.sponsorBlock) },
                    label = { Text("SponsorBlock") },
                    leadingIcon = { Icon(Icons.Rounded.SkipNext, null, Modifier.size(18.dp)) }
                )
                PillChip(
                    selected = Graph.player.audioOnly,
                    onClick = { Graph.player.updateAudioOnly(!Graph.player.audioOnly) },
                    label = { Text("Listen") }, // audio only: no video download, saves data and battery
                    leadingIcon = { Icon(Icons.Rounded.Headphones, null, Modifier.size(18.dp)) }
                )
                Box {
                    PillChip(
                        selected = Graph.player.sleepEndAt > 0,
                        onClick = { sleepMenu = true },
                        label = { Text(if (Graph.player.sleepEndAt > 0) "Sleep on" else "Sleep") },
                        leadingIcon = { Icon(Icons.Rounded.Bedtime, null, Modifier.size(18.dp)) }
                    )
                    DropdownMenu(sleepMenu, { sleepMenu = false }) {
                        listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 45 to "45 min", 60 to "60 min").forEach { (m, l) ->
                            DropdownMenuItem(
                                text = { Text(l) },
                                onClick = { Graph.player.setSleepTimer(m); sleepMenu = false }
                            )
                        }
                    }
                }
                if (info != null) {
                    PillChip(
                        selected = false,
                        onClick = { DownloadSheet.open(now.video, info) },
                        label = { Text("Download") },
                        leadingIcon = { Icon(Icons.Rounded.Download, null, Modifier.size(18.dp)) }
                    )
                }
                PillChip(
                    selected = false,
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, now.video.url)
                        }
                        context.startActivity(Intent.createChooser(send, "Share"))
                    },
                    label = { Text("Share") },
                    leadingIcon = { Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)) }
                )
            }
        }
        item {
            val related = info?.relatedItems.orEmpty()
                .filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
                .filter { it.duration !in 1L..60L }
            val locked = Graph.study.active
            if (locked) {
                Text(
                    "Related videos are off during a study session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else if (!vm.showRelated) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(Corners.large)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { vm.showRelated = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Related videos hidden", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Distraction-free mode. Tap to reveal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column {
                    // Same control, now a real ON/OFF: tap to hide the feed again.
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(Corners.large)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { vm.showRelated = false }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Related videos", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Tap to hide them again.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Rounded.ExpandLess, "Hide related videos", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    related.forEach { r ->
                        val v = com.sourabh.jarvistube.data.VideoItem(
                            url = r.url, title = r.name, channel = r.uploaderName.orEmpty(),
                            thumb = r.thumbnails.bestUrl(),
                            durationSec = r.duration, views = r.viewCount, uploaded = r.textualUploadDate,
                            isLive = r.streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM,
                            avatar = runCatching { r.uploaderAvatars.bestUrl() }.getOrNull()
                        )
                        if (!com.sourabh.jarvistube.data.ContentFilter.hides(v)) VideoCard(v, onClick = { vm.play(v) })
                    }
                }
            }
        }
    }
}
