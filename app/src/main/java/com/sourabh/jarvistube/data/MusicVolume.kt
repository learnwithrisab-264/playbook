package com.sourabh.jarvistube.data

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.LoudnessEnhancer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.player.NowPlaying
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.pow

/**
 * "Even volume" for the Music tab (like Spotify's Normalize volume): YouTube measures how loud
 * each song is, so loud songs are turned down and quiet ones lifted a little, and songs play at
 * about the same level. Only in music mode; videos always play as they are.
 */
object MusicVolume {
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var enhancer: LoudnessEnhancer? = null
    /** Each song's loudness, looked up once. */
    private val cache = HashMap<String, Double>()

    var enabled by mutableStateOf(true); private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("music", Context.MODE_PRIVATE)
        enabled = prefs.getBoolean("evenVolume", true)
        scope.launch { Graph.player.now.collect { apply(it) } }
    }

    fun update(on: Boolean) {
        enabled = on
        prefs.edit().putBoolean("evenVolume", on).apply()
        scope.launch { apply(Graph.player.now.value) }
    }

    private suspend fun apply(n: NowPlaying?) {
        if (n == null || !Graph.player.musicMode || !enabled) return reset()
        val id = SponsorBlock.videoId(n.video.url) ?: return reset()
        val db = (cache[id] ?: MusicForYou.loudnessDb(id)?.also { cache[id] = it }) ?: return reset()
        if (Graph.player.now.value?.video?.url != n.video.url || !Graph.player.musicMode) return
        // Loud songs down by as much as they're over; quiet ones up by at most 6 dB.
        val gain = (-db).coerceIn(-12.0, 6.0)
        val player = Graph.player.player
        if (gain <= 0) {
            enhancer?.enabled = false
            player.volume = 10.0.pow(gain / 20).toFloat()
        } else {
            player.volume = 1f
            runCatching {
                val e = enhancer ?: LoudnessEnhancer(player.audioSessionId).also { enhancer = it }
                e.setTargetGain((gain * 100).toInt())
                e.enabled = true
            }
        }
    }

    private fun reset() {
        Graph.player.player.volume = 1f
        enhancer?.enabled = false
    }
}
