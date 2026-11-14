package com.sourabh.jarvistube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description

/** A link inside comment text: [url] opens, [seconds] jumps within the video. */
data class TextLink(val start: Int, val end: Int, val url: String? = null, val seconds: Long? = null)

/** One comment or reply, ready to show. Read-only: PlayBook never posts or likes. */
data class Comment(
    val id: String,
    val author: String,
    val avatar: String?,
    val time: String?,
    /** As YouTube writes it ("4.8M"); null when there are none. */
    val likesText: String?,
    val text: String,
    val html: Boolean,
    val links: List<TextLink> = emptyList(),
    /** "Pinned by @channel", or null. */
    val pinnedBy: String? = null,
    val hearted: Boolean = false,
    /** The creator's picture shown with the heart. */
    val heartAvatar: String? = null,
    /** "12 replies"; null when there are none. */
    val replyText: String? = null,
    /** Where the replies start (backend-specific); null when there are none. */
    val repliesCursor: Any? = null,
    /** Written by the video's own channel. */
    val byCreator: Boolean = false,
    val verified: Boolean = false,
    /** The creator's picture when the creator replied in this thread. */
    val creatorReplyAvatar: String? = null,
    /** Mentions a moment of this video (for the Timestamps filter). */
    val hasTimestamp: Boolean = false
)

/** The first page of a video's comments. [sorts] are the orders on offer ("Top", "Newest"), if any. */
data class CommentsPage(
    val count: Int,
    val countText: String?,
    val disabled: Boolean,
    val items: List<Comment>,
    val next: Any?,
    val sorts: List<String> = emptyList()
)

/** Where comments come from: YouTube directly, or NewPipe as the fallback. */
interface CommentsBackend {
    /** [sort] is an index into [CommentsPage.sorts] (0 = Top). */
    suspend fun first(sort: Int): CommentsPage

    /** The next page of comments, or of one comment's replies. */
    suspend fun more(cursor: Any): Pair<List<Comment>, Any?>
}

/** Comments through NewPipe's CommentsInfo, always off the main thread. No sorting, no pictures. */
class NewPipeComments(private val videoUrl: String) : CommentsBackend {
    private var info: CommentsInfo? = null

    override suspend fun first(sort: Int): CommentsPage = withContext(Dispatchers.IO) {
        val i = CommentsInfo.getInfo(videoUrl)
        info = i
        CommentsPage(i.commentsCount, null, i.isCommentsDisabled, i.relatedItems.map(::toComment), i.nextPage?.takeIf { Page.isValid(it) })
    }

    override suspend fun more(cursor: Any): Pair<List<Comment>, Any?> = withContext(Dispatchers.IO) {
        val i = info ?: CommentsInfo.getInfo(videoUrl).also { info = it }
        val got = CommentsInfo.getMoreItems(i, cursor as Page)
        got.items.map(::toComment) to got.nextPage?.takeIf { Page.isValid(it) }
    }

    private fun toComment(c: CommentsInfoItem): Comment {
        val replies = c.replies?.takeIf { Page.isValid(it) }
        val text = c.commentText?.content.orEmpty()
        return Comment(
            id = c.commentId ?: c.url ?: c.hashCode().toString(),
            author = c.uploaderName.orEmpty(),
            avatar = runCatching { c.uploaderAvatars.bestUrl() }.getOrNull()?.let { if (it.startsWith("//")) "https:$it" else it },
            time = c.textualUploadDate,
            likesText = c.likeCount.takeIf { it > 0 }?.let { com.sourabh.jarvistube.ui.common.compact(it.toLong()) },
            text = text,
            html = c.commentText?.type == Description.HTML,
            pinnedBy = if (c.isPinned) "Pinned" else null,
            hearted = c.isHeartedByUploader,
            replyText = if (replies == null) null else when {
                c.replyCount == 1 -> "1 reply"
                c.replyCount > 1 -> "${com.sourabh.jarvistube.ui.common.compact(c.replyCount.toLong())} replies"
                else -> "Replies"
            },
            repliesCursor = replies,
            byCreator = runCatching { c.isChannelOwner }.getOrDefault(false),
            hasTimestamp = Regex("""(?<![\w:])\d{1,2}:\d{2}(?![\w:])""").containsMatchIn(text)
        )
    }
}
