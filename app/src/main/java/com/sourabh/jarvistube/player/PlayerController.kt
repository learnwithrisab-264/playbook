package com.sourabh.jarvistube.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sourabh.jarvistube.data.SavedItem
import com.sourabh.jarvistube.data.SkipSegment
import com.sourabh.jarvistube.data.SponsorBlock
import kotlinx.coroutines.delay
import java.io.File
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.common.MimeTypes
import com.sourabh.jarvistube.data.BROWSER_UA
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.data.Repo
import com.sourabh.jarvistube.data.VideoItem
import com.sourabh.jarvistube.data.toVideoItem
import com.sourabh.jarvistube.data.bestUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

data class NowPlaying(
    val video: VideoItem,
    val info: StreamInfo? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val quality: Int = 720,
    val heights: List<Int> = emptyList(),
    val audioTracks: List<AudioTrackOption> = emptyList(),
    val audioTrackId: String? = null,
    /** true = "Auto" quality (picked from the network); false = a resolution the user chose. */
    val autoQuality: Boolean = true,
    /** Height of the stream actually playing (may be below [quality] if that isn't offered). */
    val playingHeight: Int = 0,
    val subtitles: List<SubtitleOption> = emptyList(),
    val subtitleId: String? = null
)

/** One selectable audio language/dub. [original] marks the video's own (undubbed) track. */
data class AudioTrackOption(val id: String, val label: String, val original: Boolean)

/** One caption language. [id] distinguishes manual from auto-generated captions. */
data class SubtitleOption(val id: String, val label: String, val language: String, val url: String, val mime: String)

/** Owns the single ExoPlayer for the whole app; the foreground service just publishes it. */
@OptIn(UnstableApi::class)
class PlayerController(val app: Context, http: OkHttpClient) {

    val player: ExoPlayer = ExoPlayer.Builder(app)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        // Rewind / Forward on the notification, lock screen and headphones.
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(10_000)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        // Smoothness: async codec queueing keeps the decoder fed without main-thread hops,
        // decoder fallback avoids hard failures on picky hardware, and a deeper buffer rides
        // out network dips so playback doesn't stall.
        .setRenderersFactory(
            object : androidx.media3.exoplayer.DefaultRenderersFactory(app) {
                // Side-loaded YouTube captions (TTML/WebVTT) reach the text renderer as raw
                // samples; Media3 1.4+ refuses those unless legacy decoding is switched on.
                override fun buildTextRenderers(
                    context: Context,
                    output: androidx.media3.exoplayer.text.TextOutput,
                    outputLooper: android.os.Looper,
                    extensionRendererMode: Int,
                    out: ArrayList<androidx.media3.exoplayer.Renderer>
                ) {
                    super.buildTextRenderers(context, output, outputLooper, extensionRendererMode, out)
                    out.filterIsInstance<androidx.media3.exoplayer.text.TextRenderer>()
                        .forEach { it.experimentalSetLegacyDecodingEnabled(true) }
                }
            }
                .setEnableDecoderFallback(true)
                .forceEnableMediaCodecAsynchronousQueueing()
        )
        .setLoadControl(
            androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(30_000, 90_000, 1_000, 3_000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        )
        // Seeks land on the nearest keyframe (as YouTube does) instead of decoding up to the exact
        // millisecond, so a jump far into a long lecture needs less data before the picture shows.
        .setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dataSource = OkHttpDataSource.Factory(http).setUserAgent(BROWSER_UA)
    private val chunkedFactory = androidx.media3.datasource.DataSource.Factory {
        ChunkedHttpDataSource(dataSource.createDataSource())
    }
    private var job: Job? = null

    private val _now = MutableStateFlow<NowPlaying?>(null)
    val now: StateFlow<NowPlaying?> = _now

    private var preferredQuality = 720

    private val prefs = app.getSharedPreferences("player", Context.MODE_PRIVATE)
    private val resume = app.getSharedPreferences("resume", Context.MODE_PRIVATE)

    var sponsorBlock by mutableStateOf(prefs.getBoolean("sponsorBlock", true)); private set
    /** "Listen" mode: sound only. The video track isn't loaded at all (saves data and battery). Remembered. */
    var audioOnly by mutableStateOf(prefs.getBoolean("audioOnly", false)); private set
    var sleepEndAt by mutableLongStateOf(0L); private set
    private var sleepJob: Job? = null
    private var segments: List<SkipSegment> = emptyList()

    /** URL of the video whose first frame has been drawn; until then the UI shows its thumbnail. */
    private val _firstFrameUrl = MutableStateFlow<String?>(null)
    val firstFrameUrl: StateFlow<String?> = _firstFrameUrl

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    init {
        applyAudioOnly() // a saved Listen mode applies from the first song
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                com.sourabh.jarvistube.data.Dbg.log("error ${error.errorCodeName} at ${player.currentPosition}")
                val networkish = error.errorCode in 2000..2999
                if (networkish && autoRecoveries < 2 && _now.value?.info != null) {
                    autoRecoveries++
                    showNotice("Reconnecting…")
                    retry()
                    return
                }
                _now.update { it?.copy(loading = false, error = "Playback error: ${error.errorCodeName}") }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) savePosition()
                else autoRecoveries = 0
            }

