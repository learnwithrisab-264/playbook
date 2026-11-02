package com.sourabh.jarvistube.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.LibraryViewModel
import com.sourabh.jarvistube.Page
import com.sourabh.jarvistube.TubeViewModel
import com.sourabh.jarvistube.data.Entry
import com.sourabh.jarvistube.data.StudyChannels
import org.schabi.newpipe.extractor.Page as NpPage

/** Shows the top page of [TubeViewModel.pages] above the tabs (Back pops it). */
@Composable
fun PageHost(vm: TubeViewModel, lib: LibraryViewModel, bottomPadding: Dp, visible: Boolean) {
    val top = if (visible) vm.pages.lastOrNull() else null
    AnimatedContent(
        targetState = top,
        transitionSpec = { (slideInHorizontally(tween(200)) { it / 8 } + fadeIn(tween(160))) togetherWith fadeOut(tween(120)) },
        label = "pages"
    ) { page ->
        when (page) {
            null -> Unit
            is Page.Channel -> ChannelScreen(vm, page, bottomPadding)
            is Page.Playlist -> PlaylistScreen(vm, page, bottomPadding)
            Page.StudyChannels -> StudyChannelsScreen(vm, lib, bottomPadding)
        }
    }
}

@Composable
internal fun PageTopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
internal fun Spinner() {
    Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}

@Composable
internal fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(32.dp)
    )
}

internal data class TabState(
    val items: List<Entry> = emptyList(),
    val next: NpPage? = null,
    val loading: Boolean = false,
    val error: String? = null
)

// ------------------------------------------------------------------ channel

internal fun LazyListScope.entryItems(vm: TubeViewModel, state: TabState, onRetry: () -> Unit) {
    when {
        state.loading && state.items.isEmpty() -> items(3) { VideoCardSkeleton() }
        state.error != null && state.items.isEmpty() -> item { ErrorBox(state.error, onRetry = onRetry) }
        state.items.isEmpty() -> item { EmptyHint("Nothing here yet.") }
        else -> {
            items(state.items, key = { it.key }) { e ->
                when (e) {
                    is Entry.Video -> VideoCard(e.video, onClick = { vm.play(e.video) })
                    is Entry.Playlist -> Row(
                        Modifier.fillMaxWidth().clickable { vm.openPlaylist(e.url, e.title) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            Thumb(e.thumb, Modifier.width(150.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)))
                            if (e.count >= 0) {
                                Text(
                                    "${e.count} videos",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                                        .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.8f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Spacer(Modifier.size(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(e.owner, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    is Entry.Channel -> ChannelPickRow(vm, e.ref)
                }
            }
            if (state.loading) item { Spinner() }
        }
    }
}
