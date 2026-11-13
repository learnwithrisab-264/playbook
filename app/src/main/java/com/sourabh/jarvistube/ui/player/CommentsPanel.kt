package com.sourabh.jarvistube.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sourabh.jarvistube.data.Comment
import com.sourabh.jarvistube.ui.common.Avatar
import com.sourabh.jarvistube.ui.common.LinkedText
import com.sourabh.jarvistube.ui.common.LoadMoreEffect
import com.sourabh.jarvistube.ui.common.compact
import com.sourabh.jarvistube.ui.common.linkColor

/**
 * YouTube-style comments panel. It takes the place of the details under the video, so the video
 * keeps playing above it. "N replies" opens that thread on its own Replies page. Read-only.
 */
@Composable
internal fun CommentsPanel(
    state: CommentsState,
    links: LinkActions,
    channelName: String,
    onClose: () -> Unit,
    modifier: Modifier
) {
    var thread by remember { mutableStateOf<Comment?>(null) }
    BackHandler { if (thread != null) thread = null else onClose() }
    val open = thread
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (open != null) {
                IconButton(onClick = { thread = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to comments") }
            } else Spacer(Modifier.width(12.dp))
            Text(if (open != null) "Replies" else "Comments", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (open == null && state.count > 0) {
                Spacer(Modifier.width(8.dp))
                Text(compact(state.count.toLong()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close comments") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (open == null) CommentList(state, links, channelName) { thread = it }
        else ReplyThread(state, open, links, channelName)
    }
}

@Composable
private fun CommentList(state: CommentsState, links: LinkActions, channelName: String, onReplies: (Comment) -> Unit) {
    val listState = rememberLazyListState()
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
        when {
            state.loading -> item { PanelSpinner() }
            state.disabled -> item { PanelNote("Comments are turned off for this video.") }
            state.failed -> item { PanelNote("Couldn't load comments. Close and open them again to retry.") }
            state.items.isEmpty() -> item { PanelNote("No comments yet.") }
        }
        items(state.items, key = { it.id }) { c ->
            Column {
                CommentRow(c, links, channelName, reply = false)
                if (c.replies != null) RepliesLink(c, channelName) { onReplies(c) }
            }
        }
        if (state.loadingMore) item { PanelSpinner() }
    }
    LoadMoreEffect(listState, state.items.size, state::loadMore)
}

@Composable
private fun RepliesLink(c: Comment, channelName: String, onClick: () -> Unit) {
    val blue = linkColor()
    Row(
        Modifier.padding(start = 52.dp, bottom = 8.dp).clip(CircleShape).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (c.creatorReplied) {
            CommentAvatar(channelName, 20)
            Text(" · ", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            when {
                c.replyCount == 1 -> "1 reply"
                c.replyCount > 1 -> "${compact(c.replyCount.toLong())} replies"
                else -> "Replies"
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = blue
        )
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = blue)
    }
}

/** One thread: the comment on top, its replies under it along a thread line. */
@Composable
private fun ReplyThread(state: CommentsState, parent: Comment, links: LinkActions, channelName: String) {
    LaunchedEffect(parent.id) { if (!state.replies.containsKey(parent.id)) state.loadReplies(parent) }
    val replies = state.replies[parent.id].orEmpty()
    val busy = state.replyLoading[parent.id] == true
    val listState = rememberLazyListState()
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
        item { CommentRow(parent, links, channelName, reply = false) }
        items(replies, key = { "r" + it.id }) { r ->
            Box(Modifier.drawBehind {
                val x = 32.dp.toPx()
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            }) { CommentRow(r, links, channelName, reply = true) }
        }
        if (busy) item { PanelSpinner() }
        else if (!state.replies.containsKey(parent.id)) item { PanelNote("Couldn't load replies.") }
    }
    LoadMoreEffect(listState, replies.size) { if (state.replyNext[parent.id] != null) state.loadReplies(parent) }
}

/** A round letter in a colour picked from the name; the real picture covers it when there is one. */
@Composable
internal fun CommentAvatar(name: String, size: Int, url: String? = null) {
    val clean = name.removePrefix("@")
    val palette = listOf(0xFF7E57C2, 0xFF43A047, 0xFFE53935, 0xFF1E88E5, 0xFFF4511E, 0xFF00897B, 0xFF8E24AA, 0xFF6D4C41)
    val color = Color(palette[(clean.hashCode() and 0x7fffffff) % palette.size])
    Box(Modifier.size(size.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(clean.take(1).uppercase().ifEmpty { "?" }, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (size * 0.45f).sp)
        if (!url.isNullOrBlank()) Avatar(clean, size, url)
    }
}

@Composable
private fun CommentRow(c: Comment, links: LinkActions, channelName: String, reply: Boolean) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(start = if (reply) 52.dp else 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)) {
        CommentAvatar(c.author, if (reply) 24 else 32, c.avatar)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (c.pinned) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
                    Icon(Icons.Rounded.PushPin, null, Modifier.size(12.dp), tint = muted)
                    Spacer(Modifier.width(4.dp))
                    Text("Pinned by ${channelName.ifBlank { "the creator" }}", style = MaterialTheme.typography.labelSmall, color = muted)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The creator's own name sits in a grey pill, as on YouTube.
                Text(
                    c.author,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (c.byCreator) MaterialTheme.colorScheme.onSurface else muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).then(
                        if (c.byCreator) Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 1.dp) else Modifier
                    )
                )
                c.time?.let { Text("  ·  $it", style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 1) }
            }
            Spacer(Modifier.height(4.dp))
            LinkedText(c.text, html = c.html, onUrl = links.onUrl, onSeek = links.onSeek)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ThumbUp, null, Modifier.size(16.dp), tint = muted)
                if (c.likes > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(compact(c.likes.toLong()), style = MaterialTheme.typography.labelMedium, color = muted)
                }
                if (c.hearted) {
                    Spacer(Modifier.width(16.dp))
                    Box {
                        CommentAvatar(channelName, 18)
                        Icon(
                            Icons.Rounded.Favorite, "Hearted by the creator",
                            Modifier.size(10.dp).align(Alignment.BottomEnd).offset(x = 3.dp, y = 3.dp),
                            tint = Color(0xFFFF4E45)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelSpinner() {
    Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}

@Composable
private fun PanelNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
}
