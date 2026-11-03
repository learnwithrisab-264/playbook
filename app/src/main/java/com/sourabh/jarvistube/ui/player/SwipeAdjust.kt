package com.sourabh.jarvistube.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager

/** This screen's brightness for PlayBook only; the phone's own setting is never changed. */
class WindowBrightness(private val context: Context) {
    private val activity: Activity? = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>().firstOrNull()

    fun current(): Float {
        val own = activity?.window?.attributes?.screenBrightness ?: -1f
        if (own >= 0f) return own
        val system = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrDefault(128)
        return (system / 255f).coerceIn(0f, 1f)
    }

    fun set(level: Float) {
        val window = activity?.window ?: return
        window.attributes = window.attributes.also { it.screenBrightness = level.coerceIn(0.01f, 1f) }
    }

    /** Back to following the phone's brightness. */
    fun reset() {
        val window = activity?.window ?: return
        window.attributes = window.attributes.also { it.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
    }
}

/** The media volume, the same one the phone's volume buttons change while a video plays. */
class MediaVolume(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    fun current(): Float = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()

    fun set(level: Float) {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(level * max), 0)
    }
}

/** What a vertical swipe on the full-screen video changes, by where it starts. */
enum class SwipeZone { Brightness, Volume, Exit }

/** Left 40% is brightness, right 40% is volume, the middle keeps swipe-down-to-exit. */
fun swipeZoneAt(x: Float, width: Float): SwipeZone = when {
    x < width * 0.4f -> SwipeZone.Brightness
    x > width * 0.6f -> SwipeZone.Volume
    else -> SwipeZone.Exit
}

/** New level (0..1) after dragging [dy] pixels: up raises it, and 80% of the height covers the whole range. */
fun adjustLevel(level: Float, dy: Float, height: Float): Float =
    (level - dy / (height * 0.8f)).coerceIn(0f, 1f)
