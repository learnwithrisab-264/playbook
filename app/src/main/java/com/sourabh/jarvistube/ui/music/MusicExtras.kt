package com.sourabh.jarvistube.ui.music

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.data.MusicCard
import com.sourabh.jarvistube.data.MusicForYou
import com.sourabh.jarvistube.data.MusicVolume
import com.sourabh.jarvistube.data.VideoItem
import com.sourabh.jarvistube.data.YearStats
import com.sourabh.jarvistube.ui.common.Thumb
import kotlinx.coroutines.delay

// ---------------------------------------------------------------- Player ⋮ menu

/** ⋮ in the music player: Even volume and the sleep timer. */
@Composable
internal fun MusicPlayerMenu() {
    var open by remember { mutableStateOf(false) }
    var sleep by remember { mutableStateOf(false) }
    val controller = Graph.player
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, "More", tint = Color.White) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Even volume")
                        Text("Every song at the same loudness", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                leadingIcon = { Icon(Icons.Rounded.Equalizer, null) },
                trailingIcon = { Switch(checked = MusicVolume.enabled, onCheckedChange = { MusicVolume.update(it) }) },
                onClick = { MusicVolume.update(!MusicVolume.enabled) }
            )
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Sleep timer")
                        if (controller.sleepEndAt > 0) Text(
                            "Stops in ${sleepLeft(controller.sleepEndAt)}",
                            style = MaterialTheme.typography.bodySmall, color = SpotGreen
                        )
                    }
                },
                leadingIcon = { Icon(Icons.Rounded.Bedtime, null) },
                onClick = { open = false; sleep = true }
            )
        }
    }
    if (sleep) SleepTimerDialog(onDismiss = { sleep = false })
}

