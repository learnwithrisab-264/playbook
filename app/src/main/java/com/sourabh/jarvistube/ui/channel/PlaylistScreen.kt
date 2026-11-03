package com.sourabh.jarvistube.ui.channel

import com.sourabh.jarvistube.R
import androidx.compose.ui.res.stringResource
import com.sourabh.jarvistube.ui.common.LoadMoreEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.Page
import com.sourabh.jarvistube.TubeViewModel
import com.sourabh.jarvistube.data.ChannelRepo
import com.sourabh.jarvistube.data.Entry
import com.sourabh.jarvistube.data.PlaylistPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page as NpPage

@Composable
internal fun PlaylistScreen(vm: TubeViewModel, page: Page.Playlist, bottomPadding: Dp) {
    val scope = rememberCoroutineScope()
    var info by remember(page.url) { mutableStateOf<PlaylistPage?>(null) }
    var state by remember(page.url) { mutableStateOf(TabState(loading = true)) }
    val listState = rememberLazyListState()

    fun load(more: Boolean) {
        val cur = state
        if (more && (cur.loading || cur.next == null)) return
        state = cur.copy(loading = true, error = null)
        scope.launch {
            state = try {
                if (more) {
                    val p = ChannelRepo.playlistMore(page.url, cur.next!!)
                    TabState((cur.items + p.items).distinctBy { it.key }, p.next)
                } else {
                    val p = ChannelRepo.playlist(page.url)
                    info = p
                    TabState(p.first.items, p.first.next)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                cur.copy(loading = false, error = e.message ?: e.javaClass.simpleName)
            }
        }
    }
    LaunchedEffect(page.url) { load(more = false) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp)
    ) {
        item { PageTopBar(info?.title ?: page.title, vm::popPage) }
        info?.let { p ->
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(p.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOf(p.owner, if (p.count >= 0) "${p.count} videos" else "").filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    val videos = state.items.filterIsInstance<Entry.Video>().map { it.video }
                    Button(
                        onClick = {
                            Graph.player.clearQueue()
                            Graph.player.enqueueAll(videos.drop(1))
                            videos.firstOrNull()?.let(vm::play)
                        },
                        enabled = videos.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.play_all))
                    }
                }
            }
        }
        entryItems(vm, state, onRetry = { load(more = false) })
    }
    LoadMoreEffect(listState, state.items.size) { load(more = true) }
}

// ------------------------------------------------------------------ study channels picker