            override fun onRenderedFirstFrame() {
                com.sourabh.jarvistube.data.Dbg.log("first frame +${android.os.SystemClock.elapsedRealtime() - tPlay}ms")
                _firstFrameUrl.value = _now.value?.video?.url
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    preparedUrl?.let(::forgetPosition)
                    // Ended within moments of resuming = a stale resume point at the very end:
                    // start the video from the beginning instead of skipping to the next one.
                    if (resumedFrom > 0 && android.os.SystemClock.elapsedRealtime() - preparedAt < 5_000) {
                        resumedFrom = 0
                        player.seekTo(0)
                        player.play()
                    } else {
                        playNext()
                    }
                }
            }
        })
        // Study clock, focus time, Pomodoro breaks and the free-time limit, once a second for the
        // whole app (not tied to the screen), so background playback is counted and stopped too.
        scope.launch {
            while (true) {
                delay(1000)
                val study = Graph.study
                when (val r = study.onSecond(player.isPlaying)) {
                    null -> Unit
                    "BREAK" -> {
                        player.pause()
                        showNotice("Break time: ${study.breakMin} min. Look away from the screen.")
                    }
                    "LIMIT" -> {
                        stop()
                        study.saveUsage()
                        android.widget.Toast.makeText(app, "Daily free-time limit reached.", android.widget.Toast.LENGTH_LONG).show()
                    }
                    else -> showNotice(r)
                }
            }
        }
        // SponsorBlock skipping + periodic resume-position saving.
        scope.launch {
            var tick = 0
            while (true) {
                delay(400)
                if (!player.isPlaying) continue
                tick++
                if (sponsorBlock) checkSkip(player.currentPosition)
                if (tick % 12 == 0) savePosition()
            }
        }
    }

    // ---- Queue (a video ending only advances to what you queued; no autoplay recommendations) ----
    val queue = androidx.compose.runtime.mutableStateListOf<VideoItem>()

    /** What played before the current item (most recent last), for Previous. */
    private val history = ArrayDeque<VideoItem>()

    /** Each account keeps its own queue while the app runs; Previous never crosses accounts. */
    private val queues = HashMap<String, List<VideoItem>>()
    private var queueOwner = com.sourabh.jarvistube.data.Session.dataKey

    init {
        com.sourabh.jarvistube.data.Session.onDataKeyChange { key ->
            queues[queueOwner] = queue.toList()
            queueOwner = key
            queue.clear()
            queue.addAll(queues[key].orEmpty())
            history.clear()
        }
    }

    fun enqueue(video: VideoItem) {
        if (queue.none { it.url == video.url } && _now.value?.video?.url != video.url) queue.add(video)
    }

    fun enqueueAll(videos: List<VideoItem>) = videos.forEach(::enqueue)

    /** "Play next": puts [video] at the front of the queue (moving it there if already queued). */
    fun playNextUp(video: VideoItem) {
        if (_now.value?.video?.url == video.url) return
        queue.removeAll { it.url == video.url }
        queue.add(0, video)
    }

    fun removeFromQueue(index: Int) {
        if (index in queue.indices) queue.removeAt(index)
    }

    /** Moves a queued item (reordering in the queue sheet). */
    fun moveInQueue(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices || from == to) return
        queue.add(to, queue.removeAt(from))
    }

    fun clearQueue() = queue.clear()

    /**
     * The same gate as tapping a video (TubeViewModel.play): content filter, then study session /
     * lock / break / daily free-time limit. Null = allowed.
     */
    private fun blockReason(v: VideoItem): String? =
        com.sourabh.jarvistube.data.ContentFilter.blockReason(null, v.channel, v.title) ?: Graph.study.blockReason(v)

    /** Plays the next allowed item in the queue. Returns false if nothing was played. */
    fun playNext(): Boolean {
        while (queue.isNotEmpty()) {
            val next = queue.removeAt(0)
            if (blockReason(next) == null) {
                play(next)
                return true
            }
        }
        return false
    }

    /**
     * The Next button (mini-player, notification, lock screen, earphones): the next queued item;
     * with an empty queue, a related video, else the channel's next upload. Anything the study
     * rules or the filter would block is skipped, so during a session or lock Next never lands on
     * something that isn't allowed.
     */
    fun skipNext() {
        if (playNext()) return
        val cur = _now.value ?: return
        nextJob?.cancel()
        nextJob = scope.launch {
            showNotice("Finding the next video…", 1500)
            val pick = try {
                fallbackNext(cur)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (pick != null) play(pick)
            else showNotice(
                if (Graph.study.lockActive) "Study lock: only the locked video can play until the timer ends."
                else "Nothing else allowed to play. Add videos to the queue."
            )
        }
    }
    private var nextJob: Job? = null

    /** Runs on the main thread; only the channel fetch goes to the network. */
    private suspend fun fallbackNext(cur: NowPlaying): VideoItem? {
        if (Graph.study.lockActive) return null // only the locked video may play
        val seen = history.map { it.url }.toSet() + cur.video.url
        fun ok(v: VideoItem) = v.url !in seen && v.durationSec !in 1L..60L && blockReason(v) == null
        val related = cur.info?.relatedItems.orEmpty()
            .filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
            .map { it.toVideoItem() }
            .let(com.sourabh.jarvistube.data.ContentFilter::filter)
        related.firstOrNull(::ok)?.let { return it }
        // The channel's uploads: the one just older than this video, else any not played yet.
        val channelUrl = cur.info?.uploaderUrl?.takeIf { it.isNotBlank() } ?: cur.video.channelUrl ?: return null
        val uploads = com.sourabh.jarvistube.data.ChannelRepo.latestVideos(channelUrl)
        val idx = uploads.indexOfFirst { it.url == cur.video.url }
        val after = if (idx >= 0) uploads.drop(idx + 1) + uploads.take(idx) else uploads
        return after.firstOrNull(::ok)
    }

    /**
     * The Previous button: restarts the current item after its first 5 seconds (like YouTube
     * Music); otherwise goes back to the last item played (the current one is put back at the
     * front of the queue, so Next returns to it).
     */
    fun skipPrevious() {
        if (player.currentPosition > 5_000 || history.isEmpty()) {
            player.seekTo(0)
            return
        }
        while (history.isNotEmpty()) {
            val prev = history.removeLast()
            if (blockReason(prev) == null) {
                _now.value?.video?.let { cur -> queue.removeAll { it.url == cur.url }; queue.add(0, cur) }
                play(prev, record = false)
                return
            }
        }
        player.seekTo(0)
    }

    private fun checkSkip(pos: Long) {
        val s = segments.firstOrNull {
            pos >= (it.start * 1000).toLong() && pos < (it.end * 1000).toLong() - 500
        } ?: return
        player.seekTo((s.end * 1000).toLong())
        showNotice("Skipped ${s.label}")
    }

    private fun showNotice(msg: String, ms: Long = 3000) {
        _notice.value = msg
        scope.launch {
            delay(ms)
            if (_notice.value == msg) _notice.value = null
        }
    }

    // Which video the player actually has loaded. _now switches to a new video the instant it is
    // tapped, while the old media keeps playing until the new one is prepared; saving against
    // _now therefore stored the OLD position under the NEW video (wrong resume points, and
    // "instantly ended" videos when that position was past the new video's end).
    private val _loadedUrl = MutableStateFlow<String?>(null)
    /** URL of the media really loaded in the player (the UI only shows progress for that one). */
    val loadedUrl: StateFlow<String?> = _loadedUrl
    private var preparedUrl: String?
        get() = _loadedUrl.value
        set(value) { _loadedUrl.value = value }
    private var preparedAt = 0L
    private var resumedFrom = 0L

    private fun savePosition() {
        val url = preparedUrl ?: return
        val pos = player.currentPosition
        val dur = player.duration
        val nearEnd = dur != C.TIME_UNSET && dur > 0 && pos > dur - 10_000
        if (pos > 10_000 && !nearEnd) resume.edit().putLong(resumeKey(url), pos).apply()
        else if (nearEnd) forgetPosition(url)
    }

    // Each account has its own place in a video. A position saved before this existed has no account.
    private fun resumeKey(url: String) = com.sourabh.jarvistube.data.Session.dataKey + "|" + url

    private fun forgetPosition(url: String) { resume.edit().remove(resumeKey(url)).remove(url).apply() }

    private fun savedPosition(url: String): Long =
        (resume.getLong(resumeKey(url), 0L).takeIf { it > 0 } ?: resume.getLong(url, 0L)).takeIf { it > 10_000 } ?: 0L

    fun updateSponsorBlock(on: Boolean) {
        sponsorBlock = on
        prefs.edit().putBoolean("sponsorBlock", on).apply()
    }

    fun updateAudioOnly(on: Boolean) {
        audioOnly = on
        prefs.edit().putBoolean("audioOnly", on).apply()
        applyAudioOnly()
    }

    /** With the video track disabled ExoPlayer stops downloading the separate video stream. */
    private fun applyAudioOnly() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, audioOnly).build()
    }

    /** minutes = 0 cancels the timer. */
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            sleepEndAt = 0L
            return
        }
        sleepEndAt = System.currentTimeMillis() + minutes * 60_000L
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            player.pause()
            sleepEndAt = 0L
        }
    }

    private var tPlay = 0L

    /** [record] = remember the outgoing item for Previous (false when Previous itself plays). */
    fun play(video: VideoItem, record: Boolean = true) {
        tPlay = android.os.SystemClock.elapsedRealtime()
        if (_now.value?.video?.url == video.url && _now.value?.error == null) {
            player.playWhenReady = true
            return
        }
        if (record) _now.value?.video?.let { out ->
            history.removeAll { it.url == out.url }
            history.addLast(out)
            while (history.size > 50) history.removeFirst()
        }
        nextJob?.cancel()
        job?.cancel()
        savePosition() // remember where the outgoing video was, against its own URL
        segments = emptyList()
        val default = com.sourabh.jarvistube.AppSettings.defaultQuality
        val auto = default <= 0
        preferredQuality = if (auto) autoHeight() else default
        // A Short's "height" is its long side: 480 would pick a 240-wide stream, blurry full-screen.
        if (video.isShort) preferredQuality = maxOf(preferredQuality, 1280)
        _now.value = NowPlaying(video, quality = preferredQuality, autoQuality = auto)
        app.startService(Intent(app, PlaybackService::class.java))
        job = scope.launch { resolve(video, if (video.isShort) 0L else savedPosition(video.url)) }
        queue.firstOrNull()?.let { com.sourabh.jarvistube.data.Repo.prefetch(it.url) }
    }

    /** Plays a downloaded file. */
    fun playLocal(item: SavedItem) {
        job?.cancel()
        segments = emptyList()
        val video = VideoItem(item.url, item.title, item.channel, item.thumb, item.durationSec, -1, null, false)
        _now.value = NowPlaying(video, loading = false, quality = 0)
        com.sourabh.jarvistube.data.WatchHistory.add(video)
        app.startService(Intent(app, PlaybackService::class.java))
        val meta = MediaMetadata.Builder().setTitle(item.title).setArtist(item.channel)
            .setArtworkUri(item.thumb?.toUri()).build()
        val start = savedPosition(item.url)
        preparedUrl = item.url
        preparedAt = android.os.SystemClock.elapsedRealtime()
        resumedFrom = start
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(if (com.sourabh.jarvistube.data.Downloads.isContent(item.path)) Uri.parse(item.path) else Uri.fromFile(File(item.path)))
                .setMediaMetadata(meta).build(),
            start
        )
        player.prepare()
        player.playWhenReady = true
    }

    /** Reloads the current video with fresh stream links, at the position it was at (never 0:00). */
    fun retry() {
        val cur = _now.value ?: return
        val pos = if (preparedUrl == cur.video.url) player.currentPosition else savedPosition(cur.video.url)
        job?.cancel()
        com.sourabh.jarvistube.data.Repo.forget(cur.video.url)
        _now.value = cur.copy(loading = true, error = null)
        job = scope.launch { resolve(cur.video, pos) }
    }

    // A far seek can hit an expired or failing stream link; reconnect quietly at the same spot.
    private var autoRecoveries = 0

    /** [height] <= 0 means Auto. */
    fun setQuality(height: Int) {
        val cur = _now.value ?: return
        val info = cur.info ?: return
        val auto = height <= 0
        // The quality you pick becomes the default for every next video (Settings shows it too).
        com.sourabh.jarvistube.AppSettings.updateDefaultQuality(if (auto) 0 else height)
        preferredQuality = if (auto) autoHeight() else height
        val pos = player.currentPosition
        _now.value = cur.copy(quality = preferredQuality, autoQuality = auto)
        try {
            prepare(info, cur.video, preferredQuality, pos)
        } catch (e: Exception) {
            _now.update { it?.copy(error = e.message) }
        }
    }

    fun togglePlay() {
        if (!player.isPlaying && Graph.study.onBreak) {
            showNotice("Still on break: ${formatLeftMs(Graph.study.breakEndAt - com.sourabh.jarvistube.data.TrustedClock.now())} left")
            return
        }
        // playWhenReady is what you asked for; isPlaying is also false while buffering, which made
        // Pause do nothing mid-buffer.
        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) {
            player.pause()
            return
        }
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.play()
    }

    fun stop() {
        job?.cancel()
        savePosition()
        segments = emptyList()
        player.stop()
        player.clearMediaItems()
        preparedUrl = null
        _now.value = null
        // Closing the player ends the background service, and with it the notification.
        app.stopService(Intent(app, PlaybackService::class.java))
    }

    private suspend fun resolve(video: VideoItem, startMs: Long) {
        try {
            val info = Repo.streamInfo(video.url)
            com.sourabh.jarvistube.data.Dbg.log("streamInfo done +${android.os.SystemClock.elapsedRealtime() - tPlay}ms")
            // Second filter layer: YouTube's own category, known only now.
            com.sourabh.jarvistube.data.ContentFilter.blockReason(
                info.category, info.uploaderName.orEmpty(), info.name.orEmpty()
            )?.let { reason ->
                player.stop()
                preparedUrl = null
                _now.update { it?.copy(info = info, loading = false, error = reason) }
                return
            }
            // Saved here, for every video that really starts, with its full title and channel.
            com.sourabh.jarvistube.data.WatchHistory.add(
                video.copy(
                    title = info.name?.takeIf { it.isNotBlank() } ?: video.title,
                    channel = info.uploaderName?.takeIf { it.isNotBlank() } ?: video.channel,
                    channelUrl = info.uploaderUrl?.takeIf { it.isNotBlank() } ?: video.channelUrl,
                    thumb = video.thumb ?: runCatching { info.thumbnails.bestUrl() }.getOrNull(),
                    durationSec = if (video.durationSec > 0) video.durationSec else info.duration
                )
            )
            val heights = availableHeights(info)
            // Always start on the original-language track; other dubs are an opt-in choice.
            _now.update {
                it?.copy(
                    info = info, heights = heights, loading = false,
                    audioTracks = audioTracksOf(info), audioTrackId = defaultAudioTrackId(info),
                    subtitles = subtitleOptionsOf(info), subtitleId = null
                )
            }
            prepare(info, video, preferredQuality, startMs)
            com.sourabh.jarvistube.data.Dbg.log("prepared +${android.os.SystemClock.elapsedRealtime() - tPlay}ms")
            SponsorBlock.videoId(video.url)?.let { id ->
                scope.launch {
                    segments = runCatching { SponsorBlock.segments(id) }.getOrDefault(emptyList())
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _now.update { it?.copy(loading = false, error = e.message ?: e.javaClass.simpleName) }
        }
    }

    private fun prepare(info: StreamInfo, video: VideoItem, quality: Int, startMs: Long) {
        val meta = MediaMetadata.Builder()
            .setTitle(info.name)
            .setArtist(info.uploaderName)
            // Notification / lock-screen artwork; links opened without a thumbnail use YouTube's.
            .setArtworkUri((video.thumb ?: runCatching { info.thumbnails.bestUrl() }.getOrNull())?.toUri())
            .build()

        fun item(url: String) = MediaItem.Builder().setUri(url).setMediaMetadata(meta).build()
        fun progressive(url: String): MediaSource =
            ProgressiveMediaSource.Factory(chunkedFactory).createMediaSource(item(url))

        // Only the caption track the user picked is attached (loading every language up front
        // would slow every video start). It is a side-loaded WebVTT or TTML file from YouTube.
        val subtitle = _now.value?.subtitleId?.let { id -> subtitleOptionsOf(info).firstOrNull { it.id == id } }
        val subtitleSource: MediaSource? = subtitle?.let {
            val config = MediaItem.SubtitleConfiguration.Builder(it.url.toUri())
                .setMimeType(it.mime)
                .setLanguage(it.language)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            SingleSampleMediaSource.Factory(dataSource).createMediaSource(config, C.TIME_UNSET)
        }

        val hls = info.hlsUrl
        var playingHeight = 0
        val parts = ArrayList<MediaSource>()
        if (info.streamType == StreamType.LIVE_STREAM && !hls.isNullOrEmpty()) {
            parts.add(HlsMediaSource.Factory(dataSource).createMediaSource(item(hls)))
        } else {
            val audio = pickAudio(info, _now.value?.audioTrackId)
            val video0 = pickVideo(info, quality)
            playingHeight = video0?.height ?: 0
            when {
                video0 != null && video0.isVideoOnly && audio != null -> {
                    parts.add(progressive(video0.content))
                    parts.add(progressive(audio.content))
                }
                video0 != null -> parts.add(progressive(video0.content))
                audio != null -> parts.add(progressive(audio.content))
                else -> throw IllegalStateException("No playable streams found")
            }
        }
        subtitleSource?.let(parts::add)
        val source = if (parts.size == 1) parts[0] else MergingMediaSource(*parts.toTypedArray())

        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitle == null)
            .setPreferredTextLanguage(subtitle?.language)
            .build()
        _now.update { it?.copy(playingHeight = playingHeight) }
        preparedUrl = video.url
        preparedAt = android.os.SystemClock.elapsedRealtime()
        resumedFrom = startMs
        player.setMediaSource(source, startMs)
        player.prepare()
        player.playWhenReady = true
    }

    /** Caption languages YouTube offers for this video, manual ones first. */
    fun subtitleOptionsOf(info: StreamInfo): List<SubtitleOption> =
        info.subtitles
            // YouTube often serves captions only as TTML; ExoPlayer decodes both TTML and WebVTT.
            .filter { (it.format == MediaFormat.VTT || it.format == MediaFormat.TTML) && it.isUrl && it.content.isNotEmpty() }
            .sortedBy { if (it.format == MediaFormat.VTT) 0 else 1 } // VTT wins when both exist
            .map {
                val name = it.displayLanguageName?.takeIf { n -> n.isNotBlank() } ?: it.languageTag
                SubtitleOption(
                    id = it.languageTag + if (it.isAutoGenerated) ":auto" else "",
                    label = if (it.isAutoGenerated) "$name (auto-generated)" else name,
                    language = it.languageTag,
                    url = it.content,
                    mime = if (it.format == MediaFormat.VTT) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_TTML
                )
            }
            .distinctBy { it.id }
            .sortedBy { it.label.contains("auto-generated") }

    /** null turns captions off. Re-prepares at the current position. */
    fun setSubtitle(id: String?) {
        val cur = _now.value ?: return
        val info = cur.info ?: return
        val pos = player.currentPosition
        _now.value = cur.copy(subtitleId = id)
        try {
            prepare(info, cur.video, preferredQuality, pos)
        } catch (e: Exception) {
            _now.update { it?.copy(error = e.message) }
        }
    }

    /** Best audio for [trackId]; with no id given, the original-language track is used. */
    fun pickAudio(info: StreamInfo, trackId: String? = null): AudioStream? {
        val id = trackId ?: defaultAudioTrackId(info)
        val pool = if (id != null) info.audioStreams.filter { it.audioTrackId == id }.ifEmpty { info.audioStreams }
        else info.audioStreams
        val m4a = pool.filter { it.format == MediaFormat.M4A }
        return (m4a.ifEmpty { pool }).maxByOrNull { it.averageBitrate }
    }

    /** Distinct audio languages the video offers, original first. Empty when there's only one. */
    fun audioTracksOf(info: StreamInfo): List<AudioTrackOption> =
        info.audioStreams.filter { it.audioTrackId != null }
            .groupBy { it.audioTrackId!! }
            .map { (id, list) ->
                val s = list.first()
                val name = s.audioTrackName?.takeIf { it.isNotBlank() }
                    ?: s.audioLocale?.displayLanguage?.takeIf { it.isNotBlank() }
                    ?: id
                AudioTrackOption(id, name, s.audioTrackType == AudioTrackType.ORIGINAL)
            }
            .sortedByDescending { it.original }

    fun defaultAudioTrackId(info: StreamInfo): String? =
        audioTracksOf(info).let { t -> (t.firstOrNull { it.original } ?: t.firstOrNull())?.id }

    fun setAudioTrack(id: String) {
        val cur = _now.value ?: return
        val info = cur.info ?: return
        val pos = player.currentPosition
        _now.value = cur.copy(audioTrackId = id)
        try {
            prepare(info, cur.video, cur.quality, pos)
        } catch (e: Exception) {
            _now.update { it?.copy(error = e.message) }
        }
    }

    /** Video-only streams this phone can decode smoothly (includes VP9/AV1 for 1440p/4K). */
    fun playableVideoOnly(info: StreamInfo): List<VideoStream> =
        info.videoOnlyStreams.filter { it.isUrl && it.content.isNotEmpty() && VideoCodecs.canPlay(it) }

    /** The real resolutions on offer for this video on this device, highest first. */
    fun availableHeights(info: StreamInfo): List<Int> =
        playableVideoOnly(info).map { it.height }
            .ifEmpty { info.videoStreams.map { it.height } }
            .filter { it > 0 }
            .distinct()
            .sortedDescending()

    /**
     * Best stream at or below [quality]. At equal height: AVC before VP9 before AV1, then higher
     * frame rate, then bitrate. [avcOnly] is for downloads, which are muxed into MP4.
     */
    fun pickVideo(info: StreamInfo, quality: Int, avcOnly: Boolean = false): VideoStream? {
        val pool = playableVideoOnly(info)
            .filter { !avcOnly || VideoCodecs.mimeOf(it) == VideoCodecs.AVC }
            .ifEmpty { info.videoStreams }
        if (pool.isEmpty()) return null
        val height = pool.filter { it.height <= quality }.maxOfOrNull { it.height }
            ?: pool.minOf { it.height }
        return pool.filter { it.height == height }
            .minWithOrNull(compareBy<VideoStream>({ VideoCodecs.rank(it) }, { -it.fps }, { -it.bitrate }))
    }

    /** "Auto": up to 1080p on Wi-Fi, 480p on mobile data. 4K is always a manual choice. */
    fun autoHeight(): Int {
        val cm = app.getSystemService(android.net.ConnectivityManager::class.java)
        return if (cm?.isActiveNetworkMetered == true) 480 else 1080
    }
}

private fun formatLeftMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
