package com.sourabh.jarvistube.data

/**
 * Keeps JarvisTube a learning app: music videos, film songs, trailers, entertainment, gaming and
 * suggestive content never show up and never play.
 *
 * Two layers:
 *  1. Lists (search, feeds, related, library) are filtered by title/channel patterns, since list
 *     items carry no category.
 *  2. When a video is opened, YouTube's own category ("Music", "Entertainment", ...) is checked
 *     and playback is refused for non-learning categories.
 * Channels in "My Channels" are trusted and exempt from both.
 */
object ContentFilter {

    /** YouTube categories that are never study material. */
    private val blockedCategories = setOf(
        "music", "entertainment", "film & animation", "gaming", "comedy", "shows", "trailers", "movies"
    )

    /** Strict suggestive / adult / explicit terms. Anything matching these is ALWAYS hidden, even if title contains "study". */
    private val suggestivePatterns = listOf(
        "\\bhot\\b", "\\bsexy\\b", "\\bsex\\b", "\\bnsfw\\b", "\\bnude\\b", "\\bnudity\\b", "\\berotic\\b",
        "\\buncensored\\b", "\\b18\\+\\b", "\\badult\\b", "\\bcleavage\\b", "\\bboob(s)?\\b", "\\bbathtime\\b",
        "\\bsensual\\b", "\\bstrip(per|ping)?\\b", "\\bseductive\\b", "\\bseduction\\b", "\\bdesire\\b",
        "\\bpassion\\b", "\\btemptation\\b", "\\bbabe\\b", "\\bmodel(s)?\\b", "\\bexposing\\b", "\\bhotness\\b",
        "\\bnavel\\b", "\\bwaist\\b", "\\bromance\\b", "\\bromantic\\b", "\\bintimate\\b", "\\bintimacies\\b",
        "\\bkiss(ing)?\\b", "\\bhug(ging)?\\b", "\\bdirty\\b", "\\bexplicit\\b", "\\bprovocative\\b",
        "\\bbikini\\b", "\\blingerie\\b", "\\bswimsuit\\b", "\\bwebseries\\b", "\\basmr\\b", "\\blust\\b",
        "\\bflirt(ing)?\\b", "\\bthirst\\b", "\\bseduce\\b", "\\bchick\\b", "\\blips?\\b", "\\bbody\\b"
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val titlePatterns = listOf(
        // songs & music videos
        "\\bsongs?\\b", "\\bmusic video\\b", "\\bofficial (music )?video\\b", "\\blyric(s|al)?\\b",
        "\\bvideo song\\b", "\\baudio song\\b", "\\bjukebox\\b", "\\bremix\\b", "\\bdj\\b", "\\bmashup\\b",
        "\\bfull (video|song)\\b", "\\bkaraoke\\b", "\\bcover song\\b", "\\bunplugged\\b", "\\blofi\\b", "\\blo-fi\\b",
        "\\bbhajan\\b", "\\bqawwali\\b", "\\bghazal\\b", "\\bft\\.", "\\bfeat\\.",
        // films, shows, trailers
        "\\btrailer\\b", "\\bteaser\\b", "\\bmovie (scene|clip)s?\\b", "\\bfull movie\\b", "\\bweb series\\b",
        "\\bepisode \\d+\\b", "\\bbollywood\\b", "\\btollywood\\b", "\\bitem number\\b", "\\bbigg boss\\b",
        // entertainment & distraction
        "\\bprank\\b", "\\broast\\b", "\\bmeme(s)?\\b", "\\bfunny\\b", "\\bcomedy\\b", "\\bgameplay\\b",
        "\\bgaming\\b", "\\bmukbang\\b", "\\breaction\\b", "\\breels?\\b", "\\bvlog\\b",
        // music artists / labels showing in titles
        "\\barijit\\b", "\\bshreya ghoshal\\b", "\\bneha kakkar\\b", "\\bbadshah\\b", "\\bhoney singh\\b",
        "\\bdiljit\\b", "\\bt-series\\b"
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val channelPatterns = listOf(
        "\\bmusic\\b", "\\brecords\\b", "\\bvevo\\b", "t-series", "\\bsaregama\\b", "\\btips official\\b",
        "\\bzee music\\b", "\\bsony music\\b", "\\bspeed records\\b", "\\bdesi music\\b", "\\bmovies?\\b",
        "\\bfilms?\\b", "\\bentertainment\\b", "\\bpictures\\b", "\\bstudios\\b", "\\bcinema\\b", "\\bgaming\\b",
        "\\bcomedy\\b", "\\bbhajans?\\b", "\\bsongs?\\b", "\\blofi\\b", "\\bbeats\\b"
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val moreBlocked = listOf(
        "\\bperformance\\b", "\\bindian idol\\b", "\\bidol\\b", "\\bdance\\b", "\\bgarba\\b", "\\bnavratri\\b",
        "\\bwedding\\b", "\\bserial\\b", "\\btv show\\b", "\\bsinging\\b", "\\bsinger\\b", "\\bconcert\\b",
        "\\blive show\\b", "\\bbrahmastra\\b", "\\bkesariya\\b", "\\bpatakha\\b"
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    /**
     * Allow-list: outside trusted channels a video is only shown when its title looks like
     * learning material. Blocking keywords one by one can't keep up with covers, dance videos
     * and TV clips; requiring a study signal can.
     */
    private val studySignals = Regex(
        "\\b(lectures?|class(es)?|chapter|explained|explanation|tutorial|course|lessons?|concepts?|" +
            "one ?shot|notes|exams?|jee|neet|upsc|ssc|gate|cbse|icse|boards?|ncert|physics|math(s|ematics)?|" +
            "chemistry|biology|science|history|geography|economics|civics|polity|english grammar|grammar|" +
            "programming|coding|python|java|javascript|kotlin|android|dsa|algorithms?|data structures?|" +
            "calculus|algebra|geometry|trigonometry|statistics|probability|derivation|theorem|formula|" +
            "how to|introduction|intro to|basics|beginners?|full course|crash course|revision|pyqs?|" +
            "questions?|solutions?|problems?|practice|mock test|syllabus|strategy|study|learn(ing)?|" +
            "documentary|explainer|ted ?ed|ted talk|engineering|medical|anatomy|economy|finance|accounting)\\b",
        RegexOption.IGNORE_CASE
    )

    private fun trusted(channel: String) =
        StudyChannels.containsName(channel) ||
            runCatching { com.sourabh.jarvistube.Graph.study.isAllowed(channel) }.getOrDefault(false)

    /** Returns true if text matches any suggestive or blocked pattern. */
    fun isSuggestiveOrBlocked(text: String): Boolean {
        return suggestivePatterns.any { it.containsMatchIn(text) } ||
            titlePatterns.any { it.containsMatchIn(text) } ||
            moreBlocked.any { it.containsMatchIn(text) }
    }

    /** Cleans search query by removing suggestive words (e.g. "hot study" -> "study"). */
    fun sanitizeQuery(query: String): String {
        var clean = query
        suggestivePatterns.forEach { regex ->
            clean = regex.replace(clean, "").trim()
        }
        return clean.ifBlank { query }
    }

    private fun blocked(video: VideoItem) =
        suggestivePatterns.any { it.containsMatchIn(video.title) } ||
            titlePatterns.any { it.containsMatchIn(video.title) } ||
            moreBlocked.any { it.containsMatchIn(video.title) } ||
            channelPatterns.any { it.containsMatchIn(video.channel) }

    /** Off only when the user turned the filter off and no study session/lock is running. */
    private fun enforcing() =
        runCatching { com.sourabh.jarvistube.Graph.study.entertainmentBlocked }.getOrDefault(true)

    /** True if the item should never be listed. */
    fun hides(video: VideoItem): Boolean {
        if (!enforcing() || trusted(video.channel)) return false
        // Suggestive/adult terms or blocked patterns MUST ALWAYS hide the video, even if title contains "study"
        if (suggestivePatterns.any { it.containsMatchIn(video.title) }) return true
        return blocked(video) || !studySignals.containsMatchIn(video.title)
    }

    fun filter(videos: List<VideoItem>): List<VideoItem> = videos.filterNot(::hides)

    /** Reason to refuse playback once YouTube's category is known, or null. */
    fun blockReason(category: String?, channel: String, title: String): String? {
        if (!enforcing() || trusted(channel)) return null
        if (suggestivePatterns.any { it.containsMatchIn(title) }) {
            return "Blocked: this video contains suggestive or non-educational content."
        }
        val c = category?.trim()?.lowercase().orEmpty()
        if (c in blockedCategories) return "Blocked: \"${category}\" videos aren't study content."
        if (titlePatterns.any { it.containsMatchIn(title) } || moreBlocked.any { it.containsMatchIn(title) } ||
            channelPatterns.any { it.containsMatchIn(channel) }
        ) {
            return "Blocked: this looks like entertainment, not study content."
        }
        return null
    }
}
