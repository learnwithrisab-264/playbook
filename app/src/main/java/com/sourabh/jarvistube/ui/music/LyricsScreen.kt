package com.sourabh.jarvistube.ui.music

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.data.LyricLine
import com.sourabh.jarvistube.data.Lyrics
import com.sourabh.jarvistube.data.LyricsRepo
import com.sourabh.jarvistube.data.VideoItem
import com.sourabh.jarvistube.ui.player.PlayerUi
import kotlinx.coroutines.delay

/** Loads the song's lyrics once (null while loading; [Lyrics] with no lines = none found). */
@Composable
private fun rememberLyrics(song: VideoItem): Lyrics? =
    produceState<Lyrics?>(null, song.url) {
        value = LyricsRepo.forSong(song) ?: Lyrics(emptyList(), synced = false)
    }.value

/** The player's position, read often enough that lines change right on the beat. */
@Composable
private fun rememberFinePosition(): Long {
    var pos by remember { mutableLongStateOf(Graph.player.player.currentPosition) }
    LaunchedEffect(Unit) {
        while (true) {
            pos = Graph.player.player.currentPosition
            delay(100)
        }
    }
    return pos
}

/** What the lyrics screen lists: a sung line, or ••• for a music break (intro, solo). */
private sealed interface Row { val start: Long }
private class LineRow(val line: LyricLine) : Row { override val start get() = line.timeMs }
private class BreakRow(override val start: Long, val end: Long) : Row

/** Lines, with ••• before the first line and in long breaks (LRC marks a break with an empty line). */
private fun rowsOf(l: Lyrics): List<Row> {
    if (!l.synced) return l.lines.filter { it.text.isNotBlank() }.map(::LineRow)
    val out = ArrayList<Row>()
    val first = l.lines.firstOrNull { it.text.isNotBlank() } ?: return out
    if (first.timeMs > 4000) out += BreakRow(0, first.timeMs)
    l.lines.forEachIndexed { i, line ->
        if (line.text.isNotBlank()) out += LineRow(line)
        else {
            val next = l.lines.drop(i + 1).firstOrNull { it.text.isNotBlank() } ?: return@forEachIndexed
            if (next.timeMs - line.timeMs > 4000 && out.isNotEmpty()) out += BreakRow(line.timeMs, next.timeMs)
        }
    }
    return out
}

/** The row being sung at [pos] (-1 before anything starts). */
private fun rowAt(rows: List<Row>, synced: Boolean, pos: Long): Int =
    if (!synced) -1 else rows.indexOfLast { it.start <= pos + 150 }

