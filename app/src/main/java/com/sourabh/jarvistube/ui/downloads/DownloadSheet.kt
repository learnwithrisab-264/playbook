package com.sourabh.jarvistube.ui.downloads

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.data.DlOption
import com.sourabh.jarvistube.data.Downloads
import com.sourabh.jarvistube.data.VideoItem
import org.schabi.newpipe.extractor.stream.StreamInfo

/** Which video the app-wide download sheet is open for. */
object DownloadSheet {
    var video by mutableStateOf<VideoItem?>(null); private set
    var info by mutableStateOf<StreamInfo?>(null); private set
    fun open(v: VideoItem, known: StreamInfo? = null) { video = v; info = known }
    fun close() { video = null; info = null }
}

fun formatBytes(b: Long): String = when {
    b <= 0 -> "size unknown"
    b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.0f MB".format(b / (1L shl 20).toDouble())
    else -> "%.0f KB".format(b / 1024.0)
}

/** Pick a resolution (or audio only) with its download size, plus free space on the phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSheetHost() {
    val video = DownloadSheet.video ?: return
    val context = LocalContext.current
    var info by remember(video.url) { mutableStateOf(DownloadSheet.info) }
    var failed by remember(video.url) { mutableStateOf(false) }
    LaunchedEffect(video.url) {
        if (info == null) {
            info = runCatching { com.sourabh.jarvistube.data.Repo.streamInfo(video.url) }.getOrNull()
            failed = info == null
        }
    }
    val options = remember(info) { info?.let { Downloads.options(it) }.orEmpty() }

    ModalBottomSheet(
        onDismissRequest = { DownloadSheet.close() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Text("Download", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                video.title, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
            when {
                info == null && !failed -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
                options.isEmpty() -> Text(
                    "Nothing downloadable for this video.",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> options.forEach { o -> OptionRow(o) { start(context, video, info!!, o) } }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                "Saved to Gallery › Movies/PlayBook · ${formatBytes(Downloads.freeBytes())} free",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

private fun start(context: android.content.Context, video: VideoItem, info: StreamInfo, o: DlOption) {
    if (o.bytes > 0 && o.bytes * 2 > Downloads.freeBytes()) {
        Toast.makeText(context, "Not enough free space for ${o.label}", Toast.LENGTH_SHORT).show()
        return
    }
    Downloads.enqueue(video, info, o.quality, o.audioOnly)
    Toast.makeText(context, "Downloading ${o.label}", Toast.LENGTH_SHORT).show()
    DownloadSheet.close()
}

@Composable
private fun OptionRow(o: DlOption, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (o.audioOnly) Icons.Rounded.Headphones else Icons.Rounded.HighQuality, null)
        Spacer(Modifier.size(24.dp))
        Text(o.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(formatBytes(o.bytes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
