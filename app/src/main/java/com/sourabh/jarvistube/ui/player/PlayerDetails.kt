package com.sourabh.jarvistube.ui.player

import androidx.compose.material.icons.rounded.StayCurrentPortrait

import androidx.compose.ui.res.stringResource
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
internal fun Details(
    vm: TubeViewModel,
    now: NowPlaying,
    ui: PlayerUi,
    modifier: Modifier
) {
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
    val links = rememberLinkActions(vm, now.video.url)
    val comments = rememberComments(now.video.url)
    var commentsOpen by remember(now.video.url) { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(com.sourabh.jarvistube.ui.study.PlayerPopups.openComments) {
        if (com.sourabh.jarvistube.ui.study.PlayerPopups.openComments) {
            commentsOpen = true
            com.sourabh.jarvistube.ui.study.PlayerPopups.openComments = false
        }
    }
    // Comments take the place of the details under the video, as on YouTube; the video keeps playing.
    if (commentsOpen) {
        CommentsPanel(
            comments, links,
            channelName = info?.uploaderName ?: now.video.channel,
            channelAvatar = info?.uploaderAvatars?.bestUrl() ?: now.video.avatar,
            onClose = { commentsOpen = false },
            modifier = modifier
        )
        return
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LazyColumn(modifier.navigationBarsPadding(), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                Text(
                    info?.name ?: now.video.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { expanded = !expanded }
                )
                Spacer(Modifier.height(6.dp))
                // One quiet line under the title: views, date and "…more", like YouTube.
                val meta = listOfNotNull(
                    (info?.viewCount ?: now.video.views).takeIf { it >= 0 }?.let { "${compact(it)} views" },
                    prettyDate(info?.textualUploadDate ?: now.video.uploaded)
                ).joinToString("  ·  ")
                Row(Modifier.clip(Corners.small).clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (desc.isNotBlank()) Text(
                        if (expanded) "   Show less" else "   …more",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                // The full description opens in a soft card right under it.
                androidx.compose.animation.AnimatedVisibility(
                    visible = expanded && desc.isNotBlank(),
                    enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                ) {
                    Column(
                        Modifier.padding(top = 12.dp).fillMaxWidth()
                            .clip(Corners.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(14.dp)
                    ) {
                        Text(
                            listOfNotNull(
                                (info?.viewCount ?: now.video.views).takeIf { it >= 0 }?.let { "${String.format(java.util.Locale.US, "%,d", it)} views" },
                                prettyDate(info?.textualUploadDate ?: now.video.uploaded)
                            ).joinToString("  "),
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        // Blue links open; times jump.
                        com.sourabh.jarvistube.ui.common.LinkedText(
                            info?.description?.content.orEmpty(),
                            html = info?.description?.type == org.schabi.newpipe.extractor.stream.Description.HTML,
                            onUrl = links.onUrl,
                            onSeek = links.onSeek,
                            maxLines = Int.MAX_VALUE
                        )
                        Text(
                            "Show less",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp).clickable { expanded = false }
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
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
                Spacer(Modifier.height(12.dp))
            }
        }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Vertical: YouTube's upright full-screen view, one tap away (no longer automatic).
                if (!Graph.study.lockActive) PillChip(
                    selected = false,
                    onClick = { com.sourabh.jarvistube.ui.study.PlayerPopups.enterUpright = true },
                    label = { Text("Vertical") },
                    leadingIcon = { Icon(androidx.compose.material.icons.Icons.Rounded.StayCurrentPortrait, null, Modifier.size(18.dp)) }
                )
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
                    label = { Text(stringResource(R.string.loop)) },
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
                        label = { Text(stringResource(R.string.lock_study_session)) },
                        leadingIcon = { Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp)) }
                    )
                }
                PillChip(
                    selected = Graph.player.sponsorBlock,
                    onClick = { Graph.player.updateSponsorBlock(!Graph.player.sponsorBlock) },
                    label = { Text(stringResource(R.string.sponsorblock)) },
                    leadingIcon = { Icon(Icons.Rounded.SkipNext, null, Modifier.size(18.dp)) }
                )
                PillChip(
                    selected = Graph.player.audioOnly,
                    onClick = { Graph.player.updateAudioOnly(!Graph.player.audioOnly) },
                    label = { Text(stringResource(R.string.listen)) }, // audio only: no video download, saves data and battery
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
                        label = { Text(stringResource(R.string.download)) },
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
                    label = { Text(stringResource(R.string.share)) },
                    leadingIcon = { Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)) }
                )
            }
        }
        item {
            Box(Modifier.padding(horizontal = 12.dp).padding(top = 16.dp, bottom = 4.dp)) {
                CommentsCard(comments) { commentsOpen = true }
            }
        }
        item {
            // Same topic as this video (see Related), not YouTube's loose "people also watch" mix.
            val related = com.sourabh.jarvistube.data.Related.of(info, now.video)
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
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                        .clip(Corners.large)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { vm.showRelated = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.related_videos_hidden), style = MaterialTheme.typography.titleMedium)
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
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                            .clip(Corners.large)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { vm.showRelated = false }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.related_videos), style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Tap to hide them again.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Rounded.ExpandLess, "Hide related videos", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    related.forEach { v -> VideoCard(v, onClick = { vm.play(v) }) }
                }
            }
        }
    }
}
