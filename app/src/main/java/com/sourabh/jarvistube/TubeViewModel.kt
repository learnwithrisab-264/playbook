package com.sourabh.jarvistube

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sourabh.jarvistube.data.ContentFilter
import com.sourabh.jarvistube.data.Feed
import com.sourabh.jarvistube.data.SearchSession
import com.sourabh.jarvistube.data.SeenVideos
import com.sourabh.jarvistube.data.VideoItem
import com.sourabh.jarvistube.data.looksShort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

data class FeedState(
    val items: List<VideoItem> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null
)

class TubeViewModel(app: Application) : AndroidViewModel(app) {

    val topics = listOf(
        "Mathematics", "Physics", "Chemistry", "Biology", "Programming",
        "Computer Science", "History", "Economics", "Languages", "Exam prep"
    )

    /** null = the "All" chip: a mix of every subject. */
    var topic by mutableStateOf<String?>(null); private set
    var explore by mutableStateOf(FeedState(loading = true)); private set

    var searchOpen by mutableStateOf(false)
    val suggestions = mutableStateListOf<String>()
    private var suggestJob: Job? = null
    private var exploreSession: SearchSession? = null
    private var exploreJob: Job? = null

    var query by mutableStateOf("")
    var submittedQuery by mutableStateOf<String?>(null); private set
    var results by mutableStateOf(FeedState()); private set
    private var searchSession: SearchSession? = null
    /** Search shows two separate sections; this one holds the Shorts. */
    var shortsResults by mutableStateOf(FeedState()); private set
    var searchShowsShorts by mutableStateOf(false)
    private var shortsSearch: com.sourabh.jarvistube.data.ShortsSearch? = null
    private var shortsSearchJob: Job? = null
    private var searchJob: Job? = null
    val recents = mutableStateListOf<String>()

    /** Pages stacked above the tabs (Back pops them): channel, playlist, study-channel picker. */
    val pages = mutableStateListOf<Page>()

    fun openChannel(url: String, name: String) {
        val page = Page.Channel(url, name)
        if (pages.lastOrNull() != page) pages.add(page)
        playerExpanded = false
    }

    fun openPlaylist(url: String, title: String) {
        val page = Page.Playlist(url, title)
        if (pages.lastOrNull() != page) pages.add(page)
        playerExpanded = false
    }

    fun openStudyChannels() {
        if (pages.lastOrNull() != Page.StudyChannels) pages.add(Page.StudyChannels)
    }

    fun popPage() {
        if (pages.isNotEmpty()) pages.removeAt(pages.lastIndex)
    }

    var playerExpanded by mutableStateOf(false)
    var showRelated by mutableStateOf(false)

    private val prefs = app.getSharedPreferences("tube", Context.MODE_PRIVATE)

    /** Each account has its own recent searches. */
    private var recentsKey = "recents"

    private fun loadRecents(key: String) {
        recentsKey = "recents:$key"
        recents.clear()
        recents.addAll(prefs.getString(recentsKey, "").orEmpty().split("\n").filter { it.isNotBlank() })
    }

    /** Runs when the account changes: nothing from the previous account stays on screen. */
    private val accountChanged: (String) -> Unit = { key ->
        loadRecents(key)
        // Shorts and their saved list belong to one account; each feed reloads for the new one.
        shortsJob?.cancel()
        shortsSessions = emptyList()
        shorts = FeedState()
        loadedAt.clear()
        studyFeed = FeedState()
        channelFilter = null
        clearSearch()
        loadStudyFeed()
    }

    init {
        // The old shared list goes to the account in use.
        prefs.getString("recents", null)?.let { shared ->
            prefs.edit().putString("recents:" + com.sourabh.jarvistube.data.Session.dataKey, shared).remove("recents").apply()
        }
        loadRecents(com.sourabh.jarvistube.data.Session.dataKey)
        com.sourabh.jarvistube.data.Session.onDataKeyChange(accountChanged)
    }

    override fun onCleared() {
        com.sourabh.jarvistube.data.Session.removeDataKeyListener(accountChanged)
    }

