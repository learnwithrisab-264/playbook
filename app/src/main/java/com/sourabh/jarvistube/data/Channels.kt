package com.sourabh.jarvistube.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** A channel the user can open or add to their study list. */
data class ChannelRef(val url: String, val name: String, val avatar: String?)

/**
 * "My Study Channels": the channels Home is built from. These channels are also always allowed
 * during a Study session.
 */
object StudyChannels {
    val list = mutableStateListOf<ChannelRef>()
    private lateinit var dir: File
    private lateinit var file: File

    fun init(context: Context) {
        dir = context.filesDir
        // Before accounts had their own lists there was one shared file: it goes to the account in use.
        val shared = File(dir, "study_channels.json")
        val own = fileFor(Session.dataKey)
        if (shared.exists() && !own.exists()) shared.renameTo(own)
        load(Session.dataKey)
        Session.onDataKeyChange(::load)
    }

    private fun fileFor(key: String) = File(dir, "study_channels_$key.json")

    /** Each account has its own channels: its own file, read again whenever the account changes. */
    private fun load(key: String) {
        file = fileFor(key)
        list.clear()
        if (!file.exists()) return
        runCatching {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(ChannelRef(o.getString("url"), o.getString("name"), o.optString("avatar").ifEmpty { null }))
            }
        }
    }

    fun contains(url: String) = list.any { it.url == url }
    /** Name match that ignores case, spaces and symbols (YouTube names can carry invisible characters). */
    fun containsName(name: String) = nameKey(name).let { k -> k.isNotEmpty() && list.any { nameKey(it.name) == k } }

    /** Same channel by its UC… id, whatever form the link takes. */
    fun containsChannelUrl(url: String?): Boolean {
        val id = url?.let { Regex("channel/(UC[\\w-]{22})").find(it)?.groupValues?.get(1) } ?: return false
        return list.any { it.url.contains(id) }
    }

    fun nameKey(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    fun toggle(ref: ChannelRef) {
        // No new trusted channels during a strict session: that would be a way around it.
        val study = runCatching { com.sourabh.jarvistube.Graph.study }.getOrNull()
        if (!contains(ref.url) && study != null && study.active && study.strict) return
        if (contains(ref.url)) list.removeAll { it.url == ref.url } else list.add(ref)
        save()
    }

    fun remove(url: String) {
        list.removeAll { it.url == url }
        save()
    }

    private fun save() {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("url", it.url).put("name", it.name).put("avatar", it.avatar ?: "")) }
        file.writeText(arr.toString())
    }
}

/** Something a channel tab or playlist can list. */
sealed interface Entry {
    val key: String

    data class Video(val video: VideoItem) : Entry {
        override val key get() = "v:" + video.url
    }

    data class Playlist(val url: String, val title: String, val thumb: String?, val count: Long, val owner: String) : Entry {
        override val key get() = "p:$url"
    }

    data class Channel(val ref: ChannelRef, val subscribers: Long) : Entry {
        override val key get() = "c:" + ref.url
    }
}

class EntryPage(val items: List<Entry>, val next: Page?)

class ChannelTab(val key: String, val label: String, val handler: ListLinkHandler)

class ChannelPage(
    val ref: ChannelRef,
    val banner: String?,
    val subscribers: Long,
    val description: String,
    val tabs: List<ChannelTab>
)

class PlaylistPage(val title: String, val owner: String, val count: Long, val thumb: String?, val first: EntryPage)

/** A channel's Videos tab, page by page (builds the study feed). */
class ChannelVideos(private val url: String) {
    private var page: ChannelPage? = null
    private var tab: ChannelTab? = null
    private var next: Page? = null
    private var started = false

    val hasMore: Boolean get() = !started || next != null

    suspend fun load(): List<VideoItem> {
        val p = page ?: ChannelRepo.page(url).also { page = it }
        val videos = tab ?: p.tabs.firstOrNull { it.key == ChannelTabs.VIDEOS }?.also { tab = it }
        if (videos == null) {
            started = true
            return emptyList()
        }
        val n = next
        val entries = if (started && n != null) ChannelRepo.tabMore(p, videos, n) else ChannelRepo.tab(p, videos)
        started = true
        next = entries.next
        return entries.items.filterIsInstance<Entry.Video>().map { it.video }
    }
}

