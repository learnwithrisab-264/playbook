package com.sourabh.jarvistube.data

import androidx.compose.runtime.mutableStateMapOf
import com.sourabh.jarvistube.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray

/**
 * "Translate to English" for comments, through Google Translate's public web endpoint (the same
 * one translate.google.com uses; no key, no new library). Only the comment text is sent.
 * Results are kept for the session, so tapping again is instant.
 */
object Translate {
    /** Original text → English, once translated. */
    val done = mutableStateMapOf<String, String>()
    /** Texts being translated right now. */
    val busy = mutableStateMapOf<String, Boolean>()
    /** Texts that couldn't be translated (shown as a retry). */
    val failed = mutableStateMapOf<String, Boolean>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun toEnglish(text: String) {
        if (done.containsKey(text) || busy[text] == true) return
        busy[text] = true
        failed.remove(text)
        scope.launch {
            runCatching { request(text) }
                .onSuccess { done[text] = it }
                .onFailure { failed[text] = true }
            busy.remove(text)
        }
    }

    private suspend fun request(text: String): String = withContext(Dispatchers.IO) {
        val url = "https://translate.googleapis.com/translate_a/single".toHttpUrl().newBuilder()
            .addQueryParameter("client", "gtx")
            .addQueryParameter("sl", "auto")
            .addQueryParameter("tl", "en")
            .addQueryParameter("dt", "t")
            .addQueryParameter("q", text)
            .build()
        val body = Graph.http.newCall(Request.Builder().url(url).build()).execute().use { res ->
            if (!res.isSuccessful) throw java.io.IOException("Translate: HTTP ${res.code}")
            res.body!!.string()
        }
        val parts = JSONArray(body).getJSONArray(0)
        (0 until parts.length()).joinToString("") { parts.getJSONArray(it).optString(0) }.trim()
    }

    private val english = setOf(
        "the", "a", "an", "is", "are", "was", "were", "i", "you", "he", "she", "it", "we", "they", "this", "that",
        "to", "of", "and", "in", "on", "for", "with", "my", "your", "so", "but", "not", "be", "have", "has", "just",
        "like", "what", "how", "why", "can", "do", "does", "me", "all", "no", "yes", "love", "video", "great", "good",
        "very", "best", "thanks", "thank", "its", "it's", "i'm", "dont", "don't", "if", "at", "or", "as", "from",
        "about", "who", "when", "more", "one", "will", "would", "there", "their", "his", "her", "our", "out", "up"
    )

    /**
     * True when a comment is probably not English: mostly non-Latin letters (Hindi, Arabic, …) or
     * Latin words with almost no common English words (Hinglish, Spanish, …). Short ones are left alone.
     */
    fun looksNonEnglish(text: String): Boolean {
        // Links, @handles and #tags say nothing about the language.
        val clean = text.replace(Regex("""https?://\S+|www\.\S+|[@#]\S+"""), " ")
        val letters = clean.filter(Char::isLetter)
        if (letters.length < 4) return false
        val latin = letters.count { it in 'a'..'z' || it in 'A'..'Z' }
        if (latin.toFloat() / letters.length < 0.7f) return true
        val words = clean.lowercase().split(Regex("[^a-zà-ÿ']+")).filter { it.isNotEmpty() }
        if (words.size < 3) return false
        val foreign = words.count { it in otherLanguages }
        val common = words.count { it in english }
        // Clear signs of Hinglish / Spanish / French / German, or a long text with no English in it.
        return foreign.toFloat() / words.size >= 0.15f || (words.size >= 8 && common == 0)
    }

    /** Frequent words of Hinglish and other Latin-script languages that English never uses. */
    private val otherLanguages = setOf(
        // Hinglish
        "hai", "hain", "ho", "kya", "nahi", "nhi", "bhai", "bhi", "aur", "ka", "ki", "ke", "ko", "se", "mein", "me",
        "ye", "yeh", "wo", "woh", "toh", "to", "hum", "tum", "aap", "bahut", "bohot", "accha", "acha", "kar", "karo",
        "karna", "raha", "rahe", "rahi", "tha", "thi", "the", "hoga", "sab", "kuch", "koi", "apna", "mera", "tera",
        "kaise", "kyu", "kyun", "matlab", "yaar", "sahi", "ekdum", "liye", "wala", "wali", "jaise", "abhi", "bas",
        // Spanish / Portuguese
        "que", "el", "los", "las", "por", "para", "muy", "pero", "esto", "como", "mas", "também", "muito", "não",
        // French / German
        "les", "des", "une", "est", "avec", "pour", "pas", "und", "der", "die", "das", "nicht", "ist", "ich", "sehr"
    ) - setOf("the", "to", "me")
}
