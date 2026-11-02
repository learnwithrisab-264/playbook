package com.sourabh.jarvistube.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.MainActivity

/**
 * Publishes the app-wide player as a media session: the media notification (artwork, title,
 * channel, previous / play-pause / next, seek bar), the lock screen, the quick-settings media
 * panel, and Bluetooth / earphone buttons. A foreground service while playing, so playback keeps
 * going with the screen off or in other apps.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, SessionPlayer(Graph.player))
            .setSessionActivity(open) // tapping the notification opens the app
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away from recents keeps a playing song going (like YouTube Music);
        // with nothing playing the service ends.
        val p = Graph.player.player
        if (!p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}

/**
 * The player as the media session sees it. ExoPlayer only ever holds the one item playing, so it
 * reports no "next"; this wrapper always offers Previous and Next and routes them to the app's
 * own queue logic (PlayerController.skipNext / skipPrevious), which applies the study rules.
 */
@OptIn(UnstableApi::class)
private class SessionPlayer(private val controller: PlayerController) : ForwardingPlayer(controller.player) {
    private val extra = Player.Commands.Builder()
        .addAll(
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        ).build()

    private fun withExtra(c: Player.Commands): Player.Commands =
        c.buildUpon().apply { for (i in 0 until extra.size()) add(extra.get(i)) }.build()

    override fun getAvailableCommands(): Player.Commands = withExtra(super.getAvailableCommands())
    override fun isCommandAvailable(command: Int): Boolean = extra.contains(command) || super.isCommandAvailable(command)
    override fun hasNextMediaItem(): Boolean = true
    override fun hasPreviousMediaItem(): Boolean = true

    override fun seekToNext() = controller.skipNext()
    override fun seekToNextMediaItem() = controller.skipNext()
    override fun seekToPrevious() = controller.skipPrevious()
    override fun seekToPreviousMediaItem() = controller.skipPrevious()

    // Listeners get the same widened command set, so the notification keeps its buttons.
    private val wrapped = HashMap<Player.Listener, Player.Listener>()

    override fun addListener(listener: Player.Listener) {
        val w = object : Player.Listener by listener {
            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) =
                listener.onAvailableCommandsChanged(withExtra(availableCommands))
        }
        wrapped[listener] = w
        super.addListener(w)
    }

    override fun removeListener(listener: Player.Listener) {
        wrapped.remove(listener)?.let { super.removeListener(it) }
    }
}
