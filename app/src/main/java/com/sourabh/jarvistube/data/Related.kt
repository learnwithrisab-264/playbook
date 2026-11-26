package com.sourabh.jarvistube.data

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Related videos on the same topic as the one playing.
 *
 * YouTube's signed-out "related" list is "what this audience also watches": for a video about AI
 * game mash-ups it mixes in car crash tests and gift guides. So the list is rebuilt: videos found
 * by searching the video's own topic (its title words and tags), mixed with the related videos
 * that share that topic. The off-topic ones only fill in at the end when there are too few.
 */
object Related {
    private val ready = mutableStateMapOf<String, List<VideoItem>>()
    private val loading = HashSet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The list for [video]: the topic-matched one once ready, until then YouTube's own ranked by topic. */
    fun of(info: StreamInfo?, video: VideoItem): List<VideoItem> {
        ready[video.url]?.let { return it }
        if (info == null) return emptyList()
        val topic = Topic.of(info, video)
        return youtubeRelated(info, video).sortedByDescending { topic.score(it) }
    }

    /** Starts building the list as soon as the video's details arrive, so it's ready when you look. */
    fun prefetch(info: StreamInfo, video: VideoItem) {
        if (ready.containsKey(video.url) || !loading.add(video.url)) return
        scope.launch {
            try {
                ready[video.url] = build(info, video)
                while (ready.size > 30) ready.remove(ready.keys.first())
            } catch (_: Exception) {
                // Keeps YouTube's own list, ranked by topic.
            } finally {
                loading.remove(video.url)
            }
        }
    }

    private suspend fun build(info: StreamInfo, video: VideoItem): List<VideoItem> {
        val topic = Topic.of(info, video)
        val scored = youtubeRelated(info, video).map { it to topic.score(it) }
        val onTopic = scored.filter { it.second >= 2 }.sortedByDescending { it.second }.map { it.first }
        val searched = if (topic.query.isBlank()) emptyList() else runCatching {
            SearchSession(topic.query).loadFirst()
        }.getOrDefault(emptyList()).filter { !it.membersOnly && !it.isShort }

        // Search results and on-topic related videos, taking turns.
        val out = LinkedHashMap<String, VideoItem>()
        val a = searched.iterator()
        val b = onTopic.iterator()
        while (a.hasNext() || b.hasNext()) {
            if (a.hasNext()) a.next().let { out.putIfAbsent(it.url, it) }
            if (b.hasNext()) b.next().let { out.putIfAbsent(it.url, it) }
        }
        out.remove(video.url)
        // Re-uploads of this same video by other channels (same title) are just noise.
        val sameTitle = squash(info.name ?: video.title)
        out.values.removeAll { squash(it.title) == sameTitle }
        // Too few? Fill up with the closest of the rest.
        if (out.size < 12) scored.sortedByDescending { it.second }.forEach { (v, _) ->
            if (out.size < 20 && v.url != video.url) out.putIfAbsent(v.url, v)
        }
        return out.values.toList()
    }

    private fun youtubeRelated(info: StreamInfo, video: VideoItem): List<VideoItem> =
        info.relatedItems.filterIsInstance<StreamInfoItem>()
            .map { it.toVideoItem() }
            .filter { !it.isShort && !it.membersOnly && it.url != video.url }
            .filterNot(ContentFilter::hides)

    /** What the video is about: its title words (strongest), tags and channel. */
    private class Topic(val weights: Map<String, Int>, val channel: String, val query: String) {
        fun score(v: VideoItem): Int {
            var s = words(v.title).distinct().sumOf { weights[it] ?: 0 }
            if (channel.isNotBlank() && v.channel.equals(channel, ignoreCase = true)) s += 2
            return s
        }

        companion object {
            fun of(info: StreamInfo, video: VideoItem): Topic {
                val title = info.name?.takeIf { it.isNotBlank() } ?: video.title
                val titleWords = words(title)
                val tagWords = info.tags.orEmpty().take(15).flatMap { words(it) }
                val w = HashMap<String, Int>()
                tagWords.forEach { w[it] = maxOf(w[it] ?: 0, 1) }
                titleWords.forEach { w[it] = 2 }
                // Search with the title's meaningful words; tags help when the title is vague.
                val q = (titleWords.distinct().take(6) + tagWords.distinct().filter { it !in titleWords }.take(maxOf(0, 3 - titleWords.size)))
                    .joinToString(" ")
                return Topic(w, info.uploaderName ?: video.channel, q)
            }
        }
    }

    private val WORD = Regex("[\\p{L}\\p{N}]+")

    private fun squash(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** Lowercase words worth matching: no filler words, plurals folded ("mashups" = "mashup"). */
    private fun words(s: String): List<String> =
        WORD.findAll(s.lowercase()).map { it.value }
            .filter { it.length >= 3 && it !in STOP && !(it.all(Char::isDigit) && it.length < 4) }
            .map { if (it.length > 4 && it.endsWith('s') && !it.endsWith("ss")) it.dropLast(1) else it }
            .toList()

    private val STOP = setOf(
        // English
        "the", "and", "for", "with", "this", "that", "these", "those", "you", "your", "are", "was", "were", "why",
        "how", "what", "when", "who", "whom", "where", "which", "from", "have", "has", "had", "not", "but", "all",
        "can", "will", "just", "into", "about", "they", "them", "their", "his", "her", "its", "our", "out", "new",
        "best", "top", "video", "videos", "official", "full", "episode", "part", "live", "never", "ever", "most",
        "more", "very", "only", "one", "two", "three", "first", "last", "day", "days", "year", "years", "here",
        "there", "than", "then", "now", "get", "got", "make", "making", "made", "did", "does", "doing", "done",
        "really", "actually", "every", "everything", "something", "nothing", "thing", "things", "way", "watch",
        "should", "would", "could", "been", "being", "after", "before", "over", "under", "again", "still", "even",
        "much", "many", "some", "any", "own", "too", "off", "why", "explained", "sorry", "finally", "ft", "feat",
        "shorts", "short", "trailer", "hindi", "english", "subscribe", "channel", "vs",
        // Hindi in Latin letters
        "hai", "hain", "kya", "kaise", "aur", "mein", "main", "nahi", "nahin", "bhi", "yeh", "woh", "kar", "karo",
        "karna", "sab", "tak", "liye", "wala", "wali", "hota", "hoti", "kyu", "kyun", "par", "sath", "saath",
        // Hindi in Devanagari
        "है", "हैं", "क्या", "कैसे", "और", "में", "नहीं", "के", "की", "का", "से", "को", "भी", "यह", "वह", "लिए", "पर"
    )
}
