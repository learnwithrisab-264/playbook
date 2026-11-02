package com.sourabh.jarvistube.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.data.VideoItem
import java.util.Locale

fun formatDuration(sec: Long): String {
    if (sec <= 0) return ""
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun formatClock(ms: Long): String = formatDuration(ms / 1000).ifEmpty { "0:00" }

fun compact(n: Long): String {
    val (v, suffix) = when {
        n >= 1_000_000_000 -> n / 1e9 to "B"
        n >= 1_000_000 -> n / 1e6 to "M"
        n >= 1_000 -> n / 1e3 to "K"
        else -> return n.toString()
    }
    val t = if (v >= 100) String.format(Locale.US, "%.0f", v)
    else String.format(Locale.US, "%.1f", v).removeSuffix(".0")
    return t + suffix
}

/** Gentle press-in scale — the little detail that makes taps feel premium. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressScale(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onPress: (() -> Unit)? = null
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    if (onPress != null) LaunchedEffect(pressed) { if (pressed) onPress() }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "press"
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = source,
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick
        )
}

@Composable
fun shimmerBrush(): Brush {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "x"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val hi = MaterialTheme.colorScheme.surfaceContainerHighest
    return Brush.linearGradient(
        colors = listOf(base, hi, base),
        start = Offset(x * 1400f - 700f, 0f),
        end = Offset(x * 1400f, 350f)
    )
}

@Composable
fun Thumb(url: String?, modifier: Modifier = Modifier) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

@Composable
fun Avatar(name: String, size: Int = 36, url: String? = null) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        // The initial shows until (or unless) the real channel picture loads over it.
        Text(
            name.trim().take(1).uppercase().ifEmpty { "?" },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size * 0.42f).sp
        )
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@Composable
fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Screen-specific ⋮ entries, e.g. "Remove from watch history". */
    extraActions: List<Pair<String, () -> Unit>> = emptyList()
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun toast(msg: String) = android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()

    // Edge-to-edge thumbnail, then avatar / title / meta / ⋮ — the layout people already know.
    Column(
        modifier
            .fillMaxWidth()
            .pressScale(onClick, onPress = { com.sourabh.jarvistube.data.Repo.prefetch(video.url) }, onLongClick = {
                Graph.player.enqueue(video)
                toast("Added to queue")
            })
            .padding(bottom = 18.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            Thumb(video.thumb, Modifier.matchParentSize())
            val badge = if (video.isLive) "LIVE" else formatDuration(video.durationSec)
            if (badge.isNotEmpty()) {
                Text(
                    badge,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (video.isLive) Color(0xFFD63B3B) else Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        Row(
            Modifier.padding(start = 12.dp, end = 0.dp, top = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            val openChannel = LocalOpenChannel.current
            Box(
                if (video.channelUrl != null) Modifier.clip(CircleShape).clickable { openChannel(video) } else Modifier
            ) { Avatar(video.channel, 36, video.avatar) }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                val meta = listOfNotNull(
                    video.channel.takeIf { it.isNotBlank() },
                    video.views.takeIf { it >= 0 }?.let { "${compact(it)} views" },
                    video.uploaded
                ).joinToString(" · ")
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurface)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Play next") },
                        leadingIcon = { Icon(Icons.Rounded.SkipNext, null) },
                        onClick = { menu = false; Graph.player.playNextUp(video); toast("Plays next") }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to queue") },
                        leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) },
                        onClick = { menu = false; Graph.player.enqueue(video); toast("Added to queue") }
                    )
                    if (com.sourabh.jarvistube.data.Session.isSignedIn) {
                        DropdownMenuItem(
                            text = { Text("Save to playlist") },
                            leadingIcon = { Icon(Icons.Rounded.BookmarkBorder, null) },
                            onClick = { menu = false; SaveToPlaylist.open(video) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Download") },
                        leadingIcon = { Icon(Icons.Rounded.Download, null) },
                        onClick = { menu = false; DownloadSheet.open(video) }
                    )
                    if (video.channelUrl != null) {
                        DropdownMenuItem(
                            text = { Text("Go to channel") },
                            leadingIcon = { Icon(Icons.Rounded.AccountCircle, null) },
                            onClick = { menu = false; openChannel(video) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Rounded.Share, null) },
                        onClick = {
                            menu = false
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, video.url)
                            }
                            context.startActivity(android.content.Intent.createChooser(send, "Share"))
                        }
                    )
                    extraActions.forEach { (label, action) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = { menu = false; action() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VideoCardSkeleton() {
    val brush = shimmerBrush()
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(brush))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = 12.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(brush))
            Spacer(Modifier.size(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth(0.9f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(brush))
                Box(Modifier.fillMaxWidth(0.55f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(brush))
            }
        }
    }
}

@Composable
fun ErrorBox(
    message: String,
    onRetry: () -> Unit,
    extraLabel: String? = null,
    onExtra: (() -> Unit)? = null
) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.ErrorOutline, null,
            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text("Couldn't load", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Try again") }
        if (extraLabel != null && onExtra != null) {
            androidx.compose.material3.TextButton(onClick = onExtra) { Text(extraLabel) }
        }
    }
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Fires [onLoad] when the user scrolls within a few items of the end. */
@Composable
fun LoadMoreEffect(state: LazyListState, itemCount: Int, onLoad: () -> Unit) {
    val nearEnd by remember(state) {
        derivedStateOf {
            val total = state.layoutInfo.totalItemsCount
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 4
        }
    }
    LaunchedEffect(nearEnd, itemCount) { if (nearEnd) onLoad() }
}

/** "2022-06-21T07:38:31-07:00" -> "21 Jun 2022"; relative strings ("3 years ago") pass through. */
fun prettyDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return try {
        java.time.OffsetDateTime.parse(raw)
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    } catch (e: Exception) {
        raw
    }
}

/** YouTube-style pill: grey when idle, inverted (light on dark / dark on light) when on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PillChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        leadingIcon = leadingIcon,
        shape = RoundedCornerShape(18.dp),
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = cs.surfaceVariant,
            labelColor = cs.onSurface,
            iconColor = cs.onSurface,
            selectedContainerColor = cs.onSurface,
            selectedLabelColor = cs.surface,
            selectedLeadingIconColor = cs.surface
        )
    )
}

/** App wordmark for the top bar: the orange badge + "PlayBook" in the brand orange. */
@Composable
fun LogoMark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(com.sourabh.jarvistube.R.drawable.osmium_badge), null,
            Modifier.size(30.dp).clip(RoundedCornerShape(7.dp))
        )
        Spacer(Modifier.size(7.dp))
        Text(
            "PlayBook", fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.6).sp,
            color = Color(0xFFFF6D00)
        )
    }
}

/** Header for pages opened from another tab (Downloads, Settings): back arrow + title. */
@Composable
fun SubPageHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

/**
 * False while a bottom-bar tab is kept alive in the background (not showing). Screens use it so
 * their Back handling only applies while they are on screen.
 */
val LocalTabActive = androidx.compose.runtime.compositionLocalOf { true }

/** Opens a video's channel page; provided once at the app root so every card can use it. */
val LocalOpenChannel = androidx.compose.runtime.staticCompositionLocalOf<(VideoItem) -> Unit> { {} }
