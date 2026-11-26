package com.sourabh.jarvistube.data

import android.net.Uri
import android.os.SystemClock
import com.sourabh.jarvistube.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

data class VideoItem(
    val url: String,
    val title: String,
    val channel: String,
    val thumb: String?,
    val durationSec: Long,
    val views: Long,
    val uploaded: String?,
    val isLive: Boolean,
    /** Channel picture, when the source provides one. */
    val avatar: String? = null,
    /** Link to the uploader's channel page, when known. */
    val channelUrl: String? = null,
    /** Upload time (epoch ms) when YouTube gives a real date; used to sort the study feed. */
    val uploadedAt: Long? = null,
    /** A YouTube Short (vertical, short-form). */
    val isShort: Boolean = false,
    /** Members-only or paid: can't be watched without joining, so never listed. */
    val membersOnly: Boolean = false
)

/** Shorts never belong in a normal video list: marked by YouTube, or a minute or less. */
val VideoItem.looksShort: Boolean get() = isShort || durationSec in 1L..60L

object Repo {
    private class Entry(val at: Long, val deferred: Deferred<StreamInfo>)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = ConcurrentHashMap<String, Entry>()
    private val prefetchGate = Semaphore(2)
    /** Shorts prepare in their own line, so Home's background work can't hold them up. */
    private val shortsGate = Semaphore(3)
    private const val TTL_MS = 30 * 60 * 1000L

    private fun load(url: String): StreamInfo {
        val ex = ServiceList.YouTube.getStreamExtractor(url)
        ex.fetchPage()
        return StreamInfo.getInfo(ex)
    }

    /**
     * Opens (and pools) the TCP+TLS connection to the CDN host with a 1-byte request, so the
     * real playback request skips ~2s of connection setup.
     */
    private fun warmConnections(info: StreamInfo) {
        val player = Graph.player
        val urls = listOfNotNull(
            player.pickVideo(info, player.autoHeight())?.content,
            player.pickAudio(info)?.content
        ).distinctBy { Uri.parse(it).host }
        for (u in urls) {
            try {
                Graph.http.newCall(
                    okhttp3.Request.Builder().url("$u&range=0-0").header("User-Agent", BROWSER_UA).build()
                ).execute().use { r -> r.body?.bytes() }
            } catch (_: Exception) {
            }
        }
    }

    private fun valid(e: Entry?): Boolean =
        e != null && SystemClock.elapsedRealtime() - e.at < TTL_MS && !e.deferred.isCancelled

    private fun start(url: String, gate: Semaphore?): Deferred<StreamInfo> {
        val d = scope.async {
            try {
                if (gate != null) gate.withPermit { load(url).also(::warmConnections) } else load(url)
            } catch (e: Exception) {
                cache.remove(url)
                throw e
            }
        }
        cache[url] = Entry(SystemClock.elapsedRealtime(), d)
        if (cache.size > 12) {
            cache.entries.minByOrNull { it.value.at }?.let { cache.remove(it.key) }
        }
        return d
    }

    fun clearCache() = cache.clear()

    /** Drops one cached result, e.g. when its stream URLs stopped working. */
    fun forget(url: String) { cache.remove(url) }

    /** Resolves in the background so a later tap starts instantly. */
    fun prefetch(url: String) {
        if (!valid(cache[url])) start(url, prefetchGate)
    }

    /** Prepares a Short ahead of time, so tapping or swiping to it starts at once. */
    fun prefetchShort(url: String) {
        if (!valid(cache[url])) start(url, shortsGate)
    }

    suspend fun streamInfo(url: String): StreamInfo {
        val e = cache[url]
        val d = if (valid(e)) e!!.deferred else start(url, null)
        return d.await()
    }
}

/**
 * Paged video search. Shorts are filtered out (distraction-free by default).
 * With [asApp] it searches the way the YouTube app does (all results, not only the "videos"
 * filter): the same ranking you'd see there, plus the best-matching channel and YouTube's
 * spelling correction. Pages that the content filter empties are skipped, so results never thin out.
 */
class SearchSession(private val query: String, private val shorts: Boolean = false, private val asApp: Boolean = false) {
    private var extractor: SearchExtractor? = null
    private var next: Page? = null

    val hasMore: Boolean get() = next != null
    /** The channel your words name, shown above the videos ([asApp] only). */
    var topChannel: ChannelResult? = null; private set
    /** What YouTube searched instead, when it corrected the spelling. */
    var correctedTo: String? = null; private set

    suspend fun loadFirst(): List<VideoItem> = withContext(Dispatchers.IO) {
        val ex = ServiceList.YouTube.getSearchExtractor(query, if (asApp) emptyList() else listOf("videos"), "")
        ex.fetchPage()
        extractor = ex
        val page = ex.initialPage
        next = page.nextPage
        if (asApp) {
            correctedTo = runCatching { ex.searchSuggestion.takeIf { ex.isCorrectedSearch && it.isNotBlank() } }.getOrNull()
            topChannel = page.items.filterIsInstance<org.schabi.newpipe.extractor.channel.ChannelInfoItem>()
                .firstOrNull { matches(it.name) }
                ?.let { c ->
                    ChannelResult(
                        c.url, c.name,
                        runCatching { c.thumbnails.maxByOrNull { it.width }?.url }.getOrNull()?.let { if (it.startsWith("//")) "https:$it" else it },
                        c.subscriberCount
                    )
                }
        }
        fill(page.items.toVideos(shorts))
    }

