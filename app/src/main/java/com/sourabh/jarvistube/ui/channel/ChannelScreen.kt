package com.sourabh.jarvistube.ui.channel

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.Page
import com.sourabh.jarvistube.TubeViewModel
import com.sourabh.jarvistube.data.ChannelBrowse
import com.sourabh.jarvistube.data.ChannelHeaderInfo
import com.sourabh.jarvistube.data.ChannelPage
import com.sourabh.jarvistube.data.ChannelPost
import com.sourabh.jarvistube.data.ChannelRepo
import com.sourabh.jarvistube.data.ChannelTabPage
import com.sourabh.jarvistube.data.LibPlaylist
import com.sourabh.jarvistube.data.LibVideo
import com.sourabh.jarvistube.data.StudyChannels
import com.sourabh.jarvistube.data.VideoItem
import com.sourabh.jarvistube.data.channelIdOf
import com.sourabh.jarvistube.ui.common.Avatar
import com.sourabh.jarvistube.ui.common.EmptyLine
import com.sourabh.jarvistube.ui.common.ErrorBox
import com.sourabh.jarvistube.ui.common.LoadMoreEffect
import com.sourabh.jarvistube.ui.common.LoadingView
import com.sourabh.jarvistube.ui.common.Thumb
import com.sourabh.jarvistube.ui.common.TopicChip
import com.sourabh.jarvistube.ui.common.VideoCard
import com.sourabh.jarvistube.ui.common.VideoCardSkeleton
import com.sourabh.jarvistube.ui.common.formatDuration
import com.sourabh.jarvistube.ui.shorts.ShortCard
import com.sourabh.jarvistube.ui.theme.Corners
import com.sourabh.jarvistube.ui.theme.OnVideo
import com.sourabh.jarvistube.ui.you.SubscribeButton
import com.sourabh.jarvistube.ui.you.UploadBell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** What a tab has loaded so far. [chip] is the selected sort (Latest / Popular / Oldest). */
private data class TabUi(
    val page: ChannelTabPage = ChannelTabPage(),
    val loading: Boolean = false,
    val error: String? = null,
    val chip: Int = 0
)

