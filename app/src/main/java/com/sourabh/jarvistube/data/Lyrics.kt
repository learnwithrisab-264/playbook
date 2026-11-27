package com.sourabh.jarvistube.data

import com.sourabh.jarvistube.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray

/** One lyric line and when it starts (ms, or -1 when the lyrics have no timing). */
data class LyricLine(val timeMs: Long, val text: String)

/** A song's lyrics: [synced] when each line has a time, so the screen can follow the song. */
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean)

/**
 * Lyrics for the Music tab, like Spotify's: time-synced lines from LRCLIB (free, no account).
 * Only the song's name and artist are sent; nothing about you.
 */
object LyricsRepo {
    private val cache = HashMap<String, Lyrics?>()

    suspend fun forSong(song: VideoItem): Lyrics? {
        if (cache.containsKey(song.url)) return cache[song.url]
        val found = withContext(Dispatchers.IO) { runCatching { load(song) }.getOrNull() }
        cache[song.url] = found
        return found
    }

    private fun load(song: VideoItem): Lyrics? {
        val title = cleanTitle(song.title)
        val artist = song.channel.split(",", "&", " x ", " X ", " feat", " ft.").first().trim()
        val hits = search(title, artist).ifEmpty { search(title, null) }
        if (hits.isEmpty()) return null
        // The one whose length is closest to this song's, preferring timed lyrics.
        // Among those that fit the song, lyrics in English letters win (as Spotify shows Hindi
        // and Punjabi songs), then the closest length.
        fun native(h: Hit) = (h.synced ?: h.plain).orEmpty().any { it.code in 0x0900..0x0DFF }
        val best = hits.sortedWith(
            compareBy<Hit>(
                { it.synced == null },
                { kotlin.math.abs(it.duration - song.durationSec) > 4 },
                { native(it) },
                { kotlin.math.abs(it.duration - song.durationSec) }
            )
        ).first()
        val raw = best.synced?.let(::parseLrc) ?: best.plain?.lines()?.filter { it.isNotBlank() }?.map { LyricLine(-1, it.trim()) }
            ?: return null
        if (raw.none { it.text.isNotBlank() }) return null
        return Lyrics(raw, synced = best.synced != null)
    }

    private class Hit(val duration: Double, val synced: String?, val plain: String?)

    private fun search(title: String, artist: String?): List<Hit> {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .apply { if (!artist.isNullOrBlank()) addQueryParameter("artist_name", artist) }
            .build()
        val body = Graph.http.newCall(
            Request.Builder().url(url).header("User-Agent", "PlayBook (https://github.com)").build()
        ).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return emptyList()
        val arr = JSONArray(body)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            if (o.optBoolean("instrumental")) return@mapNotNull null
            Hit(
                o.optDouble("duration", 0.0),
                o.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" },
                o.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }

    /** "Gehra Hua (From "Dhurandhar")" → "Gehra Hua"; drops "(feat. …)", "| …", "[Official …]". */
    private fun cleanTitle(t: String): String =
        t.replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
            .substringBefore("|").substringBefore(" - ")
            .replace(Regex("\\s+"), " ").trim()
            .ifEmpty { t }

    private val STAMP = Regex("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]")

    private fun parseLrc(lrc: String): List<LyricLine> =
        lrc.lines().mapNotNull { line ->
            val stamps = STAMP.findAll(line).toList()
            if (stamps.isEmpty()) return@mapNotNull null
            val text = line.substring(stamps.last().range.last + 1).trim()
            val m = stamps.first()
            val frac = m.groupValues[3].let { if (it.isEmpty()) 0L else (it + "00").take(3).toLong() }
            val ms = m.groupValues[1].toLong() * 60_000 + m.groupValues[2].toLong() * 1000 + frac
            LyricLine(ms, text)
        }.sortedBy { it.timeMs }
            // Empty lines stay: they mark music breaks (shown as •••).
            .dropWhile { it.text.isEmpty() }
}