/** Channel pages, channel tabs, playlists and channel search, all through NewPipeExtractor. */
object ChannelRepo {
    private val infoCache = ConcurrentHashMap<String, ChannelInfo>()

    // Shorts are left out on purpose: this app is distraction-free.
    private val tabOrder = listOf(
        ChannelTabs.VIDEOS to "Videos",
        ChannelTabs.LIVESTREAMS to "Live",
        ChannelTabs.PLAYLISTS to "Playlists",
        ChannelTabs.CHANNELS to "Channels"
    )

    private suspend fun info(url: String): ChannelInfo = withContext(Dispatchers.IO) {
        infoCache[url] ?: ChannelInfo.getInfo(ServiceList.YouTube, url).also { infoCache[url] = it }
    }

    suspend fun page(url: String): ChannelPage {
        val info = info(url)
        val tabs = tabOrder.mapNotNull { (key, label) ->
            info.tabs.firstOrNull { it.contentFilters.contains(key) }?.let { ChannelTab(key, label, it) }
        }
        return ChannelPage(
            ref = ChannelRef(info.url ?: url, info.name, info.avatars.bestUrl()),
            banner = info.banners.bestUrl(),
            subscribers = info.subscriberCount,
            description = info.description.orEmpty(),
            tabs = tabs
        )
    }

    suspend fun tab(page: ChannelPage, tab: ChannelTab): EntryPage = withContext(Dispatchers.IO) {
        val t = ChannelTabInfo.getInfo(ServiceList.YouTube, tab.handler)
        EntryPage(t.relatedItems.toEntries(page.ref), t.nextPage)
    }

    suspend fun tabMore(page: ChannelPage, tab: ChannelTab, next: Page): EntryPage = withContext(Dispatchers.IO) {
        val p = ChannelTabInfo.getMoreItems(ServiceList.YouTube, tab.handler, next)
        EntryPage(p.items.toEntries(page.ref), p.nextPage)
    }

    /** First page of a channel's Videos tab. */
    suspend fun latestVideos(url: String): List<VideoItem> {
        val p = page(url)
        val videos = p.tabs.firstOrNull { it.key == ChannelTabs.VIDEOS } ?: return emptyList()
        return tab(p, videos).items.filterIsInstance<Entry.Video>().map { it.video }
    }

    suspend fun searchChannels(query: String): List<ChannelRef> = withContext(Dispatchers.IO) {
        val ex = ServiceList.YouTube.getSearchExtractor(query, listOf("channels"), "")
        ex.fetchPage()
        ex.initialPage.items.filterIsInstance<ChannelInfoItem>()
            .map { ChannelRef(it.url, it.name, it.thumbnails.bestUrl()) }
    }

    suspend fun playlist(url: String): PlaylistPage = withContext(Dispatchers.IO) {
        val info = PlaylistInfo.getInfo(ServiceList.YouTube, url)
        PlaylistPage(
            title = info.name,
            owner = info.uploaderName.orEmpty(),
            count = info.streamCount,
            thumb = info.thumbnails.bestUrl(),
            first = EntryPage(ContentFilter.filter(info.relatedItems.map { it.toVideoItem() }).map { Entry.Video(it) }, info.nextPage)
        )
    }

    suspend fun playlistMore(url: String, next: Page): EntryPage = withContext(Dispatchers.IO) {
        val p = PlaylistInfo.getMoreItems(ServiceList.YouTube, url, next)
        EntryPage(ContentFilter.filter(p.items.map { it.toVideoItem() }).map { Entry.Video(it) }, p.nextPage)
    }

    private fun List<InfoItem>.toEntries(owner: ChannelRef): List<Entry> = mapNotNull { item ->
        when (item) {
            is StreamInfoItem ->
                if (item.duration in 1L..60L) null // Shorts
                else item.toVideoItem(owner.name, owner.url, owner.avatar)
                    .takeUnless(ContentFilter::hides)?.let { Entry.Video(it) }
            is PlaylistInfoItem -> Entry.Playlist(
                item.url, item.name, item.thumbnails.bestUrl(), item.streamCount,
                item.uploaderName?.takeIf { it.isNotBlank() } ?: owner.name
            )
            is ChannelInfoItem -> Entry.Channel(ChannelRef(item.url, item.name, item.thumbnails.bestUrl()), item.subscriberCount)
            else -> null
        }
    }
}