private fun sleepLeft(endAt: Long): String {
    val m = ((endAt - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
    return if (m < 1) "less than a minute" else "$m min"
}

/** Stop the music after a while, or at the end of the song. */
@Composable
private fun SleepTimerDialog(onDismiss: () -> Unit) {
    val controller = Graph.player
    val options = listOf(5, 10, 15, 30, 45, 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Bedtime, null) },
        title = { Text("Sleep timer") },
        text = {
            Column {
                options.forEach { m ->
                    Text(
                        "$m minutes", style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable { controller.setSleepTimer(m); onDismiss() }.padding(vertical = 12.dp, horizontal = 4.dp)
                    )
                }
                Text(
                    "End of this song", style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                        // Minutes, rounded up, until the song ends.
                        val left = (controller.player.duration - controller.player.currentPosition).coerceAtLeast(0)
                        controller.setSleepTimer(((left + 59_999) / 60_000).toInt().coerceAtLeast(1))
                        onDismiss()
                    }.padding(vertical = 12.dp, horizontal = 4.dp)
                )
            }
        },
        confirmButton = {
            if (controller.sleepEndAt > 0) TextButton(onClick = { controller.setSleepTimer(0); onDismiss() }) { Text("Turn off timer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------------------------------------------------------------- Search

/**
 * Search results like Spotify's: Songs (best match first), Artists, Albums and Playlists.
 * Albums and playlists open as song lists; artists open their page.
 */
@Composable
internal fun MusicSearchPage(
    query: String,
    bottomPadding: Dp,
    onBack: () -> Unit,
    openWith: (String, suspend () -> List<VideoItem>) -> Unit
) {
    val context = LocalContext.current
    var tab by rememberSaveable(query) { mutableStateOf(0) }
    val tabs = listOf("Songs", "Artists", "Albums", "Playlists")
    BackHandler(enabled = !MusicUi.playerOpen && MusicUi.artist == null, onBack = onBack)
    val songs by produceState<List<VideoItem>?>(null, query) { value = runCatching { MusicForYou.searchSongs(query) }.getOrDefault(emptyList()) }
    val cards by produceState<List<MusicCard>?>(null, query, tab) {
        value = null
        value = runCatching {
            when (tab) {
                1 -> MusicForYou.searchArtists(query)
                2 -> MusicForYou.searchAlbums(query)
                3 -> MusicForYou.searchPlaylists(query)
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = bottomPadding + 16.dp)) {
        item {
            Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Text("“$query”", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(tabs) { i, t -> FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(t) }) }
            }
        }
        val loading = if (tab == 0) songs == null else cards == null
        if (loading) item { Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        else if (tab == 0) {
            val list = songs.orEmpty()
            if (list.isEmpty()) item { Text("No songs found.", modifier = Modifier.padding(24.dp)) }
            items(list, key = { "s:" + it.url }) { s -> SongRow(s, onClick = { playSong(context, s, list, "“$query”") }) }
        } else {
            val list = cards.orEmpty()
            if (list.isEmpty()) item { Text("Nothing found.", modifier = Modifier.padding(24.dp)) }
            items(list, key = { "c:" + it.title + (it.playlistId ?: "") }) { c ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (tab == 1) MusicUi.artist = c.title
                        else openWith(c.title) { MusicForYou.open(c) }
                    }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Artists in a circle, albums and playlists square, like Spotify.
                    Thumb(c.art, Modifier.size(56.dp).clip(if (tab == 1) CircleShape else RoundedCornerShape(4.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(c.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Your year in music

/** The card on Music home that opens "Your year in music". */
@Composable
internal fun YearCard(onOpen: () -> Unit) {
    val stats = remember { MusicForYou.yearStats() }
    if (stats.plays < 3) return
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(110.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFE91429), Color(0xFF8D67AB), Color(0xFF1E3264))))
            .clickable(onClick = onOpen)
            .padding(16.dp)
    ) {
        Column(Modifier.align(Alignment.CenterStart)) {
            Text("Your ${stats.year} in music", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
            Text("${stats.minutes} minutes · ${stats.differentSongs} songs. See your top songs and artists.", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * "Your year in music", like Spotify Wrapped: minutes listened, your top artist, your top songs and
 * artists, one tap to play your top songs, and Share.
 */
@Composable
internal fun YearInMusicScreen(bottomPadding: Dp, onBack: () -> Unit) {
    val context = LocalContext.current
    BackHandler(enabled = !MusicUi.playerOpen && MusicUi.artist == null, onBack = onBack)
    val s: YearStats = remember { MusicForYou.yearStats() }
    val top = s.topArtists.firstOrNull()
    LazyColumn(
        Modifier.fillMaxSize().background(Color(0xFF121212)),
        contentPadding = PaddingValues(bottom = bottomPadding + 24.dp)
    ) {
        item {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFFE91429), Color(0xFF8D67AB), Color(0xFF121212))))
                    .statusBarsPadding().padding(bottom = 24.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
                Text("Your ${s.year}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 44.sp, modifier = Modifier.padding(horizontal = 20.dp))
                Text("in music", color = Color.White, fontWeight = FontWeight.Black, fontSize = 44.sp, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(20.dp))
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    BigStat("${s.minutes}", "minutes listened")
                    BigStat("${s.differentSongs}", "different songs")
                    BigStat("${s.plays}", "plays")
                }
            }
        }
        if (top != null) item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("Your top artist", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { MusicUi.artist = top.first }) {
                    Thumb(top.third, Modifier.size(96.dp).clip(CircleShape))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(top.first, color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${top.second} plays", color = SpotGreen, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        item { WrapTitle("Your top songs") }
        itemsIndexed(s.topSongs, key = { _, p -> "ts:" + p.first.url }) { i, (song, n) ->
            Row(
                Modifier.fillMaxWidth().clickable { playSong(context, song, s.topSongs.map { it.first }, "Your top songs") }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.width(32.dp))
                Thumb(song.thumb, Modifier.size(52.dp).clip(RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, color = Color.White, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.channel, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("$n plays", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            }
        }
        item { WrapTitle("Your top artists") }
        itemsIndexed(s.topArtists, key = { _, a -> "ta:" + a.first }) { i, (name, n, art) ->
            Row(
                Modifier.fillMaxWidth().clickable { MusicUi.artist = name }.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.width(32.dp))
                Thumb(art, Modifier.size(52.dp).clip(CircleShape))
                Spacer(Modifier.width(12.dp))
                Text(name, color = Color.White, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$n plays", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Play your top songs, then share your year.
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(50)).background(SpotGreen)
                        .clickable(enabled = s.topSongs.isNotEmpty()) {
                            val list = s.topSongs.map { it.first }
                            playSong(context, list.first(), list, "Your top songs")
                        }.padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.Black)
                    Spacer(Modifier.width(6.dp))
                    Text("Play top songs", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White)
                        .clickable {
                            val text = buildString {
                                append("My ${s.year} in music: ${s.minutes} minutes, ${s.differentSongs} songs.\n")
                                top?.let { append("Top artist: ${it.first}\n") }
                                s.topSongs.take(3).forEachIndexed { i, p -> append("${i + 1}. ${p.first.title} — ${p.first.channel}\n") }
                            }
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share your year"))
                        }.padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Share, null, tint = Color.Black)
                    Spacer(Modifier.width(6.dp))
                    Text("Share", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Text(
                "Counts songs played in the Music tab of this app this year.",
                color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@Composable
private fun BigStat(value: String, label: String) {
    Column {
        Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp)
        Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun WrapTitle(text: String) {
    Text(
        text, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)
    )
}
