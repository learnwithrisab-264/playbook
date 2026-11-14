package com.sourabh.jarvistube.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.IsoFields

/** A video planned for today or for this week, and whether it's been watched. */
data class PlanItem(val video: VideoItem, val today: Boolean, val day: String, val week: String, val done: Boolean = false)

/**
 * The study plan at the top of the Study tab: what to watch today and this week. Videos tick
 * themselves off when watched to the end. At a new day, unfinished "today" videos move into the
 * week, and finished ones from earlier days are tidied away; unfinished week videos carry over.
 */
object StudyPlan {
    private lateinit var prefs: SharedPreferences
    val items = mutableStateListOf<PlanItem>()

    private fun todayKey() = LocalDate.now().toString()
    private fun weekKey() = LocalDate.now().let { "${it.get(IsoFields.WEEK_BASED_YEAR)}-W${it.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}" }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("study_plan", Context.MODE_PRIVATE)
        items.clear()
        runCatching {
            val arr = JSONArray(prefs.getString("items", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items += PlanItem(
                    VideoItem(
                        url = o.getString("url"), title = o.getString("title"), channel = o.optString("channel"),
                        thumb = o.optString("thumb").ifEmpty { null }, durationSec = o.optLong("dur"),
                        views = -1, uploaded = null, isLive = false, channelUrl = o.optString("channelUrl").ifEmpty { null }
                    ),
                    today = o.optBoolean("today"), day = o.optString("day"), week = o.optString("week"), done = o.optBoolean("done")
                )
            }
        }
        rollOver()
    }

    /** New day / week housekeeping. */
    fun rollOver() {
        val day = todayKey()
        val week = weekKey()
        var changed = false
        val next = items.mapNotNull { p ->
            when {
                p.today && p.day != day && p.done -> { changed = true; null }
                p.today && p.day != day -> { changed = true; p.copy(today = false, week = week) }
                !p.today && p.week != week && p.done -> { changed = true; null }
                else -> p
            }
        }
        if (changed) { items.clear(); items.addAll(next); persist() }
    }

    fun today(): List<PlanItem> = items.filter { it.today }
    fun week(): List<PlanItem> = items.filter { !it.today }
    fun planned(url: String) = items.any { it.video.url == url }

    /** Adds (or moves) a video into today's or this week's plan. */
    fun add(video: VideoItem, today: Boolean) {
        items.removeAll { it.video.url == video.url }
        items.add(PlanItem(video, today, todayKey(), weekKey()))
        persist()
    }

    fun remove(url: String) { if (items.removeAll { it.video.url == url }) persist() }

    fun setDone(url: String, done: Boolean) {
        val i = items.indexOfFirst { it.video.url == url }
        if (i >= 0 && items[i].done != done) { items[i] = items[i].copy(done = done); persist() }
    }

    /** Called when a video is watched to its end. */
    fun markWatched(url: String) {
        if (::prefs.isInitialized) setDone(url, true)
    }

    private fun persist() {
        val arr = JSONArray()
        items.forEach { p ->
            arr.put(JSONObject().apply {
                put("url", p.video.url); put("title", p.video.title); put("channel", p.video.channel)
                put("thumb", p.video.thumb ?: ""); put("dur", p.video.durationSec); put("channelUrl", p.video.channelUrl ?: "")
                put("today", p.today); put("day", p.day); put("week", p.week); put("done", p.done)
            })
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }
}
