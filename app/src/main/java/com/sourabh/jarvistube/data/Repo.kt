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
    val uploadedAt: Long? = null
)

object Repo {
    private class Entry(val at: Long, val deferred: Deferred<StreamInfo>)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = ConcurrentHashMap<String, Entry>()
    private val prefetchGate = Semaphore(2)
    private const val TTL_MS = 30 * 60 * 1000L

    private fun load(url: String): StreamInfo {
        val t0 = SystemClock.elapsedRealtime()
        val ex = ServiceList.YouTube.getStreamExtractor(url)
        ex.fetchPage()
        val t1 = SystemClock.elapsedRealtime()
        val info = StreamInfo.getInfo(ex)
        Dbg.log("streamInfo fetchPage=${t1 - t0}ms parse=${SystemClock.elapsedRealtime() - t1}ms")
        return info
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
                val t = SystemClock.elapsedRealtime()
                Graph.http.newCall(
                    okhttp3.Request.Builder().url("$u&range=0-0").header("User-Agent", BROWSER_UA).build()
                ).execute().use { r -> r.body?.bytes() }
                Dbg.log("warm ${Uri.parse(u).host} ${SystemClock.elapsedRealtime() - t}ms")
            } catch (_: Exception) {
            }
        }
    }

    private fun valid(e: Entry?): Boolean =
        e != null && SystemClock.elapsedRealtime() - e.at < TTL_MS && !e.deferred.isCancelled

    private fun start(url: String, gated: Boolean): Deferred<StreamInfo> {
        val d = scope.async {
            try {
                if (gated) prefetchGate.withPermit { load(url).also(::warmConnections) } else load(url)
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
        if (!valid(cache[url])) start(url, gated = true)
    }

    suspend fun streamInfo(url: String): StreamInfo {
        val e = cache[url]
        val d = if (valid(e)) e!!.deferred else start(url, gated = false)
        return d.await()
    }
}

/** Paged video search. Shorts are filtered out (distraction-free by default). */
class SearchSession(private val query: String) {
    private var extractor: SearchExtractor? = null
    private var next: Page? = null

    val hasMore: Boolean get() = next != null

    suspend fun loadFirst(): List<VideoItem> = withContext(Dispatchers.IO) {
        val ex = ServiceList.YouTube.getSearchExtractor(query, listOf("videos"), "")
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

private fun List<InfoItem>.toVideos(): List<VideoItem> =
    filterIsInstance<StreamInfoItem>()
        .filter { it.duration !in 1L..60L }
        .map { it.toVideoItem() }
        .let(ContentFilter::filter)

/**
 * Converts a NewPipe stream item. [fallbackChannel]/[fallbackChannelUrl]/[fallbackAvatar] fill in
 * the uploader for lists (like a channel's own Videos tab) whose items omit it.
 */
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
    uploadedAt = runCatching { uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() }.getOrNull()
)

fun List<Image>.bestUrl(): String? = maxByOrNull { it.height }?.url
