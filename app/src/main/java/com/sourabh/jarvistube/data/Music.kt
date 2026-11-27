package com.sourabh.jarvistube.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sourabh.jarvistube.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** A "Browse all" tile in the Music tab: a name, what to search YouTube Music for, a colour. */
data class Mood(val title: String, val query: String, val color: Long)

/**
 * The Music tab's data: songs from YouTube Music (search, moods) and what you played recently.
 * Songs are ordinary YouTube links, so they play through the same player (as sound only).
 */
object Music {
    val moods = listOf(
        Mood("Trending", "trending songs india 2026", 0xFFE13300),
        Mood("Bollywood", "latest bollywood hit songs", 0xFF8D67AB),
        Mood("Punjabi", "top punjabi songs", 0xFF1E3264),
        Mood("English pop", "top english pop songs", 0xFF148A08),
        Mood("Lofi", "hindi lofi songs", 0xFF503750),
        Mood("Romantic", "romantic hindi songs", 0xFFE8115B),
        Mood("Workout", "workout gym songs", 0xFF777777),
        Mood("Party", "party songs hindi english", 0xFFDC148C),
        Mood("Focus", "instrumental focus music", 0xFF27856A),
        Mood("Old is gold", "old hindi songs evergreen", 0xFFBA5D07),
        Mood("Hip hop", "indian hip hop rap songs", 0xFF0D73EC),
        Mood("Devotional", "devotional bhajan songs", 0xFFAF2896),
        Mood("Sad", "sad hindi songs", 0xFF477D95),
        Mood("Telugu", "latest telugu hit songs", 0xFF7358FF),
        Mood("Tamil", "latest tamil hit songs", 0xFFF59B23),
        Mood("Chill", "chill english songs", 0xFF2D46B9)
    )

    /** Songs you played in the Music tab, newest first. */
    val recent = mutableStateListOf<VideoItem>()

    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun init(context: Context) {
        prefs = context.getSharedPreferences("music", Context.MODE_PRIVATE)
        MusicForYou.init(context)
        MusicLibrary.init(context)
        runCatching {
            val arr = JSONArray(prefs.getString("recent", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                // Shorts that slipped in before (no length, or a minute or less) are dropped.
                if (o.optLong("dur") in 1..60) continue
                recent += VideoItem(
                    url = o.getString("url"), title = o.getString("title"), channel = o.optString("artist"),
                    thumb = o.optString("art").ifEmpty { null }, durationSec = o.optLong("dur"),
                    views = -1, uploaded = null, isLive = false
                )
            }
        }
        save()
        MusicForYou.seedIfEmpty(recent.toList())
        // Every song that plays in music mode (tapped, or next in line) counts as played.
        scope.launch {
            Graph.player.now.collect { n ->
                if (n != null && Graph.player.musicMode) remember(n.video)
            }
        }
    }

    private fun remember(song: VideoItem) {
        // Only songs: never a Short or a video that played while music mode was on.
        if (song.isShort || song.durationSec in 1..60) return
        if (recent.firstOrNull()?.url == song.url) return
        // Counts towards "Made for you" (only songs played in the Music tab).
        MusicForYou.played(song)
        recent.removeAll { it.url == song.url }
        recent.add(0, song)
        while (recent.size > 30) recent.removeAt(recent.lastIndex)
        save()
    }

    private fun save() {
        val arr = JSONArray()
        recent.forEach {
            arr.put(JSONObject().put("url", it.url).put("title", it.title).put("artist", it.channel)
                .put("art", it.thumb.orEmpty()).put("dur", it.durationSec))
        }
        prefs.edit().putString("recent", arr.toString()).apply()
    }

    /** Songs on YouTube Music for [query] (songs only: no videos, no Shorts). */
    suspend fun songs(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val ex = ServiceList.YouTube.getSearchExtractor(query, listOf("music_songs"), "")
        ex.fetchPage()
        val first = ex.initialPage
        val items = first.items.toMutableList()
        // One more page, so a mood plays for a good while.
        first.nextPage?.let { n -> runCatching { items += ex.getPage(n).items } }
        items.filterIsInstance<StreamInfoItem>().map { it.toSong() }
            .filter { it.durationSec > 60 }
            .filterNot(ContentFilter::hides)
            .distinctBy { it.url }
    }

    private fun StreamInfoItem.toSong(): VideoItem {
        val id = SponsorBlock.videoId(url) ?: url.substringAfter("v=").substringBefore('&')
        return VideoItem(
            url = "https://www.youtube.com/watch?v=$id",
            title = name,
            channel = uploaderName.orEmpty().removeSuffix(" - Topic"),
            thumb = art(thumbnails.maxByOrNull { it.width }?.url) ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            durationSec = duration,
            views = viewCount,
            uploaded = null,
            isLive = false
        )
    }

    /** YouTube Music's square album art, asked for at a size that stays sharp in the big player. */
    fun art(url: String?): String? = url
        ?.let { if (it.startsWith("//")) "https:$it" else it }
        ?.replace(Regex("=w\\d+-h\\d+"), "=w544-h544")
}

/** What the Music tab shows: a mood's (or search's) songs, loading state and errors. */
class SongList(
    val title: String,
    /** Set for your own playlist: its songs can be removed, the playlist deleted. */
    val playlist: String? = null,
    /** Lists that change while open (Liked Songs, Downloaded, a playlist) read from here. */
    val live: (() -> List<VideoItem>)? = null
) {
    var songs by mutableStateOf<List<VideoItem>>(emptyList())
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
}
