package com.sourabh.jarvistube.ui.music

import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
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

    var newPlaylist by remember { mutableStateOf(false) }
    if (newPlaylist) NewPlaylistDialog(onDismiss = { newPlaylist = false })

    /** Opens one of your own lists (Liked Songs, Downloaded, a playlist): it stays up to date. */
    fun openLive(title: String, playlist: String? = null, live: () -> List<VideoItem>) {
        focus.clearFocus()
        list = SongList(title, playlist, live).also { it.loading = false }
    }

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

    fun play(song: VideoItem, all: List<VideoItem>, source: String) = playSong(context, song, all, source)

    BackHandler(enabled = list != null && !MusicUi.playerOpen && MusicUi.artist == null) { list = null }

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
            item { SectionTitle("Your library") }
            item {
                val lib = com.sourabh.jarvistube.data.MusicLibrary
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    item(key = "liked") {
                        LibraryTile(
                            "Liked Songs", songsText(lib.liked.size), null,
                            colors = listOf(Color(0xFF450AF5), Color(0xFF8E8EE5)), icon = Icons.Rounded.Favorite
                        ) { openLive("Liked Songs") { lib.liked.toList() } }
                    }
                    item(key = "downloaded") {
                        val n = com.sourabh.jarvistube.data.Downloads.saved.count { it.audioOnly }
                        LibraryTile(
                            "Downloaded", songsText(n) + ", play offline", null,
                            colors = listOf(Color(0xFF0B7A3E), Color(0xFF1ED760)), icon = Icons.Rounded.DownloadDone
                        ) { openLive("Downloaded") { lib.downloaded() } }
                    }
                    items(lib.playlistNames, key = { "pl:$it" }) { name ->
                        val songs = lib.playlists[name].orEmpty()
                        LibraryTile(name, "Playlist · " + songsText(songs.size), songs.firstOrNull()?.thumb, icon = Icons.Rounded.MusicNote) {
                            openLive(name, playlist = name) { lib.playlists[name].orEmpty() }
                        }
                    }
                    item(key = "newPlaylist") {
                        LibraryTile("New playlist", "Make your own", null, icon = Icons.Rounded.Add) { newPlaylist = true }
                    }
                }
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
            val songs = l.live?.invoke() ?: l.songs
            item {
                Row(Modifier.padding(start = 4.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { list = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    Text(
                        l.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    if (l.playlist != null) TextButton(onClick = {
                        com.sourabh.jarvistube.data.MusicLibrary.deletePlaylist(l.playlist)
                        list = null
                    }) { Text("Delete playlist") }
                }
            }
            if (songs.isNotEmpty()) item {
                // Shuffle and the big green Play, like a Spotify playlist page.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(songsText(songs.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        val mixed = songs.shuffled()
                        play(mixed.first(), mixed, l.title)
                    }) { Icon(Icons.Rounded.Shuffle, "Shuffle play", modifier = Modifier.size(28.dp)) }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(SpotGreen)
                            .clickable { play(songs.first(), songs, l.title) },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.PlayArrow, "Play", tint = Color.Black, modifier = Modifier.size(34.dp)) }
                }
            }
            when {
                l.loading -> item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                l.error != null && songs.isEmpty() -> item {
                    Text(l.error!!, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
                }
                songs.isEmpty() && l.live != null -> item {
                    Text(
                        when (l.title) {
                            "Liked Songs" -> "Songs you like show here. Tap the heart in the player, or ⋮ › Add to Liked Songs."
                            "Downloaded" -> "Songs you download (⋮ › Download) play here without internet."
                            else -> "Add songs with ⋮ › Add to playlist."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp)
                    )
                }
                else -> items(songs, key = { "song:" + it.url }) { s ->
                    SongRow(s, onClick = { play(s, songs, l.title) }, playlist = l.playlist)
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
internal fun SongRow(s: VideoItem, onClick: () -> Unit, playlist: String? = null) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Saved for offline: a small green arrow, like Spotify's.
                val p = com.sourabh.jarvistube.data.MusicLibrary.progress(s)
                if (com.sourabh.jarvistube.data.MusicLibrary.offline(s.url) != null) {
                    Icon(Icons.Rounded.DownloadDone, "Downloaded", tint = SpotGreen, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                } else if (p != null) {
                    CircularProgressIndicator(
                        progress = { if (p < 0f) 0f else p }, modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = SpotGreen
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    listOfNotNull(s.channel.ifBlank { null }, duration(s.durationSec)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        SongMenu(s, playlist)
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

internal fun songsText(n: Int) = if (n == 1) "1 song" else "$n songs"
