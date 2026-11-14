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
import java.net.URLDecoder

/**
 * Reads comments straight from YouTube's own web API (the same calls youtube.com makes), because
 * NewPipe drops what the comments screen needs: profile pictures, Top/Newest, "Pinned by",
 * the creator's heart and exact link positions. Anything unexpected throws, and the caller falls
 * back to NewPipe.
 */
class YoutubeComments(private val videoId: String) : CommentsBackend {
    /** Continuation tokens for Top (0) and Newest (1), from the first response's sort menu. */
    private var sortTokens: List<String> = emptyList()

    override suspend fun first(sort: Int): CommentsPage = withContext(Dispatchers.IO) {
        val token = sortTokens.getOrNull(sort) ?: run {
            val watch = post(JSONObject().put("videoId", videoId))
            val section = findObject(watch) { it.optString("targetId") == "comments-section" && it.has("contents") }
                ?: return@withContext CommentsPage(-1, null, disabled = true, items = emptyList(), next = null)
            if (findObject(section) { it.has("messageRenderer") } != null && findObject(section) { it.has("continuationCommand") } == null) {
                return@withContext CommentsPage(-1, null, disabled = true, items = emptyList(), next = null)
            }
            findObject(section) { it.has("continuationCommand") }!!.getJSONObject("continuationCommand").getString("token")
        }
        val r = post(JSONObject().put("continuation", token))
        val header = findObject(r) { it.has("commentsHeaderRenderer") }?.getJSONObject("commentsHeaderRenderer")
        if (header != null && sortTokens.isEmpty()) {
            val menu = findObject(header) { it.has("subMenuItems") }?.optJSONArray("subMenuItems")
            if (menu != null) sortTokens = (0 until menu.length()).mapNotNull { i ->
                menu.getJSONObject(i).optJSONObject("serviceEndpoint")?.optJSONObject("continuationCommand")?.optString("token")
            }
        }
        val countText = header?.optJSONObject("commentsCount")?.let(::runsText)
        val count = header?.optJSONObject("countText")?.let(::runsText)?.filter(Char::isDigit)?.toIntOrNull() ?: -1
        val (items, next) = parse(r)
        CommentsPage(count, countText, disabled = false, items = items, next = next, sorts = if (sortTokens.size >= 2) listOf("Top", "Newest") else emptyList())
    }

    override suspend fun more(cursor: Any): Pair<List<Comment>, Any?> = withContext(Dispatchers.IO) {
        parse(post(JSONObject().put("continuation", cursor as String)))
    }

