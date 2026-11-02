package com.sourabh.jarvistube.data

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** Chrome-on-Android user agent (no "wv" marker, so Google's sign-in page accepts it). */
const val WEB_UA =
    "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/130.0.0.0 Mobile Safari/537.36"

/** One signed-in Google/YouTube account: its own cookie jar plus what we show for it. */
data class Account(
    val id: String,
    val cookie: String,
    val name: String? = null,
    val email: String? = null,
    val handle: String? = null,
    val photo: String? = null,
    /** Names this account's own saved data (feed, history, ...). Stays the same across re-sign-ins. */
    val dataId: String = id
)

/**
 * Holds the YouTube login cookies captured from the sign-in WebView, for any number of
 * accounts. Each account keeps its own cookies, so switching is instant and never re-asks
 * for a password. Everything except [cookie]/[sapisid] is observable from Compose.
 */
object Session {
    private lateinit var prefs: SharedPreferences

    var accounts by mutableStateOf<List<Account>>(emptyList()); private set
    var activeId by mutableStateOf<String?>(null); private set

    val active: Account? get() = accounts.firstOrNull { it.id == activeId }
    val cookie: String? get() = active?.cookie

    /** Whose data is in use right now: the active account, or "guest" when signed out. */
    val dataKey: String get() = active?.dataId ?: "guest"

    private val dataListeners = ArrayList<(String) -> Unit>()
    private var lastDataKey = "guest"
    /** Email -> dataId, kept after sign-out so signing in again finds the same data. */
    private val dataByEmail = HashMap<String, String>()

    /** [listener] runs at once whenever the data owner changes, so it can swap to that account's data. */
    fun onDataKeyChange(listener: (String) -> Unit) { dataListeners += listener }
    fun removeDataKeyListener(listener: (String) -> Unit) { dataListeners -= listener }

    private fun dataKeyMaybeChanged() {
        val key = dataKey
        if (key != lastDataKey) {
            lastDataKey = key
            dataListeners.toList().forEach { it(key) }
        }
    }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
        val raw = prefs.getString("accounts", null)
        if (raw != null) {
            accounts = runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Account(
                        o.getString("id"), o.getString("cookie"),
                        o.optString("name").ifEmpty { null }, o.optString("email").ifEmpty { null },
                        o.optString("handle").ifEmpty { null }, o.optString("photo").ifEmpty { null },
                        o.optString("dataId").ifEmpty { o.getString("id") }
                    )
                }
            }.getOrDefault(emptyList())
            activeId = prefs.getString("active", null)?.takeIf { id -> accounts.any { it.id == id } }
                ?: accounts.firstOrNull()?.id
        } else {
            // Older builds kept a single cookie: carry it over as the first account.
            prefs.getString("cookie", null)?.let { old ->
                val a = Account(newId(), old)
                accounts = listOf(a)
                activeId = a.id
                persist()
                prefs.edit().remove("cookie").apply()
            }
        }
        runCatching {
            val saved = JSONObject(prefs.getString("dataByEmail", "{}").orEmpty())
            saved.keys().forEach { dataByEmail[it] = saved.getString(it) }
        }
        accounts.forEach { a -> a.email?.let { dataByEmail.getOrPut(it) { a.dataId } } }
        lastDataKey = dataKey
    }

    val isSignedIn: Boolean get() = sapisid != null

    val sapisid: String? get() = cookie?.let(::sapisidOf)

    /** The SAPISID used to sign InnerTube requests, from one account's cookie string. */
    fun sapisidOf(cookie: String): String? = cookieValue(cookie, "SAPISID") ?: cookieValue(cookie, "__Secure-3PAPISID")

    /** Adds a freshly signed-in account (or refreshes it if it is already here) and makes it active. */
    fun addAccount(newCookie: String): Account {
        val sid = cookieValue(newCookie, "SAPISID")
        val existing = accounts.firstOrNull { sid != null && cookieValue(it.cookie, "SAPISID") == sid }
        val account = existing?.copy(cookie = newCookie) ?: Account(newId(), newCookie)
        accounts = if (existing != null) accounts.map { if (it.id == existing.id) account else it } else accounts + account
        activeId = account.id
        persist()
        dataKeyMaybeChanged()
        return account
    }

    fun switchTo(id: String) {
        if (accounts.none { it.id == id }) return
        activeId = id
        persist()
        dataKeyMaybeChanged()
    }

    /**
     * Stores the name, email and photo YouTube reported for account [id] (the account the request
     * was made for, which may no longer be the active one if the user switched meanwhile).
     */
    fun updateProfile(id: String, name: String?, email: String?, handle: String?, photo: String?) {
        val a = accounts.firstOrNull { it.id == id } ?: return
        // A Google account seen before gets its old data back, even after signing out in between.
        val dataId = email?.let { dataByEmail.getOrPut(it) { a.dataId } } ?: a.dataId
        val updated = a.copy(
            name = name ?: a.name, email = email ?: a.email, handle = handle ?: a.handle, photo = photo ?: a.photo,
            dataId = dataId
        )
        // Same Google account signed in twice: keep one entry.
        val dupe = accounts.firstOrNull { it.id != a.id && email != null && it.email == email }
        accounts = accounts.filter { it.id != dupe?.id }.map { if (it.id == a.id) updated else it }
        if (activeId == dupe?.id) activeId = a.id
        persist()
        dataKeyMaybeChanged()
    }

    private fun save(id: String, newCookie: String) {
        accounts = accounts.map { if (it.id == id) it.copy(cookie = newCookie) else it }
        persist()
    }

    /**
     * YouTube rotates some session cookies (e.g. the *SIDTS ones) via Set-Cookie on API responses.
     * Keeping our saved copy in step with them is what stops the login from going stale. [id] is
     * the account the request was sent for, so a reply that lands after a switch can't leak one
     * account's cookies into another.
     */
    fun mergeCookies(id: String, setCookieHeaders: List<String>) {
        val current = accounts.firstOrNull { it.id == id }?.cookie ?: return
        val jar = LinkedHashMap<String, String>()
        current.split(";").map { it.trim() }.filter { it.contains('=') }.forEach {
            val i = it.indexOf('=')
            jar[it.substring(0, i)] = it.substring(i + 1)
        }
        var changed = false
        for (header in setCookieHeaders) {
            val pair = header.substringBefore(';')
            val i = pair.indexOf('=')
            if (i <= 0) continue
            val name = pair.substring(0, i).trim()
            val value = pair.substring(i + 1).trim()
            val deleted = value.isEmpty() || header.contains("Max-Age=0", true) ||
                header.contains("expires=Thu, 01 Jan 1970", true)
            if (deleted) {
                if (name != "SAPISID" && jar.remove(name) != null) changed = true
            } else if (jar[name] != value) {
                jar[name] = value
                changed = true
            }
        }
        if (changed) save(id, jar.entries.joinToString("; ") { "${it.key}=${it.value}" })
    }

    /** Signs out of the active account only; the next one (if any) becomes active. */
    fun signOutActive() {
        activeId?.let(::remove)
    }

    /**
     * Removes one account (signs it out of PlayBook). The others stay signed in; if it was the
     * active one, the next saved account takes over.
     */
    fun remove(id: String) {
        if (accounts.none { it.id == id }) return
        accounts = accounts.filter { it.id != id }
        if (activeId == id) activeId = accounts.firstOrNull()?.id
        persist()
        clearWebCookies()
        dataKeyMaybeChanged() // the removed account's saved data stays on the phone for its next sign-in
    }

    fun accountById(id: String): Account? = accounts.firstOrNull { it.id == id }

    /** Empties the sign-in WebView's cookies, so "Add account" starts at a clean Google login. */
    fun clearWebCookies() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    private fun persist() {
        val arr = JSONArray()
        accounts.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("cookie", it.cookie).put("name", it.name.orEmpty())
                    .put("email", it.email.orEmpty()).put("handle", it.handle.orEmpty()).put("photo", it.photo.orEmpty())
                    .put("dataId", it.dataId)
            )
        }
        prefs.edit().putString("accounts", arr.toString()).putString("active", activeId)
            .putString("dataByEmail", JSONObject(dataByEmail as Map<*, *>).toString()).apply()
    }

    private fun newId() = java.util.UUID.randomUUID().toString()

    private fun cookieValue(all: String, name: String): String? =
        Regex("(?:^|;\\s*)" + Regex.escape(name) + "=([^;]+)").find(all)?.groupValues?.get(1)
}
