package com.sourabh.jarvistube.data

import androidx.core.net.toUri
import com.sourabh.jarvistube.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.security.MessageDigest

data class SkipSegment(val start: Double, val end: Double, val category: String) {
    val label: String
        get() = when (category) {
            "sponsor" -> "sponsor"
            "selfpromo" -> "self-promo"
            "interaction" -> "reminder"
            "intro" -> "intro"
            "outro" -> "outro"
            else -> category
        }
}

/** Community-sourced skip segments from sponsor.ajay.app (queried by hash prefix for privacy). */
object SponsorBlock {
    private const val CATEGORIES = "[\"sponsor\",\"selfpromo\",\"interaction\",\"intro\",\"outro\"]"

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
