package com.sourabh.jarvistube.data

import com.sourabh.jarvistube.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest

class SessionExpiredException : IOException("Session expired")

data class AccountProfile(val name: String?, val email: String?, val handle: String?, val photo: String?)

/** One of the user's playlists as offered by "Save to playlist", with whether it holds the video. */
data class PlaylistChoice(val id: String, val title: String, val contains: Boolean)

/** Minimal authenticated client for YouTube's internal (InnerTube) web API. */
object InnerTube {
    private const val ORIGIN = "https://www.youtube.com"
    private const val CLIENT_VERSION = "2.20261001.01.00"

    suspend fun browse(
        browseId: String? = null,
        params: String? = null,
        continuation: String? = null
    ): JSONObject {
        val body = JSONObject()
        browseId?.let { body.put("browseId", it) }
        params?.let { body.put("params", it) }
        continuation?.let { body.put("continuation", it) }
        return post("browse", body)
    }

    /**
     * One page of YouTube's search with its Shorts filter on. Needs no sign-in and sends no cookies,
     * so it works signed out and doesn't touch any account's history. [filter] is YouTube's own
     * code for "Type: Shorts", with or without a sort order.
     */
    suspend fun shortsSearch(query: String?, continuation: String?, filter: String): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject().put(
            "context",
            JSONObject().put(
                "client",
                JSONObject().put("clientName", "WEB").put("clientVersion", CLIENT_VERSION).put("hl", "en")
                    .put("gl", com.sourabh.jarvistube.AppSettings.region)
            )
        )
        if (continuation != null) body.put("continuation", continuation)
        else body.put("query", query).put("params", filter)
        val request = Request.Builder()
            .url("$ORIGIN/youtubei/v1/search?prettyPrint=false")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("User-Agent", WEB_UA)
            .build()
        Graph.http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            JSONObject(r.body?.string() ?: throw IOException("Empty response"))
        }
    }

    private val AD_MARKS = listOf("adSlot", "adClientParams", "adPlacement", "AD_BADGE", "promotedSparkles", "adBadge", "\"isAd\":true")

    /**
     * The start of the signed-in account's own Shorts feed (what the YouTube app's Shorts tab
     * plays): a token for [reelSequence]. "CA8=" with a seedless start asks for "Shorts for you".
     */
    suspend fun reelSeed(): String? {
        val json = post(
            "reel/reel_item_watch",
            JSONObject().put("params", "CA8=").put("inputType", "REEL_WATCH_INPUT_TYPE_SEEDLESS")
                .put("disablePlayerResponse", true)
        )
        return json.optString("sequenceContinuation").ifEmpty { null }
    }

    /** One page of the account's Shorts feed: video ids, and the token for the page after. */
    suspend fun reelSequence(params: String): Pair<List<String>, String?> {
        val json = post("reel/reel_watch_sequence", JSONObject().put("sequenceParams", params))
        val ids = ArrayList<String>()
        val entries = json.optJSONArray("entries") ?: JSONArray()
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            // Promoted Shorts (ads) are left out.
            val raw = entry.toString()
            if (AD_MARKS.any { raw.contains(it) }) continue
            forEach(entry, "reelWatchEndpoint") { e ->
                e.optString("videoId").takeIf { it.isNotEmpty() }?.let(ids::add)
            }
        }
        val next = find(json.optJSONObject("continuationEndpoint"), "continuationCommand")?.optString("token")?.ifEmpty { null }
        return ids.distinct() to next
    }

    // ---- account ----

    /** Name, email, @handle and photo of saved account [account] (the active one by default). */
    suspend fun accountProfile(account: Account? = Session.active): AccountProfile {
        val json = post("account/account_menu", JSONObject(), account)
        val h = find(json, "activeAccountHeaderRenderer") ?: return AccountProfile(null, null, null, null)
        val photos = h.optJSONObject("accountPhoto")?.optJSONArray("thumbnails")
        // The menu often leaves the email out; the account list carries it (each of our logins
        // holds one Google account, so the first email-shaped string is this account's address).
        val email = text(h.optJSONObject("email")) ?: runCatching {
            val list = post("account/accounts_list", JSONObject(), account).toString()
            Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").find(list)?.value
        }.getOrNull()
        return AccountProfile(
            name = text(h.optJSONObject("accountName")),
            email = email,
            handle = text(h.optJSONObject("channelHandle")),
            photo = photos?.optJSONObject(photos.length() - 1)?.optString("url")?.takeIf { it.isNotEmpty() }
        )
    }

    // ---- subscriptions ----

    suspend fun subscribe(channelId: String) {
        post("subscription/subscribe", JSONObject().put("channelIds", JSONArray().put(channelId)))
    }

    suspend fun unsubscribe(channelId: String) {
        post("subscription/unsubscribe", JSONObject().put("channelIds", JSONArray().put(channelId)))
    }

    /** Whether the signed-in account follows [channelId], read from the channel page itself. */
    suspend fun isSubscribed(channelId: String): Boolean {
        val json = browse(channelId)
        find(json, "subscriptionStateEntity")?.let { return it.optBoolean("subscribed") }
        find(json, "subscribeButtonRenderer")?.let { return it.optBoolean("subscribed") }
        return false
    }

    // ---- history ----

    suspend fun removeFromHistory(feedbackToken: String) {
        post(
            "feedback",
            JSONObject().put("feedbackTokens", JSONArray().put(feedbackToken)).put("isFeedbackTokenUnencrypted", false)
        )
    }

    // ---- playlists ----

    suspend fun playlistsFor(videoId: String): List<PlaylistChoice> {
        val json = post("playlist/get_add_to_playlist", JSONObject().put("videoIds", JSONArray().put(videoId)))
        val out = ArrayList<PlaylistChoice>()
        forEach(json, "playlistAddToOptionRenderer") { o ->
            val id = o.optString("playlistId").ifEmpty { return@forEach }
            out += PlaylistChoice(id, text(o.optJSONObject("title")).orEmpty(), o.optString("containsSelectedVideos") == "ALL")
        }
        return out
    }

    suspend fun addToPlaylist(playlistId: String, videoId: String) = editPlaylist(
        playlistId, JSONObject().put("addedVideoId", videoId).put("action", "ACTION_ADD_VIDEO")
    )

    suspend fun removeFromPlaylist(playlistId: String, videoId: String, setVideoId: String?) = editPlaylist(
        playlistId,
        JSONObject().put("removedVideoId", videoId).put("action", "ACTION_REMOVE_VIDEO")
            .also { a -> setVideoId?.let { a.put("setVideoId", it) } }
    )

    suspend fun createPlaylist(title: String, videoId: String?) {
        val body = JSONObject().put("title", title).put("privacyStatus", "PRIVATE")
        videoId?.let { body.put("videoIds", JSONArray().put(it)) }
        post("playlist/create", body)
    }

    suspend fun deletePlaylist(playlistId: String) {
        post("playlist/delete", JSONObject().put("playlistId", playlistId))
    }

    private suspend fun editPlaylist(playlistId: String, action: JSONObject) {
        post("browse/edit_playlist", JSONObject().put("playlistId", playlistId).put("actions", JSONArray().put(action)))
    }

    // ---- transport ----

    /**
     * Sends one request as [account] (captured when the call starts, so switching accounts
     * mid-request never mixes two accounts' cookies).
     */
    private suspend fun post(
        endpoint: String,
        body: JSONObject,
        account: Account? = Session.active
    ): JSONObject = withContext(Dispatchers.IO) {
        val cookie = account?.cookie
        val sapisid = cookie?.let(Session::sapisidOf)
        if (account == null || cookie == null || sapisid == null) throw SessionExpiredException()

        val client = JSONObject()
            .put("clientName", "WEB")
            .put("clientVersion", CLIENT_VERSION)
            .put("hl", "en")
            .put("gl", com.sourabh.jarvistube.AppSettings.region)
        body.put("context", JSONObject().put("client", client))

        val ts = System.currentTimeMillis() / 1000
        val request = Request.Builder()
            .url("$ORIGIN/youtubei/v1/$endpoint?prettyPrint=false")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("Cookie", cookie)
            .header("Authorization", "SAPISIDHASH ${ts}_${sha1("$ts $sapisid $ORIGIN")}")
            .header("Origin", ORIGIN)
            .header("X-Origin", ORIGIN)
            .header("X-Goog-AuthUser", "0")
            .header("X-Youtube-Client-Name", "1")
            .header("X-Youtube-Client-Version", CLIENT_VERSION)
            .header("User-Agent", WEB_UA)
            .build()

        Graph.http.newCall(request).execute().use { r ->
            if (r.code == 401 || r.code == 403) throw SessionExpiredException()
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            Session.mergeCookies(account.id, r.headers("Set-Cookie"))
            JSONObject(r.body?.string() ?: throw IOException("Empty response"))
        }
    }

    // ---- JSON helpers ----

    private fun text(o: JSONObject?): String? {
        if (o == null) return null
        o.optString("simpleText").takeIf { it.isNotEmpty() }?.let { return it }
        o.optString("content").takeIf { it.isNotEmpty() }?.let { return it }
        val runs = o.optJSONArray("runs") ?: return null
        return (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }
            .ifEmpty { null }
    }

    /** First object stored under [key] anywhere in the tree. */
    private fun find(node: Any?, key: String): JSONObject? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject(key)?.let { return it }
                val keys = node.keys()
                while (keys.hasNext()) find(node.opt(keys.next()), key)?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) find(node.opt(i), key)?.let { return it }
        }
        return null
    }

    private fun forEach(node: Any?, key: String, block: (JSONObject) -> Unit) {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = node.opt(k)
                    if (k == key && v is JSONObject) block(v) else forEach(v, key, block)
                }
            }
            is JSONArray -> for (i in 0 until node.length()) forEach(node.opt(i), key, block)
        }
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
