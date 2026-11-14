package com.sourabh.jarvistube.lock

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.sourabh.jarvistube.Graph

/**
 * Notices which app comes to the front while a Lock Study Session runs, and answers anything not
 * allowed with the lock screen. Outside a session it does nothing and reads nothing.
 */
class StudyGuardService : AccessibilityService() {
    private var lastBlockAt = 0L
    private var lastToastAt = 0L

    override fun onServiceConnected() {
        instance = this
        if (Graph.study.lockActive) StudyLockService.start(this)
    }

    /**
     * Runs every second during a session, because the events below only report changes: an app
     * already on screen when the guard (re)starts after a crash would otherwise stay there.
     */
    fun check() {
        val pkg = rootInActiveWindow?.packageName?.toString() ?: return
        if (Graph.study.lockActive && pkg != "com.android.systemui" && !(isBankingApp(pkg) && BankPause.canPause(this))) blockUnlessAllowed(pkg)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val study = Graph.study
        study.tick()
        val opened = event.packageName?.toString()
        if (!study.lockActive) {
            // Banking apps object to any accessibility service: step aside until you leave them.
            if (opened != null && isBankingApp(opened)) {
                if (BankPause.canPause(this)) BankPause.pause(this, opened) else BankPause.stepAside(this)
            }
            return
        }
        // During a lock, payments still work, but only when the guard is sure to switch back on.
        if (opened != null && isBankingApp(opened) && BankPause.canPause(this)) {
            BankPause.pause(this, opened)
            return
        }
        if (!StudyLockService.running) StudyLockService.start(this)
        val pkg = event.packageName?.toString() ?: return
        if (pkg == "com.android.systemui") return closeShade()
        // Small floating windows also send this event: only act on what really fills the screen.
        if (rootInActiveWindow?.packageName?.toString() == pkg) blockUnlessAllowed(pkg)
    }

    private fun blockUnlessAllowed(pkg: String) {
        if (lockAllows(pkg, packageName, dialer(), keyboards())) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockAt < 500) return
        lastBlockAt = now
        // Straight back to the locked video in PlayBook (it reopens full screen); the emergency
        // exit lives on the video page, under the lock badge.
        startActivity(
            android.content.Intent(this, com.sourabh.jarvistube.MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
        if (now - lastToastAt > 5000) {
            lastToastAt = now
            android.widget.Toast.makeText(
                this, "Study lock: ${com.sourabh.jarvistube.ui.study.formatLeft(Graph.study.lockRemainingMs)} left. Back to your video.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun closeShade() {
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        if (!locked && Build.VERSION.SDK_INT >= 31) performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
    }

    private fun dialer(): String? = getSystemService(TelecomManager::class.java).defaultDialerPackage

    private fun keyboards(): Set<String> =
        getSystemService(InputMethodManager::class.java).enabledInputMethodList.mapTo(HashSet()) { it.packageName }

    override fun onInterrupt() = Unit

    companion object {
        var instance: StudyGuardService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, StudyGuardService::class.java).flattenToString()
            val on = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            return on.orEmpty().split(':').any { it.equals(me, ignoreCase = true) }
        }

        fun canShowOverApps(context: Context) = Settings.canDrawOverlays(context)

        fun overlaySettings(context: Context) = android.content.Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName)
        )
    }
}