    suspend fun loadMore(): List<VideoItem> = withContext(Dispatchers.IO) {
        val ex = extractor ?: return@withContext emptyList()
        val n = next ?: return@withContext emptyList()
        val page = ex.getPage(n)
        next = page.nextPage
        fill(page.items.toVideos(shorts))
    }

    /** Keeps reading pages (up to three more) until there are enough videos to show. */
    private fun fill(first: List<VideoItem>): List<VideoItem> {
        if (!asApp) return first
        val got = first.toMutableList()
        var tries = 0
        while (got.size < 10 && tries < 3) {
            val ex = extractor ?: break
            val n = next ?: break
            val page = runCatching { ex.getPage(n) }.getOrNull() ?: break
            next = page.nextPage
            got += page.items.toVideos(shorts)
            tries++
        }
        return got.distinctBy { it.url }
    }

    /** The channel is what you meant if its name matches your words (ignoring spaces and case). */
    private fun matches(name: String): Boolean {
        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        val n = norm(name)
        val q = norm(query)
        return n.isNotEmpty() && q.length >= 3 && (n == q || n.startsWith(q) || q.startsWith(n) || q.contains(n))
    }
}

/** A channel found by search. */
data class ChannelResult(val url: String, val name: String, val avatar: String?, val subscribers: Long)

/**
 * Paged Trending kiosk for the region NewPipe was set up with (AppSettings.region). Falls back
 * to the service's default kiosk on extractor versions that no longer ship "Trending".
 */
class TrendingSession {
    private var extractor: org.schabi.newpipe.extractor.kiosk.KioskExtractor<out InfoItem>? = null
    private var next: Page? = null

    /** True when the real "Trending" kiosk was used (not the default fallback). */
    var isTrending = true; private set

    val hasMore: Boolean get() = next != null

    suspend fun loadFirst(): List<VideoItem> = withContext(Dispatchers.IO) {
        val kiosks = ServiceList.YouTube.kioskList
        isTrending = "Trending" in kiosks.availableKiosks
        val ex = if (isTrending) kiosks.getExtractorById("Trending", null) else kiosks.defaultKioskExtractor
        ex.fetchPage()
        extractor = ex
        val page = ex.initialPage
        next = page.nextPage
        page.items.toVideos()
    }

    suspend fun loadMore(): List<VideoItem> = withContext(Dispatchers.IO) {
        val ex = extractor ?: return@withContext emptyList()
        val n = next ?: return@withContext emptyList()
        val page = ex.getPage(n)
        next = page.nextPage
        page.items.toVideos()
    }
}

/**
 * Paged Shorts for a search, from YouTube's own Shorts filter (NewPipe's video search leaves most
 * Shorts out). Results pass the same content filter as every other list.
 */
class ShortsSearch(private val query: String, private val filter: String = SORTS.first()) {
    private var next: String? = null
    private var started = false

    val hasMore: Boolean get() = !started || next != null

    suspend fun load(): List<VideoItem> {
        val (items, token) = withContext(Dispatchers.Default) {
            LibraryParser.parseShorts(InnerTube.shortsSearch(if (started) null else query, next, filter))
        }
        started = true
        next = token
        return ContentFilter.filter(items)
    }

    companion object {
        /** YouTube's codes for Shorts by relevance (twice, so it stays most common), date and views. */
        val SORTS = listOf("EgIQCQ==", "EgIQCQ==", "CAISAhAJ", "CAMSAhAJ")
    }
}

/** Keeps normal videos, or with [shorts] only Shorts, so the two never mix. */
private fun List<InfoItem>.toVideos(shorts: Boolean = false): List<VideoItem> =
    filterIsInstance<StreamInfoItem>()
        .filter { it.isShortItem() == shorts }
        .map { it.toVideoItem() }
        .let(ContentFilter::filter)

/**
 * Converts a NewPipe stream item. [fallbackChannel]/[fallbackChannelUrl]/[fallbackAvatar] fill in
 * the uploader for lists (like a channel's own Videos tab) whose items omit it.
 */
/** YouTube's own Shorts mark, or a video of a minute or less (older Shorts have no mark). */
private fun StreamInfoItem.isShortItem() = isShortFormContent || url.contains("/shorts/") || duration in 1L..60L

fun StreamInfoItem.toVideoItem(
    fallbackChannel: String? = null,
    fallbackChannelUrl: String? = null,
    fallbackAvatar: String? = null
): VideoItem = VideoItem(
    url = url,
    title = name,
    channel = uploaderName?.takeIf { it.isNotBlank() } ?: fallbackChannel.orEmpty(),
    thumb = thumbnails.bestUrl(),
    durationSec = duration,
    views = viewCount,
    uploaded = textualUploadDate,
    isLive = streamType == StreamType.LIVE_STREAM || streamType == StreamType.AUDIO_LIVE_STREAM,
    avatar = runCatching { uploaderAvatars.bestUrl() }.getOrNull() ?: fallbackAvatar,
    channelUrl = uploaderUrl?.takeIf { it.isNotBlank() } ?: fallbackChannelUrl,
    uploadedAt = runCatching { uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() }.getOrNull(),
    isShort = isShortItem(),
    membersOnly = contentAvailability == org.schabi.newpipe.extractor.stream.ContentAvailability.MEMBERSHIP ||
        contentAvailability == org.schabi.newpipe.extractor.stream.ContentAvailability.PAID
)

fun List<Image>.bestUrl(): String? = maxByOrNull { it.height }?.url
