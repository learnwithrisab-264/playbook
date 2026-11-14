package com.sourabh.jarvistube.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow

// ---- Finding links in descriptions and comments (plain Kotlin, unit-tested) ----

private val timeRegex = Regex("""(?<![\w:/.])(?:(\d{1,2}):)?(\d{1,2}):(\d{2})(?![\w:])""")
private val urlRegex = Regex("""(?:https?://|www\.)[^\s<>"]+[^\s<>".,;:!?)\]]""")
private val idRegex = Regex("""^[A-Za-z0-9_-]{11}$""")

/** "12:34" → 754, "1:02:03" → 3723; null when it isn't a clock time. */
fun parseTimestamp(text: String): Long? {
    val m = timeRegex.matchEntire(text.trim()) ?: return null
    val h = m.groupValues[1].toLongOrNull() ?: 0
    val min = m.groupValues[2].toLong()
    val s = m.groupValues[3].toLong()
    if (s > 59 || (m.groupValues[1].isNotEmpty() && min > 59)) return null
    return h * 3600 + min * 60 + s
}

/** The 11-letter video id of a YouTube watch, youtu.be, Shorts or live link; null for anything else. */
fun youtubeVideoId(url: String): String? {
    val u = runCatching { java.net.URI(if (url.startsWith("www.")) "https://$url" else url) }.getOrNull() ?: return null
    val host = u.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
    val path = u.path.orEmpty()
    val id = when (host) {
        "youtu.be" -> path.trim('/').substringBefore('/')
        "youtube.com", "music.youtube.com" -> when {
            path == "/watch" -> queryParam(u.rawQuery, "v")
            path.startsWith("/shorts/") || path.startsWith("/live/") || path.startsWith("/embed/") ->
                path.split('/').getOrNull(2)
            else -> null
        }
        else -> null
    }
    return id?.takeIf { idRegex.matches(it) }
}

/** The start time of a YouTube link ("t=754", "t=12m34s", "start=754") in seconds. */
fun linkStartSeconds(url: String): Long? {
    val query = runCatching { java.net.URI(url).rawQuery }.getOrNull()
    val t = queryParam(query, "t") ?: queryParam(query, "start") ?: return null
    t.toLongOrNull()?.let { return it }
    val m = Regex("""^(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?$""").matchEntire(t) ?: return null
    val (h, min, s) = m.destructured
    return (h.toLongOrNull() ?: 0) * 3600 + (min.toLongOrNull() ?: 0) * 60 + (s.toLongOrNull() ?: 0)
}

private fun queryParam(query: String?, name: String): String? =
    query?.split('&')?.firstOrNull { it.substringBefore('=') == name }?.substringAfter('=', "")?.takeIf { it.isNotEmpty() }

/** A link found in plain text: a web address, or a time to jump to. */
data class FoundLink(val start: Int, val end: Int, val url: String? = null, val seconds: Long? = null)

/** Web addresses and clock times (12:34, 1:02:03) in plain text, in order, never overlapping. */
fun findLinks(text: String): List<FoundLink> {
    val urls = urlRegex.findAll(text).map {
        FoundLink(it.range.first, it.range.last + 1, url = if (it.value.startsWith("www.")) "https://${it.value}" else it.value)
    }.toList()
    val times = timeRegex.findAll(text)
        .filter { m -> urls.none { m.range.first < it.end && m.range.last >= it.start } }
        .mapNotNull { m -> parseTimestamp(m.value)?.let { FoundLink(m.range.first, m.range.last + 1, seconds = it) } }
    return (urls + times).sortedBy { it.start }
}

// ---- Showing them ----

/** YouTube's link blue, lighter on dark backgrounds. */
@Composable
fun linkColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF3EA6FF) else Color(0xFF065FD4)

/**
 * Text from YouTube (HTML or plain) with blue tappable links: [onUrl] gets web and YouTube links,
 * [onSeek] gets clock times. Taps on ordinary text go to whatever is around it (expand/collapse).
 */
@Composable
fun LinkedText(
    text: String,
    html: Boolean,
    onUrl: (String) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    maxLines: Int = Int.MAX_VALUE,
    /** Exact link positions from the source (YouTube comments); bare links and times are still found. */
    links: List<com.sourabh.jarvistube.data.TextLink> = emptyList(),
    /** Whether the text was cut off at [maxLines]. */
    onOverflow: (Boolean) -> Unit = {}
) {
    val styles = TextLinkStyles(SpanStyle(color = linkColor()))
    val annotated = remember(text, html, links, styles, onUrl, onSeek) { linkify(text, html, links, styles, onUrl, onSeek) }
    Text(
        annotated, modifier, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        onTextLayout = { onOverflow(it.hasVisualOverflow) }
    )
}

/** Times look like small blue chips, as on YouTube ("0:41"). */
private fun timeStyles(styles: TextLinkStyles): TextLinkStyles {
    val c = styles.style?.color ?: Color(0xFF3EA6FF)
    return TextLinkStyles(SpanStyle(color = c, background = c.copy(alpha = 0.16f)))
}

private fun linkify(
    text: String,
    html: Boolean,
    known: List<com.sourabh.jarvistube.data.TextLink>,
    styles: TextLinkStyles,
    onUrl: (String) -> Unit,
    onSeek: (Long) -> Unit
): AnnotatedString {
    if (known.isNotEmpty() && !html) {
        // Positions are exact for the untouched text, so it isn't trimmed here.
        return buildAnnotatedString {
            append(text)
            known.forEach { k -> addLink(clickable(k.url, k.seconds, styles, onUrl, onSeek), k.start, k.end) }
            findLinks(text).filter { f -> known.none { f.start < it.end && f.end > it.start } }.forEach { f ->
                addLink(clickable(f.url, f.seconds, styles, onUrl, onSeek), f.start, f.end)
            }
        }
    }
    // HTML anchors (YouTube writes its own timestamps and links this way) keep their real address.
    val base = if (html) AnnotatedString.fromHtml(text, linkStyles = styles) { link ->
        (link as? LinkAnnotation.Url)?.url?.let(onUrl)
    } else AnnotatedString(text)
    val plain = base.text.trim()
    val offset = base.text.indexOf(plain).coerceAtLeast(0)
    val trimmed = base.subSequence(offset, offset + plain.length)
    val taken = trimmed.getLinkAnnotations(0, trimmed.length)
    return buildAnnotatedString {
        append(trimmed)
        // Bare addresses and times that aren't already inside an anchor.
        findLinks(plain).filter { f -> taken.none { f.start < it.end && f.end > it.start } }.forEach { f ->
            addLink(clickable(f.url, f.seconds, styles, onUrl, onSeek), f.start, f.end)
        }
    }
}

private fun clickable(url: String?, seconds: Long?, styles: TextLinkStyles, onUrl: (String) -> Unit, onSeek: (Long) -> Unit): LinkAnnotation.Clickable =
    if (url != null) LinkAnnotation.Clickable(url, styles) { onUrl(url) }
    else LinkAnnotation.Clickable("t$seconds", timeStyles(styles)) { onSeek(seconds ?: 0) }
