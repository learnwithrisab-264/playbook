package com.sourabh.jarvistube.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/** One watched video and when it was last watched. */
data class WatchedVideo(val video: VideoItem, val watchedAt: Long)

/**
 * Everything watched in the app, newest first, saved on the phone for each account separately.
 * YouTube's own history can't show these: the app doesn't report what you watch to YouTube.
 * A video appears once: watching it again moves it to the top with the new time.
 */
object WatchHistory {
    private const val MAX = 2000
    private lateinit var prefs: SharedPreferences
    private var storeKey = ""
    val items = mutableStateListOf<WatchedVideo>()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)
        load(Session.dataKey)
        Session.onDataKeyChange(::load)
    }

    private fun load(key: String) {
        storeKey = "items:$key"
        items.clear()
        runCatching {
            val arr = JSONArray(prefs.getString(storeKey, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items += WatchedVideo(
                    VideoItem(
                        url = o.getString("url"), title = o.getString("title"), channel = o.optString("channel"),
                        thumb = o.optString("thumb").ifEmpty { null }, durationSec = o.optLong("dur"),
                        views = -1, uploaded = null, isLive = false,
                        avatar = o.optString("avatar").ifEmpty { null },
                        channelUrl = o.optString("channelUrl").ifEmpty { null },
                        isShort = o.optBoolean("short")
                    ),
                    o.getLong("at")
                )
            }
        }
    }

    /** Records a video as watched now. */
    fun add(video: VideoItem) {
        items.removeAll { it.video.url == video.url }
        items.add(0, WatchedVideo(video, System.currentTimeMillis()))
        while (items.size > MAX) items.removeAt(items.lastIndex)
        persist()
    }

    fun remove(url: String) {
        if (items.removeAll { it.video.url == url }) persist()
    }

    /** Clears only the account in use. */
    fun clear() {
        items.clear()
        persist()
    }

    private fun persist() {
        val arr = JSONArray()
        items.forEach {
            val v = it.video
            arr.put(
                JSONObject().put("url", v.url).put("title", v.title).put("channel", v.channel)
                    .put("thumb", v.thumb.orEmpty()).put("avatar", v.avatar.orEmpty())
                    .put("channelUrl", v.channelUrl.orEmpty()).put("dur", v.durationSec)
                    .put("short", v.isShort).put("at", it.watchedAt)
            )
        }
        prefs.edit().putString(storeKey, arr.toString()).apply()
    }
}