/**
 * A channel the way YouTube shows it: banner, avatar and name with the verified mark, @handle,
 * subscribers and video count, the description with "...more", links, a full-width Subscribe,
 * and YouTube's own tabs (Home, Videos, Shorts, Live, Podcasts, Playlists, Posts). Videos can be
 * sorted Latest / Popular / Oldest. Plus PlayBook's own "My Channels" and upload alerts.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChannelScreen(vm: TubeViewModel, page: Page.Channel, bottomPadding: Dp) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var basic by remember(page.url) { mutableStateOf<ChannelPage?>(null) }
    var head by remember(page.url) { mutableStateOf<ChannelHeaderInfo?>(null) }
    var channelId by remember(page.url) { mutableStateOf<String?>(null) }
    var error by remember(page.url) { mutableStateOf<String?>(null) }
    var selected by rememberSaveable(page.url) { mutableStateOf("Home") }
    val tabs = remember(page.url) { mutableStateMapOf<String, TabUi>() }
    val listState = rememberLazyListState()

    suspend fun loadHeader() {
        try {
            val b = ChannelRepo.page(page.url)
            basic = b
            val id = channelIdOf(b.ref.url) ?: channelIdOf(page.url) ?: throw IllegalStateException("No channel id")
            channelId = id
            val (h, home) = ChannelBrowse.header(id)
            head = h
            tabs["Home"] = TabUi(home)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
    }
    LaunchedEffect(page.url) { loadHeader() }

    val h = head
    // Shorts follow the same rules as the Shorts tab (hidden during study sessions and locks).
    val shownTabs = h?.tabs.orEmpty().filter { it.title != "Shorts" || vm.shortsAllowed }

    fun open(tab: String) {
        val id = channelId ?: return
        val ref = h?.tabs?.firstOrNull { it.title == tab } ?: return
        if (tabs[tab]?.page?.let { it.items.isNotEmpty() || it.shorts.isNotEmpty() || it.posts.isNotEmpty() } == true) return
        tabs[tab] = TabUi(loading = true)
        scope.launch {
            tabs[tab] = runCatching { TabUi(ChannelBrowse.tab(id, ref.params)) }
                .getOrElse { TabUi(error = it.message ?: "Couldn't load") }
        }
    }

    fun more(tab: String) {
        val cur = tabs[tab] ?: return
        val token = cur.page.next ?: return
        if (cur.loading) return
        tabs[tab] = cur.copy(loading = true)
        scope.launch {
            tabs[tab] = runCatching {
                val p = ChannelBrowse.more(token)
                cur.copy(
                    page = cur.page.copy(
                        items = (cur.page.items + p.items).distinctBy { it.key },
                        shorts = (cur.page.shorts + p.shorts).distinctBy { it.url },
                        posts = (cur.page.posts + p.posts).distinctBy { it.id },
                        next = p.next
                    ),
                    loading = false
                )
            }.getOrElse { cur.copy(loading = false) }
        }
    }

    fun sort(tab: String, chip: Int) {
        val cur = tabs[tab] ?: return
        val token = cur.page.chips.getOrNull(chip)?.second ?: return
        if (chip == cur.chip) return
        tabs[tab] = cur.copy(loading = true, chip = chip, page = cur.page.copy(items = emptyList()))
        scope.launch {
            tabs[tab] = runCatching {
                val p = ChannelBrowse.more(token)
                TabUi(page = p.copy(chips = cur.page.chips), chip = chip)
            }.getOrElse { cur.copy(loading = false) }
        }
    }

    LaunchedEffect(selected, channelId) { if (selected != "Home") open(selected) }
    val ui = tabs[selected] ?: TabUi(loading = true)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::popPage) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Spacer(Modifier.weight(1f))
                basic?.let { UploadBell(it.ref) }
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, basic?.ref?.url ?: page.url) }
                    context.startActivity(Intent.createChooser(send, "Share"))
                }) { Icon(Icons.Rounded.Share, "Share channel") }
            }
        }
        if (h == null) {
            if (error != null) item {
                ErrorBox(error!!, onRetry = { error = null; scope.launch { loadHeader() } })
            } else item { LoadingView() }
            return@LazyColumn
        }
        item { ChannelHeaderView(h, basic, page.name) }
        stickyHeader {
            val index = shownTabs.indexOfFirst { it.title == selected }.coerceAtLeast(0)
            ScrollableTabRow(
                selectedTabIndex = index,
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onSurface,
                indicator = { positions ->
                    if (index < positions.size) TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(positions[index]), height = 2.dp, color = MaterialTheme.colorScheme.onSurface
                    )
                },
                divider = { Box(Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.outlineVariant)) }
            ) {
                shownTabs.forEach { t ->
                    Tab(
                        selected = t.title == selected,
                        onClick = { selected = t.title },
                        text = {
                            Text(
                                t.title,
                                fontWeight = if (t.title == selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (t.title == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }
        }
        // Sort chips (Latest / Popular / Oldest) when the tab has them.
        if (ui.page.chips.isNotEmpty()) item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ui.page.chips.size) { i -> TopicChip(ui.page.chips[i].first, ui.chip == i) { sort(selected, i) } }
            }
        }
        val p = ui.page
        val empty = p.items.isEmpty() && p.shorts.isEmpty() && p.posts.isEmpty()
        when {
            ui.loading && empty -> items(3) { VideoCardSkeleton() }
            ui.error != null && empty -> item { ErrorBox(ui.error, onRetry = { tabs.remove(selected); open(selected) }) }
            empty -> item { EmptyLine("Nothing here yet.") }
            selected == "Shorts" -> {
                val rows = p.shorts.chunked(2)
                items(rows.size) { r ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rows[r].forEach { v ->
                            Box(Modifier.weight(1f)) {
                                ShortCard(v) { vm.openShorts({ tabs["Shorts"]?.page?.shorts.orEmpty() }, v) { more("Shorts") } }
                            }
                        }
                        if (rows[r].size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            selected == "Posts" -> items(p.posts, key = { it.id }) { post -> PostCard(post, h, basic) }
            else -> {
                // Home: big cards; Shorts on Home in a sideways row. Other tabs: compact rows.
                if (selected == "Home" && p.shorts.isNotEmpty() && vm.shortsAllowed) item {
                    Text("Shorts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(p.shorts, key = { it.url }) { v ->
                            Box(Modifier.width(150.dp)) { ShortCard(v) { vm.openShorts({ p.shorts }, v) {} } }
                        }
                    }
                }
                items(p.items, key = { it.key }) { item ->
                    when (item) {
                        is LibVideo -> if (selected == "Home") VideoCard(item.video, onClick = { vm.play(item.video) })
                        else VideoRow(item.video) { vm.play(item.video) }
                        is LibPlaylist -> PlaylistRow(item) { vm.openPlaylist("https://www.youtube.com/playlist?list=${item.id}", item.title) }
                        else -> Unit
                    }
                }
            }
        }
        if (ui.loading && !empty) item { LoadingView() }
    }
    if (h != null && !ui.loading && ui.page.next != null) {
        LoadMoreEffect(listState, ui.page.items.size + ui.page.shorts.size + ui.page.posts.size) { more(selected) }
    }
}

@Composable
private fun ChannelHeaderView(h: ChannelHeaderInfo, basic: ChannelPage?, fallbackName: String) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val name = h.name.ifBlank { basic?.ref?.name ?: fallbackName }
    val url = basic?.ref?.url
    Column(Modifier.fillMaxWidth()) {
        (h.banner ?: basic?.banner)?.let { b ->
            Thumb(b, Modifier.fillMaxWidth().padding(horizontal = 16.dp).aspectRatio(6.2f).clip(Corners.large))
        }
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(name, 84, h.avatar ?: basic?.ref?.avatar)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (basic?.verified == true) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.CheckCircle, "Verified", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                h.handle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
                val stats = listOfNotNull(h.subscribers, h.videos).joinToString(" • ")
                if (stats.isNotEmpty()) Text(stats, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (h.description.isNotBlank()) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    h.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!expanded) Text("…more", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Bottom))
            }
        }
        h.linkText?.let { link ->
            Row(
                Modifier.fillMaxWidth().clickable(enabled = h.linkUrl != null && !com.sourabh.jarvistube.Graph.study.lockActive) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(h.linkUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Link, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(link, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(12.dp))
        // Subscribe, full width as on YouTube (shown when signed in).
        SubscribeButton(url, name, Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(44.dp))
        // PlayBook's own: put this channel in My Channels (Home feed).
        basic?.let { b ->
            val inStudy = StudyChannels.contains(b.ref.url)
            val label = if (inStudy) "In My Channels" else "Add to My Channels"
            if (inStudy) FilledTonalButton(onClick = { StudyChannels.toggle(b.ref) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(label)
            } else OutlinedButton(onClick = { StudyChannels.toggle(b.ref) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(label)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** A compact video row (Videos / Live / Podcasts tabs), as on YouTube. */
