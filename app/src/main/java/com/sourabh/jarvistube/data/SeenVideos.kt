package com.sourabh.jarvistube.data

import android.content.Context
import android.content.SharedPreferences

/** The three feeds that each remember what you have seen, separately. */
enum class Feed { MyFeed, Recommended, Shorts }

private val VIDEO_ID = Regex("(?:v=|/shorts/|youtu\\.be/)([\\w-]{11})")

/** The same video can arrive as a watch link or a Shorts link; its ID is what counts. */
fun videoIdOf(url: String): String = VIDEO_ID.find(url)?.groupValues?.get(1) ?: url

/** Video IDs, oldest first. Past [cap] the oldest are forgotten, so they can come back one day. */
class SeenList(private val cap: Int) {
    private val ids = LinkedHashSet<String>()

    val size: Int get() = ids.size

    operator fun contains(id: String) = id in ids

    /** Returns true when something new was added. */
    fun add(id: String): Boolean {
        if (!ids.add(id)) return false
        if (ids.size > cap) ids.remove(ids.first())
        return true
    }

    fun encode(): String = ids.joinToString("\n")

    fun decode(saved: String) {
        ids.clear()
        saved.split("\n").filter { it.isNotBlank() }.forEach(::add)
    }
}

/** New items only; when there are fewer than [minFresh], seen ones follow so a feed is never empty. */
fun <T> freshFirst(items: List<T>, minFresh: Int, isSeen: (T) -> Boolean): List<T> {
    val (seen, fresh) = items.partition(isSeen)
    return if (fresh.size >= minFresh) fresh else fresh + seen
}

/**
 * What has appeared on screen in each feed, saved on the phone for each account separately.
 * Videos in Watch History count as seen too.
 */
object SeenVideos {
    private const val CAP = 3000
    private lateinit var prefs: SharedPreferences
    private var owner = ""
    private val lists = HashMap<Feed, SeenList>()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("seen", Context.MODE_PRIVATE)
        load(Session.dataKey)
        Session.onDataKeyChange(::load)
    }

    private fun load(key: String) {
        owner = key
        Feed.entries.forEach { feed ->
            lists[feed] = SeenList(CAP).apply { decode(prefs.getString(storeKey(feed), "").orEmpty()) }
        }
    }

    private fun storeKey(feed: Feed) = "${feed.name}:$owner"

    fun mark(feed: Feed, urls: List<String>) {
        val list = lists.getValue(feed)
        var changed = false
        urls.forEach { if (list.add(videoIdOf(it))) changed = true }
        if (changed) prefs.edit().putString(storeKey(feed), list.encode()).apply()
    }

    private fun seenCheck(feed: Feed): (VideoItem) -> Boolean {
        val list = lists.getValue(feed)
        val watched = WatchHistory.items.mapTo(HashSet()) { videoIdOf(it.video.url) }
        return { v -> videoIdOf(v.url).let { it in list || it in watched } }
    }

    fun unseen(feed: Feed, items: List<VideoItem>): List<VideoItem> = items.filterNot(seenCheck(feed))

    fun pick(feed: Feed, items: List<VideoItem>, minFresh: Int): List<VideoItem> =
        freshFirst(items, minFresh, seenCheck(feed))
}