    fun play(video: VideoItem) {
        (com.sourabh.jarvistube.data.ContentFilter.blockReason(null, video.channel, video.title)
            ?: Graph.study.blockReason(video))?.let {
            Toast.makeText(getApplication(), it, Toast.LENGTH_LONG).show()
            return
        }
        Graph.player.play(video)
        playerExpanded = true
    }

    fun playLocal(item: com.sourabh.jarvistube.data.SavedItem) {
        val probe = VideoItem(item.url, item.title, item.channel, item.thumb, item.durationSec, -1, null, false)
        Graph.study.blockReason(probe)?.let {
            Toast.makeText(getApplication(), it, Toast.LENGTH_LONG).show()
            return
        }
        Graph.player.playLocal(item)
        playerExpanded = true
    }

    // ---- Home: new videos from "My Study Channels" (no recommendations) ----
    var studyFeed by mutableStateOf(FeedState()); private set
    /** Channel URL the Home chips are filtered to; null = All. */
    var channelFilter by mutableStateOf<String?>(null)
    private var studyJob: Job? = null

    private var studySessions: List<com.sourabh.jarvistube.data.ChannelVideos> = emptyList()

    fun loadStudyFeed() {
        studyJob?.cancel()
        stamp(Feed.MyFeed)
        // A different set of channels leads each time; the others wait for Load more.
        val sessions = com.sourabh.jarvistube.data.StudyChannels.list.toList().shuffled()
            .map { com.sourabh.jarvistube.data.ChannelVideos(it.url) }
        studySessions = sessions
        if (sessions.isEmpty()) {
            studyFeed = FeedState()
            return
        }
        studyJob = viewModelScope.launch {
            studyFeed = studyFeed.copy(loading = true, error = null) // keep what is shown while refreshing
            studyFeed = runCatchingFeed {
                val all = channelPages(sessions.take(15))
                if (all.isEmpty()) throw java.io.IOException("Couldn't load videos from your channels")
                FeedState(items = arrangeMyFeed(all))
            }
            studyFeed.items.take(3).forEach { com.sourabh.jarvistube.data.Repo.prefetch(it.url) }
        }
    }

    fun loadMoreStudy() {
        val cur = studyFeed
        val sessions = studySessions.filter { it.hasMore }.shuffled().take(8)
        if (cur.loading || cur.loadingMore || sessions.isEmpty() || cur.items.isEmpty()) return
        studyJob = viewModelScope.launch {
            studyFeed = cur.copy(loadingMore = true)
            studyFeed = runCatchingFeed(fallback = cur) {
                val more = SeenVideos.unseen(Feed.MyFeed, channelPages(sessions)).shuffled()
                cur.copy(items = (cur.items + more).distinctBy { it.url }, loadingMore = false)
            }
        }
    }

