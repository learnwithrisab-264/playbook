package com.sourabh.jarvistube.ui.channel

import com.sourabh.jarvistube.ui.common.Avatar
import com.sourabh.jarvistube.ui.common.compact
import com.sourabh.jarvistube.ui.common.ErrorBox
import com.sourabh.jarvistube.ui.common.LoadMoreEffect
import com.sourabh.jarvistube.ui.common.Thumb
import com.sourabh.jarvistube.ui.common.TopicChip
import com.sourabh.jarvistube.ui.you.SubscribeButton
import com.sourabh.jarvistube.ui.you.UploadBell
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.Page
import com.sourabh.jarvistube.TubeViewModel
import com.sourabh.jarvistube.data.ChannelPage
import com.sourabh.jarvistube.data.ChannelRepo
import com.sourabh.jarvistube.data.ChannelTab
import com.sourabh.jarvistube.data.StudyChannels
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page as NpPage

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChannelScreen(vm: TubeViewModel, page: Page.Channel, bottomPadding: Dp) {
    val scope = rememberCoroutineScope()
    var channel by remember(page.url) { mutableStateOf<ChannelPage?>(null) }
    var error by remember(page.url) { mutableStateOf<String?>(null) }
    var selected by rememberSaveable(page.url) { mutableStateOf("home") }
    val tabStates = remember(page.url) { mutableStateMapOf<String, TabState>() }
    val listState = rememberLazyListState()

    fun loadTab(c: ChannelPage, tab: ChannelTab, more: Boolean) {
        val cur = tabStates[tab.key] ?: TabState()
        if (cur.loading || (more && cur.next == null)) return
        tabStates[tab.key] = cur.copy(loading = true, error = null)
        scope.launch {
            tabStates[tab.key] = try {
                val p = if (more) ChannelRepo.tabMore(c, tab, cur.next!!) else ChannelRepo.tab(c, tab)
                TabState((cur.items + p.items).distinctBy { it.key }, p.next)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                cur.copy(loading = false, error = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    LaunchedEffect(page.url) {
        try {
            channel = ChannelRepo.page(page.url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
    }
    val c = channel
    // "Home" shows the About text plus the latest uploads (the Videos tab's first page).
    val activeTab: ChannelTab? = c?.let { ch ->
        if (selected == "home") ch.tabs.firstOrNull { it.key == "videos" } else ch.tabs.firstOrNull { it.key == selected }
    }
    LaunchedEffect(c, activeTab?.key) {
        if (c != null && activeTab != null && tabStates[activeTab.key] == null) loadTab(c, activeTab, more = false)
    }
    val state = activeTab?.let { tabStates[it.key] } ?: TabState(loading = c == null && error == null)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp)
    ) {
        item { PageTopBar(c?.ref?.name ?: page.name, vm::popPage) }
        if (error != null && c == null) {
            item { ErrorBox(error!!, onRetry = { error = null; scope.launch { runCatching { channel = ChannelRepo.page(page.url) }.onFailure { error = it.message } } }) }
            return@LazyColumn
        }
        if (c != null) {
            item { ChannelHeader(c) }
            stickyHeader {
                Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { TopicChip("Home", selected == "home") { selected = "home" } }
                        items(c.tabs, key = { it.key }) { t -> TopicChip(t.label, selected == t.key) { selected = t.key } }
                    }
                }
            }
            if (selected == "home" && c.description.isNotBlank()) {
                item {
                    Text(
                        c.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
        if (c != null && activeTab == null) {
            item { EmptyHint("This channel has nothing here.") }
        } else {
            entryItems(vm, state, onRetry = { if (c != null && activeTab != null) loadTab(c, activeTab, more = false) })
        }
    }
    if (c != null && activeTab != null && selected != "home") {
        LoadMoreEffect(listState, state.items.size) { loadTab(c, activeTab, more = true) }
    }
}

@Composable
internal fun ChannelHeader(c: ChannelPage) {
    val inStudy = StudyChannels.contains(c.ref.url)
    Column {
        if (c.banner != null) {
            Thumb(c.banner, Modifier.fillMaxWidth().aspectRatio(6.2f).padding(horizontal = 12.dp).clip(RoundedCornerShape(12.dp)))
        }
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(c.ref.name, 72, c.ref.avatar)
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(c.ref.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (c.subscribers >= 0) {
                    Text(
                        "${compact(c.subscribers)} subscribers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            UploadBell(c.ref)
            SubscribeButton(c.ref.url, c.ref.name)
        }
        Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
            if (inStudy) {
                FilledTonalButton(onClick = { StudyChannels.toggle(c.ref) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("In my channels")
                }
            } else {
                Button(onClick = { StudyChannels.toggle(c.ref) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Add to my channels")
                }
            }
        }
    }
}

// ------------------------------------------------------------------ playlist
