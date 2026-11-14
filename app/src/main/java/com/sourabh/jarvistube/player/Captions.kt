package com.sourabh.jarvistube.player

import com.sourabh.jarvistube.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/** One spoken word and when it starts. */
data class CaptionWord(val text: String, val startMs: Long)

/** One caption line, shown from [startMs] until [endMs]. [words] carry timings when YouTube has them. */
data class CaptionLine(val startMs: Long, val endMs: Long, val words: List<CaptionWord>) {
    val text: String = words.joinToString("") { it.text }.trim()
    /** Word-by-word timing is known (auto-generated captions), so the spoken word can be lit up. */
    val timed: Boolean = words.size > 1
}

/**
 * Captions read from YouTube's word-timed format (json3) and drawn by PlayBook itself, instead of
 * ExoPlayer's text track. Two wins:
 *  - YouTube's auto captions "roll": each line stays up while the next one starts, which showed
 *    as two stacked layers. Here a line ends the moment the next begins, so one clean line shows.
 *  - Every word's start time is known, so the word being spoken can be highlighted, as on YouTube.
 * Switching captions no longer re-prepares the video either.
 */
object Captions {
    /** YouTube's caption link, asked for in json3; [translateTo] uses YouTube's own translation. */
    fun json3Url(url: String, translateTo: String? = null): String {
        val base = Regex("([?&])fmt=[^&]*").replace(url) { it.groupValues[1] + "fmt=json3" }
            .let { if (it.contains("fmt=json3")) it else "$it&fmt=json3" }
        return if (translateTo != null) "$base&tlang=$translateTo" else base
    }

    suspend fun load(url: String): List<CaptionLine> = withContext(Dispatchers.IO) {
        val body = Graph.http.newCall(Request.Builder().url(url).build()).execute().use { res ->
            if (!res.isSuccessful) throw java.io.IOException("Captions: HTTP ${res.code}")
            res.body!!.string()
        }
        parse(body)
    }

    /** json3 → lines. Pure (no network), so it is unit-tested. */
    fun parse(json: String): List<CaptionLine> {
        val events = JSONObject(json).optJSONArray("events") ?: return emptyList()
        val raw = ArrayList<Pair<Long, Pair<Long, List<CaptionWord>>>>()
        for (i in 0 until events.length()) {
            val e = events.getJSONObject(i)
            val segs = e.optJSONArray("segs") ?: continue
            val start = e.optLong("tStartMs")
            val dur = e.optLong("dDurationMs")
            val words = (0 until segs.length()).map { j ->
                val s = segs.getJSONObject(j)
                CaptionWord(s.optString("utf8"), start + s.optLong("tOffsetMs", 0))
            }
            // Line-break events ("\n" alone) and empty ones carry no text.
            if (words.all { it.text.isBlank() }) continue
            raw += start to ((start + dur) to words)
        }
        raw.sortBy { it.first }
        return raw.mapIndexed { i, (start, rest) ->
            val (end, words) = rest
            val nextStart = raw.getOrNull(i + 1)?.first ?: Long.MAX_VALUE
            // Rolling captions overlap: cut each line where the next one begins.
            CaptionLine(start, minOf(end, nextStart), words)
        }.filter { it.endMs > it.startMs }
    }

    /** The line on screen at [posMs] (binary search), or null. */
    fun lineAt(lines: List<CaptionLine>, posMs: Long): CaptionLine? {
        var lo = 0
        var hi = lines.lastIndex
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val l = lines[mid]
            when {
                posMs < l.startMs -> hi = mid - 1
                posMs >= l.endMs -> lo = mid + 1
                else -> return l
            }
        }
        return null
    }

    /** Index of the word being spoken in [line] at [posMs], or -1. */
    fun wordAt(line: CaptionLine, posMs: Long): Int {
        if (!line.timed) return -1
        return line.words.indexOfLast { it.startMs <= posMs && it.text.isNotBlank() }
    }
}
