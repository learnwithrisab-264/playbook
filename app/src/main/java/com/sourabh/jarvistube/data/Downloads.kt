package com.sourabh.jarvistube.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.sourabh.jarvistube.Graph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

data class SavedItem(
    val id: String,
    val url: String,
    val title: String,
    val channel: String,
    val thumb: String?,
    val path: String,
    val durationSec: Long,
    val audioOnly: Boolean,
    val sizeBytes: Long
)

data class DlProgress(val title: String, val fraction: Float, val stage: String, val failed: Boolean = false, val paused: Boolean = false)

/** One choice in the download sheet; [quality] is the height (0 with [audioOnly]). */
data class DlOption(val label: String, val quality: Int, val audioOnly: Boolean, val bytes: Long)

/** Offline downloads: video+audio fetched in chunks, muxed on-device, then saved to the Gallery. */
object Downloads {
    val saved = mutableStateListOf<SavedItem>()
    val active = mutableStateMapOf<String, DlProgress>()

    private class Req(
        val key: String,
        val video: VideoItem,
        val info: StreamInfo,
        val quality: Int,
        val audioOnly: Boolean
    )

    private val queue = ArrayDeque<Req>()
    private val jobs = HashMap<String, Job>()
    /** Paused downloads: their part files stay, so Resume carries on where they stopped. */
    private val paused = HashMap<String, Req>()
    private val pausing = HashSet<String>()
    private lateinit var appCtx: Context
    private lateinit var dir: File

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        dir = (ctx.getExternalFilesDir("downloads") ?: File(ctx.filesDir, "downloads")).also { it.mkdirs() }
        load()
        // Downloads from older builds sit in app storage; move them to the Gallery once.
        if (saved.any { !isContent(it.path) }) {
            Thread {
                val moved = saved.map { s ->
                    if (isContent(s.path)) s else publish(File(s.path), s.audioOnly)?.let { s.copy(path = it) } ?: s
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    saved.clear(); saved.addAll(moved); persist()
                }
            }.start()
        }
    }

    /** `content://` = the copy in the phone's shared Movies/Music folder (shown in Gallery). */
    fun isContent(path: String) = path.startsWith("content://")

    /**
     * Moves a finished file into Movies/PlayBook (videos) or Music/PlayBook (audio) through
     * MediaStore, so Gallery and music apps list it. Needs no storage permission on Android 10+.
     * Returns the content URI, or null (the file then stays in app storage).
     */
    private fun publish(file: File, audioOnly: Boolean): String? {
        if (!file.exists()) return null
        val resolver = appCtx.contentResolver
        val collection = if (audioOnly) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (audioOnly) "audio/mp4" else "video/mp4")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                (if (audioOnly) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES) + "/PlayBook"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out, 1 shl 16) } }
                ?: throw IOException("No output stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            file.delete()
            uri.toString()
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    private fun exists(path: String): Boolean =
        if (isContent(path)) runCatching {
            appCtx.contentResolver.openFileDescriptor(Uri.parse(path), "r")?.use { true } ?: false
        }.getOrDefault(false)
        else File(path).exists()

    fun keyFor(video: VideoItem, quality: Int, audioOnly: Boolean) =
        video.url + if (audioOnly) "#audio" else "#$quality"

    fun enqueue(video: VideoItem, info: StreamInfo, quality: Int, audioOnly: Boolean) {
        val key = keyFor(video, quality, audioOnly)
        if (active[key]?.failed == false) return
        active[key] = DlProgress(video.title, -1f, "Queued")
        queue.add(Req(key, video, info, quality, audioOnly))
        appCtx.startForegroundService(Intent(appCtx, DownloadService::class.java))
    }

    fun cancel(key: String) {
        pausing.remove(key)
        jobs[key]?.cancel()
        queue.removeAll { it.key == key }
        paused.remove(key)?.let { deleteParts(it) }
        active.remove(key)
    }

    /** Stops a download for now, keeping what has arrived. */
    fun pause(key: String) {
        val waiting = queue.firstOrNull { it.key == key }
        if (waiting != null) {
            queue.remove(waiting)
            paused[key] = waiting
            active[key]?.let { active[key] = it.copy(stage = "Paused", paused = true) }
            return
        }
        if (jobs[key] == null) return
        pausing.add(key)
        jobs[key]?.cancel()
    }

    /** Carries on a paused download from where it stopped. */
    fun resume(key: String) {
        val r = paused.remove(key) ?: return
        active[key]?.let { active[key] = it.copy(stage = "Resuming", paused = false) }
        queue.addFirst(r)
        appCtx.startForegroundService(Intent(appCtx, DownloadService::class.java))
    }

    private fun baseName(r: Req): String {
        val id = SponsorBlock.videoId(r.video.url) ?: r.video.url.hashCode().toString()
        return r.video.title.replace(Regex("[^\\p{L}\\p{N} ._-]"), "").trim().take(60).ifEmpty { "video" } + " [$id]"
    }

    private fun deleteParts(r: Req) {
        val base = baseName(r)
        listOf("$base.m4a.part", "$base.mp4.part", "$base.v.part", "$base.a.part").forEach { File(dir, it).delete() }
    }

    fun delete(item: SavedItem) {
        if (isContent(item.path)) runCatching { appCtx.contentResolver.delete(Uri.parse(item.path), null, null) }
        else File(item.path).delete()
        saved.removeAll { it.id == item.id }
        persist()
    }

    /** Drains the queue; called by [DownloadService]. */
    suspend fun runQueue() = coroutineScope {
        while (queue.isNotEmpty()) {
            val r = queue.removeFirst()
            val j = launch { process(r) }
            jobs[r.key] = j
            j.join()
            jobs.remove(r.key)
        }
    }

    // ---------------------------------------------------------------- work

    private class Tracker(val key: String, val title: String, val total: Long) {
        @Volatile var done = 0L
        @Volatile var stage = "Downloading"
        private var shownAt = 0L
        private val startedAt = android.os.SystemClock.elapsedRealtime()
        private var startDone = -1L
        /** Progress shows about four times a second, not on every 64 KB: the list stays smooth. */
        @Synchronized fun add(n: Int) {
            if (startDone < 0) startDone = done
            done += n
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - shownAt < 250 && (total <= 0 || done < total)) return
            shownAt = now
            val secs = (now - startedAt) / 1000f
            val speed = if (secs > 0.5f) ((done - startDone) / secs).toLong() else 0L
            val text = if (speed > 0) "$stage · ${formatSpeed(speed)}" else stage
            val p = DlProgress(title, if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f, text)
            android.os.Handler(android.os.Looper.getMainLooper()).post { if (active[key]?.paused != true) active[key] = p }
        }
    }

    private fun formatSpeed(bps: Long): String =
        if (bps >= 1_000_000) String.format(java.util.Locale.US, "%.1f MB/s", bps / 1_000_000f) else "${bps / 1000} KB/s"

    private suspend fun process(r: Req) {
        val base = baseName(r)
        val temps = ArrayList<File>()
        var keepParts = false
        try {
            val out: File
            if (r.audioOnly) {
                val a = Graph.player.pickAudio(r.info) ?: throw IOException("No audio stream available")
                val t = Tracker(r.key, r.video.title, clen(a.content))
                out = File(dir, "$base.m4a")
                val part = File(dir, "$base.m4a.part").also(temps::add)
                fetch(a.content, part, t)
                part.renameTo(out)
            } else {
                val v = Graph.player.pickVideo(r.info, r.quality, avcOnly = true) ?: throw IOException("No video stream available")
                out = File(dir, "$base.mp4")
                if (!v.isVideoOnly) {
                    val t = Tracker(r.key, r.video.title, clen(v.content))
                    val part = File(dir, "$base.mp4.part").also(temps::add)
                    fetch(v.content, part, t)
                    part.renameTo(out)
                } else {
                    val a = Graph.player.pickAudio(r.info) ?: throw IOException("No audio stream available")
                    val cv = clen(v.content)
                    val ca = clen(a.content)
                    val t = Tracker(r.key, r.video.title, if (cv > 0 && ca > 0) cv + ca else 0)
                    val vf = File(dir, "$base.v.part").also(temps::add)
                    val af = File(dir, "$base.a.part").also(temps::add)
                    // Picture and sound arrive together, so the whole thing finishes sooner.
                    t.stage = "Downloading"
                    coroutineScope {
                        val vj = async { fetch(v.content, vf, t) }
                        val aj = async { fetch(a.content, af, t) }
                        vj.await(); aj.await()
                    }
                    active[r.key] = DlProgress(r.video.title, -1f, "Merging")
                    withContext(Dispatchers.IO) { mux(vf, af, out) }
                }
            }
            val size = out.length()
            active[r.key] = DlProgress(r.video.title, -1f, "Saving to Gallery")
            val location = withContext(Dispatchers.IO) { publish(out, r.audioOnly) } ?: out.absolutePath
            saved.add(
                0,
                SavedItem(
                    id = r.key, url = r.video.url, title = r.video.title, channel = r.video.channel,
                    thumb = r.video.thumb, path = location, durationSec = r.video.durationSec,
                    audioOnly = r.audioOnly, sizeBytes = size
                )
            )
            persist()
            active.remove(r.key)
        } catch (e: CancellationException) {
            if (pausing.remove(r.key)) {
                // Paused: keep the parts and the place in the list.
                keepParts = true
                paused[r.key] = r
                val p = active[r.key]
                active[r.key] = DlProgress(r.video.title, p?.fraction ?: -1f, "Paused", paused = true)
            } else active.remove(r.key)
            throw e
        } catch (e: Exception) {
            active[r.key] = DlProgress(r.video.title, 0f, e.message ?: "Download failed", failed = true)
        } finally {
            if (!keepParts) temps.forEach { it.delete() }
        }
    }

    /** What can be downloaded for this video: each MP4 resolution and audio only, with sizes. */
    fun options(info: StreamInfo): List<DlOption> {
        val player = Graph.player
        val audio = player.pickAudio(info)
        val aBytes = audio?.let { clen(it.content) } ?: 0L
        val heights = (info.videoOnlyStreams + info.videoStreams)
            .filter { it.isUrl && it.height > 0 && com.sourabh.jarvistube.player.VideoCodecs.mimeOf(it) == com.sourabh.jarvistube.player.VideoCodecs.AVC }
            .map { player.qualityOf(it) }.distinct().sortedDescending()
        val videos = heights.mapNotNull { h ->
            val v = player.pickVideo(info, h, avcOnly = true)?.takeIf { player.qualityOf(it) == h } ?: return@mapNotNull null
            if (v.isVideoOnly && audio == null) return@mapNotNull null
            DlOption("${h}p", h, false, clen(v.content) + if (v.isVideoOnly) aBytes else 0L)
        }
        return videos + listOfNotNull(audio?.let { DlOption("Audio only", 0, true, aBytes) })
    }

    /** Space taken by downloads, and free space left on the phone. */
    fun usedBytes(): Long = saved.sumOf { it.sizeBytes }
    fun freeBytes(): Long = runCatching { android.os.StatFs(Environment.getExternalStorageDirectory().path).availableBytes }.getOrDefault(0L)

    private fun clen(url: String): Long =
        Regex("[?&]clen=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

    /** Chunked download (googlevideo throttles single long requests). */
    private suspend fun fetch(url: String, dest: File, t: Tracker) = withContext(Dispatchers.IO) {
        val chunk = 9_900_000L
        // A paused download carries on from what it already has.
        var offset = if (dest.exists()) dest.length() else 0L
        val total = clen(url)
        if (offset > 0) synchronized(t) { t.done += offset }
        if (total in 1..offset) return@withContext
        var finished = false
        java.io.FileOutputStream(dest, offset > 0).buffered(256 * 1024).use { os ->
            val buf = ByteArray(128 * 1024)
            while (!finished) {
                currentCoroutineContext().ensureActive()
                val req = Request.Builder()
                    .url("$url&range=$offset-${offset + chunk - 1}")
                    .header("User-Agent", BROWSER_UA)
                    .build()
                var got = 0L
                Graph.http.newCall(req).execute().use { r ->
                    if (r.code == 416) {
                        finished = true
                        return@use
                    }
                    if (!r.isSuccessful) throw IOException("Download failed (HTTP ${r.code})")
                    val ins = r.body?.byteStream() ?: throw IOException("Empty response")
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = ins.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n)
                        got += n
                        t.add(n)
                    }
                }
                offset += got
                if (got < chunk) finished = true
            }
        }
    }

    private fun mux(videoFile: File, audioFile: File, out: File) {
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val vEx = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
        val aEx = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }
        try {
            val vt = trackOf(vEx, "video/")
            val at = trackOf(aEx, "audio/")
            vEx.selectTrack(vt)
            aEx.selectTrack(at)
            val vOut = muxer.addTrack(vEx.getTrackFormat(vt))
            val aOut = muxer.addTrack(aEx.getTrackFormat(at))
            muxer.start()
            // Picture and sound written side by side in time order: much faster than all of one
            // then all of the other, which makes the MP4 writer buffer the whole first track.
            copyInterleaved(vEx, vOut, aEx, aOut, muxer)
            muxer.stop()
        } finally {
            runCatching { muxer.release() }
            vEx.release()
            aEx.release()
        }
    }

    private fun trackOf(ex: MediaExtractor, prefix: String): Int {
        for (i in 0 until ex.trackCount) {
            if (ex.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME).orEmpty().startsWith(prefix)) return i
        }
        throw IOException("Missing $prefix track")
    }

    private fun copyInterleaved(vEx: MediaExtractor, vOut: Int, aEx: MediaExtractor, aOut: Int, muxer: MediaMuxer) {
        val buf = ByteBuffer.allocateDirect(4 * 1024 * 1024)
        val info = MediaCodec.BufferInfo()
        var vDone = false
        var aDone = false
        while (!vDone || !aDone) {
            // Whichever track is behind in time goes next.
            val useVideo = !vDone && (aDone || vEx.sampleTime <= aEx.sampleTime)
            val ex = if (useVideo) vEx else aEx
            buf.clear()
            val size = ex.readSampleData(buf, 0)
            if (size < 0) {
                if (useVideo) vDone = true else aDone = true
                continue
            }
            info.set(
                0, size, ex.sampleTime,
                if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            )
            muxer.writeSampleData(if (useVideo) vOut else aOut, buf, info)
            ex.advance()
        }
    }

    private fun copySamples(ex: MediaExtractor, muxer: MediaMuxer, outTrack: Int) {
        val buf = ByteBuffer.allocate(2 * 1024 * 1024)
        val info = MediaCodec.BufferInfo()
        while (true) {
            buf.clear()
            val size = ex.readSampleData(buf, 0)
            if (size < 0) break
            info.set(
                0, size, ex.sampleTime,
                if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            )
            muxer.writeSampleData(outTrack, buf, info)
            ex.advance()
        }
    }

    // ---------------------------------------------------------------- index

    private fun indexFile() = File(dir, "index.json")

    private fun load() {
        saved.clear()
        val f = indexFile()
        if (!f.exists()) return
        runCatching {
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val item = SavedItem(
                    o.getString("id"), o.getString("url"), o.getString("title"), o.optString("channel"),
                    o.optString("thumb").ifEmpty { null }, o.getString("path"), o.optLong("duration"),
                    o.optBoolean("audioOnly"), o.optLong("size")
                )
                if (exists(item.path)) saved.add(item)
            }
        }
    }

    private fun persist() {
        val arr = JSONArray()
        saved.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("url", it.url).put("title", it.title)
                    .put("channel", it.channel).put("thumb", it.thumb ?: "").put("path", it.path)
                    .put("duration", it.durationSec).put("audioOnly", it.audioOnly).put("size", it.sizeBytes)
            )
        }
        indexFile().writeText(arr.toString())
    }
}
