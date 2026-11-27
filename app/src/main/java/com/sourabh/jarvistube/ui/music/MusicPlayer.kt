package com.sourabh.jarvistube.ui.music

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.data.VideoItem
import com.sourabh.jarvistube.player.NowPlaying
import com.sourabh.jarvistube.ui.common.Thumb
import com.sourabh.jarvistube.ui.player.PlayerUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Whether the big music player is open (the mini bar shows otherwise). */
object MusicUi {
    var playerOpen by mutableStateOf(false)
}

private val Ink = Color(0xFF121212)
private val Soft = Color(0xFFB3B3B3)

/** The song's main colour, taken from its art, for the player's background (Spotify style). */
@Composable
private fun artColor(url: String?): Color {
    var c by remember(url) { mutableStateOf(Color(0xFF404040)) }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        // A tiny copy of the cover is enough for its colour.
        val small = url.replace(Regex("=w[0-9]+-h[0-9]+"), "=w60-h60").replace("hqdefault.jpg", "default.jpg")
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                Graph.http.newCall(okhttp3.Request.Builder().url(small).build()).execute().use { r ->
                    val bytes = r.body?.bytes() ?: return@use null
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?.let { android.graphics.Bitmap.createScaledBitmap(it, 12, 12, true) }
                }
            }.getOrNull()
        } ?: return@LaunchedEffect
        // The most colourful of the average and the pixels, a little darkened so white text reads.
        var r = 0f; var g = 0f; var b = 0f; var n = 0
        var best = Color(0xFF404040); var bestSat = -1f
        for (x in 0 until bmp.width) for (y in 0 until bmp.height) {
            val p = bmp.getPixel(x, y)
            val col = Color(p)
            r += col.red; g += col.green; b += col.blue; n++
            val mx = maxOf(col.red, col.green, col.blue); val mn = minOf(col.red, col.green, col.blue)
            val sat = if (mx == 0f) 0f else (mx - mn) / mx
            if (sat * mx > bestSat && mx > 0.2f) { bestSat = sat * mx; best = col }
        }
        val avg = Color(r / n, g / n, b / n)
        c = lerp(lerp(avg, best, 0.6f), Color.Black, 0.35f)
    }
    val animated by animateColorAsState(c, tween(600), label = "artColor")
    return animated
}

/** The slim bar above the tabs while a song plays: art, title, artist, play/pause, progress. */
@Composable
fun MusicMiniBar(ui: PlayerUi, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val now by Graph.player.now.collectAsStateWithLifecycle()
    val n = now ?: return
    val tint = artColor(n.video.thumb)
    Box(
        modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp)).background(tint)
            .clickable(onClick = onOpen)
            .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy < -12f) onOpen() } }
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(n.video.thumb, Modifier.size(42.dp).clip(RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(n.video.title, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(n.video.channel, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { Graph.player.togglePlay() }) {
                if (n.loading) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            IconButton(onClick = { Graph.player.stop() }) { Icon(Icons.Rounded.Close, "Stop", tint = Color.White.copy(alpha = 0.8f)) }
        }
        val frac = if (ui.duration > 0) (ui.position.toFloat() / ui.duration).coerceIn(0f, 1f) else 0f
        Box(Modifier.align(Alignment.BottomStart).padding(horizontal = 8.dp).fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.25f)))
        Box(Modifier.align(Alignment.BottomStart).padding(start = 8.dp).fillMaxWidth(frac * 0.985f).height(2.dp).background(Color.White))
    }
}

