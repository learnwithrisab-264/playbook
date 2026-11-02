package com.sourabh.jarvistube.data

import org.json.JSONArray
import org.json.JSONObject

sealed interface LibItem {
    val key: String
}

/** [feedbackToken] removes it from watch history; [setVideoId] identifies it inside a playlist. */
data class LibVideo(val video: VideoItem, val feedbackToken: String? = null, val setVideoId: String? = null) : LibItem {
    override val key get() = "v:" + video.url
}

data class LibPlaylist(val id: String, val title: String, val sub: String, val thumb: String?) : LibItem {
    override val key get() = "p:$id"
}

data class LibChannel(val id: String, val name: String, val sub: String, val thumb: String?) : LibItem {
    override val key get() = "c:$id"
}

class ParsedPage(val items: List<LibItem>, val next: String?)

/**
 * Walks an InnerTube response and pulls out videos, playlists and channels.
 * Deliberately schema-agnostic: YouTube moves things around (old *Renderer objects vs new
 * lockupViewModel), so we look for the known item shapes anywhere in the tree.
 */
object LibraryParser {

    fun parse(root: JSONObject): ParsedPage {
        val items = ArrayList<LibItem>()
        var token: String? = null
        fun walk(node: Any?) {
            when (node) {
                is JSONObject -> {
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = node.opt(k)
                        when (k) {
                            "videoRenderer", "gridVideoRenderer", "compactVideoRenderer", "playlistVideoRenderer" ->
                                (v as? JSONObject)?.let(::video)?.let(items::add)
                            "lockupViewModel" -> (v as? JSONObject)?.let(::lockup)?.let(items::add)
                            "gridPlaylistRenderer", "playlistRenderer" ->
                                (v as? JSONObject)?.let(::playlist)?.let(items::add)
                            "channelRenderer", "gridChannelRenderer" ->
                                (v as? JSONObject)?.let(::channel)?.let(items::add)
                            "continuationCommand" -> (v as? JSONObject)?.optString("token")
                                ?.takeIf { it.isNotEmpty() }?.let { token = it }
                            else -> walk(v)
                        }
                    }
                }
                is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i))
            }
        }
        walk(root)
        return ParsedPage(items, token)
    }

    /** Shorts out of a Shorts search response, plus the token for the next page. */
    fun parseShorts(root: JSONObject): Pair<List<VideoItem>, String?> {
        val out = ArrayList<VideoItem>()
        var token: String? = null
        fun walk(node: Any?) {
            when (node) {
                is JSONObject -> {
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = node.opt(k)
                        when (k) {
                            "shortsLockupViewModel" -> (v as? JSONObject)?.let(::short)?.let(out::add)
                            "continuationCommand" -> (v as? JSONObject)?.optString("token")
                                ?.takeIf { it.isNotEmpty() }?.let { token = it }
                            else -> walk(v)
                        }
                    }
                }
                is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i))
            }
        }
        walk(root)
        return out to token
    }

    private fun short(o: JSONObject): VideoItem? {
        val id = o.optString("entityId").removePrefix("shorts-shelf-item-").ifEmpty { return null }
        val title = o.optJSONObject("overlayMetadata")?.optJSONObject("primaryText")?.optString("content").orEmpty()
            .ifEmpty { o.optString("accessibilityText").substringBeforeLast(", ").substringBeforeLast(", ") }
            .ifEmpty { return null }
        return VideoItem(
            url = "https://www.youtube.com/watch?v=$id",
            title = title,
            channel = "",
            thumb = findFirstArray(o.optJSONObject("thumbnail"), "sources")?.let { lastThumb(it) }
                ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            durationSec = 0,
            views = -1,
            uploaded = o.optJSONObject("overlayMetadata")?.optJSONObject("secondaryText")?.optString("content")?.ifEmpty { null },
            isLive = false,
            isShort = true
        )
    }

    // ---- classic renderers ----

    private fun video(o: JSONObject): LibItem? {
        val id = o.optString("videoId").ifEmpty { return null }
        val title = text(o.optJSONObject("title")).ifEmpty { return null }
        val dur = parseDuration(text(o.optJSONObject("lengthText")))
        if (dur in 1L..60L) return null // Shorts
        val channel = text(
            o.optJSONObject("shortBylineText") ?: o.optJSONObject("ownerText") ?: o.optJSONObject("longBylineText")
        )
        val meta = listOf(text(o.optJSONObject("viewCountText")), text(o.optJSONObject("publishedTimeText")))
            .filter { it.isNotBlank() }.joinToString(" · ")
        return LibVideo(
            VideoItem(
                url = "https://www.youtube.com/watch?v=$id",
                title = title,
                channel = channel,
                thumb = lastThumb(o.optJSONObject("thumbnail")?.optJSONArray("thumbnails")),
                durationSec = dur,
                views = -1,
                uploaded = meta.ifEmpty { null },
                isLive = false,
                avatar = lastThumb(
                    findFirstArray(o.optJSONObject("channelThumbnailSupportedRenderers") ?: o.optJSONObject("channelThumbnail"), "thumbnails")
                )
            ),
            feedbackToken = findString(o, "feedbackToken"),
            setVideoId = o.optString("setVideoId").ifEmpty { null }
        )
    }

    private fun playlist(o: JSONObject): LibItem? {
        val id = o.optString("playlistId").ifEmpty { return null }
        val title = text(o.optJSONObject("title")).ifEmpty { return null }
        val count = o.optString("videoCountText").ifEmpty { text(o.optJSONObject("videoCountShortText")) }
        val thumbs = o.optJSONArray("thumbnails")?.optJSONObject(0)?.optJSONArray("thumbnails")
            ?: o.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        return LibPlaylist(id, title, count, lastThumb(thumbs))
    }

    private fun channel(o: JSONObject): LibItem? {
        val id = o.optString("channelId").ifEmpty { return null }
        val name = text(o.optJSONObject("title")).ifEmpty { return null }
        val sub = text(o.optJSONObject("subscriberCountText")).ifEmpty { text(o.optJSONObject("videoCountText")) }
        return LibChannel(id, name, sub, lastThumb(o.optJSONObject("thumbnail")?.optJSONArray("thumbnails")))
    }

    // ---- new lockupViewModel ----

    private fun lockup(o: JSONObject): LibItem? {
        val type = o.optString("contentType")
        val id = o.optString("contentId").ifEmpty { return null }
        val meta = o.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
        val title = meta?.optJSONObject("title")?.optString("content").orEmpty().ifEmpty { return null }
        val thumb = findFirstArray(o.optJSONObject("contentImage"), "sources")?.let { lastThumb(it) }
        val rows = ArrayList<List<String>>()
        findFirstArray(meta, "metadataRows")?.let { arr ->
            for (i in 0 until arr.length()) {
                val parts = arr.optJSONObject(i)?.optJSONArray("metadataParts") ?: continue
                rows.add((0 until parts.length()).mapNotNull {
                    parts.optJSONObject(it)?.optJSONObject("text")?.optString("content")?.takeIf { s -> s.isNotBlank() }
                })
            }
        }
        val badge = badgeText(o.optJSONObject("contentImage"))

        return when (type) {
            "LOCKUP_CONTENT_TYPE_VIDEO" -> {
                val dur = parseDuration(badge)
                if (dur in 1L..60L) return null
                val channel = rows.firstOrNull()?.firstOrNull().orEmpty()
                val rest = rows.drop(1).flatten().joinToString(" · ")
                LibVideo(
                    VideoItem(
                        url = "https://www.youtube.com/watch?v=$id",
                        title = title,
                        channel = channel,
                        thumb = thumb,
                        durationSec = dur,
                        views = -1,
                        uploaded = rest.ifEmpty { null },
                        isLive = false,
                        avatar = findFirstArray(meta?.optJSONObject("image"), "sources")?.let { lastThumb(it) }
                    ),
                    feedbackToken = findString(o, "feedbackToken")
                )
            }
            "LOCKUP_CONTENT_TYPE_PLAYLIST", "LOCKUP_CONTENT_TYPE_PODCAST" ->
                LibPlaylist(id, title, listOf(badge) .plus(rows.flatten()).filter { it.isNotBlank() }.joinToString(" · "), thumb)
            else -> null
        }
    }

    // ---- helpers ----

    private fun text(o: JSONObject?): String {
        if (o == null) return ""
        o.optString("simpleText").takeIf { it.isNotEmpty() }?.let { return it }
        o.optString("content").takeIf { it.isNotEmpty() }?.let { return it }
        val runs = o.optJSONArray("runs") ?: return ""
        return (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }
    }

    private fun lastThumb(arr: JSONArray?): String? {
        if (arr == null || arr.length() == 0) return null
        val url = arr.optJSONObject(arr.length() - 1)?.optString("url").orEmpty().ifEmpty { return null }
        return if (url.startsWith("//")) "https:$url" else url
    }

    private fun parseDuration(s: String): Long {
        if (!s.contains(':')) return 0
        val parts = s.split(':').map { it.trim().toLongOrNull() ?: return 0 }
        return parts.fold(0L) { acc, p -> acc * 60 + p }
    }

    private fun badgeText(node: Any?): String {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("thumbnailBadgeViewModel")?.optString("text")?.takeIf { it.isNotEmpty() }
                    ?.let { return it }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val r = badgeText(node.opt(keys.next()))
                    if (r.isNotEmpty()) return r
                }
            }
            is JSONArray -> for (i in 0 until node.length()) {
                val r = badgeText(node.opt(i))
                if (r.isNotEmpty()) return r
            }
        }
        return ""
    }

    private fun findString(node: Any?, key: String): String? {
        when (node) {
            is JSONObject -> {
                node.optString(key).takeIf { it.isNotEmpty() && node.opt(key) is String }?.let { return it }
                val keys = node.keys()
                while (keys.hasNext()) findString(node.opt(keys.next()), key)?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findString(node.opt(i), key)?.let { return it }
        }
        return null
    }

    private fun findFirstArray(node: Any?, key: String): JSONArray? {
        when (node) {
            is JSONObject -> {
                node.optJSONArray(key)?.let { return it }
                val keys = node.keys()
                while (keys.hasNext()) findFirstArray(node.opt(keys.next()), key)?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findFirstArray(node.opt(i), key)?.let { return it }
        }
        return null
    }
}
