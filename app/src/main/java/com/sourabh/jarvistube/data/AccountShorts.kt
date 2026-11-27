package com.sourabh.jarvistube.data

import com.sourabh.jarvistube.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject

/**
 * Shorts picked for the signed-in YouTube account, as the YouTube app shows them:
 *  1. the Shorts shelves in the account's home feed (with titles), then
 *  2. the account's own Shorts feed ("Shorts for you"), which goes on and on.
 * Signed out, or if the account can't be reached, the caller falls back to topic searches.
 */
class AccountShorts {
    private var homeStarted = false
    private var homeToken: String? = null
    private var homeDone = false
    private var seq: String? = null
    private var seqStarted = false
    private var seqDone = false

    val hasMore: Boolean get() = !homeDone || !seqDone

    /** The next batch (about 12 or more), newest picks first. */
    suspend fun next(): List<VideoItem> {
        val out = ArrayList<VideoItem>()
        // 1. Home feed shelves.
        var pages = 0
        while (!homeDone && out.size < 12 && pages < 3) {
            val json = if (!homeStarted) InnerTube.browse("FEwhat_to_watch")
            else InnerTube.browse(continuation = homeToken ?: break)
            homeStarted = true
            pages++
            out += LibraryParser.parseShorts(json).first
            homeToken = LibraryParser.parse(json).next
            if (homeToken == null) homeDone = true
        }
        if (homeToken == null) homeDone = true
        // 2. The account's Shorts feed.
        var reads = 0
        while (!seqDone && out.size < 12 && reads < 3) {
            if (!seqStarted) {
                seqStarted = true
                seq = runCatching { InnerTube.reelSeed() }.getOrNull()
            }
            val p = seq ?: run { seqDone = true; break }
            val (ids, next) = InnerTube.reelSequence(p)
            reads++
            seq = next
            if (next == null) seqDone = true
            out += titled(ids.filter { id -> out.none { it.url.endsWith(id) } })
        }
        return out.distinctBy { it.url }.let(ContentFilter::filter)
    }

    /** Titles and channels for bare ids, from YouTube's public oEmbed (no sign-in, no cookies). */
    private suspend fun titled(ids: List<String>): List<VideoItem> = coroutineScope {
        ids.map { id ->
            async(Dispatchers.IO) {
                runCatching {
                    val url = "https://www.youtube.com/oembed".toHttpUrl().newBuilder()
                        .addQueryParameter("url", "https://www.youtube.com/shorts/$id")
                        .addQueryParameter("format", "json").build()
                    val o = Graph.http.newCall(Request.Builder().url(url).build()).execute()
                        .use { r -> if (r.isSuccessful) r.body?.string()?.let(::JSONObject) else null }
                        ?: return@runCatching null
                    VideoItem(
                        url = "https://www.youtube.com/watch?v=$id",
                        title = o.optString("title").ifEmpty { return@runCatching null },
                        channel = o.optString("author_name"),
                        thumb = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                        durationSec = 0,
                        views = -1,
                        uploaded = null,
                        isLive = false,
                        isShort = true
                    )
                }.getOrNull()
            }
        }.awaitAll().filterNotNull()
    }
}

