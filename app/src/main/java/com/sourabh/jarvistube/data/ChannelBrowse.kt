package com.sourabh.jarvistube.data

import com.sourabh.jarvistube.AppSettings
import com.sourabh.jarvistube.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

/** A tab of a channel as YouTube lists it (Home, Videos, Shorts, Live, Podcasts, Playlists, Posts). */
data class ChannelTabRef(val title: String, val params: String)

/** The channel header as YouTube shows it. */
data class ChannelHeaderInfo(
    val name: String,
    val handle: String?,
    val subscribers: String?,
    val videos: String?,
    val description: String,
    val linkText: String?,
    val linkUrl: String?,
    val banner: String?,
    val avatar: String?,
    val tabs: List<ChannelTabRef>
)

/** A community post. */
data class ChannelPost(val id: String, val text: String, val time: String?, val likes: String?, val images: List<String>)

/** One page of a tab: videos/playlists, Shorts, posts, its sort chips, and where the next page is. */
data class ChannelTabPage(
    val items: List<LibItem> = emptyList(),
    val shorts: List<VideoItem> = emptyList(),
    val posts: List<ChannelPost> = emptyList(),
    val chips: List<Pair<String, String>> = emptyList(),
    val next: String? = null
)

/**
 * Channel pages straight from YouTube's web API, signed out (no cookies, no account history):
 * the full header (@handle, video count, links), every tab YouTube shows, the Latest / Popular /
 * Oldest sort of the Videos tab, and community posts, none of which NewPipe exposes.
 */