    private fun post(body: JSONObject): JSONObject {
        val loc = AppSettings.localization()
        body.put("context", JSONObject().put("client", JSONObject()
            .put("clientName", "WEB")
            .put("clientVersion", YoutubeParsingHelper.getClientVersion())
            .put("hl", loc.languageCode)
            .put("gl", AppSettings.region)))
        val req = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/next?prettyPrint=false")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36")
            .header("Origin", "https://www.youtube.com")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        Graph.http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw java.io.IOException("YouTube comments: HTTP ${res.code}")
            return JSONObject(res.body!!.string())
        }
    }

    /** Comments (threads or replies) and the token for the next page, from one response. */
    private fun parse(r: JSONObject): Pair<List<Comment>, String?> {
        val entities = HashMap<String, JSONObject>()
        r.optJSONObject("frameworkUpdates")?.optJSONObject("entityBatchUpdate")?.optJSONArray("mutations")?.let { m ->
            for (i in 0 until m.length()) {
                val mut = m.getJSONObject(i)
                val payload = mut.optJSONObject("payload") ?: continue
                val inner = payload.keys().asSequence().firstOrNull()?.let(payload::optJSONObject) ?: continue
                entities[mut.optString("entityKey")] = inner
            }
        }
        val items = ArrayList<Comment>()
        var next: String? = null
        val endpoints = r.optJSONArray("onResponseReceivedEndpoints") ?: JSONArray()
        for (e in 0 until endpoints.length()) {
            val ep = endpoints.getJSONObject(e)
            val list = (ep.optJSONObject("reloadContinuationItemsCommand") ?: ep.optJSONObject("appendContinuationItemsAction"))
                ?.optJSONArray("continuationItems") ?: continue
            for (i in 0 until list.length()) {
                val it = list.getJSONObject(i)
                when {
                    it.has("commentThreadRenderer") -> {
                        val t = it.getJSONObject("commentThreadRenderer")
                        val vm = t.optJSONObject("commentViewModel")?.optJSONObject("commentViewModel") ?: continue
                        val replies = t.optJSONObject("replies")?.optJSONObject("commentRepliesRenderer")
                        val replyToken = replies?.let { rr -> findObject(rr) { o -> o.has("continuationCommand") } }
                            ?.getJSONObject("continuationCommand")?.optString("token")
                        val creatorReply = replies?.optJSONObject("viewRepliesCreatorThumbnail")?.let(::firstThumb)
                        toComment(vm, entities, replyToken, creatorReply)?.let(items::add)
                    }
                    it.has("commentViewModel") -> {
                        val o = it.getJSONObject("commentViewModel")
                        toComment(o.optJSONObject("commentViewModel") ?: o, entities, null, null)?.let(items::add)
                    }
                    it.has("continuationItemRenderer") -> {
                        next = findObject(it) { o -> o.has("continuationCommand") }?.getJSONObject("continuationCommand")?.optString("token")
                    }
                }
            }
        }
        return items to next
    }

    private fun toComment(vm: JSONObject, entities: Map<String, JSONObject>, replyToken: String?, creatorReply: String?): Comment? {
        val c = entities[vm.optString("commentKey")] ?: return null
        val props = c.optJSONObject("properties") ?: return null
        val author = c.optJSONObject("author") ?: JSONObject()
        val toolbar = c.optJSONObject("toolbar") ?: JSONObject()
        val state = entities[vm.optString("toolbarStateKey")]
        val content = props.optJSONObject("content") ?: JSONObject()
        val text = content.optString("content")
        val links = linksOf(text, content.optJSONArray("commandRuns"))
        val replies = toolbar.optString("replyCount").takeIf { it.isNotBlank() && it != "0" }
        return Comment(
            id = props.optString("commentId").ifEmpty { vm.optString("commentKey") },
            author = author.optString("displayName"),
            avatar = author.optString("avatarThumbnailUrl").ifBlank { null },
            time = props.optString("publishedTime").ifBlank { null },
            likesText = toolbar.optString("likeCountNotliked").takeIf { it.isNotBlank() && it != "0" },
            text = text,
            html = false,
            links = links,
            pinnedBy = vm.optString("pinnedText").ifBlank { null },
            hearted = state?.optString("heartState") == "TOOLBAR_HEART_STATE_HEARTED",
            heartAvatar = toolbar.optString("creatorThumbnailUrl").ifBlank { null },
            replyText = if (replyToken != null) replies?.let { if (it == "1") "1 reply" else "$it replies" } ?: "Replies" else null,
            repliesCursor = replyToken,
            byCreator = author.optBoolean("isCreator"),
            verified = author.optBoolean("isVerified") || author.optBoolean("isArtist"),
            creatorReplyAvatar = creatorReply,
            hasTimestamp = links.any { it.seconds != null }
        )
    }

    /** Link positions YouTube marked in the text: times in this video seek, other links open. */
    private fun linksOf(text: String, runs: JSONArray?): List<TextLink> {
        if (runs == null) return emptyList()
        return (0 until runs.length()).mapNotNull { i ->
            val run = runs.getJSONObject(i)
            val start = run.optInt("startIndex", -1)
            val end = start + run.optInt("length", 0)
            if (start < 0 || end > text.length || end <= start) return@mapNotNull null
            val cmd = run.optJSONObject("onTap")?.optJSONObject("innertubeCommand") ?: return@mapNotNull null
            val watch = cmd.optJSONObject("watchEndpoint")
            when {
                watch != null && watch.optString("videoId") == videoId && watch.has("startTimeSeconds") ->
                    TextLink(start, end, seconds = watch.optLong("startTimeSeconds"))
                watch != null -> TextLink(start, end, url = "https://www.youtube.com/watch?v=" + watch.optString("videoId") +
                    (if (watch.has("startTimeSeconds")) "&t=" + watch.optLong("startTimeSeconds") + "s" else ""))
                else -> {
                    val raw = cmd.optJSONObject("urlEndpoint")?.optString("url")
                        ?: cmd.optJSONObject("commandMetadata")?.optJSONObject("webCommandMetadata")?.optString("url")
                        ?: return@mapNotNull null
                    TextLink(start, end, url = cleanUrl(raw))
                }
            }
        }.sortedBy { it.start }
    }

    companion object {
        /** YouTube wraps outside links in /redirect?q=…; relative links are on youtube.com. */
        fun cleanUrl(raw: String): String {
            val abs = if (raw.startsWith("/")) "https://www.youtube.com$raw" else raw
            if (!abs.contains("youtube.com/redirect")) return abs
            val q = abs.substringAfter("?").split('&').firstOrNull { it.startsWith("q=") }?.substringAfter("q=")
            return q?.let { URLDecoder.decode(it, "UTF-8") } ?: abs
        }

        private fun runsText(o: JSONObject): String? {
            val runs = o.optJSONArray("runs") ?: return o.optString("simpleText").ifBlank { null }
            return (0 until runs.length()).joinToString("") { runs.getJSONObject(it).optString("text") }.ifBlank { null }
        }

        private fun firstThumb(o: JSONObject): String? =
            o.optJSONArray("thumbnails")?.optJSONObject(0)?.optString("url")?.ifBlank { null }

        /** Depth-first search for the first object matching [test]. */
        fun findObject(root: Any?, test: (JSONObject) -> Boolean): JSONObject? {
            when (root) {
                is JSONObject -> {
                    if (test(root)) return root
                    for (k in root.keys()) findObject(root.opt(k), test)?.let { return it }
                }
                is JSONArray -> for (i in 0 until root.length()) findObject(root.opt(i), test)?.let { return it }
            }
            return null
        }
    }
}
