package com.sourabh.jarvistube.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sourabh.jarvistube.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** A shelf from YouTube Music's home for your account ("Quick picks", "Mixed for you" …). */
data class MusicShelf(val title: String, val cards: List<MusicCard>)

/** One item on a shelf: a song (plays at once) or a mix/playlist/album (opens its songs). */
data class MusicCard(
    val title: String,
    val subtitle: String,
    val art: String?,
    val videoId: String? = null,
    val playlistId: String? = null,
    val albumId: String? = null
) {
    val isSong: Boolean get() = videoId != null && playlistId == null
    fun asSong(): VideoItem = VideoItem(
        url = "https://www.youtube.com/watch?v=$videoId", title = title,
        channel = subtitle.substringBefore(" • "), thumb = art, durationSec = 0,
        views = -1, uploaded = null, isLive = false
    )
}

/** A mix made from what you play in this app, opened like a playlist. */
data class LocalMix(val title: String, val subtitle: String, val art: String?, val load: suspend () -> List<VideoItem>)

/**
 * "Made for you" in the Music tab, from what you listen to:
 *  - in this app: play counts per song and artist (kept on the phone), turned into mixes of your
 *    top artists, "On repeat" and radios of songs you play most;
 *  - on your YouTube account: YouTube Music's own home for you (Quick picks, Mixed for you …),
 *    which already knows what you listen to on YouTube and YouTube Music.
 * Only the Music tab uses this; nothing else in the app changes.
 */
object MusicForYou {
    private lateinit var prefs: SharedPreferences
    private const val MUSIC = "https://music.youtube.com"
    private const val CLIENT_VERSION = "1.20250101.01.00"

    /** Plays per song (url → count and the song), newest last played first. */
    private val plays = LinkedHashMap<String, Pair<Int, VideoItem>>()

    /** Account shelves, loaded once per app run (and on refresh). */
    var shelves by mutableStateOf<List<MusicShelf>>(emptyList()); private set
    var shelvesLoading by mutableStateOf(false); private set
    private var shelvesFor: String? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("music_plays", Context.MODE_PRIVATE)
        runCatching {
            val arr = JSONArray(prefs.getString("plays", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val v = VideoItem(
                    url = o.getString("url"), title = o.getString("title"), channel = o.optString("artist"),
                    thumb = o.optString("art").ifEmpty { null }, durationSec = o.optLong("dur"),
                    views = -1, uploaded = null, isLive = false
                )
                plays[v.url] = o.optInt("n", 1) to v
            }
        }
    }

    /** First run: what you already played (Recently played) starts your counts. */
    fun seedIfEmpty(recent: List<VideoItem>) {
        if (plays.isEmpty()) recent.asReversed().forEach(::played)
    }

    /** A song started in the Music tab: counted towards your taste. */
    fun played(song: VideoItem) {
        if (!::prefs.isInitialized) return
        val before = plays.remove(song.url)
        plays[song.url] = ((before?.first ?: 0) + 1) to song
        while (plays.size > 300) plays.remove(plays.keys.first())
        val arr = JSONArray()
        plays.values.forEach { (n, v) ->
            arr.put(JSONObject().put("url", v.url).put("title", v.title).put("artist", v.channel)
                .put("art", v.thumb.orEmpty()).put("dur", v.durationSec).put("n", n))
        }
        prefs.edit().putString("plays", arr.toString()).apply()
    }

    private fun mainArtist(v: VideoItem) = v.channel.split(",", "&", " x ", " feat", " ft.").first().trim()

