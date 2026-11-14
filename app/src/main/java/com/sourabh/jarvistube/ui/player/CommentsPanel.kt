package com.sourabh.jarvistube.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import com.sourabh.jarvistube.ui.common.PillChip
import com.sourabh.jarvistube.ui.common.linkColor

/**
 * YouTube-style comments panel. It takes the place of the details under the video, so the video
 * keeps playing above it. It slides up on open and down on close; "N replies" slides in that
 * thread's Replies page, and Back returns to the same scroll position. Read-only.
 *
 * Beyond YouTube: a Timestamps filter (every comment that points at a moment of this video, like
 * viewer-made chapters) and pages loaded well before you reach the bottom.
 */
@Composable
internal fun CommentsPanel(
    state: CommentsState,
    links: LinkActions,
    channelName: String,
    channelAvatar: String?,
    onClose: () -> Unit,
    modifier: Modifier
) {
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    var thread by remember { mutableStateOf<Comment?>(null) }
    var onlyTimes by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val close = { shown.targetState = false }
    // Hand back to the details once the slide-down has finished.
    LaunchedEffect(shown.isIdle, shown.currentState) { if (shown.isIdle && !shown.currentState) onClose() }
    BackHandler { if (thread != null) thread = null else close() }

    Box(modifier) {
        AnimatedVisibility(
            visibleState = shown,
            enter = slideInVertically(tween(260)) { it / 3 } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(220)) { it / 3 } + fadeOut(tween(180))
        ) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
                }
                val open = thread
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (open != null) {
                        IconButton(onClick = { thread = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to comments") }
                    } else Spacer(Modifier.width(12.dp))
                    Text(if (open != null) "Replies" else "Comments", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (open == null) state.shortCount?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = close) { Icon(Icons.Rounded.Close, "Close comments") }
                }
                if (open == null && !state.disabled) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.sorts.forEachIndexed { i, label ->
                            PillChip(selected = !onlyTimes && state.sort == i, onClick = {
                                onlyTimes = false
                                state.setSortOrder(i)
                            }, label = { Text(label) })
                        }
                        PillChip(
                            selected = onlyTimes,
                            onClick = { onlyTimes = !onlyTimes },
                            label = { Text("Timestamps") },
                            leadingIcon = { Icon(Icons.Rounded.Schedule, null, Modifier.size(16.dp)) }
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AnimatedContent(
                    targetState = open,
                    transitionSpec = {
                        if (targetState != null) slideInHorizontally(tween(240)) { it } togetherWith slideOutHorizontally(tween(240)) { -it / 4 }
                        else slideInHorizontally(tween(240)) { -it / 4 } togetherWith slideOutHorizontally(tween(240)) { it }
                    },
                    label = "comments-page"
                ) { page ->
                    if (page == null) CommentList(state, listState, onlyTimes, links, channelName, channelAvatar) { thread = it }
                    else ReplyThread(state, page, links, channelName, channelAvatar)
                }
            }
        }
    }
}

/** Loads the next page while there are still [ahead] items to go, so scrolling never waits. */
@Composable
private fun PrefetchEffect(state: LazyListState, itemCount: Int, ahead: Int = 8, onLoad: () -> Unit) {
    val near by remember(state) {
        derivedStateOf {
            val total = state.layoutInfo.totalItemsCount
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - ahead
        }
    }
    LaunchedEffect(near, itemCount) { if (near) onLoad() }
}

@Composable
private fun CommentList(
    state: CommentsState,
    listState: LazyListState,
    onlyTimes: Boolean,
    links: LinkActions,
    channelName: String,
    channelAvatar: String?,
    onReplies: (Comment) -> Unit
) {
    val shown = if (onlyTimes) state.items.filter { it.hasTimestamp } else state.items
    // The filter keeps reading pages until it has a screenful (or there are no more).
    LaunchedEffect(onlyTimes, shown.size, state.loadingMore, state.hasMore) {
        if (onlyTimes && shown.size < 12 && state.hasMore && !state.loadingMore) state.loadMore()
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        when {
            state.loading -> items(5) { CommentSkeleton() }
            state.disabled -> item { PanelNote("Comments are turned off for this video.") }
            state.failed -> item { PanelNote("Couldn't load comments. Close and open them again to retry.") }
            shown.isEmpty() && onlyTimes && (state.hasMore || state.loadingMore) -> item { PanelNote("Looking for comments with timestamps…") }
            shown.isEmpty() && onlyTimes -> item { PanelNote("No comments point to a moment in this video.") }
            shown.isEmpty() -> item { PanelNote("No comments yet.") }
        }
        if (!state.loading) items(shown, key = { it.id }) { c ->
            Column {
                CommentRow(c, links, channelAvatar, reply = false)
                if (c.repliesCursor != null) RepliesLink(c, channelName, channelAvatar) { onReplies(c) }
            }
        }
        if (state.loadingMore) item { PanelSpinner() }
    }
    PrefetchEffect(listState, state.items.size, onLoad = state::loadMore)
}