/** Spotify's lyrics card under the player controls: the lines being sung; tap for all of them. */
@Composable
internal fun LyricsCard(song: VideoItem, tint: Color, onOpen: () -> Unit) {
    val lyrics = rememberLyrics(song) ?: return
    if (lyrics.lines.none { it.text.isNotBlank() }) return
    val rows = remember(lyrics) { rowsOf(lyrics) }
    val pos = rememberFinePosition()
    val i = rowAt(rows, lyrics.synced, pos).coerceAtLeast(0)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(lerp(tint, Color.Black, 0.25f))
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("Lyrics", color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        rows.drop(i).filterIsInstance<LineRow>().take(2).forEachIndexed { k, r ->
            Text(
                r.line.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (k == 0 && lyrics.synced && rows.getOrNull(i) is LineRow) Color.White else Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Full-screen synced lyrics, as in Spotify: the line being sung is bright and full size, the view
 * glides with the song (pausing a few seconds if you scroll yourself), ••• pulse through music
 * breaks, tap any line to jump there, Share sends the current line. The screen stays on.
 */
@Composable
internal fun LyricsScreen(song: VideoItem, ui: PlayerUi, tint: Color, onClose: () -> Unit) {
    val context = LocalContext.current
    val controller = Graph.player
    val lyrics = rememberLyrics(song)
    val rows = remember(lyrics) { lyrics?.let(::rowsOf).orEmpty() }
    val synced = lyrics?.synced == true
    val pos = rememberFinePosition()
    val list = rememberLazyListState()
    BackHandler(onBack = onClose)

    // Reading along: the screen doesn't dim or lock.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val current = rowAt(rows, synced, pos)
    // The first list item is a note when the lyrics aren't timed.
    val lead = if (lyrics != null && !synced) 1 else 0
    var userScrolledAt by remember { mutableLongStateOf(0L) }
    var autoScrolling by remember { mutableStateOf(false) }
    var placed by remember(song.url) { mutableStateOf(false) }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { moving ->
            if (moving && !autoScrolling) userScrolledAt = System.currentTimeMillis()
        }
    }
    // Keeps the line being sung about a third of the way down, gliding from line to line.
    LaunchedEffect(current, rows) {
        if (current < 0 || rows.isEmpty()) return@LaunchedEffect
        if (System.currentTimeMillis() - userScrolledAt < 4000) return@LaunchedEffect
        autoScrolling = true
        runCatching { glideTo(list, current + lead, animate = placed) }
        placed = true
        autoScrolling = false
    }

    val bg = lerp(tint, Color(0xFF121212), 0.15f)
    Box(Modifier.fillMaxSize().background(bg).clickable(enabled = false) {}) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Header: close, song and artists.
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close lyrics", tint = Color.White, modifier = Modifier.size(30.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(song.title, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.channel, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(48.dp))
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    lyrics == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                    rows.isEmpty() -> Text(
                        "No lyrics for this song yet.", color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Center)
                    )
                    else -> LazyColumn(
                        state = list,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 240.dp)
                    ) {
                        if (!synced) item {
                            Text(
                                "These lyrics aren't timed to the song.", color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                            )
                        }
                        itemsIndexed(rows) { i, r ->
                            when (r) {
                                is BreakRow -> BreakDots(
                                    active = i == current,
                                    progress = ((pos - r.start).toFloat() / (r.end - r.start)).coerceIn(0f, 1f)
                                )
                                is LineRow -> LyricText(
                                    r.line.text,
                                    state = when {
                                        !synced -> 0
                                        i == current -> 0
                                        i < current -> -1
                                        else -> 1
                                    },
                                    onClick = if (synced) ({ controller.player.seekTo(r.line.timeMs) }) else null
                                )
                            }
                        }
                        item {
                            Text(
                                "Lyrics provided by LRCLIB", color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp)
                            )
                        }
                    }
                }
                // Lines fade in at the top and out under the controls.
                Box(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().height(28.dp)
                        .background(Brush.verticalGradient(listOf(bg, Color.Transparent)))
                )
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(72.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, bg)))
                )
            }

            // Bottom: Share, seek line, previous · play/pause · next.
            Column(Modifier.fillMaxWidth().background(bg).padding(horizontal = 20.dp)) {
                Row(Modifier.padding(vertical = 8.dp)) {
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.25f))
                            .clickable {
                                val line = (rows.getOrNull(current) as? LineRow)?.line?.text
                                    ?: rows.filterIsInstance<LineRow>().firstOrNull()?.line?.text
                                val text = listOfNotNull(line?.let { "“$it”" }, "${song.title} — ${song.channel}", song.url).joinToString("\n")
                                context.startActivity(
                                    Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share lyrics")
                                )
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Share, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
                var seeking by remember { mutableStateOf<Float?>(null) }
                val dur = ui.duration.coerceAtLeast(1L)
                val frac = seeking ?: (pos.toFloat() / dur).coerceIn(0f, 1f)
                SeekLine(
                    frac = frac,
                    onSeek = { seeking = it },
                    onSeekDone = { seeking?.let { controller.player.seekTo((it * dur).toLong()) }; seeking = null }
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(clock((frac * dur).toLong()), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.weight(1f))
                    Text(clock(ui.duration), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { controller.skipPrevious() }) { Icon(Icons.Rounded.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(36.dp)) }
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(Color.White).clickable { controller.togglePlay() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", tint = Color.Black, modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = { controller.skipNext() }) { Icon(Icons.Rounded.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(36.dp)) }
                }
            }
        }
    }
}

/** Scrolls so item [index] sits a third of the way down: a smooth glide, or a jump on opening. */
private suspend fun glideTo(list: LazyListState, index: Int, animate: Boolean) {
    fun anchor() = (list.layoutInfo.viewportEndOffset - list.layoutInfo.viewportStartOffset) * 0.3f
    val seen = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (seen != null && animate) {
        list.animateScrollBy(seen.offset - anchor(), tween(550, easing = FastOutSlowInEasing))
        return
    }
    list.scrollToItem(index)
    list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.let { list.scrollBy(it.offset - anchor()) }
}

/**
 * One line. [state]: -1 sung, 0 now, 1 still to come. The size changes by scale (not font size),
 * so lines never re-wrap or jump while the highlight moves.
 */
@Composable
private fun LyricText(text: String, state: Int, onClick: (() -> Unit)?) {
    val scale by animateFloatAsState(if (state == 0) 1f else 0.93f, tween(300), label = "scale")
    val color by animateColorAsState(
        when (state) {
            0 -> Color.White
            -1 -> Color.White.copy(alpha = 0.5f)
            else -> Color.White.copy(alpha = 0.72f)
        },
        tween(300), label = "color"
    )
    Text(
        text, color = color, fontSize = 25.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 10.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0.5f) }
    )
}

/** Spotify's ••• for a music break: they light up one by one as the break goes on, and breathe. */
@Composable
private fun BreakDots(active: Boolean, progress: Float) {
    val breathe by rememberInfiniteTransition(label = "dots").animateFloat(
        0.9f, 1.1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe"
    )
    Row(
        Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            .graphicsLayer { val s = if (active) breathe else 0.9f; scaleX = s; scaleY = s; transformOrigin = TransformOrigin(0f, 0.5f) },
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(3) { k ->
            val lit = active && progress >= (k + 1) / 3.5f
            val alpha by animateFloatAsState(if (lit) 1f else if (active) 0.45f else 0.3f, tween(300), label = "dot")
            Box(Modifier.size(12.dp).clip(CircleShape).background(Color.White.copy(alpha = alpha)))
        }
    }
}
