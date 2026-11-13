package com.sourabh.jarvistube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description

/** One comment or reply, ready to show. Read-only: PlayBook never posts or likes. */
data class Comment(
    val id: String,
    val author: String,
    val avatar: String?,
    val time: String?,
    val likes: Int,
    val text: String,
    val html: Boolean,
    val pinned: Boolean,
    val hearted: Boolean,
    val replyCount: Int,
    val replies: Page?,
    /** Written by the video's own channel (shown as a highlighted name). */
    val byCreator: Boolean = false,
    /** The video's channel replied in this thread. */
    val creatorReplied: Boolean = false
)

/** The first page of a video's comments. [count] is -1 when YouTube doesn't say. */
data class CommentsPage(val count: Int, val disabled: Boolean, val items: List<Comment>, val next: Page?)

/** Comments through NewPipe's CommentsInfo, always off the main thread. */
class CommentsSource(private val videoUrl: String) {
    private var info: CommentsInfo? = null

    suspend fun first(): CommentsPage = withContext(Dispatchers.IO) {
        val i = CommentsInfo.getInfo(videoUrl)
        info = i
        CommentsPage(i.commentsCount, i.isCommentsDisabled, i.relatedItems.map(::toComment), i.nextPage?.takeIf { Page.isValid(it) })
    }

    /** The next page of comments, or of one comment's replies. */
    suspend fun more(page: Page): Pair<List<Comment>, Page?> = withContext(Dispatchers.IO) {
        val i = info ?: CommentsInfo.getInfo(videoUrl).also { info = it }
        val got = CommentsInfo.getMoreItems(i, page)
        got.items.map(::toComment) to got.nextPage?.takeIf { Page.isValid(it) }
    }

    private fun toComment(c: CommentsInfoItem) = Comment(
        id = c.commentId ?: c.url ?: c.hashCode().toString(),
        author = c.uploaderName.orEmpty(),
        avatar = runCatching { c.uploaderAvatars.bestUrl() }.getOrNull()
            ?.let { if (it.startsWith("//")) "https:$it" else it },
        time = c.textualUploadDate,
        likes = c.likeCount,
        text = c.commentText?.content.orEmpty(),
        html = c.commentText?.type == Description.HTML,
        pinned = c.isPinned,
        hearted = c.isHeartedByUploader,
        replyCount = c.replyCount,
        replies = c.replies?.takeIf { Page.isValid(it) },
        byCreator = runCatching { c.isChannelOwner }.getOrDefault(false),
        creatorReplied = runCatching { c.hasCreatorReply() }.getOrDefault(false)
    )
}
