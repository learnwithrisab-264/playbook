package com.sourabh.jarvistube.data

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings

/**
 * Time that changing the phone's clock can't fake, for every lock in the app.
 *
 * Within one boot it runs on the uptime clock (unaffected by the date/time settings) from a
 * trusted starting point. That starting point comes from Google's server time whenever the
 * phone is online; after a reboot without internet it never goes back before the last time
 * seen, so setting the clock forward is corrected at the next sync and setting it back gains nothing.
 */
object TrustedClock {
    private var prefs: SharedPreferences? = null
    @Volatile private var baseWall = 0L
    @Volatile private var baseElapsed = 0L
    private var lastSavedAt = 0L
    private var lastSyncAt = SystemClock.elapsedRealtime()
    private var appContext: Context? = null

    fun init(context: Context) {
        if (prefs != null) return
        appContext = context.applicationContext
        val p = context.applicationContext.getSharedPreferences("clock", Context.MODE_PRIVATE)
        prefs = p
        val boot = bootCount(context)
        val elapsed = SystemClock.elapsedRealtime()
        val savedBoot = p.getInt("boot", -1)
        val savedElapsed = p.getLong("elapsed", -1L)
        if (savedBoot == boot && savedElapsed in 0..elapsed) {
            // Same boot: keep counting from the saved trusted point; the wall clock is ignored.
            baseWall = p.getLong("wall", System.currentTimeMillis())
            baseElapsed = savedElapsed
        } else {
            // New boot: the uptime clock restarted. Never earlier than the last trusted time.
            baseWall = maxOf(System.currentTimeMillis(), p.getLong("last", 0L))
            baseElapsed = elapsed
            save(boot)
        }
        Thread { syncWithNetwork(context.applicationContext) }.start()
    }

    /** Trusted "now" in epoch milliseconds. */
    fun now(): Long {
        if (prefs == null) return System.currentTimeMillis()
        val t = baseWall + (SystemClock.elapsedRealtime() - baseElapsed)
        val e = SystemClock.elapsedRealtime()
        if (e - lastSavedAt > 30_000) {
            lastSavedAt = e
            prefs?.edit()?.putLong("last", t)?.apply()
        }
        // Re-check against the network every 30 minutes while the app runs.
        if (e - lastSyncAt > 30 * 60_000L && appContext != null) {
            lastSyncAt = e
            Thread { appContext?.let(::syncWithNetwork) }.start()
        }
        return t
    }

    /** How far the phone's own clock is off from trusted time (positive = phone is ahead). */
    fun skewMs(): Long = System.currentTimeMillis() - now()

    /** Re-anchors on Google's server time (the Date header of a tiny request). */
    fun syncWithNetwork(context: Context) {
        runCatching {
            val req = okhttp3.Request.Builder().url("https://www.google.com/generate_204").head().build()
            val start = SystemClock.elapsedRealtime()
            com.sourabh.jarvistube.Graph.http.newCall(req).execute().use { r ->
                val date = r.headers.getDate("Date") ?: return
                val rtt = SystemClock.elapsedRealtime() - start
                baseWall = date.time + rtt / 2
                baseElapsed = SystemClock.elapsedRealtime()
                save(bootCount(context))
            }
        }
    }

    private fun save(boot: Int) {
        prefs?.edit()?.putLong("wall", baseWall)?.putLong("elapsed", baseElapsed)?.putInt("boot", boot)
            ?.putLong("last", baseWall + (SystemClock.elapsedRealtime() - baseElapsed))?.apply()
    }

    private fun bootCount(context: Context): Int =
        runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1)
}
