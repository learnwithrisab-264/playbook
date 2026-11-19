package com.sourabh.jarvistube.ui.you

import com.sourabh.jarvistube.R
import androidx.compose.ui.res.stringResource
import com.sourabh.jarvistube.ui.common.Avatar
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.LibraryViewModel
import com.sourabh.jarvistube.data.InnerTube
import com.sourabh.jarvistube.data.PlaylistChoice
import com.sourabh.jarvistube.data.Session
import com.sourabh.jarvistube.data.VideoItem
import com.sourabh.jarvistube.data.channelIdOf
import kotlinx.coroutines.launch

/**
 * YouTube's Subscribe / Subscribed button for the signed-in account. Hidden when signed out
 * or when the channel id isn't known. Unsubscribing asks first, like YouTube.
 */
@Composable
fun SubscribeButton(channelUrl: String?, channelName: String, modifier: Modifier = Modifier) {
    val id = channelIdOf(channelUrl) ?: return
    if (!Session.isSignedIn) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var subscribed by remember(id, Session.activeId) { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(id, Session.activeId) {
        subscribed = runCatching { InnerTube.isSubscribed(id) }.getOrNull()
    }

    fun set(value: Boolean) {
        busy = true
        scope.launch {
            val ok = runCatching { if (value) InnerTube.subscribe(id) else InnerTube.unsubscribe(id) }.isSuccess
            busy = false
            if (ok) subscribed = value
            Toast.makeText(
                context,
                if (!ok) "Couldn't update the subscription" else if (value) "Subscribed to $channelName" else "Unsubscribed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    when (subscribed) {
        null -> Unit
        true -> FilledTonalButton(onClick = { confirm = true }, enabled = !busy, modifier = modifier) {
            Icon(Icons.Rounded.NotificationsNone, null, Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.subscribed))
        }
        false -> Button(
            onClick = { set(true) }, enabled = !busy, modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) { Text(stringResource(R.string.subscribe)) }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            text = { Text("Unsubscribe from $channelName?") },
            confirmButton = { TextButton(onClick = { confirm = false; set(false) }) { Text(stringResource(R.string.unsubscribe)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

/** 🔔 on a channel page: get a notification when this channel uploads. Works signed out too. */
@Composable
fun UploadBell(ref: com.sourabh.jarvistube.data.ChannelRef) {
    val context = LocalContext.current
    val on = com.sourabh.jarvistube.data.UploadAlerts.isOn(ref.url)
    androidx.compose.material3.IconButton(onClick = {
        com.sourabh.jarvistube.data.UploadAlerts.toggle(ref)
        Toast.makeText(
            context,
            if (!on) "You'll be notified when ${ref.name} uploads" else "Notifications off for ${ref.name}",
            Toast.LENGTH_SHORT
        ).show()
    }) {
        Icon(
            if (on) androidx.compose.material.icons.Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone,
            if (on) "Turn off upload notifications" else "Notify me of new uploads"
        )
    }
}

/** Which video the app-wide "Save to playlist" sheet is open for. */
object SaveToPlaylist {
    var video by mutableStateOf<VideoItem?>(null); private set
    fun open(v: VideoItem) { video = v }
    fun close() { video = null }
}

/** Hosted once at the app root: tick playlists (Watch later included) to save or remove the video. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveToPlaylistHost() {
    val video = SaveToPlaylist.video ?: return
    val videoId = LibraryViewModel.videoId(video.url) ?: return SaveToPlaylist.close()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val choices = remember(videoId) { mutableStateListOf<PlaylistChoice>() }
    var loading by remember(videoId) { mutableStateOf(true) }
    var newTitle by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        val list = runCatching { InnerTube.playlistsFor(videoId) }.getOrNull()
        choices.clear()
        list?.let(choices::addAll)
        loading = false
        if (list == null) Toast.makeText(context, "Couldn't load your playlists", Toast.LENGTH_SHORT).show()
    }
    LaunchedEffect(videoId) { load() }

    ModalBottomSheet(
        onDismissRequest = { SaveToPlaylist.close() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(stringResource(R.string.save_video_to), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            if (loading) {
                com.sourabh.jarvistube.ui.theme.ChakraSpinner(36.dp, Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
            }
            choices.forEachIndexed { i, p ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        val want = !p.contains
                        choices[i] = p.copy(contains = want)
                        scope.launch {
                            val ok = runCatching {
                                if (want) InnerTube.addToPlaylist(p.id, videoId) else InnerTube.removeFromPlaylist(p.id, videoId, null)
                            }.isSuccess
                            if (!ok) choices[i] = p
                            Toast.makeText(
                                context,
                                if (!ok) "Couldn't update ${p.title}" else if (want) "Saved to ${p.title}" else "Removed from ${p.title}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = p.contains, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                    Text(p.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newTitle, onValueChange = { newTitle = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.new_playlist_name)) }, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(8.dp))
                TextButton(
                    enabled = newTitle.isNotBlank() && !creating,
                    onClick = {
                        creating = true
                        scope.launch {
                            val ok = runCatching { InnerTube.createPlaylist(newTitle.trim(), videoId) }.isSuccess
                            creating = false
                            Toast.makeText(context, if (ok) "Saved to new playlist \"${newTitle.trim()}\"" else "Couldn't create the playlist", Toast.LENGTH_SHORT).show()
                            if (ok) { newTitle = ""; load() }
                        }
                    }
                ) { Text(stringResource(R.string.create)) }
            }
        }
    }
}

/**
 * Account switcher, like YouTube's: every saved account with its photo, name and email,
 * the active one ticked; add another account, or sign out of the current one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(lib: LibraryViewModel, onAddAccount: () -> Unit, onDismiss: () -> Unit) {
    var confirmSignOut by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(stringResource(R.string.accounts), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            lib.accounts.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().clickable { lib.switchAccount(a.id); onDismiss() }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(a.name ?: a.email ?: "?", 40, a.photo)
                    Spacer(Modifier.size(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name ?: "Google account", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(a.handle, a.email).joinToString(" · ").ifEmpty { "Loading details…" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (a.id == Session.activeId) Icon(Icons.Rounded.Check, "Current account", tint = MaterialTheme.colorScheme.primary)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SheetAction(Icons.Rounded.Add, "Add account") { onDismiss(); onAddAccount() }
            SheetAction(Icons.AutoMirrored.Rounded.Logout, "Sign out of this account") { confirmSignOut = true }
            Spacer(Modifier.height(4.dp))
        }
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.sign_out)) },
            text = { Text("${Session.active?.email ?: "This account"} will be removed from PlayBook. Other accounts stay signed in.") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; lib.signOut(); onDismiss() }) { Text(stringResource(R.string.sign_out_2)) } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null)
        Spacer(Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
