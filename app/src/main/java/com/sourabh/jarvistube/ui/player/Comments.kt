package com.sourabh.jarvistube.ui.player

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.data.Comment
import com.sourabh.jarvistube.data.CommentsSource
import com.sourabh.jarvistube.ui.common.Avatar
import com.sourabh.jarvistube.ui.common.LinkedText
import com.sourabh.jarvistube.ui.common.LoadMoreEffect
import com.sourabh.jarvistube.ui.common.compact
import com.sourabh.jarvistube.ui.theme.Corners
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

/** A video's comments: the first page, more pages on scroll, and replies per comment. */
@Stable
internal class CommentsState(videoUrl: String, private val scope: CoroutineScope) {
    private val source = CommentsSource(videoUrl)
    var count by mutableIntStateOf(-1); private set
    var disabled by mutableStateOf(false); private set
    var failed by mutableStateOf(false); private set
    var loading by mutableStateOf(true); private set
    var loadingMore by mutableStateOf(false); private set
    var items by mutableStateOf(emptyList<Comment>()); private set
    private var next: Page? = null
    private var job: Job? = null

    /** Replies shown under a comment (by comment id), where the next page of them is, and which are loading. */
    val replies = mutableStateMapOf<String, List<Comment>>()
    val replyNext = mutableStateMapOf<String, Page?>()
    val replyLoading = mutableStateMapOf<String, Boolean>()

    fun load() {
        job?.cancel()
        loading = true
        failed = false
        job = scope.launch {
            runCatching { source.first() }
                .onSuccess { p ->
                    count = p.count; disabled = p.disabled; items = p.items; next = p.next
                }
                .onFailure { if (it !is kotlinx.coroutines.CancellationException) failed = true }
            loading = false
        }
    }

    fun loadMore() {
        val page = next ?: return
        if (loading || loadingMore) return
        loadingMore = true
        scope.launch {
            runCatching { source.more(page) }.onSuccess { (more, nextPage) ->
                items = (items + more).distinctBy { it.id }
                next = nextPage
            }
            loadingMore = false
        }
    }

    fun loadReplies(c: Comment) {
        val page = if (replies.containsKey(c.id)) replyNext[c.id] else c.replies
        if (page == null || replyLoading[c.id] == true) return
        replyLoading[c.id] = true
        scope.launch {
            runCatching { source.more(page) }.onSuccess { (more, nextPage) ->
                replies[c.id] = (replies[c.id].orEmpty() + more).distinctBy { it.id }
                replyNext[c.id] = nextPage
            }
            replyLoading[c.id] = false
        }
    }

    fun hideReplies(c: Comment) {
        replies.remove(c.id)
        replyNext.remove(c.id)
    }
}

/** Starts loading a moment after the video, so the video gets the network first. */
@Composable
internal fun rememberComments(videoUrl: String): CommentsState {
    val scope = rememberCoroutineScope()
    val state = remember(videoUrl) { CommentsState(videoUrl, scope) }
    LaunchedEffect(state) {
        delay(1500)
        state.load()
    }
    return state
}

/** The card under the description: the count and the first comment; a tap opens them all. */
@Composable
internal fun CommentsCard(state: CommentsState, links: LinkActions, onOpen: () -> Unit) {
    val first = state.items.firstOrNull()
    Column(
        Modifier.fillMaxWidth()
            .clip(Corners.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = !state.disabled) { if (state.failed) state.load() else onOpen() }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Comments", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (state.count > 0) {
                Spacer(Modifier.width(8.dp))
                Text(compact(state.count.toLong()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            state.loading -> CardNote("Loading comments…")
            state.disabled -> CardNote("Comments are turned off for this video.")
            state.failed -> CardNote("Couldn't load comments. Tap to try again.")
            first == null -> CardNote("No comments yet.")
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(first.author.removePrefix("@"), 24, first.avatar)
                Spacer(Modifier.width(8.dp))
                // Plain preview here; links work once the comments are open.
                Text(
                    androidx.core.text.HtmlCompat.fromHtml(first.text, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CardNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommentsSheet(state: CommentsState, links: LinkActions, onDismiss: () -> Unit) {
    val listState = rememberLazyListState()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            if (state.count > 0) "Comments  ${compact(state.count.toLong())}" else "Comments",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyColumn(state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
            when {
                state.loading -> item { SheetSpinner() }
                state.disabled -> item { SheetNote("Comments are turned off for this video.") }
                state.failed -> item { SheetNote("Couldn't load comments.") }
                state.items.isEmpty() -> item { SheetNote("No comments yet.") }
            }
            items(state.items, key = { it.id }) { c ->
                Column {
                    CommentRow(c, links, reply = false)
                    val shown = state.replies[c.id]
                    shown?.forEach { r -> CommentRow(r, links, reply = true) }
                    val busy = state.replyLoading[c.id] == true
                    val moreReplies = if (shown == null) c.replies != null else state.replyNext[c.id] != null
                    when {
                        busy -> Box(Modifier.padding(start = 56.dp, bottom = 8.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                        moreReplies -> TextButton(onClick = { state.loadReplies(c) }, modifier = Modifier.padding(start = 44.dp)) {
                            Text(
                                when {
                                    shown != null -> "Show more replies"
                                    c.replyCount > 0 -> "Show ${c.replyCount} " + if (c.replyCount == 1) "reply" else "replies"
                                    else -> "Show replies"
                                }
                            )
                        }
                        shown != null -> TextButton(onClick = { state.hideReplies(c) }, modifier = Modifier.padding(start = 44.dp)) {
                            Text("Hide replies")
                        }
                    }
                }
            }
            if (state.loadingMore) item { SheetSpinner() }
        }
        LoadMoreEffect(listState, state.items.size, state::loadMore)
    }
}

@Composable
private fun CommentRow(c: Comment, links: LinkActions, reply: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(start = if (reply) 56.dp else 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Avatar(c.author.removePrefix("@"), if (reply) 24 else 32, c.avatar)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (c.pinned) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PushPin, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("Pinned", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                listOfNotNull(c.author.ifBlank { null }, c.time).joinToString("  ·  "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            LinkedText(c.text, html = c.html, onUrl = links.onUrl, onSeek = links.onSeek)
            Row(
                Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Rounded.ThumbUp, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (c.likes > 0) {
                    Text(compact(c.likes.toLong()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (c.hearted) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Favorite, "Hearted by the creator", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun SheetSpinner() {
    Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}

@Composable
private fun SheetNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp)
    )
}
