package com.sourabh.jarvistube.lock

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.provider.Settings
import com.sourabh.jarvistube.Graph

/** Banking and payment apps: package names, plus any app whose name says "bank" or "upi". */
private val BANKING = setOf(
    "net.one97.paytm", "com.phonepe.app", "com.google.android.apps.nbu.paisa.user", "in.org.npci.upiapp",
    "com.sbi.lotusintouch", "com.sbi.SBIFreedomPlus", "com.sbi.upi", "com.snapwork.hdfc", "com.enstage.wibmo.hdfc",
    "com.csam.icici.bank.imobile", "com.axis.mobile", "com.msf.kbank.mobile", "com.bankofbaroda.mconnect",
    "com.canarabank.mobility", "com.idfcfirstbank.optimus", "com.dreamplug.androidapp", "com.mobikwik_new",
    "com.freecharge.android", "com.whatsapp.payments", "money.super.payments", "com.naviapp", "com.fampay.in"
)

/** Popups that can show over a banking app (OTP, permissions, system bars): they change nothing. */
private val NEUTRAL = setOf(
    "android", "com.android.systemui", "com.google.android.gms", "com.google.android.permissioncontroller",
    "com.android.permissioncontroller", "com.google.android.inputmethod.latin"
)

fun isBankingApp(pkg: String): Boolean {
    val p = pkg.lowercase()
    return pkg in BANKING || "bank" in p || "upi" in p
}

/**
 * Banking apps refuse to work, or warn about a "suspicious app", while any accessibility service is
 * on. So when one opens (and no Study Lock is running; during a lock banking apps are blocked
 * anyway) the guard switches itself off, and [BankPauseService] switches it back on as soon as
 * another app comes to the front. Only works after these two adb grants:
 *   adb shell pm grant <package> android.permission.WRITE_SECURE_SETTINGS
 *   adb shell appops set <package> GET_USAGE_STATS allow
 * Without them nothing is paused, so the guard can never be left switched off.
 */
object BankPause {
    private const val PREFS = "bank_pause"
    private const val KEY = "paused"

    fun canPause(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED &&
            usageAccess(context)

    private fun usageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isPaused(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    private fun setPaused(context: Context, on: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).commit()

    /** Called by the guard when a banking app comes to the front. */
    fun pause(service: AccessibilityService, bankPkg: String) {
        setPaused(service, true)
        BankPauseService.start(service, bankPkg)
        service.disableSelf()
    }

    /** Switches the guard back on (no-op unless it was paused for banking). */
    fun resume(context: Context) {
        if (!isPaused(context)) return
        runCatching {
            val cr = context.contentResolver
            val me = ComponentName(context, StudyGuardService::class.java).flattenToString()
            val on = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            if (on.split(':').none { it.equals(me, ignoreCase = true) }) {
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, if (on.isBlank()) me else "$on:$me")
            }
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        }.onSuccess { setPaused(context, false) }
    }

    /** After a restart or the app being killed mid-pause: carry on watching, so the guard comes back. */
    fun recover(context: Context) {
        if (isPaused(context)) BankPauseService.start(context, null)
    }
}

/** Watches which app is in front while the guard is paused, and switches it back on when banking ends. */
class BankPauseService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var front: String? = null

    private val tick = object : Runnable {
        override fun run() {
            front = latestForeground() ?: front
            val f = front
            val done = !BankPause.isPaused(this@BankPauseService) || Graph.study.lockActive ||
                (f != null && !isBankingApp(f) && f !in NEUTRAL)
            if (done) {
                BankPause.resume(this@BankPauseService)
                stopSelf()
            } else handler.postDelayed(this, 1000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_BANK)?.let { front = it }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Study guard paused for banking", NotificationManager.IMPORTANCE_MIN)
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Study guard paused")
            .setContentText("Paused while a banking app is open. It turns back on when you leave it.")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIFICATION, n)
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 1000)
        return START_STICKY
    }

    /** The app most recently brought to the front in the last minute, or null if none moved. */
    private fun latestForeground(): String? {
        val usm = getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 60_000, now)
        val e = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) last = e.packageName
        }
        return last
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "bank_pause"
        private const val NOTIFICATION = 4711
        private const val EXTRA_BANK = "bank"

        fun start(context: Context, bankPkg: String?) {
            runCatching {
                context.startForegroundService(Intent(context, BankPauseService::class.java).putExtra(EXTRA_BANK, bankPkg))
            }
        }
    }
}