@Composable
private fun VideoRow(v: VideoItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.width(168.dp).aspectRatio(16f / 9f).clip(Corners.medium).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Thumb(v.thumb, Modifier.matchParentSize())
            val badge = if (v.isLive) "LIVE" else formatDuration(v.durationSec)
            if (badge.isNotEmpty()) Text(
                badge, color = Color.White, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(Corners.tiny)
                    .background(if (v.isLive) com.sourabh.jarvistube.ui.theme.Brand.live else OnVideo.scrimStrong).padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(v.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            // On a channel's own tabs YouTube puts "131K views · 14 hours ago" where a channel name would be.
            val meta = listOfNotNull(v.channel.ifBlank { null }, v.uploaded).joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun PlaylistRow(p: LibPlaylist, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumb(p.thumb, Modifier.width(168.dp).aspectRatio(16f / 9f).clip(Corners.medium))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (p.sub.isNotBlank()) Text(p.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

/** A community post: the channel, when, the text, its images, likes. Read-only. */
@Composable
private fun PostCard(post: ChannelPost, h: ChannelHeaderInfo, basic: ChannelPage?) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(h.name, 36, h.avatar ?: basic?.ref?.avatar)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(h.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                post.time?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = muted) }
            }
        }
        if (post.text.isNotBlank()) Text(post.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
        if (post.images.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(post.images) { img ->
                    Thumb(img, Modifier.width(if (post.images.size == 1) 340.dp else 260.dp).aspectRatio(1f).clip(Corners.large))
                }
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ThumbUp, null, Modifier.size(16.dp), tint = muted)
            post.likes?.let { Spacer(Modifier.width(6.dp)); Text(it, style = MaterialTheme.typography.labelMedium, color = muted) }
        }
        Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(0.5.dp).background(MaterialTheme.colorScheme.outlineVariant))
    }
}