object ChannelBrowse {
    private fun post(body: JSONObject): JSONObject {
        body.put("context", JSONObject().put("client", JSONObject()
            .put("clientName", "WEB")
            .put("clientVersion", YoutubeParsingHelper.getClientVersion())
            .put("hl", "en")
            .put("gl", AppSettings.region)))
        val req = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        Graph.http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw java.io.IOException("Channel: HTTP ${res.code}")
            return JSONObject(res.body!!.string())
        }
    }

    suspend fun header(channelId: String): Pair<ChannelHeaderInfo, ChannelTabPage> = withContext(Dispatchers.IO) {
        val r = post(JSONObject().put("browseId", channelId))
        val h = YoutubeComments.findObject(r) { it.has("pageHeaderViewModel") }?.optJSONObject("pageHeaderViewModel")
        val rows = h?.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")
        val parts = ArrayList<String>()
        if (rows != null) for (i in 0 until rows.length()) {
            val ps = rows.optJSONObject(i)?.optJSONArray("metadataParts") ?: continue
            for (j in 0 until ps.length()) ps.optJSONObject(j)?.optJSONObject("text")?.optString("content")?.takeIf { it.isNotBlank() }?.let(parts::add)
        }
        val attribution = h?.optJSONObject("attribution")?.optJSONObject("attributionViewModel")
        val linkText = listOfNotNull(
            attribution?.optJSONObject("text")?.optString("content"),
            attribution?.optJSONObject("suffix")?.optString("content")
        ).joinToString(" ") { it.trim() }.trim().ifBlank { null }
        val linkUrl = attribution?.let { a ->
            YoutubeComments.findObject(a) { it.has("webCommandMetadata") }?.optJSONObject("webCommandMetadata")?.optString("url")
        }?.let(YoutubeComments::cleanUrl)
        val tabs = ArrayList<ChannelTabRef>()
        collect(r, "tabRenderer").forEach { t ->
            val title = t.optString("title")
            val params = t.optJSONObject("endpoint")?.optJSONObject("browseEndpoint")?.optString("params")
            if (title.isNotBlank() && !params.isNullOrBlank()) tabs += ChannelTabRef(title, java.net.URLDecoder.decode(params, "UTF-8"))
        }
        val info = ChannelHeaderInfo(
            name = h?.optJSONObject("title")?.optJSONObject("dynamicTextViewModel")?.optJSONObject("text")?.optString("content").orEmpty(),
            handle = parts.firstOrNull { it.startsWith("@") },
            subscribers = parts.firstOrNull { it.contains("subscriber", true) },
            videos = parts.firstOrNull { it.contains("video", true) },
            description = h?.optJSONObject("description")?.optJSONObject("descriptionPreviewViewModel")
                ?.optJSONObject("description")?.optString("content").orEmpty().trim(),
            linkText = linkText,
            linkUrl = linkUrl,
            banner = lastSource(h?.optJSONObject("banner")),
            avatar = lastSource(h?.optJSONObject("image")),
            tabs = tabs
        )
        info to page(r)
    }

    /** First page of a tab (by its params). */
    suspend fun tab(channelId: String, params: String): ChannelTabPage = withContext(Dispatchers.IO) {
        page(post(JSONObject().put("browseId", channelId).put("params", params)))
    }

    /** Next page, or a sort chip's list (Latest / Popular / Oldest). */
    suspend fun more(token: String): ChannelTabPage = withContext(Dispatchers.IO) {
        page(post(JSONObject().put("continuation", token)))
    }

    private fun page(r: JSONObject): ChannelTabPage {
        val chips = collect(r, "chipViewModel").mapNotNull { c ->
            val label = c.optString("text").ifBlank { return@mapNotNull null }
            val token = YoutubeComments.findObject(c) { it.has("continuationCommand") }
                ?.optJSONObject("continuationCommand")?.optString("token") ?: return@mapNotNull null
            label to token
        }
        // The next-page token sits in the last continuationItemRenderer, not in the chips.
        val next = collect(r, "continuationItemRenderer").lastOrNull()?.let { c ->
            YoutubeComments.findObject(c) { it.has("continuationCommand") }?.optJSONObject("continuationCommand")?.optString("token")
        }
        val parsed = LibraryParser.parse(r)
        val shorts = LibraryParser.parseShorts(r).first
        val posts = collect(r, "backstagePostRenderer").mapNotNull(::postOf)
        return ChannelTabPage(parsed.items, shorts, posts, chips, next)
    }

    private fun postOf(p: JSONObject): ChannelPost? {
        val id = p.optString("postId").ifBlank { return null }
        val text = p.optJSONObject("contentText")?.optJSONArray("runs")?.let { runs ->
            (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }
        }.orEmpty()
        val images = ArrayList<String>()
        p.optJSONObject("backstageAttachment")?.let { a ->
            collect(a, "backstageImageRenderer").forEach { img -> lastThumb(img.optJSONObject("image"))?.let(images::add) }
        }
        return ChannelPost(
            id = id,
            text = text,
            time = runsOrSimple(p.optJSONObject("publishedTimeText")),
            likes = runsOrSimple(p.optJSONObject("voteCount"))?.takeIf { it != "0" },
            images = images
        )
    }

    private fun runsOrSimple(o: JSONObject?): String? {
        o ?: return null
        o.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
        val runs = o.optJSONArray("runs") ?: return null
        return (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }.ifBlank { null }
    }

    private fun lastThumb(o: JSONObject?): String? {
        val t = o?.optJSONArray("thumbnails") ?: return null
        return t.optJSONObject(t.length() - 1)?.optString("url")?.let { if (it.startsWith("//")) "https:$it" else it }
    }

    private fun lastSource(o: JSONObject?): String? {
        val src = YoutubeComments.findObject(o) { it.has("sources") }?.optJSONArray("sources") ?: return null
        return src.optJSONObject(src.length() - 1)?.optString("url")?.let { if (it.startsWith("//")) "https:$it" else it }
    }

    /** Every object under [key], anywhere in the tree, in order. */
    private fun collect(root: Any?, key: String): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        fun walk(n: Any?) {
            when (n) {
                is JSONObject -> for (k in n.keys()) { val v = n.opt(k); if (k == key && v is JSONObject) out += v else walk(v) }
                is JSONArray -> for (i in 0 until n.length()) walk(n.opt(i))
            }
        }
        walk(root)
        return out
    }
}
