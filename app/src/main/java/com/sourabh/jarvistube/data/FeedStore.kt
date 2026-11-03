package com.sourabh.jarvistube.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The last list of each feed, saved on the phone for each account separately. A feed changes only
 * when you refresh it, so this is what you find again after the app was closed.
 */
object FeedStore {
    class Saved(val items: List<VideoItem>, val note: String)

    private const val MAX = 300
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dir: File

    fun init(context: Context) {
        dir = File(context.filesDir, "feeds").apply { mkdirs() }
    }

    /** One file per feed, account and region, so two accounts can never read each other's list. */
    fun fileName(feed: Feed, owner: String, tag: String) = "${feed.name}_${owner}_$tag.json"

    private fun file(feed: Feed, owner: String, tag: String) = File(dir, fileName(feed, owner, tag))


    /** The saved list, or null when there is none yet. [tag] separates regions. */
    fun load(feed: Feed, tag: String = ""): Saved? = runCatching {
        val o = JSONObject(file(feed, Session.dataKey, tag).readText())
        val arr = o.getJSONArray("items")
        val items = (0 until arr.length()).map { i ->
            val v = arr.getJSONObject(i)
            VideoItem(
                url = v.getString("url"), title = v.getString("title"), channel = v.optString("channel"),
                thumb = v.optString("thumb").ifEmpty { null }, durationSec = v.optLong("dur"),
                views = v.optLong("views", -1), uploaded = v.optString("uploaded").ifEmpty { null },
                isLive = v.optBoolean("live"), avatar = v.optString("avatar").ifEmpty { null },
                channelUrl = v.optString("channelUrl").ifEmpty { null },
                uploadedAt = if (v.has("at")) v.getLong("at") else null, isShort = v.optBoolean("short")
            )
        }
        Saved(items, o.optString("note"))
    }.getOrNull()?.takeIf { it.items.isNotEmpty() }

    /**
     * [note] is anything the feed needs back with the list. [owner] is the account the list was
     * loaded for: a load that finishes after an account switch still lands in its own account.
     */
    fun save(feed: Feed, items: List<VideoItem>, tag: String = "", note: String = "", owner: String = Session.dataKey) {
        val target = file(feed, owner, tag)
        val arr = JSONArray()
        items.take(MAX).forEach { v ->
            val o = JSONObject().put("url", v.url).put("title", v.title).put("channel", v.channel)
                .put("thumb", v.thumb.orEmpty()).put("dur", v.durationSec).put("views", v.views)
                .put("uploaded", v.uploaded.orEmpty()).put("live", v.isLive).put("avatar", v.avatar.orEmpty())
                .put("channelUrl", v.channelUrl.orEmpty()).put("short", v.isShort)
            v.uploadedAt?.let { o.put("at", it) }
            arr.put(o)
        }
        val text = JSONObject().put("note", note).put("items", arr).toString()
        scope.launch {
            val temp = File(target.path + ".tmp")
            temp.writeText(text)
            temp.renameTo(target)
        }
    }
}
