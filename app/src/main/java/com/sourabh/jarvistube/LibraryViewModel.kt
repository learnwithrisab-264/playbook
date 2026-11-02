package com.sourabh.jarvistube

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sourabh.jarvistube.data.Account
import com.sourabh.jarvistube.data.InnerTube
import com.sourabh.jarvistube.data.LibChannel
import com.sourabh.jarvistube.data.LibPlaylist
import com.sourabh.jarvistube.data.LibVideo
import com.sourabh.jarvistube.data.LibItem
import com.sourabh.jarvistube.data.LibraryParser
import com.sourabh.jarvistube.data.Session
import com.sourabh.jarvistube.data.SessionExpiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class LibTab(val label: String, val browseId: String) {
    Subscriptions("Subscriptions", "FEsubscriptions"),
    History("History", "FEhistory"),
    Playlists("Playlists", "FEplaylist_aggregation"),
    Channels("Channels", "FEchannels")
}

data class LibFeed(
    val items: List<LibItem> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val next: String? = null,
    val sessionProblem: Boolean = false
)

data class LibDetail(val title: String, val browseId: String, val params: String? = null)

/** One paged, authenticated browse feed. */
class LibLoader(
    private val scope: CoroutineScope,
    private val browseId: String,
    private val params: String? = null,
    private val onExpired: () -> Unit
) {
    var state by mutableStateOf(LibFeed(loading = true)); private set
    var started = false; private set
    private var job: Job? = null

    fun reset() {
        job?.cancel()
        started = false
        state = LibFeed(loading = true)
    }

    fun refresh() {
        started = true
        job?.cancel()
        state = LibFeed(loading = true)
        job = scope.launch { state = fetch(null, emptyList()) }
    }

    /** Drops one item locally after YouTube confirmed its removal. */
    fun remove(key: String) {
        state = state.copy(items = state.items.filterNot { it.key == key })
    }

    fun more() {
        val s = state
        val token = s.next ?: return
        if (s.loading || s.loadingMore || s.items.isEmpty()) return
        job = scope.launch {
            state = s.copy(loadingMore = true)
            state = fetch(token, s.items)
        }
    }

    private suspend fun fetch(token: String?, prev: List<LibItem>): LibFeed = try {
        val json = if (token == null) InnerTube.browse(browseId, params) else InnerTube.browse(continuation = token)
        val page = LibraryParser.parse(json)
        LibFeed(items = (prev + page.items).distinctBy { it.key }, next = page.next)
    } catch (e: CancellationException) {
        throw e
    } catch (e: SessionExpiredException) {
        // Never sign out automatically: one rejected request must not cost the user their login.
        LibFeed(
            items = prev,
            error = "YouTube didn't accept the saved login this time. Try again, or sign in again if it keeps happening.",
            sessionProblem = true
        )
    } catch (e: Exception) {
        LibFeed(items = prev, error = e.message ?: e.javaClass.simpleName)
    }
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    var signedIn by mutableStateOf(Session.isSignedIn); private set
    var tab by mutableStateOf(LibTab.Subscriptions); private set
    var detail by mutableStateOf<LibDetail?>(null); private set
    var detailLoader by mutableStateOf<LibLoader?>(null); private set

    /** The signed-in account shown in the You header (name, email, photo). */
    val account: Account? get() = Session.active
    val accounts: List<Account> get() = Session.accounts

    private val loaders = LibTab.entries.associateWith {
        LibLoader(viewModelScope, it.browseId, onExpired = ::signOut)
    }

    init {
        // Fill in name / email / photo for any saved account that is missing them.
        Session.accounts.filter { it.email == null || it.photo == null }.forEach { loadProfile(it) }
    }

    fun loader(t: LibTab): LibLoader = loaders.getValue(t)

    fun selectTab(t: LibTab) {
        tab = t
        ensure(t)
    }

    fun ensure(t: LibTab = tab) {
        if (signedIn && !loader(t).started) loader(t).refresh()
    }

    fun openDetail(d: LibDetail) {
        detail = d
        detailLoader = LibLoader(viewModelScope, d.browseId, d.params, onExpired = ::signOut).also { it.refresh() }
    }

    fun closeDetail() {
        detail = null
        detailLoader = null
    }

    fun onSignedIn(cookie: String) {
        Session.addAccount(cookie)
        reloadForAccount()
    }

    /**
     * Each account has its own trusted channels, so changing account during a study session or
     * lock could swap in a list that lets anything play. The change waits until the lock ends.
     */
    private fun accountLocked(): Boolean {
        val locked = Graph.study.active || Graph.study.lockActive
        if (locked) toast("Accounts can't be changed during a study session or lock.")
        return locked
    }

    /** Clears the sign-in page's cookies so the next sign-in adds a different Google account. */
    fun prepareAddAccount() = Session.clearWebCookies()

    fun switchAccount(id: String) {
        if (id == Session.activeId || accountLocked()) return
        Session.switchTo(id)
        reloadForAccount()
    }

    /** Signs out of the current account; another saved account (if any) takes over. */
    fun signOut() {
        Session.activeId?.let(::removeAccount)
    }

    /**
     * Removes any one saved account; the others stay signed in. Removing the active one switches
     * to the next account (or signed out) and reloads the library for it.
     */
    fun removeAccount(id: String) {
        if (accountLocked()) return
        val wasActive = id == Session.activeId
        Session.remove(id)
        if (wasActive) reloadForAccount()
    }

    private fun reloadForAccount() {
        signedIn = Session.isSignedIn
        // Drop every loaded list (Subscriptions, History, Playlists, Channels) so nothing from the
        // previous account stays on screen; the current tab reloads now, the others when opened.
        loaders.values.forEach { it.reset() }
        closeDetail()
        if (signedIn) {
            ensure(tab)
            loadProfile()
        }
        // Accounts added in older builds may still lack a name/photo: fill them in quietly.
        Session.accounts.filter { it.id != Session.activeId && (it.email == null || it.photo == null) }
            .forEach { loadProfile(it) }
    }

    private fun loadProfile(account: Account? = Session.active) {
        val a = account ?: return
        viewModelScope.launch {
            runCatching { InnerTube.accountProfile(a) }.getOrNull()?.let {
                Session.updateProfile(a.id, it.name, it.email, it.handle, it.photo)
            }
        }
    }

    // ---- managing the library ----

    private fun act(done: String, block: suspend () -> Unit, after: () -> Unit) {
        viewModelScope.launch {
            val ok = runCatching { block() }.isSuccess
            if (ok) after()
            toast(if (ok) done else "Couldn't do that. Try again.")
        }
    }

    private fun toast(msg: String) =
        android.widget.Toast.makeText(getApplication(), msg, android.widget.Toast.LENGTH_SHORT).show()

    private fun currentLoader(): LibLoader = detailLoader ?: loader(tab)

    fun removeFromHistory(item: LibVideo) {
        val token = item.feedbackToken ?: return toast("YouTube didn't offer removal for this video.")
        act("Removed from watch history", { InnerTube.removeFromHistory(token) }) {
            loader(LibTab.History).remove(item.key)
        }
    }

    fun removeFromPlaylist(item: LibVideo) {
        val playlistId = detail?.browseId?.removePrefix("VL") ?: return
        val videoId = videoId(item.video.url) ?: return
        act("Removed from playlist", { InnerTube.removeFromPlaylist(playlistId, videoId, item.setVideoId) }) {
            currentLoader().remove(item.key)
        }
    }

    fun deletePlaylist(p: LibPlaylist) {
        act("Playlist deleted", { InnerTube.deletePlaylist(p.id) }) { loader(LibTab.Playlists).remove(p.key) }
    }

    fun createPlaylist(title: String) {
        if (title.isBlank()) return
        act("Playlist created", { InnerTube.createPlaylist(title.trim(), null) }) { loader(LibTab.Playlists).refresh() }
    }

    fun unsubscribe(c: LibChannel) {
        act("Unsubscribed from ${c.name}", { InnerTube.unsubscribe(c.id) }) {
            loader(LibTab.Channels).remove(c.key)
            loader(LibTab.Subscriptions).reset()
        }
    }

    companion object {
        fun videoId(url: String): String? = android.net.Uri.parse(url).getQueryParameter("v")
    }
}
