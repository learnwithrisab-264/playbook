package com.sourabh.jarvistube.ui.common

import com.sourabh.jarvistube.R
import androidx.compose.ui.res.stringResource
import com.sourabh.jarvistube.ui.theme.Brand
import com.sourabh.jarvistube.ui.theme.Corners
import kotlinx.coroutines.flow.collectLatest
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.runtime.setValue
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
import com.sourabh.jarvistube.data.VideoItem

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
        Text(stringResource(R.string.couldn_t_load), style = MaterialTheme.typography.titleMedium)
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
        Button(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
        if (extraLabel != null && onExtra != null) {
            androidx.compose.material3.TextButton(onClick = onExtra) { Text(extraLabel) }
        }
    }
}

/** The one loading look for lists and pages: a spinner centred in the free space. */
@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = com.sourabh.jarvistube.ui.theme.Space.xl), contentAlignment = Alignment.Center) {
        com.sourabh.jarvistube.ui.theme.ChakraSpinner(36.dp)
    }
}

/** A small "nothing here" line for short lists inside a page. Big empty screens use [EmptyState]. */
@Composable
fun EmptyLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(com.sourabh.jarvistube.ui.theme.Space.xxl)
    )
}

/** The one section heading style, used in Settings, Study and channel pages. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 8.dp)
    )
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
        Spacer(Modifier.height(24.dp))
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

/** Remembers which videos of [feed] are on screen, while their tab is showing. */
@Composable
fun TrackSeen(state: LazyListState, feed: com.sourabh.jarvistube.data.Feed) {
    val active = LocalTabActive.current
    LaunchedEffect(state, feed, active) {
        if (active) markSeen(feed) { state.layoutInfo.visibleItemsInfo.map { it.key } }
    }
}

/** Same as [TrackSeen] for the Shorts grid. */
@Composable
fun TrackSeen(state: androidx.compose.foundation.lazy.grid.LazyGridState, feed: com.sourabh.jarvistube.data.Feed) {
    val active = LocalTabActive.current
    LaunchedEffect(state, feed, active) {
        if (active) markSeen(feed) { state.layoutInfo.visibleItemsInfo.map { it.key } }
    }
}

/** A video counts as seen once it has stayed on screen for a second; just flying past doesn't. */
private suspend fun markSeen(feed: com.sourabh.jarvistube.data.Feed, keys: () -> List<Any>) {
    androidx.compose.runtime.snapshotFlow(keys).collectLatest { visible ->
        kotlinx.coroutines.delay(1000)
        com.sourabh.jarvistube.data.SeenVideos.mark(feed, visible.filterIsInstance<String>().filter { it.startsWith("http") })
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
        shape = Corners.pill,
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = cs.surfaceVariant,
            labelColor = cs.onSurface,
            iconColor = cs.onSurface,
            selectedContainerColor = cs.primary,
            selectedLabelColor = cs.onPrimary,
            selectedLeadingIconColor = cs.onPrimary
        )
    )
}

/** App wordmark for the top bar: the orange badge + "PlayBook" in the brand orange. */
@Composable
fun LogoMark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(com.sourabh.jarvistube.R.drawable.osmium_badge), null,
            Modifier.size(30.dp).clip(Corners.small)
        )
        Spacer(Modifier.size(7.dp))
        Text(
            "PlayBook", fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.6).sp,
            color = Brand.orange
        )
    }
}

/** Header for pages opened from another tab (Downloads, Settings): back arrow + title. */
@Composable
fun SubPageHeader(title: String, onBack: () -> Unit, large: Boolean = false) {
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
            }
            if (!large) Text(title, style = MaterialTheme.typography.titleLarge)
        }
        // A big page title under the back arrow, for top-level pages like Settings.
        if (large) Text(
            title, style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(start = 20.dp, top = 2.dp, bottom = 4.dp)
        )
    }
}

/**
 * False while a bottom-bar tab is kept alive in the background (not showing). Screens use it so
 * their Back handling only applies while they are on screen.
 */
val LocalTabActive = androidx.compose.runtime.compositionLocalOf { true }

/** Opens a video's channel page; provided once at the app root so every card can use it. */
val LocalOpenChannel = androidx.compose.runtime.staticCompositionLocalOf<(VideoItem) -> Unit> { {} }
