package com.sourabh.jarvistube.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/** A video you stopped part-way through, for the "Continue watching" row. */
data class InProgress(val video: VideoItem, val positionMs: Long, val durationMs: Long) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** Unfinished videos, newest first. Finished ones (within 10 s of the end) drop off. */
object WatchProgress {
    private const val MAX = 30
    private lateinit var prefs: SharedPreferences
    val items = mutableStateListOf<InProgress>()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        runCatching {
            val arr = JSONArray(prefs.getString("items", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items += InProgress(
                    VideoItem(
                        url = o.getString("url"), title = o.getString("title"), channel = o.optString("channel"),
                        thumb = o.optString("thumb").ifEmpty { null }, durationSec = o.optLong("dur") / 1000,
                        views = -1, uploaded = null, isLive = false,
                        avatar = o.optString("avatar").ifEmpty { null }, channelUrl = o.optString("channelUrl").ifEmpty { null }
                    ),
                    o.getLong("pos"), o.optLong("dur")
                )
            }
        }
    }

    fun update(video: VideoItem, positionMs: Long, durationMs: Long) {
        items.removeAll { it.video.url == video.url }
        items.add(0, InProgress(video, positionMs, durationMs))
        while (items.size > MAX) items.removeAt(items.lastIndex)
        persist()
    }

    fun remove(url: String) {
        if (items.removeAll { it.video.url == url }) persist()
    }

    private fun persist() {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject().put("url", it.video.url).put("title", it.video.title).put("channel", it.video.channel)
                    .put("thumb", it.video.thumb.orEmpty()).put("avatar", it.video.avatar.orEmpty())
                    .put("channelUrl", it.video.channelUrl.orEmpty()).put("pos", it.positionMs).put("dur", it.durationMs)
            )
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }
}
