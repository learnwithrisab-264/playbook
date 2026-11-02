package com.sourabh.jarvistube.lock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.MainActivity

private const val CHANNEL = "study_lock"
private const val NOTIFICATION = 2001

/** Keeps a session alive when you leave the app, shows the time left, and ends it on time. */
class StudyLockService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var seconds = 0
    private val tick = object : Runnable {
        override fun run() {
            Graph.study.tick()
            if (!Graph.study.lockActive) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
            StudyGuardService.instance?.check()
            // Keeps "X min left" current, and brings the notification back if it was swiped away.
            if (++seconds % 15 == 0) {
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification())
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        running = true
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Lock Study Session", NotificationManager.IMPORTANCE_LOW)
        )
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION, notification())
        }
        handler.removeCallbacks(tick)
        tick.run()
        return START_STICKY
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Study session locked, ${minutesLeftText(Graph.study.lockRemainingMs)}")
            .setContentText("Only PlayBook and Phone work until the timer ends")
            .setWhen(System.currentTimeMillis() + Graph.study.lockRemainingMs)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    companion object {
        /** Whether the service is alive; the guard restarts it if the system stopped it. */
        var running = false
            private set

        fun start(context: Context) {
            // Refused only when the app is in the background; the guard or next launch starts it then.
            runCatching { context.startForegroundService(Intent(context, StudyLockService::class.java)) }
        }
    }
}

/** A session still running after a restart carries on. */
class LockBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Graph.study.lockActive) StudyLockService.start(context)
    }
}

/** For development only. Protected by DUMP, so only adb on a computer can send it: nothing on the phone can. */
class LockEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Graph.study.endLockEarly()
}
