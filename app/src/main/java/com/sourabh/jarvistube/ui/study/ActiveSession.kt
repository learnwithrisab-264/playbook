package com.sourabh.jarvistube.ui.study

import com.sourabh.jarvistube.ui.common.Avatar
import com.sourabh.jarvistube.ui.common.ErrorBox
import com.sourabh.jarvistube.ui.common.VideoCard
import com.sourabh.jarvistube.ui.common.VideoCardSkeleton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import com.sourabh.jarvistube.data.StudyChannels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.TubeViewModel
import kotlinx.coroutines.delay

/**
 * Focus mode home, laid out like YouTube's Subscriptions page: a slim header with the time left,
 * a strip of your channels to pick from (or All), a search box, then the videos.
 */
@Composable
internal fun ActiveSession(vm: TubeViewModel, bottomPadding: Dp) {
    val study = Graph.study
    var remaining by remember { mutableLongStateOf(study.remainingMs) }
    var query by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<String?>(null) } // channel name; null = All
    val perChannel = remember { mutableStateMapOf<String, List<com.sourabh.jarvistube.data.VideoItem>>() }
    var channelLoading by remember { mutableStateOf(false) }
    var extendDialog by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }

    LaunchedEffect(study.endAt) {
        while (true) {
            remaining = study.remainingMs
            delay(1000)
        }
    }
    LaunchedEffect(study.active) {
        if (study.active) {
            if (vm.studyFeed.items.isEmpty()) vm.loadStudyFeed()
            if (study.feed.items.isEmpty()) study.loadFeed(null)
        }
    }

    // Every channel allowed during focus: "My channels" (with photos) plus names added in Study.
    val channels = remember(StudyChannels.list.size, study.allowed.size) {
        val known = StudyChannels.list.map { Triple(it.name, it.avatar, it.url) }
        val extra = study.allowed.filterNot { StudyChannels.containsName(it) }.map { Triple(it, null, null) }
        known + extra
    }
    // Picking a channel with a known page loads its newest uploads; others filter the search feed.
    LaunchedEffect(selected) {
        val name = selected ?: return@LaunchedEffect
        val url = channels.firstOrNull { it.first == name }?.third ?: return@LaunchedEffect
        if (perChannel[name] != null) return@LaunchedEffect
        channelLoading = true
        perChannel[name] = runCatching { com.sourabh.jarvistube.data.ChannelRepo.latestVideos(url) }.getOrDefault(emptyList())
        channelLoading = false
    }

    // Only videos that can actually play now: from one of the allowed channels.
    fun allowed(v: com.sourabh.jarvistube.data.VideoItem) = v.channel.isBlank() || study.isAllowed(v.channel, v.channelUrl)
    val allVideos = (vm.studyFeed.items + study.feed.items).filter(::allowed).distinctBy { it.url }
    val shown = when {
        searched != null -> study.feed.items.filter { allowed(it) && (selected == null || it.channel.equals(selected, true)) }
        selected != null -> perChannel[selected]?.takeIf { it.isNotEmpty() }
            ?: allVideos.filter { it.channel.equals(selected, true) }
        else -> allVideos
    }
    val loading = when {
        searched != null -> study.feed.loading
        selected != null -> channelLoading && shown.isEmpty()
        else -> allVideos.isEmpty() && (vm.studyFeed.loading || study.feed.loading)
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().imePadding(),
        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp)
    ) {
        // Header: title + countdown pill, a thin progress line, and the lock line.
        item {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, null, Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Focus mode", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(
                        "${remainingText(remaining / 1000)} left",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                val total = (study.sessionMinutes * 60_000f).coerceAtLeast(1f)
                val done = (1f - remaining / total).coerceIn(0f, 1f)
                // Plain YouTube-style progress line: grey track, red fill, no gaps or end dots.
                Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(done).fillMaxHeight().background(Color(0xFFFF6D00)))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(40.dp)) {
                    Text(
                        if (study.strict) "Locked until ${lockDate(study.endAt)}" else "Ends at ${lockDate(study.endAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (study.longLock) TextButton(onClick = { extendDialog = true }) { Text("Extend") }
                    if (!study.strict) TextButton(onClick = { confirmEnd = true }) { Text("End early") }
                }
            }
        }

        // Channel strip: All, then each channel. The picked one is ringed, the rest dimmed.
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ChannelBubble("All", null, selected == null) { selected = null }
                }
                items(channels, key = { it.first }) { (name, avatar, _) ->
                    ChannelBubble(name, avatar, selected == name) { selected = if (selected == name) null else name }
                }
            }
        }

        // Search inside the allowed channels.
        item {
            TextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(if (selected != null) "Search $selected" else "Search your channels") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (searched != null || query.isNotEmpty()) {
                        IconButton(onClick = { query = ""; searched = null }) { Icon(Icons.Rounded.Close, "Clear") }
                    }
                },
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (query.isNotBlank()) {
                        searched = query.trim()
                        study.loadFeed(listOfNotNull(searched, selected).joinToString(" "))
                    }
                }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        when {
            loading -> items(3) { VideoCardSkeleton() }
            searched != null && study.feed.error != null && shown.isEmpty() ->
                item { ErrorBox(study.feed.error!!, onRetry = { study.loadFeed(searched) }) }
            shown.isEmpty() -> item {
                Text(
                    if (searched != null) "Nothing found for \"$searched\"." else "No videos yet from ${selected ?: "your channels"}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp)
                )
            }
            else -> items(shown, key = { it.url }) { v -> VideoCard(v, onClick = { vm.play(v) }) }
        }
    }

    if (extendDialog) {
        LongLockDialog(
            base = study.endAt,
            title = "Extend Study lock",
            confirmLabel = "Extend",
            onConfirm = { until -> study.extendLongLock(until); extendDialog = false },
            onDismiss = { extendDialog = false }
        )
    }
    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End focus early?") },
            confirmButton = { TextButton(onClick = { confirmEnd = false; study.endEarly() }) { Text("End") } },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Keep going") } }
        )
    }
}

@Composable
internal fun ChannelBubble(name: String, avatar: String?, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.width(68.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 4.dp)
            .alpha(if (selected) 1f else 0.6f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(60.dp).clip(CircleShape)
                .background(if (selected) cs.onSurface else Color.Transparent)
                .padding(2.dp).clip(CircleShape).background(cs.background).padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            if (avatar == null && name == "All") {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(cs.surfaceVariant), contentAlignment = Alignment.Center) {
                    Text("All", fontWeight = FontWeight.Bold)
                }
            } else {
                Avatar(name, 52, avatar)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            name, style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
