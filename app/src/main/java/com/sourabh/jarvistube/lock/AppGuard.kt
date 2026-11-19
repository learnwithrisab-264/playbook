package com.sourabh.jarvistube.lock

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.MainActivity
import com.sourabh.jarvistube.ui.theme.JarvisTubeTheme

/**
 * The Study Lock's guard, without any Accessibility service.
 *
 * Two ordinary permissions do the work instead:
 *  - Usage access tells which app is in front (Android's own app-usage log, read only during a lock).
 *  - Display over other apps lets PlayBook cover a blocked app at once with the lock screen, and
 *    bring itself back to the front from the background.
 *
 * Because no Accessibility service is on, banking and payment apps no longer complain about PlayBook.
 */
object AppGuard {
    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun canShowOverApps(context: Context) = Settings.canDrawOverlays(context)

    /** Both permissions in place: a lock can start and will hold. */
    fun ready(context: Context) = hasUsageAccess(context) && canShowOverApps(context)

    fun usageAccessSettings(context: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun overlaySettings(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens a settings page; some phones refuse the per-app link, so fall back to the full list. */
    fun open(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching { context.startActivity(Intent(intent.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

/**
 * Which app is in front, from Android's usage log. Reads only new events each time (a cursor),
 * so checking several times a second costs next to nothing.
 */
internal class ForegroundWatcher(context: Context) {
    private val usm = context.getSystemService(UsageStatsManager::class.java)
    private var cursor = 0L
    private var lastAt = 0L
    var front: String? = null
        private set

    fun poll(): String? {
        val usm = usm ?: return front
        val now = System.currentTimeMillis()
        // First look goes back far enough to know what is on screen right now; later looks
        // overlap by a second so an event logged a little late is never missed.
        val from = if (cursor == 0L) now - 6 * 60 * 60_000L else cursor - 1_000L
        val events = runCatching { usm.queryEvents(from, now) }.getOrNull() ?: return front
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED && e.timeStamp >= lastAt) {
                front = e.packageName
                lastAt = e.timeStamp
            }
        }
        cursor = now
        return front
    }
}

/**
 * Covers a blocked app instantly with the lock screen, as a window over every app. It goes away by
 * itself as soon as PlayBook (or another allowed app) is in front again.
 */
internal class BlockShield(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var root: FrameLayout? = null
    private var owner: ShieldOwner? = null

    val showing get() = root != null

    fun show() {
        if (root != null || !AppGuard.canShowOverApps(context)) return
        val owner = ShieldOwner().also { this.owner = it }
        // Back on the shield = back to the study video, never to the blocked app.
        val frame = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) backToStudy()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        frame.setViewTreeLifecycleOwner(owner)
        frame.setViewTreeSavedStateRegistryOwner(owner)
        val compose = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                JarvisTubeTheme {
                    BlockScreen(
                        onStudy = ::backToStudy,
                        onPhone = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                            hide()
                        }
                    )
                }
            }
        }
        frame.addView(compose, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Typing the emergency-exit phrase needs the keyboard to push the screen up.
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            title = "PlayBook study lock"
        }
        if (runCatching { wm.addView(frame, lp) }.isSuccess) root = frame else owner.destroy()
    }

    fun hide() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        owner?.destroy()
        owner = null
    }

    private fun backToStudy() {
        runCatching { context.startActivity(studyIntent(context)) }
        hide()
    }
}

internal fun studyIntent(context: Context) = Intent(context, MainActivity::class.java)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)

/** Lets a Compose screen live in a plain window (no Activity), for the shield. */
private class ShieldOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry get() = saved.savedStateRegistry

    init {
        saved.performAttach()
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

/**
 * The guard loop, run by [StudyLockService] while a lock is on: several times a second it checks
 * the app in front and, if it isn't allowed, covers it and brings PlayBook back.
 */
internal class LockGuard(private val context: Context) {
    private val watcher = ForegroundWatcher(context)
    private val shield = BlockShield(context)
    private var lastBounceAt = 0L
    private var lastToastAt = 0L

    fun check() {
        if (!Graph.study.lockActive) return stop()
        // With the phone locked nothing can be used anyway; the check resumes after unlocking.
        if (context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
        val front = watcher.poll() ?: return
        if (allowed(front)) {
            if (shield.showing) shield.hide()
            return
        }
        shield.show()
        bounce()
    }

    fun stop() = shield.hide()

    private fun allowed(pkg: String): Boolean =
        lockAllows(pkg, context.packageName, dialer(), keyboards()) ||
            // Payments always come first.
            isBankingApp(pkg)

    private fun bounce() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBounceAt < 700) return
        lastBounceAt = now
        // Straight back to the locked video (it reopens full screen); the emergency exit lives
        // there under the lock badge, and on the shield.
        runCatching { context.startActivity(studyIntent(context)) }
        if (now - lastToastAt > 5000) {
            lastToastAt = now
            android.widget.Toast.makeText(
                context,
                "Study lock: ${com.sourabh.jarvistube.ui.study.formatLeft(Graph.study.lockRemainingMs)} left. Back to your video.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun dialer(): String? = runCatching {
        context.getSystemService(TelecomManager::class.java).defaultDialerPackage
    }.getOrNull()

    private fun keyboards(): Set<String> = runCatching {
        context.getSystemService(InputMethodManager::class.java).enabledInputMethodList.mapTo(HashSet()) { it.packageName }
    }.getOrDefault(emptySet())
}

/** Banking and payment apps: package names, plus any app whose name says "bank" or "upi". */
private val BANKING = setOf(
    "net.one97.paytm", "com.phonepe.app", "com.google.android.apps.nbu.paisa.user", "in.org.npci.upiapp",
    "com.sbi.lotusintouch", "com.sbi.SBIFreedomPlus", "com.sbi.upi", "com.snapwork.hdfc", "com.enstage.wibmo.hdfc",
    "com.csam.icici.bank.imobile", "com.axis.mobile", "com.msf.kbank.mobile", "com.bankofbaroda.mconnect",
    "com.canarabank.mobility", "com.idfcfirstbank.optimus", "com.dreamplug.androidapp", "com.mobikwik_new",
    "com.freecharge.android", "com.whatsapp.payments", "money.super.payments", "com.naviapp", "com.fampay.in"
)

fun isBankingApp(pkg: String): Boolean {
    val p = pkg.lowercase()
    return pkg in BANKING || "bank" in p || "upi" in p
}
