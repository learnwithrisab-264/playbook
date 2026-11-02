package com.sourabh.jarvistube.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * The 🔔 on channel pages: about once an hour (when online) PlayBook checks each belled
 * channel's newest upload and posts a notification for anything new. Tapping it plays the video.
 */
object UploadAlerts {
    private const val WORK = "upload-alerts"
    const val CHANNEL_ID = "uploads"
    private lateinit var prefs: SharedPreferences
    private lateinit var appCtx: Context
    val channels = mutableStateListOf<ChannelRef>()

    fun init(context: Context) {
        appCtx = context.applicationContext
        prefs = appCtx.getSharedPreferences("alerts", Context.MODE_PRIVATE)
        // The old shared bells go to the account in use; after that each account has its own.
        prefs.getString("channels", null)?.let { shared ->
            prefs.edit().putString("channels:" + Session.dataKey, shared).remove("channels").apply()
        }
        load(Session.dataKey)
        Session.onDataKeyChange { load(it); schedule() }
        appCtx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "New uploads", NotificationManager.IMPORTANCE_DEFAULT)
        )
        schedule()
    }

    private var storeKey = "channels"

    private fun load(key: String) {
        storeKey = "channels:$key"
        channels.clear()
        runCatching {
            val arr = JSONArray(prefs.getString(storeKey, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                channels += ChannelRef(o.getString("url"), o.getString("name"), o.optString("avatar").ifEmpty { null })
            }
        }
    }

    fun isOn(url: String) = channels.any { it.url == url }

    fun toggle(ref: ChannelRef) {
        if (isOn(ref.url)) channels.removeAll { it.url == ref.url } else channels += ref
        prefs.edit().putString(storeKey, JSONArray().apply {
            channels.forEach { put(JSONObject().put("url", it.url).put("name", it.name).put("avatar", it.avatar.orEmpty())) }
        }.toString()).apply()
        schedule()
    }

    private fun schedule() {
        val wm = WorkManager.getInstance(appCtx)
        if (channels.isEmpty()) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val req = PeriodicWorkRequestBuilder<UploadCheckWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** One pass over every belled channel; the first pass only remembers what is there. */
    suspend fun check(context: Context) {
        for (c in channels.toList()) {
            val newest = runCatching { ChannelRepo.latestVideos(c.url) }.getOrNull()?.firstOrNull() ?: continue
            val key = "seen:" + c.url
            val seen = prefs.getString(key, null)
            prefs.edit().putString(key, newest.url).apply()
            if (seen != null && seen != newest.url) notify(context, c, newest)
        }
    }

    private fun notify(context: Context, c: ChannelRef, v: VideoItem) {
        val open = Intent(context, com.sourabh.jarvistube.MainActivity::class.java)
            .putExtra("play_url", v.url)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            context, v.url.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.sourabh.jarvistube.R.drawable.ic_osmium_mono)
            .setContentTitle("New from ${c.name}")
            .setContentText(v.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(v.title))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching {
            context.getSystemService(NotificationManager::class.java)?.notify(v.url.hashCode(), n)
        }
    }
}

class UploadCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        UploadAlerts.check(applicationContext)
        return Result.success()
    }
}