/** The big player: art-coloured background, large cover, seek bar and Spotify's controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen(ui: PlayerUi, onClose: () -> Unit) {
    val context = LocalContext.current
    val controller = Graph.player
    val now by controller.now.collectAsStateWithLifecycle()
    val n: NowPlaying = now ?: run { LaunchedEffect(Unit) { onClose() }; return }
    val tint = artColor(n.video.thumb)
    var queueOpen by remember { mutableStateOf(false) }
    BackHandler(onBack = onClose)

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(tint, lerp(tint, Ink, 0.75f), Ink)))
            .clickable(enabled = false) {}
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)
        ) {
            // Top: close, where it's playing from, the queue.
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp)
                    .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy > 16f) onClose() } },
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close", tint = Color.White, modifier = Modifier.size(32.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYING FROM", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(controller.musicSource.ifBlank { "Music" }, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { queueOpen = true }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Up next", tint = Color.White) }
            }

            Spacer(Modifier.weight(1f))
            // The cover: swipe left for the next song, right for the one before.
            var drag by remember { mutableFloatStateOf(0f) }
            AnimatedContent(
                targetState = n.video.url,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label = "cover",
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (drag < -120f) controller.skipNext() else if (drag > 120f) controller.skipPrevious()
                                drag = 0f
                            },
                            onDragCancel = { drag = 0f }
                        ) { _, dx -> drag += dx }
                    }
            ) { _ ->
                Thumb(
                    n.video.thumb,
                    Modifier.fillMaxSize().shadow(24.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp))
                )
            }
            Spacer(Modifier.weight(1f))

            // Title and artist.
            Text(
                n.video.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                maxLines = 1, modifier = Modifier.fillMaxWidth().basicMarquee()
            )
            Text(n.video.channel, color = Soft, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(16.dp))

            // Seek bar: follows the song, and you can drag it.
            var seeking by remember { mutableStateOf<Float?>(null) }
            val dur = ui.duration.coerceAtLeast(1L)
            val frac = seeking ?: (ui.position.toFloat() / dur).coerceIn(0f, 1f)
            SeekLine(
                frac = frac,
                onSeek = { seeking = it },
                onSeekDone = { seeking?.let { controller.player.seekTo((it * dur).toLong()) }; seeking = null }
            )
            Row(Modifier.fillMaxWidth()) {
                Text(clock((frac * dur).toLong()), color = Soft, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text(clock(ui.duration), color = Soft, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(8.dp))

            // Shuffle · Previous · Play/Pause · Next · Repeat one
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = {
                    controller.shuffleQueue()
                    Toast.makeText(context, "Up next shuffled", Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Rounded.Shuffle, "Shuffle", tint = Color.White, modifier = Modifier.size(26.dp)) }
                IconButton(onClick = { controller.skipPrevious() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(40.dp))
                }
                Box(
                    Modifier.size(68.dp).clip(CircleShape).background(Color.White).clickable { controller.togglePlay() },
                    contentAlignment = Alignment.Center
                ) {
                    if (n.loading || ui.isBuffering) CircularProgressIndicator(Modifier.size(28.dp), color = Color.Black, strokeWidth = 3.dp)
                    else Icon(if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", tint = Color.Black, modifier = Modifier.size(38.dp))
                }
                IconButton(onClick = { controller.skipNext() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = { ui.toggleLoop() }) {
                    Icon(
                        if (ui.loop) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat this song",
                        tint = if (ui.loop) SpotGreen else Color.White, modifier = Modifier.size(26.dp)
                    )
                }
            }
            n.error?.let {
                Text(it, color = Color(0xFFFF8A80), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (queueOpen) {
        ModalBottomSheet(onDismissRequest = { queueOpen = false }, containerColor = Color(0xFF181818)) {
            Text("Up next", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            val q = controller.queue.toList()
            if (q.isEmpty()) Text("Nothing queued. Next plays a similar song.", color = Soft, modifier = Modifier.padding(16.dp))
            LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                itemsIndexed(q, key = { _, s -> "q:" + s.url }) { i, s ->
                    QueueRow(s, onClick = {
                        // Jump to this song: everything before it is skipped.
                        repeat(i) { controller.removeFromQueue(0) }
                        controller.playNext()
                    }, onRemove = { controller.removeFromQueue(i) })
                }
            }
        }
    }
}

@Composable
private fun QueueRow(s: VideoItem, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumb(s.thumb, Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title, color = Color.White, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.channel, color = Soft, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "Remove", tint = Soft) }
    }
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Spotify's slim seek line: a thin track, a white fill and a small dot; tap or drag to move. */
@Composable
private fun SeekLine(frac: Float, onSeek: (Float) -> Unit, onSeekDone: () -> Unit) {
    var width by remember { mutableFloatStateOf(1f) }
    Box(
        Modifier.fillMaxWidth().height(28.dp)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { o -> onSeek((o.x / width).coerceIn(0f, 1f)); onSeekDone() }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> onSeek((o.x / width).coerceIn(0f, 1f)) },
                    onDragEnd = onSeekDone,
                    onDragCancel = onSeekDone
                ) { change, _ -> onSeek((change.position.x / width).coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.25f)))
        Box(Modifier.fillMaxWidth(frac).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
        Box(
            Modifier.offset { androidx.compose.ui.unit.IntOffset((frac * width - 6.dp.toPx()).toInt(), 0) }.size(12.dp).clip(CircleShape).background(Color.White)
        )
    }
}

