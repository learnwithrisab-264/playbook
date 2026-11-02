package com.sourabh.jarvistube.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.MainActivity
import java.lang.reflect.Proxy

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
            .setCallback(SeekButtons)
            // Previous, Play/Pause and Next come from the player; these add Rewind and Forward.
            .setMediaButtonPreferences(
                ImmutableList.of(
                    CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
                        .setDisplayName("Rewind 10 seconds")
                        .setSessionCommand(REWIND)
                        .setSlots(CommandButton.SLOT_OVERFLOW)
                        .build(),
                    CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_10)
                        .setDisplayName("Forward 10 seconds")
                        .setSessionCommand(FORWARD)
                        .setSlots(CommandButton.SLOT_OVERFLOW)
                        .build()
                )
            )
            .build()
            // Without this the notification waits for a controller to connect, and none does.
            .also(::addSession)
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

private val REWIND = SessionCommand("com.sourabh.jarvistube.REWIND", Bundle.EMPTY)
private val FORWARD = SessionCommand("com.sourabh.jarvistube.FORWARD", Bundle.EMPTY)

/** Rewind and Forward are custom commands: the system media card ignores any other extra button. */
@OptIn(UnstableApi::class)
private object SeekButtons : MediaSession.Callback {
    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
        MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(REWIND).add(FORWARD).build()
            )
            .build()

    override fun onCustomCommand(
        session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle
    ): ListenableFuture<SessionResult> {
        when (customCommand) {
            REWIND -> Graph.player.player.seekBack()
            FORWARD -> Graph.player.player.seekForward()
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
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
        // A proxy, because every Player.Listener method must reach the session. Most of them are
        // Java default methods, which Kotlin's `by` delegation silently skips: the session then
        // never heard about playback and froze, breaking PiP, notification and lock-screen buttons.
        val w = Proxy.newProxyInstance(
            Player.Listener::class.java.classLoader, arrayOf(Player.Listener::class.java)
        ) { proxy, method, args ->
            val a = args ?: emptyArray()
            when (method.name) {
                "equals" -> proxy === a[0]
                "hashCode" -> System.identityHashCode(proxy)
                "onAvailableCommandsChanged" -> method.invoke(listener, withExtra(a[0] as Player.Commands))
                else -> method.invoke(listener, *a)
            }
        } as Player.Listener
        wrapped[listener] = w
        super.addListener(w)
    }

    override fun removeListener(listener: Player.Listener) {
        wrapped.remove(listener)?.let { super.removeListener(it) }
    }
}
