package com.sourabh.jarvistube.ui.music

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.data.Music
import com.sourabh.jarvistube.data.MusicForYou
import com.sourabh.jarvistube.data.SongList
import com.sourabh.jarvistube.data.VideoItem
import com.sourabh.jarvistube.ui.common.Thumb
import kotlinx.coroutines.launch

/** Spotify's green, for the Play button and "on" states in the Music tab. */
internal val SpotGreen = Color(0xFF1ED760)

/**
 * The Music tab, Spotify style: search YouTube Music, "Browse all" mood tiles and Recently
 * played. Songs play as sound only in the music player (MusicPlayer.kt); videos are untouched.
 */
@Composable
fun MusicScreen(bottomPadding: Dp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var list by remember { mutableStateOf<SongList?>(null) }

    /** Opens a song list: a search, a mood, or a mix / playlist made for you. */
    fun openWith(title: String, load: suspend () -> List<VideoItem>) {
        focus.clearFocus()
        val l = SongList(title)
        list = l
        scope.launch {
            try {
                l.songs = load()
                if (l.songs.isEmpty()) l.error = "No songs found."
            } catch (e: Exception) {
                l.error = "Couldn't load songs. Check your connection."
            } finally {
                l.loading = false
            }
        }
    }

    fun open(title: String, search: String) = openWith(title) { Music.songs(search) }

    // Made for you: mixes from what you play here, and YouTube Music's picks for your account.
    val mixes = remember(Music.recent.firstOrNull()?.url, Music.recent.size) { MusicForYou.localMixes() }
    val signedIn = com.sourabh.jarvistube.data.Session.isSignedIn
    LaunchedEffect(signedIn) { MusicForYou.loadShelves() }

    fun play(song: VideoItem, all: List<VideoItem>, source: String) {
        val reason = com.sourabh.jarvistube.data.ContentFilter.blockReason(null, song.channel, song.title, song.url)
            ?: Graph.study.blockReason(song)
        if (reason != null) {
            Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
            return
        }
        val i = all.indexOfFirst { it.url == song.url }
        Graph.player.playMusic(song, if (i >= 0) all.drop(i + 1) else emptyList(), source)
    }

    BackHandler(enabled = list != null && !MusicUi.playerOpen) { list = null }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().imePadding(),
        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp)
    ) {
        val l = list
        if (l == null) {
            item {
                Text(
                    "Music", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
                )
            }
            item {
                TextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("What do you want to listen to?") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) open("\"${query.trim()}\"", query.trim()) }),
                    shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
            if (Music.recent.isNotEmpty()) {
                item { SectionTitle("Recently played") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(Music.recent.toList(), key = { it.url }) { s ->
                            Column(Modifier.width(132.dp).clickable { play(s, Music.recent.toList(), "Recently played") }) {
                                Thumb(s.thumb, Modifier.size(132.dp).clip(RoundedCornerShape(6.dp)))
                                Spacer(Modifier.height(8.dp))
                                Text(s.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(s.channel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            if (mixes.isNotEmpty()) {
                item { SectionTitle("Made for you") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(mixes, key = { "mix:" + it.title }) { m ->
                            MixCard(m.title, m.subtitle, m.art) { openWith(m.title, m.load) }
                        }
                    }
                }
            }
            // YouTube Music's home for your account: what you listen to on YouTube shapes it.
            if (signedIn && (MusicForYou.shelves.isNotEmpty() || MusicForYou.shelvesLoading)) {
                item {
                    Text(
                        "From your YouTube account",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp)
                    )
                }
                if (MusicForYou.shelves.isEmpty()) item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
            MusicForYou.shelves.take(8).forEach { shelf ->
                item(key = "shelf:" + shelf.title) {
                    Text(
                        shelf.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
                    )
                }
                item(key = "shelfRow:" + shelf.title) {
                    val songs = shelf.cards.filter { it.isSong }.map { it.asSong() }
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(shelf.cards, key = { c -> "c:" + (c.videoId ?: "") + (c.playlistId ?: "") + (c.albumId ?: "") + c.title }) { c ->
                            MixCard(c.title, c.subtitle, c.art, small = c.isSong) {
                                if (c.isSong) {
                                    val song = c.asSong()
                                    play(song, songs, shelf.title)
                                    // Then songs like it, so the music carries on (like Spotify's autoplay).
                                    scope.launch {
                                        val more = runCatching { MusicForYou.radio(song) }.getOrDefault(emptyList())
                                        if (Graph.player.musicMode) Graph.player.enqueueAll(more.take(30))
                                    }
                                }
                                else openWith(c.title) { MusicForYou.open(c) }
                            }
                        }
                    }
                }
            }
            item { SectionTitle("Browse all") }
            Music.moods.chunked(2).forEach { pair ->
                item(key = "mood:" + pair.first().title) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { m ->
                            Box(
                                Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(8.dp))
                                    .background(Color(m.color))
                                    .clickable { open(m.title, m.query) }
                            ) {
                                Text(
                                    m.title, color = Color.White, fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                                // A tilted note in the corner, like Spotify's tilted covers.
                                Box(
                                    Modifier.align(Alignment.BottomEnd).offset(x = 14.dp, y = 8.dp).rotate(25f)
                                        .size(62.dp).clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black.copy(alpha = 0.22f)),
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(34.dp)) }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        } else {
            item {
                Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { list = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    Text(l.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (l.songs.isNotEmpty()) item {
                // Shuffle and the big green Play, like a Spotify playlist page.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${l.songs.size} songs", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        val mixed = l.songs.shuffled()
                        play(mixed.first(), mixed, l.title)
                    }) { Icon(Icons.Rounded.Shuffle, "Shuffle play", modifier = Modifier.size(28.dp)) }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(SpotGreen)
                            .clickable { play(l.songs.first(), l.songs, l.title) },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.PlayArrow, "Play", tint = Color.Black, modifier = Modifier.size(34.dp)) }
                }
            }
            when {
                l.loading -> item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                l.error != null && l.songs.isEmpty() -> item {
                    Text(l.error!!, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
                }
                else -> items(l.songs, key = { "song:" + it.url }) { s ->
                    SongRow(s, onClick = { play(s, l.songs, l.title) }, onQueue = {
                        Graph.player.enqueue(s)
                        Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                    })
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp)
    )
}

/** One song: square art, title (green while it plays), artist and length. */
@Composable
internal fun SongRow(s: VideoItem, onClick: () -> Unit, onQueue: (() -> Unit)? = null) {
    val now by Graph.player.now.collectAsStateWithLifecycle()
    val playing = Graph.player.musicMode && now?.video?.url == s.url
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumb(s.thumb, Modifier.size(52.dp).aspectRatio(1f).clip(RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                s.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (playing) SpotGreen else MaterialTheme.colorScheme.onSurface
            )
            Text(
                listOfNotNull(s.channel.ifBlank { null }, duration(s.durationSec)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (onQueue != null) IconButton(onClick = onQueue) {
            Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun duration(sec: Long): String? =
    if (sec <= 0) null else "%d:%02d".format(sec / 60, sec % 60)

/** A square cover with its name under it, for mixes, playlists and songs made for you. */
@Composable
private fun MixCard(title: String, subtitle: String, art: String?, small: Boolean = false, onClick: () -> Unit) {
    val size = if (small) 132.dp else 150.dp
    Column(Modifier.width(size).clickable(onClick = onClick)) {
        Box(Modifier.size(size).clip(RoundedCornerShape(6.dp))) {
            Thumb(art, Modifier.fillMaxSize())
            if (!small) {
                // The mix's name on a band of colour along the bottom, like Spotify's Daily Mixes.
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                        .padding(start = 10.dp, end = 10.dp, top = 24.dp, bottom = 8.dp)
                ) {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (small) Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
}
