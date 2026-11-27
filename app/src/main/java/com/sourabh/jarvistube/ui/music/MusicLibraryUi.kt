package com.sourabh.jarvistube.ui.music

import android.content.Context
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.data.ArtistPage
import com.sourabh.jarvistube.data.MusicCard
import com.sourabh.jarvistube.data.MusicForYou
import com.sourabh.jarvistube.data.MusicLibrary
import com.sourabh.jarvistube.data.VideoItem
import com.sourabh.jarvistube.ui.common.Thumb
import kotlinx.coroutines.launch

/** Plays [song] in the Music tab with the rest of [all] after it, after the usual checks. */
internal fun playSong(context: Context, song: VideoItem, all: List<VideoItem>, source: String) {
    val reason = com.sourabh.jarvistube.data.ContentFilter.blockReason(null, song.channel, song.title, song.url)
        ?: Graph.study.blockReason(song)
    if (reason != null) {
        Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
        return
    }
    val i = all.indexOfFirst { it.url == song.url }
    Graph.player.playMusic(song, if (i >= 0) all.drop(i + 1) else emptyList(), source)
}

/** The first artist named in "A, B & C". */
internal fun firstArtist(s: String) = s.split(",", "&", " x ", " feat", " ft.").first().trim()

/** ⋮ on a song: like, add to a playlist, download, queue, go to the artist. */
@Composable
internal fun SongMenu(song: VideoItem, playlist: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val liked = MusicLibrary.isLiked(song.url)
            DropdownMenuItem(
                text = { Text(if (liked) "Remove from Liked Songs" else "Add to Liked Songs") },
                leadingIcon = { Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = if (liked) SpotGreen else Color.Unspecified) },
                onClick = { open = false; MusicLibrary.toggleLike(song) }
            )
            DropdownMenuItem(
                text = { Text("Add to playlist") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                onClick = { open = false; pick = true }
            )
            if (playlist != null) DropdownMenuItem(
                text = { Text("Remove from this playlist") },
                leadingIcon = { Icon(Icons.Rounded.RemoveCircleOutline, null) },
                onClick = { open = false; MusicLibrary.removeFrom(playlist, song.url) }
            )
            val saved = MusicLibrary.offline(song.url) != null
            val p = MusicLibrary.progress(song)
            DropdownMenuItem(
                text = { Text(if (saved) "Downloaded" else if (p != null) "Downloading…" else "Download") },
                leadingIcon = { Icon(if (saved) Icons.Rounded.DownloadDone else Icons.Rounded.Download, null, tint = if (saved) SpotGreen else Color.Unspecified) },
                enabled = !saved && p == null,
                onClick = {
                    open = false
                    scope.launch {
                        runCatching { MusicLibrary.download(song) }
                            .onSuccess { Toast.makeText(context, "Downloading for offline", Toast.LENGTH_SHORT).show() }
                            .onFailure { Toast.makeText(context, "Couldn't download. Check your connection.", Toast.LENGTH_SHORT).show() }
                    }
                }
            )
            DropdownMenuItem(
                text = { Text("Add to queue") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                onClick = {
                    open = false
                    Graph.player.enqueue(song)
                    Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                }
            )
            if (song.channel.isNotBlank()) DropdownMenuItem(
                text = { Text("Go to artist") },
                leadingIcon = { Icon(Icons.Rounded.Person, null) },
                onClick = { open = false; MusicUi.artist = firstArtist(song.channel) }
            )
        }
    }
    if (pick) AddToPlaylistDialog(song, onDismiss = { pick = false })
}