    /** Mixes from what you played here: your top artists, On repeat, and a radio of your latest song. */
    fun localMixes(): List<LocalMix> {
        if (plays.isEmpty()) return emptyList()
        val songs = plays.values.toList().asReversed() // most recent first
        val out = ArrayList<LocalMix>()
        // Artists by how often you play them (recent breaks ties).
        val byArtist = songs.groupBy { mainArtist(it.second) }.filterKeys { it.isNotBlank() }
            .mapValues { (_, list) -> list.sumOf { it.first } }
            .entries.sortedByDescending { it.value }.map { it.key }
        byArtist.take(3).forEachIndexed { i, artist ->
            val top = songs.filter { mainArtist(it.second) == artist }.maxByOrNull { it.first }!!.second
            val others = byArtist.filter { it != artist }.take(2)
            out += LocalMix(
                "Daily Mix ${i + 1}",
                (listOf(artist) + others).joinToString(", ") + " and more",
                top.thumb
            ) {
                // The artist's radio (songs like theirs) mixed with more of their own songs.
                val radio = runCatching { radio(top) }.getOrDefault(emptyList())
                val own = runCatching { Music.songs("$artist songs") }.getOrDefault(emptyList())
                interleave(radio, own).distinctBy { it.url }.take(50)
            }
        }
        val repeat = songs.filter { it.first >= 2 }.sortedByDescending { it.first }.map { it.second }
        if (repeat.size >= 3) out += LocalMix("On repeat", "Songs you play again and again", repeat.first().thumb) { repeat }
        songs.firstOrNull()?.second?.let { last ->
            out += LocalMix("Because you listened to ${last.title.substringBefore(" (")}", "Songs like it", last.thumb) {
                radio(last)
            }
        }
        return out
    }

    private fun interleave(a: List<VideoItem>, b: List<VideoItem>): List<VideoItem> {
        val out = ArrayList<VideoItem>()
        for (i in 0 until maxOf(a.size, b.size)) {
            a.getOrNull(i)?.let(out::add)
            b.getOrNull(i)?.let(out::add)
        }
        return out
    }

    /** "Song radio": YouTube Music's endless list of songs like [song]. */
    suspend fun radio(song: VideoItem): List<VideoItem> {
        val id = SponsorBlock.videoId(song.url) ?: return emptyList()
        return queue(id, "RDAMVM$id").filter { it.url != song.url }
    }

    /** The songs of a mix, playlist or radio (YouTube Music's "Up next" for it). */
    suspend fun queue(videoId: String?, playlistId: String?): List<VideoItem> {
        val body = JSONObject().put("isAudioOnly", true).put("enablePersistentPlaylistPanel", true)
            .put("tunerSettingValue", "AUTOMIX_SETTING_NORMAL")
        videoId?.let { body.put("videoId", it) }
        playlistId?.let { body.put("playlistId", it) }
        val json = post("next", body)
        val out = ArrayList<VideoItem>()
        forEach(json, "playlistPanelVideoRenderer") { r ->
            val id = r.optString("videoId").ifEmpty { return@forEach }
            val title = runs(r.optJSONObject("title")).ifEmpty { return@forEach }
            out += VideoItem(
                url = "https://www.youtube.com/watch?v=$id",
                title = title,
                channel = r.optJSONObject("longBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty(),
                thumb = Music.art(lastThumb(r.optJSONObject("thumbnail"))),
                durationSec = clockSeconds(runs(r.optJSONObject("lengthText"))),
                views = -1, uploaded = null, isLive = false
            )
        }
        return out.distinctBy { it.url }.filterNot(ContentFilter::hides)
    }

    /** An album's songs: its playlist, then the queue. */
    suspend fun album(albumId: String): List<VideoItem> {
        val json = post("browse", JSONObject().put("browseId", albumId))
        var pid: String? = null
        forEach(json, "watchPlaylistEndpoint") { if (pid == null) pid = it.optString("playlistId").ifEmpty { null } }
        if (pid == null) forEach(json, "watchEndpoint") { if (pid == null) pid = it.optString("playlistId").ifEmpty { null } }
        return pid?.let { queue(null, it) }.orEmpty()
    }

    /** YouTube Music's home for the signed-in account (once per run; [force] to refresh). */
    suspend fun loadShelves(force: Boolean = false) {
        val who = if (Session.isSignedIn) Session.dataKey else null
        if (who == null) { shelves = emptyList(); shelvesFor = null; return }
        if (!force && shelvesFor == who && shelves.isNotEmpty()) return
        shelvesLoading = true
        try {
            val json = post("browse", JSONObject().put("browseId", "FEmusic_home"), signedIn = true)
            val out = ArrayList<MusicShelf>()
            forEach(json, "musicCarouselShelfRenderer") { shelf ->
                val title = runs(
                    shelf.optJSONObject("header")?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")?.optJSONObject("title")
                ).ifEmpty { return@forEach }
                val cards = ArrayList<MusicCard>()
                val items = shelf.optJSONArray("contents") ?: return@forEach
                for (i in 0 until items.length()) {
                    val it = items.optJSONObject(i) ?: continue
                    it.optJSONObject("musicResponsiveListItemRenderer")?.let(::songCard)?.let(cards::add)
                    it.optJSONObject("musicTwoRowItemRenderer")?.let(::twoRowCard)?.let(cards::add)
                }
                if (cards.isNotEmpty()) out += MusicShelf(title, cards)
            }
            shelves = out
            shelvesFor = who
        } catch (e: Exception) {
            if (force || shelves.isEmpty()) shelves = emptyList()
        } finally {
            shelvesLoading = false
        }
    }

    private fun songCard(r: JSONObject): MusicCard? {
        val cols = r.optJSONArray("flexColumns") ?: return null
        fun col(i: Int) = runs(cols.optJSONObject(i)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text"))
        var id: String? = r.optJSONObject("playlistItemData")?.optString("videoId")?.ifEmpty { null }
        if (id == null) forEach(r, "watchEndpoint") { if (id == null) id = it.optString("videoId").ifEmpty { null } }
        val title = col(0).ifEmpty { return null }
        return MusicCard(title, col(1), Music.art(lastThumb(r.optJSONObject("thumbnail"))), videoId = id ?: return null)
    }

    private fun twoRowCard(r: JSONObject): MusicCard? {
        val title = runs(r.optJSONObject("title")).ifEmpty { return null }
        val subtitle = runs(r.optJSONObject("subtitle"))
        val art = Music.art(lastThumb(r.optJSONObject("thumbnailRenderer")))
        val nav = r.optJSONObject("navigationEndpoint") ?: return null
        nav.optJSONObject("watchEndpoint")?.let { w ->
            return MusicCard(title, subtitle, art, videoId = w.optString("videoId").ifEmpty { null }, playlistId = w.optString("playlistId").ifEmpty { null })
        }
        val browse = nav.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
        return when {
            browse.startsWith("VL") -> MusicCard(title, subtitle, art, playlistId = browse.removePrefix("VL"))
            browse.startsWith("MPRE") -> MusicCard(title, subtitle, art, albumId = browse)
            else -> null // artists, podcasts: not songs
        }
    }

    /** The songs behind a card that isn't a single song. */
    suspend fun open(card: MusicCard): List<VideoItem> = when {
        card.albumId != null -> album(card.albumId)
        else -> queue(card.videoId, card.playlistId)
    }

    // ---- YouTube Music's web API ----

    private suspend fun post(endpoint: String, body: JSONObject, signedIn: Boolean = Session.isSignedIn): JSONObject =
        withContext(Dispatchers.IO) {
            body.put(
                "context", JSONObject().put(
                    "client", JSONObject().put("clientName", "WEB_REMIX").put("clientVersion", CLIENT_VERSION)
                        .put("hl", "en").put("gl", com.sourabh.jarvistube.AppSettings.region)
                )
            )
            val req = Request.Builder()
                .url("$MUSIC/youtubei/v1/$endpoint?prettyPrint=false")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .header("User-Agent", WEB_UA)
                .header("Origin", MUSIC)
                .header("X-Origin", MUSIC)
                .header("X-Youtube-Client-Name", "67")
                .header("X-Youtube-Client-Version", CLIENT_VERSION)
            val cookie = Session.active?.cookie
            val sapisid = cookie?.let(Session::sapisidOf)
            if (signedIn && cookie != null && sapisid != null) {
                val ts = System.currentTimeMillis() / 1000
                req.header("Cookie", cookie)
                    .header("Authorization", "SAPISIDHASH ${ts}_${sha1("$ts $sapisid $MUSIC")}")
                    .header("X-Goog-AuthUser", "0")
            }
            Graph.http.newCall(req.build()).execute().use { r ->
                if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
                JSONObject(r.body?.string() ?: throw java.io.IOException("Empty response"))
            }
        }

    private fun runs(o: JSONObject?): String {
        if (o == null) return ""
        o.optString("simpleText").takeIf { it.isNotEmpty() }?.let { return it }
        val r = o.optJSONArray("runs") ?: return ""
        return (0 until r.length()).joinToString("") { r.optJSONObject(it)?.optString("text").orEmpty() }
    }

    private fun lastThumb(o: JSONObject?): String? {
        var url: String? = null
        forEach(o, "thumbnail") { t ->
            val arr = t.optJSONArray("thumbnails") ?: return@forEach
            arr.optJSONObject(arr.length() - 1)?.optString("url")?.let { url = it }
        }
        if (url == null) o?.optJSONArray("thumbnails")?.let { arr -> url = arr.optJSONObject(arr.length() - 1)?.optString("url") }
        return url
    }

    private fun clockSeconds(t: String): Long =
        t.split(":").mapNotNull { it.trim().toLongOrNull() }.fold(0L) { acc, n -> acc * 60 + n }

    private fun forEach(node: Any?, key: String, block: (JSONObject) -> Unit) {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = node.opt(k)
                    if (k == key && v is JSONObject) block(v) else forEach(v, key, block)
                }
            }
            is JSONArray -> for (i in 0 until node.length()) forEach(node.opt(i), key, block)
        }
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