@Composable
private fun RepliesLink(c: Comment, channelName: String, channelAvatar: String?, onClick: () -> Unit) {
    val blue = linkColor()
    Row(
        Modifier.padding(start = 52.dp, bottom = 8.dp).clip(CircleShape).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (c.creatorReplyAvatar != null) {
            CommentAvatar(channelName, 20, c.creatorReplyAvatar ?: channelAvatar)
            Text(" · ", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(c.replyText ?: "Replies", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = blue)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = blue)
    }
}

/** One thread: the comment on top, its replies under it along a thread line. */
@Composable
private fun ReplyThread(state: CommentsState, parent: Comment, links: LinkActions, channelName: String, channelAvatar: String?) {
    LaunchedEffect(parent.id) { if (!state.replies.containsKey(parent.id)) state.loadReplies(parent) }
    val replies = state.replies[parent.id].orEmpty()
    val busy = state.replyLoading[parent.id] == true
    val listState = rememberLazyListState()
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { CommentRow(parent, links, channelAvatar, reply = false, startExpanded = true) }
        items(replies, key = { "r" + it.id }) { r ->
            Box(Modifier.drawBehind {
                val x = 32.dp.toPx()
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            }) { CommentRow(r, links, channelAvatar, reply = true) }
        }
        if (busy) items(if (replies.isEmpty()) 3 else 1) { CommentSkeleton(reply = true) }
        else if (!state.replies.containsKey(parent.id)) item { PanelNote("Couldn't load replies.") }
    }
    PrefetchEffect(listState, replies.size, ahead = 4) { if (state.replyNext[parent.id] != null) state.loadReplies(parent) }
}

/** The author's picture over a coloured letter (the letter shows until the picture loads). */
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
private fun CommentRow(c: Comment, links: LinkActions, channelAvatar: String?, reply: Boolean, startExpanded: Boolean = false) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var expanded by remember(c.id) { mutableStateOf(startExpanded) }
    var cut by remember(c.id) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = if (reply) 52.dp else 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)) {
        CommentAvatar(c.author, if (reply) 24 else 36, c.avatar)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            c.pinnedBy?.let { pinned ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    Icon(Icons.Rounded.PushPin, null, Modifier.size(12.dp), tint = muted)
                    Spacer(Modifier.width(4.dp))
                    Text(pinned, style = MaterialTheme.typography.labelSmall, color = muted)
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
                            .padding(horizontal = 8.dp, vertical = 2.dp) else Modifier
                    )
                )
                if (c.verified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.CheckCircle, "Verified", Modifier.size(12.dp), tint = muted)
                }
                c.time?.let { Text("  ·  $it", style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 1) }
            }
            Spacer(Modifier.height(4.dp))
            // Long comments fold to six lines, with Read more, as on YouTube.
            LinkedText(
                c.text, html = c.html, links = c.links,
                onUrl = links.onUrl, onSeek = links.onSeek,
                maxLines = if (expanded) Int.MAX_VALUE else 6,
                onOverflow = { if (!expanded) cut = it },
                modifier = Modifier.clickable(enabled = cut || expanded) { expanded = !expanded }
            )
            if (cut || (expanded && !startExpanded)) {
                Text(
                    if (expanded) "Show less" else "Read more",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = muted,
                    modifier = Modifier.padding(top = 4.dp).clickable { expanded = !expanded }
                )
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ThumbUp, null, Modifier.size(16.dp), tint = muted)
                c.likesText?.let {
                    Spacer(Modifier.width(6.dp))
                    Text(it, style = MaterialTheme.typography.labelMedium, color = muted)
                }
                if (c.hearted) {
                    Spacer(Modifier.width(18.dp))
                    Box {
                        CommentAvatar("creator", 18, c.heartAvatar ?: channelAvatar)
                        Icon(
                            Icons.Rounded.Favorite, "Hearted by the creator",
                            Modifier.size(11.dp).align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp),
                            tint = Color(0xFFFF4E45)
                        )
                    }
                }
            }
        }
    }
}

/** Grey placeholder rows while comments load, instead of a lone spinner. */
@Composable
private fun CommentSkeleton(reply: Boolean = false) {
    val tone = MaterialTheme.colorScheme.surfaceVariant
    Row(Modifier.fillMaxWidth().padding(start = if (reply) 52.dp else 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp)) {
        Box(Modifier.size(if (reply) 24.dp else 36.dp).clip(CircleShape).background(tone))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(140.dp).height(10.dp).clip(CircleShape).background(tone))
            Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(tone))
            Box(Modifier.fillMaxWidth(0.6f).height(12.dp).clip(CircleShape).background(tone))
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
