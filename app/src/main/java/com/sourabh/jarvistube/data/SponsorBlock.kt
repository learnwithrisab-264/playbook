package com.sourabh.jarvistube.data

import androidx.core.net.toUri
import com.sourabh.jarvistube.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.security.MessageDigest

/** A kind of segment SponsorBlock users mark, with the colour SponsorBlock shows it in. */
data class SponsorCategory(val key: String, val label: String, val description: String, val color: Long)

val SPONSOR_CATEGORIES = listOf(
    SponsorCategory("sponsor", "Sponsor", "Paid promotion and sponsor reads", 0xFF00D400),
    SponsorCategory("selfpromo", "Self-promotion", "Merch, donations, the creator's other channels", 0xFFFFFF00),
    SponsorCategory("interaction", "Interaction reminder", "Asking you to like, subscribe or follow", 0xFFCC00FF),
    SponsorCategory("intro", "Intro", "Intro animation or a pause before the content", 0xFF00FFFF),
    SponsorCategory("outro", "Endcards and credits", "End screens and credits", 0xFF0202ED),
    SponsorCategory("preview", "Preview or recap", "Clips of what's coming or what was shown before", 0xFF008FD6),
    SponsorCategory("filler", "Filler tangent", "Off-topic jokes and tangents", 0xFF7300FF),
    SponsorCategory("music_offtopic", "Non-music part", "Talking in a music video", 0xFFFF9900)
)

/** Skipped until you change it: the same five PlayBook always skipped. */
val DEFAULT_SKIP_CATEGORIES = setOf("sponsor", "selfpromo", "interaction", "intro", "outro")

data class SkipSegment(val start: Double, val end: Double, val category: String) {
    val label: String
        get() = SPONSOR_CATEGORIES.firstOrNull { it.key == category }?.label?.lowercase() ?: category
}

/** The segment to jump out of at [posMs], if any. Stops half a second early so it never loops. */
fun segmentToSkip(segments: List<SkipSegment>, posMs: Long, skip: Set<String>): SkipSegment? =
    segments.firstOrNull {
        it.category in skip && posMs >= (it.start * 1000).toLong() && posMs < (it.end * 1000).toLong() - 500
    }

/** Community-sourced skip segments from sponsor.ajay.app (queried by hash prefix for privacy). */
object SponsorBlock {
    // Every kind is fetched, so the seek bar can show them all; only the chosen ones are skipped.
    private val CATEGORIES = SPONSOR_CATEGORIES.joinToString(",", "[", "]") { "\"${it.key}\"" }

    fun videoId(url: String): String? = url.toUri().getQueryParameter("v")

    suspend fun segments(videoId: String): List<SkipSegment> = withContext(Dispatchers.IO) {
        val prefix = MessageDigest.getInstance("SHA-256").digest(videoId.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(4)
        val url = "https://sponsor.ajay.app/api/skipSegments/$prefix" +
            "?categories=" + URLEncoder.encode(CATEGORIES, "UTF-8") +
            "&actionTypes=" + URLEncoder.encode("[\"skip\"]", "UTF-8")
        Graph.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) return@use emptyList()
            val all = JSONArray(r.body?.string() ?: return@use emptyList())
            val out = ArrayList<SkipSegment>()
            for (i in 0 until all.length()) {
                val v = all.optJSONObject(i) ?: continue
                if (v.optString("videoID") != videoId) continue
                val segs = v.optJSONArray("segments") ?: continue
                for (j in 0 until segs.length()) {
                    val s = segs.optJSONObject(j) ?: continue
                    val range = s.optJSONArray("segment") ?: continue
                    out.add(SkipSegment(range.optDouble(0), range.optDouble(1), s.optString("category")))
                }
            }
            out.filter { it.end > it.start }.sortedBy { it.start }
        }
    }
}
