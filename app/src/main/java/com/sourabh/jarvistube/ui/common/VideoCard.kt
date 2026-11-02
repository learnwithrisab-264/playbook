package com.sourabh.jarvistube.ui.common

import com.sourabh.jarvistube.ui.downloads.DownloadSheet
import com.sourabh.jarvistube.ui.you.SaveToPlaylist
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.data.VideoItem

@Composable
fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Screen-specific ⋮ entries, e.g. "Remove from watch history". */
    extraActions: List<Pair<String, () -> Unit>> = emptyList()
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun toast(msg: String) = android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()

    // Edge-to-edge thumbnail, then avatar / title / meta / ⋮ — the layout people already know.
    Column(
        modifier
            .fillMaxWidth()
            .pressScale(onClick, onPress = { com.sourabh.jarvistube.data.Repo.prefetch(video.url) }, onLongClick = {
                Graph.player.enqueue(video)
                toast("Added to queue")
            })
            .padding(bottom = 18.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            Thumb(video.thumb, Modifier.matchParentSize())
            val badge = if (video.isLive) "LIVE" else formatDuration(video.durationSec)
            if (badge.isNotEmpty()) {
                Text(
                    badge,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (video.isLive) Color(0xFFD63B3B) else Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        Row(
            Modifier.padding(start = 12.dp, end = 0.dp, top = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            val openChannel = LocalOpenChannel.current
            Box(
                if (video.channelUrl != null) Modifier.clip(CircleShape).clickable { openChannel(video) } else Modifier
            ) { Avatar(video.channel, 36, video.avatar) }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                val meta = listOfNotNull(
                    video.channel.takeIf { it.isNotBlank() },
                    video.views.takeIf { it >= 0 }?.let { "${compact(it)} views" },
                    video.uploaded
                ).joinToString(" · ")
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurface)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Play next") },
                        leadingIcon = { Icon(Icons.Rounded.SkipNext, null) },
                        onClick = { menu = false; Graph.player.playNextUp(video); toast("Plays next") }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to queue") },
                        leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) },
                        onClick = { menu = false; Graph.player.enqueue(video); toast("Added to queue") }
                    )
                    if (com.sourabh.jarvistube.data.Session.isSignedIn) {
                        DropdownMenuItem(
                            text = { Text("Save to playlist") },
                            leadingIcon = { Icon(Icons.Rounded.BookmarkBorder, null) },
                            onClick = { menu = false; SaveToPlaylist.open(video) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Download") },
                        leadingIcon = { Icon(Icons.Rounded.Download, null) },
                        onClick = { menu = false; DownloadSheet.open(video) }
                    )
                    if (video.channelUrl != null) {
                        DropdownMenuItem(
                            text = { Text("Go to channel") },
                            leadingIcon = { Icon(Icons.Rounded.AccountCircle, null) },
                            onClick = { menu = false; openChannel(video) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Rounded.Share, null) },
                        onClick = {
                            menu = false
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, video.url)
                            }
                            context.startActivity(android.content.Intent.createChooser(send, "Share"))
                        }
                    )
                    extraActions.forEach { (label, action) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = { menu = false; action() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VideoCardSkeleton() {
    val brush = shimmerBrush()
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(brush))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = 12.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(brush))
            Spacer(Modifier.size(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth(0.9f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(brush))
                Box(Modifier.fillMaxWidth(0.55f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(brush))
            }
        }
    }
}