/** Pick one of your playlists for [song], or make a new one with it. */
@Composable
internal fun AddToPlaylistDialog(song: VideoItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column {
                MusicLibrary.playlistNames.forEach { n ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                            val added = MusicLibrary.addTo(n, song)
                            Toast.makeText(context, if (added) "Added to $n" else "Already in $n", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, null)
                        Spacer(Modifier.width(12.dp))
                        Text(n, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("${MusicLibrary.playlists[n]?.size ?: 0}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    name, { name = it }, singleLine = true,
                    placeholder = { Text("New playlist name") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                if (MusicLibrary.createPlaylist(name)) {
                    MusicLibrary.addTo(name.trim(), song)
                    Toast.makeText(context, "Added to ${name.trim()}", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(context, "You already have a playlist with that name", Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text("Create and add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Make an empty playlist. */
@Composable
internal fun NewPlaylistDialog(onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Name") }) },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { MusicLibrary.createPlaylist(name); onDismiss() }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** A tile in "Your library": a cover (or a coloured square with an icon) and its name. */
@Composable
internal fun LibraryTile(
    title: String, subtitle: String, art: String?,
    colors: List<Color>? = null, icon: ImageVector? = null, onClick: () -> Unit
) {
    Column(Modifier.width(132.dp).clickable(onClick = onClick)) {
        Box(
            Modifier.size(132.dp).clip(RoundedCornerShape(6.dp))
                .then(if (colors != null) Modifier.background(Brush.linearGradient(colors)) else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)),
            contentAlignment = Alignment.Center
        ) {
            if (art != null && colors == null) Thumb(art, Modifier.fillMaxSize())
            else Icon(icon ?: Icons.Rounded.MusicNote, null, tint = if (colors != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * An artist's page, like Spotify's: picture and name, Play / Shuffle / Artist radio, their top
 * songs, then albums and singles (tap to open). Opens from "Go to artist" or the player.
 */
@Composable
internal fun ArtistScreen(name: String, bottomPadding: Dp, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(enabled = !MusicUi.playerOpen, onBack = onBack)
    val page by produceState<Result<ArtistPage>?>(null, name) { value = runCatching { MusicForYou.artist(name) } }
    var album by remember(name) { mutableStateOf<Pair<MusicCard, List<VideoItem>?>?>(null) }
    val bg = MaterialTheme.colorScheme.background

    Box(Modifier.fillMaxSize().background(bg).clickable(enabled = false) {}) {
        val p = page
        when {
            p == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            p.isFailure -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Couldn't open $name.", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onBack) { Text("Back") }
            }
            else -> {
                val a = p.getOrThrow()
                val shown = album
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding + 16.dp)) {
                    // Header: the artist's picture fading into the page, name over it.
                    item {
                        Box(Modifier.fillMaxWidth().height(300.dp)) {
                            Thumb(if (shown != null) shown.first.art else a.art, Modifier.fillMaxSize())
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent, bg))))
                            IconButton(onClick = { if (album != null) album = null else onBack() }, modifier = Modifier.statusBarsPadding().padding(4.dp)) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White)
                            }
                            Text(
                                if (shown != null) shown.first.title else a.name,
                                style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                            )
                        }
                    }
                    val songs = if (shown != null) shown.second.orEmpty() else a.songs
                    val source = if (shown != null) shown.first.title else a.name
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (shown != null && shown.second == null) "Loading…" else if (songs.size == 1) "1 song" else "${songs.size} songs",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            if (shown == null) IconButton(onClick = {
                                // Artist radio: songs like theirs, starting from their top song.
                                val top = a.songs.firstOrNull() ?: return@IconButton
                                scope.launch {
                                    val radio = runCatching { MusicForYou.radio(top) }.getOrDefault(emptyList())
                                    playSong(context, top, listOf(top) + radio, "${a.name} radio")
                                }
                            }) { Icon(Icons.Rounded.Radio, "Artist radio", modifier = Modifier.size(26.dp)) }
                            IconButton(enabled = songs.isNotEmpty(), onClick = {
                                val mixed = songs.shuffled()
                                playSong(context, mixed.first(), mixed, source)
                            }) { Icon(Icons.Rounded.Shuffle, "Shuffle", modifier = Modifier.size(26.dp)) }
                            Spacer(Modifier.width(6.dp))
                            Box(
                                Modifier.size(56.dp).clip(CircleShape).background(SpotGreen)
                                    .clickable(enabled = songs.isNotEmpty()) { playSong(context, songs.first(), songs, source) },
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Rounded.PlayArrow, "Play", tint = Color.Black, modifier = Modifier.size(34.dp)) }
                        }
                    }
                    if (shown == null) item { SmallTitle("Popular") }
                    if (shown != null && shown.second == null) item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    items(songs, key = { "a:" + it.url }) { s -> SongRow(s, onClick = { playSong(context, s, songs, source) }) }
                    if (shown == null) {
                        listOf("Albums" to a.albums, "Singles" to a.singles).forEach { (title, cards) ->
                            if (cards.isNotEmpty()) {
                                item { SmallTitle(title) }
                                item {
                                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        items(cards, key = { c -> title + (c.albumId ?: c.playlistId ?: c.title) }) { c ->
                                            LibraryTile(c.title, c.subtitle, c.art) {
                                                album = c to null
                                                scope.launch {
                                                    val list = runCatching {
                                                        if (c.albumId != null) MusicForYou.album(c.albumId, a.name, c.art) else MusicForYou.open(c)
                                                    }.getOrDefault(emptyList())
                                                    if (album?.first == c) album = c to list
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp)
    )
}
