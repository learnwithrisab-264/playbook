package com.sourabh.jarvistube

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sourabh.jarvistube.data.SearchSession
import com.sourabh.jarvistube.data.VideoItem
import com.sourabh.jarvistube.data.looksShort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Study mode: timed focus sessions where only allowed channels can be watched,
 * plus a daily limit on free-time watching. Everything persists, and the session
 * clock is wall-clock based, so killing the app does not end a session.
 */
class StudyState(context: Context) {
    private val p = context.getSharedPreferences("study", Context.MODE_PRIVATE)

    init {
        // One-time reset requested by the user (27 Oct 2026): every running lock ends, so the app
        // starts clean. Runs before the fields below read their saved values.
        if (!p.getBoolean("locksReset1", false)) {
            p.edit().putLong("endAt", 0L).putBoolean("longLock", false).putLong("lockEndAt", 0L).remove("lockUrl")
                .putLong("limitLockUntil", 0L).putLong("filterLockUntil", 0L).putBoolean("locksReset1", true).commit()
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var feedJob: Job? = null

    /** "Your channels" in the Study tab: each account has its own list. Locks stay device-wide. */
    val allowed = mutableStateListOf<String>()
    private var allowedKey = "allowed"

    private fun loadAllowed(key: String) {
        allowedKey = "allowed:$key"
        allowed.clear()
        allowed.addAll(p.getString(allowedKey, "").orEmpty().split("\n").filter { it.isNotBlank() })
    }

    init {
        // The old shared list goes to the account in use.
        p.getString("allowed", null)?.let { shared ->
            p.edit().putString("allowed:" + com.sourabh.jarvistube.data.Session.dataKey, shared).remove("allowed").apply()
        }
        loadAllowed(com.sourabh.jarvistube.data.Session.dataKey)
        com.sourabh.jarvistube.data.Session.onDataKeyChange(::loadAllowed)
    }

    // Session
    var endAt by mutableLongStateOf(p.getLong("endAt", 0L)); private set
    var strict by mutableStateOf(p.getBoolean("strict", false)); private set
    var sessionMinutes by mutableIntStateOf(p.getInt("sessionMinutes", 0)); private set
    var active by mutableStateOf(false); private set
    var justCompleted by mutableIntStateOf(0) // minutes of the session that just finished

    // Settings for the next session
    var minutes by mutableIntStateOf(p.getInt("minutes", 45))
    var strictNext by mutableStateOf(p.getBoolean("strictNext", true))

    // Free-time limit. Once locked it can only be LOWERED until the lock ends (a commitment
    // device: raising it in a weak moment is exactly what the lock prevents).
    var dailyLimit by mutableIntStateOf(p.getInt("limit", 0)); private set
    var usageSec by mutableIntStateOf(0); private set
    var limitLockUntil by mutableLongStateOf(p.getLong("limitLockUntil", 0L)); private set
    val limitLocked: Boolean get() = limitLockUntil > com.sourabh.jarvistube.data.TrustedClock.now()

    /** Returns false (and changes nothing) if the lock forbids this change. */
    fun updateDailyLimit(minutes: Int): Boolean {
        if (limitLocked && (minutes == 0 || minutes > dailyLimit)) return false
        dailyLimit = minutes
        p.edit().putInt("limit", minutes).apply()
        return true
    }

    fun lockLimit(days: Int) {
        if (dailyLimit <= 0 || days <= 0) return
        limitLockUntil = maxOf(limitLockUntil, com.sourabh.jarvistube.data.TrustedClock.now()) + days * 86_400_000L
        p.edit().putLong("limitLockUntil", limitLockUntil).apply()
    }

    // ---- Block Shorts: one timer for the whole phone, so switching account can't get around it ----
    var shortsBlockUntil by mutableLongStateOf(p.getLong("shortsBlockUntil", 0L)); private set
    val shortsBlocked: Boolean get() = shortsBlockUntil > com.sourabh.jarvistube.data.TrustedClock.now()

    /**
     * Blocks Shorts until [until] (trusted time). Like the other locks it can only be extended:
     * an earlier time changes nothing. Written to disk at once, so a crash or restart can't end it.
     */
    fun blockShortsUntil(until: Long) {
        if (until <= shortsBlockUntil) return
        shortsBlockUntil = until
        p.edit().putLong("shortsBlockUntil", until).commit()
        val playing = Graph.player.now.value?.video
        if (playing != null && playing.looksShort) Graph.player.stop()
    }

    // ---- Research-backed focus helpers ----
    // Daily goal + streak (goal-setting and streaks raise follow-through), Pomodoro-style
    // breaks (short scheduled breaks sustain attention), and a 7-day history.
    var dailyGoal by mutableIntStateOf(p.getInt("goal", 60)); private set
    // Pomodoro breaks were removed from the app; kept off so old saved settings never pause videos.
    var breaksEnabled by mutableStateOf(false); private set
    var focusBlockMin by mutableIntStateOf(p.getInt("focusBlock", 25)); private set
    var breakMin by mutableIntStateOf(p.getInt("breakMin", 5)); private set
    var breakEndAt by mutableLongStateOf(0L); private set
    val onBreak: Boolean get() = breakEndAt > com.sourabh.jarvistube.data.TrustedClock.now()
    private var sinceBreakSec = 0
    var focusTodaySec by mutableIntStateOf(0); private set
    private var day = LocalDate.now().toString()
    /** date -> focused seconds, last 14 days. */
    val history = androidx.compose.runtime.mutableStateMapOf<String, Int>()

    fun updateGoal(m: Int) { dailyGoal = m; p.edit().putInt("goal", m).apply() }
    fun updateBreaks(on: Boolean, block: Int = focusBlockMin, brk: Int = breakMin) {
        breaksEnabled = on; focusBlockMin = block; breakMin = brk
        p.edit().putBoolean("breaks", on).putInt("focusBlock", block).putInt("breakMin", brk).apply()
    }
    // Music/film/entertainment filter. The switch is the user's choice in free time, but it is
    // always enforced while a study session or a video lock is running.
    var blockEntertainment by mutableStateOf(p.getBoolean("blockEnt", true)); private set
    /** Filter lock: the filter stays on until this time, whatever the switch says. */
    var filterLockUntil by mutableLongStateOf(p.getLong("filterLockUntil", 0L)); private set
    val filterLocked: Boolean get() = filterLockUntil > com.sourabh.jarvistube.data.TrustedClock.now()
    val entertainmentBlocked: Boolean get() = blockEntertainment || active || lockActive || filterLocked

    /** Locks the filter on for [days] more days (added to a running lock, never shortening it). */
    fun lockFilter(days: Int) {
        if (days <= 0) return
        filterLockUntil = maxOf(filterLockUntil, com.sourabh.jarvistube.data.TrustedClock.now()) + days * 86_400_000L
        blockEntertainment = true
        p.edit().putLong("filterLockUntil", filterLockUntil).putBoolean("blockEnt", true).commit()
    }

    /** Returns false if a running session/lock forbids turning the filter off. */
    fun updateBlockEntertainment(on: Boolean): Boolean {
        if (!on && (active || lockActive || filterLocked)) return false
        blockEntertainment = on
        p.edit().putBoolean("blockEnt", on).apply()
        return true
    }

    fun skipBreak() { if (!strict || !active) breakEndAt = 0L }

    private fun loadHistory() {
        history.clear()
        p.getString("history", "").orEmpty().split(";").forEach {
            val (d, s) = it.split("=").takeIf { kv -> kv.size == 2 } ?: return@forEach
            s.toIntOrNull()?.let { v -> history[d] = v }
        }
        focusTodaySec = history[day] ?: 0
    }

    private fun saveHistory() {
        history[day] = focusTodaySec
        val keep = history.keys.sortedDescending().take(14).toSet()
        history.keys.retainAll(keep)
        p.edit().putString("history", history.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()
    }

    /** Consecutive days (ending today, or yesterday if today isn't done yet) that met the goal. */
    val streak: Int
        get() {
            val goalSec = dailyGoal * 60
            var d = LocalDate.now()
            if ((history[d.toString()] ?: 0) < goalSec) d = d.minusDays(1)
            var n = 0
            while ((history[d.toString()] ?: 0) >= goalSec) { n++; d = d.minusDays(1) }
            return n
        }

    fun lastDays(n: Int): List<Pair<LocalDate, Int>> =
        (n - 1 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }.map { it to (history[it.toString()] ?: 0) }

    private fun rollDay() {
        val today = LocalDate.now().toString()
        if (today == day) return
        saveHistory()
        day = today
        focusTodaySec = history[today] ?: 0
        usageSec = 0
        saveUsage()
    }

    /**
     * Called once a second by the app-wide player loop (so background play counts too).
     * Returns BREAK / LIMIT / a message, or null.
     */
    fun onSecond(playing: Boolean): String? {
        tick()
        rollDay()
        val now = com.sourabh.jarvistube.data.TrustedClock.now()
        if (breakEndAt in 1..now) {
            breakEndAt = 0L
            return "Break over. Back to focus!"
        }
        val studying = active || lockActive
        if (studying && playing && !onBreak) {
            focusTodaySec++
            sinceBreakSec++
            if (focusTodaySec % 30 == 0) saveHistory()
            if (breaksEnabled && active && !lockActive && sinceBreakSec >= focusBlockMin * 60) {
                sinceBreakSec = 0
                breakEndAt = now + breakMin * 60_000L
                saveHistory()
                return "BREAK"
            }
        }
        if (!studying && playing) {
            addUsage(1)
            if (limitReached) return "LIMIT"
        }
        return null
    }

    // Stats
    var totalSessions by mutableIntStateOf(p.getInt("sessions", 0)); private set
    var totalMinutes by mutableIntStateOf(p.getInt("totalMinutes", 0)); private set

    var feed by mutableStateOf(FeedState()); private set

    init {
        val today = LocalDate.now().toString()
        usageSec = if (p.getString("usageDate", "") == today) p.getInt("usageSec", 0) else 0
        loadHistory()
        // The running-session check is in the last init block, after every field is loaded.
    }

    val remainingMs: Long get() = (endAt - com.sourabh.jarvistube.data.TrustedClock.now()).coerceAtLeast(0L)

    /**
     * Allowed if the name matches (ignoring case, spaces and invisible characters YouTube sometimes
     * adds) or, when known, the channel link is one of "My channels".
     */
    fun isAllowed(channel: String, channelUrl: String? = null): Boolean {
        val key = com.sourabh.jarvistube.data.StudyChannels.nameKey(channel)
        return (key.isNotEmpty() && allowed.any { com.sourabh.jarvistube.data.StudyChannels.nameKey(it) == key }) ||
            com.sourabh.jarvistube.data.StudyChannels.containsName(channel) ||
            com.sourabh.jarvistube.data.StudyChannels.containsChannelUrl(channelUrl)
    }

    /** Everything a session may play from: the allowed list plus "My Study Channels". */
    val allChannelNames: List<String>
        get() = (allowed + com.sourabh.jarvistube.data.StudyChannels.list.map { it.name })
            .distinctBy { it.lowercase() }

    /** Returns null if playable, otherwise the reason it is blocked. */
    fun blockReason(video: VideoItem): String? = when {
        lockActive && video.url == lockUrl -> null
        onBreak -> "Break time: rest your eyes for a few minutes."
        lockActive -> "Study lock: only the locked video can play until the timer ends."
        active && !isAllowed(video.channel, video.channelUrl) -> "Study mode: only your allowed channels can be played."
        shortsBlocked && video.looksShort -> "Shorts are blocked for " +
            com.sourabh.jarvistube.data.timeLeftText(shortsBlockUntil - com.sourabh.jarvistube.data.TrustedClock.now()) + "."
        !active && dailyLimit > 0 && usageSec >= dailyLimit * 60 -> "Daily free-time limit reached."
        else -> null
    }

    // ---- Channels ----
    fun addChannel(name: String) {
        val n = name.trim()
        if (n.isEmpty() || isAllowed(n) || active) return
        allowed.add(n)
        saveAllowed()
    }

    fun removeChannel(name: String) {
        if (active) return
        allowed.removeAll { it.equals(name, ignoreCase = true) }
        saveAllowed()
    }

    private fun saveAllowed() = p.edit().putString(allowedKey, allowed.joinToString("\n")).apply()

    // ---- Session ----
    fun start() {
        if (active || allChannelNames.isEmpty()) return
        endAt = com.sourabh.jarvistube.data.TrustedClock.now() + minutes * 60_000L
        sessionStartAt = com.sourabh.jarvistube.data.TrustedClock.now()
        longLock = false
        strict = strictNext
        sessionMinutes = minutes
        active = true
        sinceBreakSec = 0
        p.edit().putLong("endAt", endAt).putLong("startAt", sessionStartAt).putBoolean("longLock", false)
            .putBoolean("strict", strict).putInt("sessionMinutes", sessionMinutes)
            .putInt("minutes", minutes).putBoolean("strictNext", strictNext).apply()
        loadFeed(null)
    }

    // ---- Multi-day Study Mode lock ----
    // Like Jarvis Strict mode with an expiration time: a strict session that runs until a chosen
    // date and time. It can't be ended early, only extended.
    var longLock by mutableStateOf(p.getBoolean("longLock", false)); private set
    var sessionStartAt by mutableLongStateOf(p.getLong("startAt", 0L)); private set

    /** Starts the lock; returns false if a session is already running or no channel is allowed. */
    fun startLongLock(untilMs: Long): Boolean {
        val now = com.sourabh.jarvistube.data.TrustedClock.now()
        if (active || allChannelNames.isEmpty() || untilMs <= now + 60_000L) return false
        endAt = untilMs
        sessionStartAt = now
        strict = true
        longLock = true
        sessionMinutes = ((untilMs - now) / 60_000L).toInt()
        active = true
        sinceBreakSec = 0
        p.edit().putLong("endAt", endAt).putLong("startAt", now).putBoolean("strict", true)
            .putBoolean("longLock", true).putInt("sessionMinutes", sessionMinutes).commit()
        loadFeed(null)
        return true
    }

    /** Pushes the end of a running lock later. Never earlier. */
    fun extendLongLock(untilMs: Long): Boolean {
        if (!active || !longLock || untilMs <= endAt) return false
        endAt = untilMs
        sessionMinutes = ((untilMs - sessionStartAt) / 60_000L).toInt()
        p.edit().putLong("endAt", endAt).putInt("sessionMinutes", sessionMinutes).commit()
        return true
    }

    /** Non-strict sessions only. */
    fun endEarly() {
        if (!active || strict) return
        finish(counted = false)
    }

    fun tick() {
        if (active && com.sourabh.jarvistube.data.TrustedClock.now() >= endAt) complete()
        if (lockActive && com.sourabh.jarvistube.data.TrustedClock.now() >= lockEndAt) releaseLock()
        checkSchedule()
    }

    // ---- Scheduled focus ----
    // Every chosen weekday at a set time, a strict focus session starts by itself and runs for
    // the set length. It can't be changed while it is running.
    var scheduleOn by mutableStateOf(p.getBoolean("schedOn", false)); private set
    var scheduleStartMin by mutableIntStateOf(p.getInt("schedStart", 18 * 60)); private set
    var scheduleLenMin by mutableIntStateOf(p.getInt("schedLen", 120)); private set
    /** Bit 0 = Monday … bit 6 = Sunday. */
    var scheduleDays by mutableIntStateOf(p.getInt("schedDays", 0b1111111)); private set

    /** Returns false while a focus session is running (the schedule is then locked). */
    fun updateSchedule(on: Boolean = scheduleOn, startMin: Int = scheduleStartMin, lenMin: Int = scheduleLenMin, days: Int = scheduleDays): Boolean {
        if (active) return false
        scheduleOn = on; scheduleStartMin = startMin; scheduleLenMin = lenMin; scheduleDays = days
        p.edit().putBoolean("schedOn", on).putInt("schedStart", startMin).putInt("schedLen", lenMin).putInt("schedDays", days).apply()
        checkSchedule()
        return true
    }

    private fun checkSchedule() {
        if (!scheduleOn || active) return
        val window = scheduleWindow(p, com.sourabh.jarvistube.data.TrustedClock.now()) ?: return
        if (p.getLong("schedStarted", 0L) == window.first) return // already ran (e.g. it ended early)
        val now = com.sourabh.jarvistube.data.TrustedClock.now()
        endAt = window.last
        sessionStartAt = now
        strict = true
        longLock = false
        sessionMinutes = ((window.last - window.first) / 60_000L).toInt()
        active = true
        sinceBreakSec = 0
        p.edit().putLong("endAt", endAt).putLong("startAt", now).putBoolean("strict", true).putBoolean("longLock", false)
            .putInt("sessionMinutes", sessionMinutes).putLong("schedStarted", window.first).commit()
        loadFeed(null)
    }

    companion object {
        /** The scheduled window (start..end, epoch ms) containing [now], or null. Also used by the provider. */
        fun scheduleWindow(p: android.content.SharedPreferences, now: Long): LongRange? {
            if (!p.getBoolean("schedOn", false)) return null
            val start = p.getInt("schedStart", 18 * 60)
            val len = p.getInt("schedLen", 120)
            val days = p.getInt("schedDays", 0b1111111)
            val zone = java.time.ZoneId.systemDefault()
            val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            // Today's window, or yesterday's if it runs past midnight.
            for (d in listOf(today, today.minusDays(1))) {
                if (days and (1 shl (d.dayOfWeek.value - 1)) == 0) continue
                val s = d.atStartOfDay(zone).plusMinutes(start.toLong()).toInstant().toEpochMilli()
                val e = s + len * 60_000L
                if (now in s until e) return s..e
            }
            return null
        }
    }

    // ---- Timed video lock ("Lock Study Session") ----
    // Wall-clock based and saved immediately, so a crash, a force-stop or a reboot cannot reset
    // it: the next launch reopens the locked video until lockEndAt.
    var lockEndAt by mutableLongStateOf(p.getLong("lockEndAt", 0L)); private set
    var lockUrl by mutableStateOf(p.getString("lockUrl", null)); private set
    private var lockTitle: String? = p.getString("lockTitle", null)
    private var lockChannel: String? = p.getString("lockChannel", null)
    private var lockThumb: String? = p.getString("lockThumb", null)
    var lockActive by mutableStateOf(lockEndAt > com.sourabh.jarvistube.data.TrustedClock.now()); private set

    val lockRemainingMs: Long get() = (lockEndAt - com.sourabh.jarvistube.data.TrustedClock.now()).coerceAtLeast(0L)

    fun startLock(video: VideoItem, minutes: Int) {
        if (minutes <= 0 || lockActive) return
        lockEndAt = com.sourabh.jarvistube.data.TrustedClock.now() + minutes * 60_000L
        lockUrl = video.url
        lockTitle = video.title
        lockChannel = video.channel
        lockThumb = video.thumb
        lockActive = true
        p.edit().putLong("lockEndAt", lockEndAt).putString("lockUrl", video.url)
            .putString("lockTitle", video.title).putString("lockChannel", video.channel)
            .putString("lockThumb", video.thumb).commit()
    }

    private fun releaseLock() {
        lockActive = false
        lockEndAt = 0L
        p.edit().putLong("lockEndAt", 0L).remove("lockUrl").apply()
    }

    /** The video to reopen while a lock is running. */
    fun lockedVideo(): VideoItem? {
        val url = lockUrl ?: return null
        return VideoItem(url, lockTitle ?: "Study video", lockChannel.orEmpty(), lockThumb, 0, -1, null, false)
    }

    private fun complete() = finish(counted = true)

    private fun finish(counted: Boolean) {
        // A multi-day lock is not one study session: its focus time is already in the daily history.
        if (counted && !longLock) {
            totalSessions += 1
            totalMinutes += sessionMinutes
            justCompleted = sessionMinutes
            p.edit().putInt("sessions", totalSessions).putInt("totalMinutes", totalMinutes).apply()
        }
        endAt = 0L
        active = false
        longLock = false
        feedJob?.cancel()
        feed = FeedState()
        p.edit().putLong("endAt", 0L).putBoolean("longLock", false).apply()
    }

    fun saveSettings() {
        p.edit().putInt("minutes", minutes).putBoolean("strictNext", strictNext).putInt("limit", dailyLimit).apply()
    }

    // ---- Free-time usage ----
    fun addUsage(seconds: Int) {
        usageSec += seconds
        if (usageSec % 10 == 0) saveUsage()
    }

    fun saveUsage() {
        p.edit().putString("usageDate", LocalDate.now().toString()).putInt("usageSec", usageSec).apply()
    }

    val limitReached: Boolean get() = !active && dailyLimit > 0 && usageSec >= dailyLimit * 60

    // ---- Study feed (allowed channels only) ----
    fun loadFeed(query: String?) {
        feedJob?.cancel()
        val channels = allChannelNames
        if (channels.isEmpty()) {
            feed = FeedState()
            return
        }
        feedJob = scope.launch {
            feed = FeedState(loading = true)
            try {
                val perChannel = channels.map { ch ->
                    async {
                        runCatching {
                            SearchSession(listOfNotNull(query?.takeIf { it.isNotBlank() }, ch).joinToString(" "))
                                .loadFirst()
                        }.getOrDefault(emptyList()).filter { it.channel.equals(ch, ignoreCase = true) }
                    }
                }.awaitAll()
                feed = FeedState(items = roundRobin(perChannel).distinctBy { it.url })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                feed = FeedState(error = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun roundRobin(lists: List<List<VideoItem>>): List<VideoItem> {
        val out = ArrayList<VideoItem>()
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) lists.forEach { l -> l.getOrNull(i)?.let(out::add) }
        return out
    }

    init {
        // Last, so every field above is loaded: resume or finish a session saved before a restart,
        // then start a scheduled one if its time has come.
        val now = com.sourabh.jarvistube.data.TrustedClock.now()
        if (endAt > 0L) {
            if (endAt > now) active = true else complete()
        }
        checkSchedule()
    }
}
