package com.sourabh.jarvistube.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONArray
import org.json.JSONObject

/**
 * Your music library, kept on the phone: Liked Songs and your own playlists, plus songs saved
 * for offline (through the app's downloader, sound only). Used only by the Music tab.
 */
object MusicLibrary {
    private lateinit var prefs: SharedPreferences

    /** Liked songs, newest first. */
    val liked = mutableStateListOf<VideoItem>()

    /** Your playlists by name (in the order you made them), each with its songs. */
    val playlists = mutableStateMapOf<String, List<VideoItem>>()
    private val order = mutableStateListOf<String>()
    val playlistNames: List<String> get() = order.toList()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("music_library", Context.MODE_PRIVATE)
        runCatching { liked.addAll(read(JSONArray(prefs.getString("liked", "[]")))) }
        runCatching {
            val o = JSONObject(prefs.getString("playlists", "{}"))
            val names = JSONArray(prefs.getString("playlistOrder", "[]"))
            for (i in 0 until names.length()) {
                val n = names.getString(i)
                playlists[n] = read(o.optJSONArray(n) ?: JSONArray())
                order += n
            }
        }
    }

    // ---- Liked Songs ----

    fun isLiked(url: String) = liked.any { it.url == url }

    fun toggleLike(song: VideoItem): Boolean {
        val was = isLiked(song.url)
        if (was) liked.removeAll { it.url == song.url } else liked.add(0, song)
        save()
        return !was
    }

    // ---- Playlists ----

    fun createPlaylist(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty() || playlists.containsKey(n)) return false
        playlists[n] = emptyList()
        order += n
        save()
        return true
    }

    fun addTo(name: String, song: VideoItem): Boolean {
        val cur = playlists[name] ?: return false
        if (cur.any { it.url == song.url }) return false
        playlists[name] = cur + song
        save()
        return true
    }

    fun removeFrom(name: String, url: String) {
        playlists[name]?.let { playlists[name] = it.filterNot { s -> s.url == url } }
        save()
    }

    fun deletePlaylist(name: String) {
        playlists.remove(name)
        order.remove(name)
        save()
    }

    // ---- Offline ----

    private fun dlKey(song: VideoItem) = Downloads.keyFor(song, 0, true)

    /** The saved (sound only) copy of [url], if it was downloaded. */
    fun offline(url: String): SavedItem? = Downloads.saved.firstOrNull { it.url == url && it.audioOnly }

    /** 0..1 while downloading, -1 while waiting, null when not downloading. */
    fun progress(song: VideoItem): Float? = Downloads.active[dlKey(song)]?.takeIf { !it.failed }?.fraction

    /** Downloaded songs (sound only), newest first. */
    fun downloaded(): List<VideoItem> = Downloads.saved.filter { it.audioOnly }.asReversed().map {
        VideoItem(it.url, it.title, it.channel, it.thumb, it.durationSec, -1, null, false)
    }

    /** Saves [song] for offline listening, sound only. */
    suspend fun download(song: VideoItem) {
        if (offline(song.url) != null) return
        val info = Repo.streamInfo(song.url)
        Downloads.enqueue(song, info, 0, true)
    }

    // ---- storage ----

    private fun read(arr: JSONArray): List<VideoItem> = (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        VideoItem(
            url = o.getString("url"), title = o.getString("title"), channel = o.optString("artist"),
            thumb = o.optString("art").ifEmpty { null }, durationSec = o.optLong("dur"),
            views = -1, uploaded = null, isLive = false
        )
    }

    private fun write(list: List<VideoItem>) = JSONArray().apply {
        list.forEach {
            put(JSONObject().put("url", it.url).put("title", it.title).put("artist", it.channel)
                .put("art", it.thumb.orEmpty()).put("dur", it.durationSec))
        }
    }

    private fun save() {
        val all = JSONObject()
        order.forEach { all.put(it, write(playlists[it].orEmpty())) }
        prefs.edit()
            .putString("liked", write(liked).toString())
            .putString("playlists", all.toString())
            .putString("playlistOrder", JSONArray(order.toList()).toString())
            .apply()
    }
}
