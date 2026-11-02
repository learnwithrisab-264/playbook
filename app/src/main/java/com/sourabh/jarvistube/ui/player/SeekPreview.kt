package com.sourabh.jarvistube.ui.player

import com.sourabh.jarvistube.ui.common.formatClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import org.schabi.newpipe.extractor.stream.StreamInfo
import kotlin.math.abs
import kotlin.math.roundToInt

/** Sprite pages already downloaded, shared across drags (YouTube storyboards are a few JPEGs). */
private val pageCache = mutableStateMapOf<String, ImageBitmap>()

/**
 * The frame preview shown while scrubbing: YouTube's storyboard image for [positionMs], with
 * the time under it, kept above the finger and inside the player's width.
 */
@Composable
fun SeekPreview(info: StreamInfo?, positionMs: Long, durationMs: Long, small: Boolean = false) {
    val frames = remember(info) {
        info?.previewFrames.orEmpty().filter { it.urls.isNotEmpty() && it.frameWidth > 0 }
            .minByOrNull { abs(it.frameWidth - 160) }
    }
    val context = LocalContext.current
    val bounds = frames?.getFrameBoundsAt(positionMs)
    val url = bounds?.let { frames.urls.getOrNull(it[0]) }
    LaunchedEffect(url) {
        if (url == null || pageCache.containsKey(url)) return@LaunchedEffect
        val result = SingletonImageLoader.get(context)
            .execute(ImageRequest.Builder(context).data(url).allowHardware(false).build())
        (result as? SuccessResult)?.image?.toBitmap()?.let { pageCache[url] = it.asImageBitmap() }
    }
    val page = url?.let { pageCache[it] }

    BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
        val density = LocalDensity.current
        val previewW = if (small) 128.dp else 176.dp
        val previewH = if (frames != null) previewW * (frames.frameHeight.toFloat() / frames.frameWidth) else 0.dp
        val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        val widthPx = with(density) { maxWidth.toPx() }
        val boxPx = with(density) { (if (page != null) previewW else 64.dp).toPx() }
        val x = (fraction * widthPx - boxPx / 2).coerceIn(0f, (widthPx - boxPx).coerceAtLeast(0f))
        Column(
            Modifier.offset { IntOffset(x.roundToInt(), 0) },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (page != null && bounds != null) {
                Canvas(
                    Modifier.size(previewW, previewH).clip(RoundedCornerShape(8.dp))
                        .border(1.5.dp, Color.White, RoundedCornerShape(8.dp))
                ) {
                    drawImage(
                        page,
                        srcOffset = IntOffset(bounds[1], bounds[2]),
                        srcSize = IntSize((bounds[3] - bounds[1]).coerceAtLeast(1), (bounds[4] - bounds[2]).coerceAtLeast(1)),
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    )
                }
            }
            Box(
                Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(formatClock(positionMs), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
