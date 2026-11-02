package com.sourabh.jarvistube

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.sourabh.jarvistube.data.TrustedClock

/**
 * Read-only study-session status for JarvisGuard: content://com.sourabh.jarvistube.study/state
 * -> columns (active, endAt, strict, lockEndAt). Reads the saved prefs directly because providers
 * can be queried before Application.onCreate has finished.
 *
 * Times are checked against [TrustedClock], so changing the phone's clock doesn't end a lock.
 * The end times handed to JarvisGuard are converted to the phone's own clock, because that is
 * what JarvisGuard compares them with.
 */
class StudyStateProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor {
        val ctx = context!!
        TrustedClock.init(ctx)
        val prefs = ctx.getSharedPreferences("study", Context.MODE_PRIVATE)
        val now = TrustedClock.now()
        val toPhoneClock = { t: Long -> System.currentTimeMillis() + (t - now) }
        // A scheduled focus window counts as a running session even before the app opens it.
        val schedule = StudyState.scheduleWindow(prefs, now)
        val endAt = maxOf(prefs.getLong("endAt", 0L), schedule?.last ?: 0L)
        val active = endAt > now
        // lockEndAt: end of the timed video lock (0 when none). JarvisGuard keeps the user in
        // PlayBook until then, while still allowing calls, alarms and settings.
        val lockEndAt = prefs.getLong("lockEndAt", 0L).takeIf { it > now }?.let(toPhoneClock) ?: 0L
        val strict = prefs.getBoolean("strict", false) || schedule != null
        return MatrixCursor(arrayOf("active", "endAt", "strict", "lockEndAt")).apply {
            addRow(arrayOf<Any>(if (active) 1 else 0, if (active) toPhoneClock(endAt) else 0L, if (strict) 1 else 0, lockEndAt))
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.sourabh.jarvistube.study"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
