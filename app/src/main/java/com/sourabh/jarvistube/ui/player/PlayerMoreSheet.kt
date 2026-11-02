package com.sourabh.jarvistube.ui.player

import com.sourabh.jarvistube.ui.downloads.DownloadSheet
import com.sourabh.jarvistube.ui.study.PlayerPopups
import com.sourabh.jarvistube.ui.you.SaveToPlaylist
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.AppSettings
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.player.NowPlaying

private fun speedLabel(s: Float) = AppSettings.speedLabel(s)

/**
 * The player's ⋮ sheet, laid out like YouTube's: Quality, Captions, Playback speed, Audio track,
 * Sleep timer and Loop, then the extras from the action row (queue, SponsorBlock, audio only,
 * download, share, study lock). Hosted outside the auto-hiding controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerMoreSheetHost(now: NowPlaying, ui: PlayerUi) {
    if (!PlayerPopups.moreSheet) return
    val context = LocalContext.current
    val player = Graph.player
    val study = Graph.study
    val locked = study.lockActive
    var page by remember { mutableStateOf<String?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    val close = { PlayerPopups.moreSheet = false }

    if (showQueue) QueueSheet(onDismiss = { showQueue = false; close() })

    ModalBottomSheet(
        onDismissRequest = close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) {
            when (page) {
                null -> {
                    if (now.heights.isNotEmpty()) {
                        val playing = now.playingHeight.takeIf { it > 0 }
                        SheetRow(
                            Icons.Rounded.HighQuality, "Quality",
                            if (now.autoQuality) "Auto" + (playing?.let { " (${it}p)" } ?: "")
                            else AppSettings.qualityLabel(playing ?: now.quality)
                        ) { page = "quality" }
                    }
                    if (now.subtitles.isNotEmpty()) {
                        SheetRow(
                            Icons.Rounded.ClosedCaption, "Captions",
                            now.subtitles.firstOrNull { it.id == now.subtitleId }?.label ?: "Off"
                        ) { page = "captions" }
                    }
                    SheetRow(Icons.Rounded.Speed, "Playback speed", speedLabel(ui.speed)) { page = "speed" }
                    if (now.audioTracks.size > 1) {
                        SheetRow(
                            Icons.Rounded.Language, "Audio track",
                            now.audioTracks.firstOrNull { it.id == now.audioTrackId }?.label ?: "Original"
                        ) { page = "audio" }
                    }
                    SheetRow(
                        Icons.Rounded.Bedtime, "Sleep timer",
                        if (player.sleepEndAt > 0) "On" else "Off"
                    ) { page = "sleep" }
                    SwitchRow(Icons.Rounded.Repeat, "Loop video", ui.loop) { ui.toggleLoop() }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    SwitchRow(Icons.Rounded.Headphones, "Listen (audio only)", player.audioOnly) { player.updateAudioOnly(it) }
                    SwitchRow(Icons.Rounded.SkipNext, "SponsorBlock (skip sponsors)", player.sponsorBlock) { player.updateSponsorBlock(it) }
                    if (!locked) {
                        SheetRow(Icons.Rounded.QueueMusic, "Queue", "${player.queue.size}") { showQueue = true }
                        if (now.info != null) SheetRow(Icons.Rounded.Download, "Download", null) { close(); DownloadSheet.open(now.video, now.info) }
                        if (com.sourabh.jarvistube.data.Session.isSignedIn) {
                            SheetRow(Icons.Rounded.BookmarkBorder, "Save to playlist", null) { close(); SaveToPlaylist.open(now.video) }
                        }
                        SheetRow(Icons.Rounded.Share, "Share", null) {
                            close()
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, now.video.url)
                            }
                            context.startActivity(Intent.createChooser(send, "Share"))
                        }
                        SheetRow(
                            Icons.Rounded.Lock, "Lock study session",
                            null
                        ) {
                            close()
                            PlayerPopups.lockPicker = true
                        }
                    }
                }
                "quality" -> SubPage("Quality", { page = null }) {
                    Choice("Auto", now.autoQuality) { player.setQuality(0); close() }
                    now.heights.forEach { h ->
                        Choice(AppSettings.qualityLabel(h), !now.autoQuality && h == now.playingHeight) { player.setQuality(h); close() }
                    }
                }
                "captions" -> SubPage("Captions", { page = null }) {
                    Choice("Off", now.subtitleId == null) { player.setSubtitle(null); close() }
                    now.subtitles.forEach { s -> Choice(s.label, s.id == now.subtitleId) { player.setSubtitle(s.id); close() } }
                }
                "speed" -> SubPage("Playback speed", { page = null }) {
                    AppSettings.speeds.forEach { s -> Choice(speedLabel(s), s == ui.speed) { player.player.setPlaybackSpeed(s); close() } }
                }
                "audio" -> SubPage("Audio track", { page = null }) {
                    now.audioTracks.forEach { t ->
                        val tag = t.original && !t.label.contains("original", ignoreCase = true)
                        Choice(if (tag) "${t.label} (original)" else t.label, t.id == now.audioTrackId) {
                            player.setAudioTrack(t.id); close()
                        }
                    }
                }
                "sleep" -> SubPage("Sleep timer", { page = null }) {
                    listOf(0 to "Off", 15 to "15 minutes", 30 to "30 minutes", 45 to "45 minutes", 60 to "1 hour").forEach { (m, l) ->
                        Choice(l, m == 0 && player.sleepEndAt == 0L) { player.setSleepTimer(m); close() }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, title: String, value: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(24.dp))
        Spacer(Modifier.size(20.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(4.dp))
            Icon(Icons.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(24.dp))
        Spacer(Modifier.size(20.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onBack).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.KeyboardArrowLeft, "Back", Modifier.size(28.dp))
        Spacer(Modifier.size(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
    HorizontalDivider()
    content()
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.size(if (selected) 0.dp else 32.dp))
        if (selected) {
            Icon(Icons.Rounded.Check, null, Modifier.size(24.dp))
            Spacer(Modifier.size(8.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
