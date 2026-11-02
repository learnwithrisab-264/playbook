package com.sourabh.jarvistube.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import kotlinx.coroutines.delay

/** Compose-observable snapshot of the player (position polled, everything else event-driven). */
@Stable
class PlayerUi(private val player: Player) {
    var isPlaying by mutableStateOf(player.isPlaying)
    var state by mutableIntStateOf(player.playbackState)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var buffered by mutableLongStateOf(0L)
    var speed by mutableFloatStateOf(player.playbackParameters.speed)
    var loop by mutableStateOf(player.repeatMode == Player.REPEAT_MODE_ONE)
    /** Caption lines on screen now; drawn by CaptionOverlay (PlayerView's own SubtitleView is hidden). */
    var cues by mutableStateOf<List<Cue>>(emptyList())

    fun refresh() {
        position = player.currentPosition
        duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        buffered = player.bufferedPosition
    }

    val isBuffering: Boolean get() = state == Player.STATE_BUFFERING

    fun toggleLoop() {
        loop = !loop
        player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }
}

@Composable
fun rememberPlayerUi(player: Player): PlayerUi {
    val ui = remember(player) { PlayerUi(player) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                ui.isPlaying = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                ui.state = playbackState
                ui.refresh()
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                ui.speed = playbackParameters.speed
            }

            override fun onCues(cueGroup: CueGroup) {
                ui.cues = cueGroup.cues
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            ui.refresh()
            delay(250)
        }
    }
    return ui
}