    /** One or more pages from each channel: older pages are read until a few unseen videos turn up. */
    private suspend fun channelPages(sessions: List<com.sourabh.jarvistube.data.ChannelVideos>): List<VideoItem> =
        coroutineScope {
            sessions.map { s ->
                async {
                    runCatching {
                        val got = ArrayList<VideoItem>()
                        repeat(3) { if (s.hasMore && SeenVideos.unseen(Feed.MyFeed, got).size < 6) got += s.load() }
                        got.toList()
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten().distinctBy { it.url }
        }

    /** Uploads from the last two days stay on top; the rest change order each time. */
    private fun arrangeMyFeed(all: List<VideoItem>): List<VideoItem> {
        val fresh = SeenVideos.unseen(Feed.MyFeed, all)
        val cutoff = System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L
        val (recent, older) = fresh.partition { (it.uploadedAt ?: 0L) >= cutoff }
        val arranged = when (kotlin.random.Random.nextInt(3)) {
            0 -> older.sortedByDescending { it.uploadedAt ?: 0L }
            1 -> older.shuffled()
            else -> interleave(older.groupBy { it.channel }.values.shuffled())
        }
        val shown = recent.sortedByDescending { it.uploadedAt } + arranged
        return if (fresh.size >= 12) shown else shown + (all - fresh.toSet())
    }

    // ---- When feeds refresh: on every app open, on opening a tab, and on pull-to-refresh ----
    private val loadedAt = HashMap<Feed, Long>()

    private fun stamp(feed: Feed) { loadedAt[feed] = android.os.SystemClock.elapsedRealtime() }

    /** The app came back after a while away: every feed is out of date. */
    fun expireFeeds() = loadedAt.clear()

    /** Refreshes [feed] unless it loaded moments ago. Returns true when it did. */
    fun freshen(feed: Feed): Boolean {
        val last = loadedAt[feed]
        if (last != null && android.os.SystemClock.elapsedRealtime() - last < 60_000L) return false
        when (feed) {
            Feed.MyFeed -> loadStudyFeed()
            Feed.Recommended -> {
                if (!recommendedAllowed) return false
                loadRecommended()
            }
            Feed.Shorts -> {
                val blocked = Graph.study.shortsBlockUntil > com.sourabh.jarvistube.data.TrustedClock.now()
                if (!shortsAllowed || blocked || shortsViewer != null) return false
                if (shorts.items.isEmpty()) ensureShorts() else fetchShorts(keepShown = true)
            }
        }
        return true
    }

    /** Subjects plus your own channels, each with a changing angle, so searches differ every time. */
    private fun rotatedTerms(count: Int): List<String> {
        val pool = topics + com.sourabh.jarvistube.data.StudyChannels.list.map { it.name }
        return pool.shuffled().take(count).map { "$it ${searchAngles.random()}" }
    }

    private val searchAngles = listOf(
        "explained", "for beginners", "crash course", "full lecture", "tips and tricks",
        "in simple words", "important concepts", "practice problems"
    )

    // ---- Recommended tab: the account's YouTube home feed, or Trending when signed out ----
    var recommended by mutableStateOf(FeedState()); private set
    /** Small line above the list naming where the videos come from. */
    var recommendedSource by mutableStateOf<String?>(null); private set
    private var recommendedJob: Job? = null
    /** Account + region the shown list was loaded for; a change reloads it. */
    private var recommendedKey: String? = null
    private var homeToken: String? = null
    private var trending: com.sourabh.jarvistube.data.TrendingSession? = null

    /** Never during a study session, multi-day lock, scheduled window or timed video lock. */
    val recommendedAllowed: Boolean
        get() = AppSettings.showRecommended && !Graph.study.active && !Graph.study.lockActive

    private fun recommendedKeyNow() = "${com.sourabh.jarvistube.data.Session.activeId}|${AppSettings.region}"

    /** Loads the tab the first time it opens, and again after an account or region change. */
    fun ensureRecommended() {
        if (recommendedKey != recommendedKeyNow()) loadRecommended()
    }

    fun loadRecommended() {
        if (!recommendedAllowed) return
        stamp(Feed.Recommended)
        recommendedJob?.cancel()
        val key = recommendedKeyNow()
        // Same source: keep what is shown while refreshing. New account/region: start clean.
        recommended = if (key == recommendedKey) recommended.copy(loading = true, error = null) else FeedState(loading = true)
        recommendedKey = key
        recommendedJob = viewModelScope.launch {
            recommended = runCatchingFeed { FeedState(items = firstRecommendedPage()) }
            recommended.items.take(3).forEach { com.sourabh.jarvistube.data.Repo.prefetch(it.url) }
        }
    }

    fun loadMoreRecommended() {
        if (!recommendedAllowed) return
        val cur = recommended
        val token = homeToken
        val session = trending
        if (cur.loading || cur.loadingMore || cur.items.isEmpty()) return
        recommendedJob = viewModelScope.launch {
            recommended = cur.copy(loadingMore = true)
            recommended = runCatchingFeed(fallback = cur) {
                val next = when {
                    session?.hasMore == true -> session.loadMore()
                    token != null -> homeFeed(token)
                    else -> emptyList()
                }
                // At the end of YouTube's list, or with nothing new on the page, new searches carry on.
                val more = SeenVideos.unseen(Feed.Recommended, next).ifEmpty { subjectMix() }
                cur.copy(items = (cur.items + more).distinctBy { it.url }, loadingMore = false)
            }
        }
    }

    /** Signed in: the account's home feed. Signed out, or if that fails: Trending for the region. */
    private suspend fun firstRecommendedPage(): List<VideoItem> {
        if (com.sourabh.jarvistube.data.Session.isSignedIn) {
            try {
                val items = homeFeed(null)
                trending = null
                recommendedSource = "From your YouTube account"
                // YouTube repeats itself: searches on your subjects fill the gap.
                if (items.size >= 12) return items
                return (items + runCatching { subjectMix() }.getOrDefault(emptyList())).distinctBy { it.url }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Expired login, watch history off (empty home feed), network hiccup: fall back to
                // Trending. Never sign out here.
                com.sourabh.jarvistube.data.Dbg.log("home feed failed, using Trending: ${e.message}")
            }
        }
        homeToken = null
        val session = com.sourabh.jarvistube.data.TrendingSession().also { trending = it }
        try {
            val items = SeenVideos.pick(Feed.Recommended, session.loadFirst(), 8)
            recommendedSource = (if (session.isTrending) "Trending in " else "Popular in ") + AppSettings.regionName
            return items
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // YouTube removed its Trending page (NewPipe v0.26.5 can't load it): show popular
            // study videos for the region instead of an error.
            com.sourabh.jarvistube.data.Dbg.log("trending failed, using study mix: ${e.message}")
        }
        trending = null
        val mixed = subjectMix()
        recommendedSource = "Popular study videos in ${AppSettings.regionName}"
        return mixed
    }

    /** One or two pages for five rotating searches, interleaved so no subject dominates. */
    private suspend fun subjectMix(): List<VideoItem> = coroutineScope {
        val pages = rotatedTerms(5).map { term ->
            async {
                runCatching {
                    val s = SearchSession(term)
                    val first = s.loadFirst()
                    val all = if (kotlin.random.Random.nextBoolean() && s.hasMore) first + s.loadMore() else first
                    all.shuffled()
                }.getOrDefault(emptyList())
            }
        }.awaitAll()
        val mixed = interleave(pages)
        if (mixed.isEmpty()) throw java.io.IOException("No videos could be loaded")
        SeenVideos.pick(Feed.Recommended, mixed, 8)
    }

    /**
     * Unseen videos from the signed-in home feed (FEwhat_to_watch), filtered and shuffled. Follows
     * continuations until there are enough, so what you have seen doesn't leave a short list.
     */
    private suspend fun homeFeed(start: String?): List<VideoItem> {
        var token = start
        var sawVideos = false
        val found = ArrayList<VideoItem>()
        for (i in 0 until 6) {
            val json = if (token == null) com.sourabh.jarvistube.data.InnerTube.browse("FEwhat_to_watch")
            else com.sourabh.jarvistube.data.InnerTube.browse(continuation = token)
            val page = com.sourabh.jarvistube.data.LibraryParser.parse(json)
            // Shorts have their own tab: they never appear in Recommended.
            val videos = page.items.filterIsInstance<com.sourabh.jarvistube.data.LibVideo>().map { it.video }
                .filterNot { it.looksShort }
            if (videos.isNotEmpty()) sawVideos = true
            token = page.next
            found += SeenVideos.unseen(Feed.Recommended, ContentFilter.filter(videos))
            if (found.size >= 20 || token == null) break
        }
        if (start == null && !sawVideos) throw java.io.IOException("Your YouTube home feed is empty")
        homeToken = token
        return found.distinctBy { it.url }.shuffled()
    }

    // ---- Shorts tab: Shorts about the study subjects, loaded the first time the tab opens ----
    var shorts by mutableStateOf(FeedState()); private set
    private var shortsSessions: List<com.sourabh.jarvistube.data.ShortsSearch> = emptyList()
    private var shortsJob: Job? = null

    /** The full-screen Shorts viewer, while it is open. */
    var shortsViewer by mutableStateOf<ShortsViewer?>(null); private set

    fun openShorts(items: () -> List<VideoItem>, video: VideoItem, onMore: () -> Unit) {
        if (!shortsAllowed) return
        playerExpanded = false
        shortsViewer = ShortsViewer(items, items().indexOfFirst { it.url == video.url }.coerceAtLeast(0), onMore)
    }

    fun closeShorts() {
        if (shortsViewer == null) return
        shortsViewer = null
        Graph.player.stop()
    }

    /** The same checks as play(), without opening the normal player. */
    fun playShort(video: VideoItem) {
        val reason = ContentFilter.blockReason(null, video.channel, video.title) ?: Graph.study.blockReason(video)
        if (reason != null) Toast.makeText(getApplication(), reason, Toast.LENGTH_LONG).show()
        else {
            SeenVideos.mark(Feed.Shorts, listOf(video.url))
            Graph.player.play(video)
        }
    }

    /** Like Recommended: never during a study session, multi-day lock, schedule or video lock. */
    val shortsAllowed: Boolean get() = !Graph.study.active && !Graph.study.lockActive

    private val shortsPrefs = app.getSharedPreferences("shorts", Context.MODE_PRIVATE)
    private fun shortsCacheKey() = "list:" + com.sourabh.jarvistube.data.Session.dataKey + ":" + AppSettings.region
    private fun shortsSavedAtKey() = "at:" + com.sourabh.jarvistube.data.Session.dataKey + ":" + AppSettings.region

    /** The saved list, only a placeholder for an hour while new Shorts load. It still passes today's content filter. */
    private fun cachedShorts(): List<VideoItem> = runCatching {
        val age = System.currentTimeMillis() - shortsPrefs.getLong(shortsSavedAtKey(), 0L)
        if (age !in 0L..3_600_000L) return emptyList()
        val arr = org.json.JSONArray(shortsPrefs.getString(shortsCacheKey(), "[]"))
        val list = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            VideoItem(
                url = o.getString("url"), title = o.getString("title"), channel = "",
                thumb = o.optString("thumb").ifEmpty { null }, durationSec = 0, views = -1,
                uploaded = o.optString("views").ifEmpty { null }, isLive = false, isShort = true
            )
        }
        SeenVideos.unseen(Feed.Shorts, ContentFilter.filter(list))
    }.getOrDefault(emptyList())

    private fun saveShorts(list: List<VideoItem>) {
        val arr = org.json.JSONArray()
        list.take(40).forEach {
            arr.put(
                org.json.JSONObject().put("url", it.url).put("title", it.title)
                    .put("thumb", it.thumb.orEmpty()).put("views", it.uploaded.orEmpty())
            )
        }
        shortsPrefs.edit().putString(shortsCacheKey(), arr.toString())
            .putLong(shortsSavedAtKey(), System.currentTimeMillis()).apply()
    }

    /** Readies the first Shorts, so tapping one starts at once instead of after 2-3 seconds. */
    private fun prepareFirstShorts() {
        shorts.items.take(3).forEach { com.sourabh.jarvistube.data.Repo.prefetchShort(it.url) }
    }

    fun ensureShorts() {
        if (shortsSessions.isNotEmpty() || shorts.items.isNotEmpty() || shorts.loading) return
        val saved = cachedShorts()
        if (saved.isEmpty()) return loadShorts()
        // Show the saved list now; the new Shorts replace it when they arrive.
        shorts = FeedState(items = saved)
        prepareFirstShorts()
        fetchShorts(keepShown = true)
    }

    fun loadShorts() = fetchShorts(keepShown = false)

    private fun newShortsSessions() = rotatedTerms(5).map {
        com.sourabh.jarvistube.data.ShortsSearch(it, com.sourabh.jarvistube.data.ShortsSearch.SORTS.random())
    }

    /** Pages of one search, read until a few unseen Shorts turn up. */
    private suspend fun freshShorts(s: com.sourabh.jarvistube.data.ShortsSearch): List<VideoItem> {
        val got = ArrayList<VideoItem>()
        repeat(3) { if (s.hasMore && SeenVideos.unseen(Feed.Shorts, got).size < 8) got += s.load() }
        return got.shuffled()
    }

    private fun fetchShorts(keepShown: Boolean) {
        if (!shortsAllowed) return
        shortsJob?.cancel()
        stamp(Feed.Shorts)
        val sessions = newShortsSessions()
        shortsSessions = sessions
        val t0 = android.os.SystemClock.elapsedRealtime()
        val shown = shorts.items
        shortsJob = viewModelScope.launch {
            if (!keepShown) shorts = shorts.copy(loading = true, error = null)
            val arrived = ArrayList<List<VideoItem>>()
            // Every search shows as soon as it answers, instead of waiting for the slowest.
            sessions.map { s ->
                async {
                    val page = try {
                        freshShorts(s)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        emptyList()
                    }
                    arrived += page
                    val fresh = SeenVideos.unseen(Feed.Shorts, interleave(arrived))
                    if (!keepShown && fresh.isNotEmpty()) {
                        val first = shorts.items.isEmpty() || arrived.size == 1
                        shorts = FeedState(items = fresh, loading = arrived.size < sessions.size)
                        if (first) {
                            com.sourabh.jarvistube.data.Dbg.log("shorts: first shown ${android.os.SystemClock.elapsedRealtime() - t0}ms")
                            prepareFirstShorts()
                        }
                    }
                }
            }.awaitAll()
            val fresh = SeenVideos.pick(Feed.Shorts, interleave(arrived), 8)
            if (fresh.isNotEmpty()) saveShorts(fresh)
            shorts = when {
                fresh.isEmpty() -> shorts.copy(loading = false)
                // Inside the viewer new Shorts join the end, so nothing jumps under your finger.
                shortsViewer != null -> shorts.copy(items = (shown + fresh).distinctBy { it.url }, loading = false)
                else -> FeedState(items = fresh)
            }
            prepareFirstShorts()
        }
    }

    fun loadMoreShorts() {
        val cur = shorts
        if (!shortsAllowed || cur.loading || cur.loadingMore || cur.items.isEmpty()) return
        // Searches that have run out are replaced by new ones, so the list never ends.
        val sessions = shortsSessions.filter { it.hasMore }.ifEmpty { newShortsSessions().also { shortsSessions = it } }
        shortsJob = viewModelScope.launch {
            shorts = cur.copy(loadingMore = true)
            shorts = runCatchingFeed(fallback = cur) {
                var pages = sessions.map { s -> async { runCatching { freshShorts(s) }.getOrDefault(emptyList()) } }.awaitAll()
                var more = SeenVideos.unseen(Feed.Shorts, interleave(pages)).filterNot { it in cur.items }
                if (more.isEmpty()) {
                    val again = newShortsSessions().also { shortsSessions = it }
                    pages = again.map { s -> async { runCatching { freshShorts(s) }.getOrDefault(emptyList()) } }.awaitAll()
                    more = SeenVideos.unseen(Feed.Shorts, interleave(pages))
                }
                cur.copy(items = (cur.items + more).distinctBy { it.url }, loadingMore = false)
            }
        }
    }

    /** One from each list in turn, so no subject dominates. */
    private fun interleave(pages: Collection<List<VideoItem>>): List<VideoItem> {
        val mixed = ArrayList<VideoItem>()
        for (i in 0 until (pages.maxOfOrNull { it.size } ?: 0)) pages.forEach { p -> p.getOrNull(i)?.let(mixed::add) }
        return mixed.distinctBy { it.url }
    }

    // ---- Explore ----
    fun selectTopic(t: String?) {
        if (t == topic) return
        topic = t
        loadExplore()
    }

    fun loadExplore() {
        exploreJob?.cancel()
        val t = topic
        if (t == null) {
            // "All": one page from several subjects, interleaved so no subject dominates.
            exploreSession = null
            exploreJob = viewModelScope.launch {
                explore = FeedState(loading = true)
                explore = runCatchingFeed { FeedState(items = subjectMix()) }
                explore.items.take(4).forEach { com.sourabh.jarvistube.data.Repo.prefetch(it.url) }
            }
            return
        }
        val session = SearchSession("$t full lecture explained").also { exploreSession = it }
        exploreJob = viewModelScope.launch {
            explore = FeedState(loading = true)
            explore = runCatchingFeed { FeedState(items = session.loadFirst().distinctBy { it.url }) }
            explore.items.take(4).forEach { com.sourabh.jarvistube.data.Repo.prefetch(it.url) }
        }
    }

    // ---- Search suggestions (the autocomplete list under the search box) ----
    fun updateSuggestions(text: String) {
        suggestJob?.cancel()
        if (text.isBlank() || ContentFilter.isSuggestiveOrBlocked(text)) {
            suggestions.clear()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(180)
            val list = withContext(Dispatchers.IO) {
                runCatching {
                    org.schabi.newpipe.extractor.ServiceList.YouTube.suggestionExtractor.suggestionList(text)
                }.getOrDefault(emptyList())
            }
            suggestions.clear()
            suggestions.addAll(list.filterNot { ContentFilter.isSuggestiveOrBlocked(it) }.take(8))
        }
    }

    fun openSearch() {
        clearSearch()
        searchOpen = true
    }

    fun closeSearch() {
        searchOpen = false
        suggestions.clear()
    }

    fun loadMoreExplore() {
        val session = exploreSession ?: return
        val cur = explore
        if (cur.loading || cur.loadingMore || !session.hasMore || cur.items.isEmpty()) return
        exploreJob = viewModelScope.launch {
            explore = cur.copy(loadingMore = true)
            explore = runCatchingFeed(fallback = cur) {
                cur.copy(items = (cur.items + session.loadMore()).distinctBy { it.url }, loadingMore = false)
            }
        }
    }

    // ---- Search ----
    fun submitSearch(q: String = query) {
        val rawText = q.trim()
        if (rawText.isEmpty()) return
        val text = ContentFilter.sanitizeQuery(rawText)
        query = rawText
        submittedQuery = rawText
        recents.remove(rawText)
        recents.add(0, rawText)
        while (recents.size > 10) recents.removeAt(recents.lastIndex)
        prefs.edit().putString(recentsKey, recents.joinToString("\n")).apply()

        searchJob?.cancel()
        searchShowsShorts = false
        val session = SearchSession(text).also { searchSession = it }
        val shortsSession = com.sourabh.jarvistube.data.ShortsSearch(text).also { shortsSearch = it }
        shortsSearchJob?.cancel()
        shortsSearchJob = viewModelScope.launch {
            shortsResults = FeedState(loading = true)
            shortsResults = runCatchingFeed { FeedState(items = shortsSession.load().distinctBy { it.url }) }
        }
        searchJob = viewModelScope.launch {
            results = FeedState(loading = true)
            results = runCatchingFeed { FeedState(items = session.loadFirst().distinctBy { it.url }) }
            results.items.take(4).forEach { com.sourabh.jarvistube.data.Repo.prefetch(it.url) }
        }
    }

    fun loadMoreSearch() {
        val session = searchSession ?: return
        val cur = results
        if (cur.loading || cur.loadingMore || !session.hasMore || cur.items.isEmpty()) return
        searchJob = viewModelScope.launch {
            results = cur.copy(loadingMore = true)
            results = runCatchingFeed(fallback = cur) {
                cur.copy(items = (cur.items + session.loadMore()).distinctBy { it.url }, loadingMore = false)
            }
        }
    }

    fun loadMoreSearchShorts() {
        val session = shortsSearch ?: return
        val cur = shortsResults
        if (cur.loading || cur.loadingMore || !session.hasMore || cur.items.isEmpty()) return
        shortsSearchJob = viewModelScope.launch {
            shortsResults = cur.copy(loadingMore = true)
            shortsResults = runCatchingFeed(fallback = cur) {
                cur.copy(items = (cur.items + session.load()).distinctBy { it.url }, loadingMore = false)
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        query = ""
        shortsSearchJob?.cancel()
        shortsResults = FeedState()
        submittedQuery = null
        results = FeedState()
    }

    fun clearRecents() {
        recents.clear()
        prefs.edit().remove(recentsKey).apply()
    }

    fun removeRecent(text: String) {
        recents.remove(text)
        prefs.edit().putString(recentsKey, recents.joinToString("\n")).apply()
    }

    private suspend fun runCatchingFeed(fallback: FeedState = FeedState(), block: suspend () -> FeedState): FeedState =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fallback.copy(loading = false, loadingMore = false, error = e.message ?: e.javaClass.simpleName)
        }
}

/** What the Shorts viewer is showing: a live list, where to start, and how to load more. */
class ShortsViewer(val items: () -> List<VideoItem>, val start: Int, val onMore: () -> Unit)

sealed interface Page {
    data class Channel(val url: String, val name: String) : Page
    data class Playlist(val url: String, val title: String) : Page
    data object StudyChannels : Page
}
