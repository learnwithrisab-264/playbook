package com.sourabh.jarvistube.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.data.Comment
import com.sourabh.jarvistube.data.CommentsBackend
import com.sourabh.jarvistube.data.NewPipeComments
import com.sourabh.jarvistube.data.YoutubeComments
import com.sourabh.jarvistube.ui.common.compact
import com.sourabh.jarvistube.ui.theme.Corners
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A video's comments: the first page, more pages ahead of the scroll, replies per thread, Top or
 * Newest. Kept per video (the last few), so reopening or coming back from fullscreen is instant.
 */
@Stable
internal class CommentsState private constructor(private val videoUrl: String) {
    private var backend: CommentsBackend = youtubeId(videoUrl)?.let(::YoutubeComments) ?: NewPipeComments(videoUrl)
    var count by mutableIntStateOf(-1); private set
    /** YouTube's own short count ("2.4M"), when it gives one. */
    var countText by mutableStateOf<String?>(null); private set
    var disabled by mutableStateOf(false); private set
    var failed by mutableStateOf(false); private set
    var loading by mutableStateOf(false); private set
    var loadingMore by mutableStateOf(false); private set
    var items by mutableStateOf(emptyList<Comment>()); private set
    /** Orders on offer ("Top", "Newest"); empty when the source can't sort. */
    var sorts by mutableStateOf(emptyList<String>()); private set
    var sort by mutableIntStateOf(0); private set
    var started = false; private set
    private var next: Any? = null
    private var job: Job? = null

    /** Replies shown under a comment (by comment id), where the next page of them is, and which are loading. */
    val replies = mutableStateMapOf<String, List<Comment>>()
    val replyNext = mutableStateMapOf<String, Any?>()
    val replyLoading = mutableStateMapOf<String, Boolean>()

    val shortCount: String? get() = countText ?: count.takeIf { it > 0 }?.let { compact(it.toLong()) }
    val hasMore: Boolean get() = next != null

    fun load() {
        started = true
        job?.cancel()
        loading = true
        failed = false
        job = scope.launch {
            val got = runCatching { backend.first(sort) }.recoverCatching { e ->
                if (e is kotlinx.coroutines.CancellationException || backend is NewPipeComments) throw e
                // YouTube changed something: NewPipe still works, without pictures or sorting.
                backend = NewPipeComments(videoUrl)
                backend.first(0)
            }
            got.onSuccess { p ->
                count = p.count; countText = p.countText; disabled = p.disabled
                items = p.items; next = p.next; sorts = p.sorts
            }.onFailure { if (it !is kotlinx.coroutines.CancellationException) failed = true }
            loading = false
        }
    }

    /** Switches between Top and Newest, starting again from the first page. */
    fun setSortOrder(i: Int) {
        if (i == sort || i !in sorts.indices) return
        sort = i
        items = emptyList()
        next = null
        load()
    }

    fun loadMore() {
        val page = next ?: return
        if (loading || loadingMore) return
        loadingMore = true
        scope.launch {
            runCatching { backend.more(page) }.onSuccess { (more, nextPage) ->
                items = (items + more).distinctBy { it.id }
                next = nextPage
            }
            loadingMore = false
        }
    }

    fun loadReplies(c: Comment) {
        val page = if (replies.containsKey(c.id)) replyNext[c.id] else c.repliesCursor
        if (page == null || replyLoading[c.id] == true) return
        replyLoading[c.id] = true
        scope.launch {
            runCatching { backend.more(page) }.onSuccess { (more, nextPage) ->
                replies[c.id] = (replies[c.id].orEmpty() + more).distinctBy { it.id }
                replyNext[c.id] = nextPage
            }
            replyLoading[c.id] = false
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val cache = object : LinkedHashMap<String, CommentsState>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CommentsState>) = size > 6
        }

        fun of(videoUrl: String): CommentsState = cache.getOrPut(videoUrl) { CommentsState(videoUrl) }

        private fun youtubeId(url: String): String? = runCatching {
            org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory.getInstance().getId(url)
        }.getOrNull()
    }
}

/** Starts loading a moment after the video, so the video gets the network first. */
@Composable
internal fun rememberComments(videoUrl: String): CommentsState {
    val state = CommentsState.of(videoUrl)
    LaunchedEffect(state) {
        if (!state.started) {
            delay(1500)
            if (!state.started) state.load()
        }
    }
    return state
}

/** The card under the description: the count and the first comment; a tap opens them all. */
@Composable
internal fun CommentsCard(state: CommentsState, onOpen: () -> Unit) {
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
            state.shortCount?.let {
                Spacer(Modifier.width(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            state.loading || !state.started -> CardNote("Loading comments…")
            state.disabled -> CardNote("Comments are turned off for this video.")
            state.failed -> CardNote("Couldn't load comments. Tap to try again.")
            first == null -> CardNote("No comments yet.")
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                CommentAvatar(first.author, 24, first.avatar)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (first.html) androidx.core.text.HtmlCompat.fromHtml(first.text, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
                    else first.text,
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
