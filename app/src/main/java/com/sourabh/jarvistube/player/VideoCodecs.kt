package com.sourabh.jarvistube.player

import android.media.MediaCodecList
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Which YouTube video streams this phone can really play smoothly.
 *
 * YouTube only serves 1440p/2160p as VP9 or AV1, while H.264 (AVC) stops at 1080p. So the quality
 * list must include VP9/AV1 streams to offer 4K, but only when a *hardware* decoder handles them
 * at that size: a software VP9/AV1 decode of 4K would stutter badly. Nothing is offered that the
 * device can't decode.
 */
object VideoCodecs {
    const val AVC = "video/avc"
    const val VP9 = "video/x-vnd.on2.vp9"
    const val AV1 = "video/av01"

    private val cache = HashMap<String, Boolean>()

    fun mimeOf(s: VideoStream): String? {
        val codec = s.codec?.lowercase().orEmpty()
        return when {
            codec.startsWith("avc") -> AVC
            codec.startsWith("vp9") || codec.startsWith("vp09") -> VP9
            codec.startsWith("av01") -> AV1
            s.format == MediaFormat.WEBM -> VP9
            s.format == MediaFormat.MPEG_4 -> AVC
            else -> null
        }
    }

    fun canPlay(s: VideoStream): Boolean {
        val mime = mimeOf(s) ?: return false
        val h = s.height.takeIf { it > 0 } ?: return false
        val w = s.width.takeIf { it > 0 } ?: (h * 16 / 9)
        val key = "$mime:$w:$h"
        return synchronized(cache) { cache.getOrPut(key) { supported(mime, w, h) } }
    }

    /** Lower is preferred at equal height: AVC is the most battery-friendly and compatible. */
    fun rank(s: VideoStream): Int = when (mimeOf(s)) {
        AVC -> 0
        VP9 -> 1
        AV1 -> 2
        else -> 3
    }

    private fun supported(mime: String, width: Int, height: Int): Boolean {
        val needHardware = mime != AVC || height > 1080
        return MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            !info.isEncoder &&
                info.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
                (!needHardware || info.isHardwareAccelerated) &&
                runCatching {
                    val caps = info.getCapabilitiesForType(mime).videoCapabilities
                    caps != null && (caps.isSizeSupported(width, height) || caps.isSizeSupported(height, width))
                }.getOrDefault(false)
        }
    }
}
