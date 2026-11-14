package com.sourabh.jarvistube.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.sourabh.jarvistube.AppSettings

/**
 * The home-screen icon follows the theme: one launcher alias per theme in the manifest
 * (".IconAgni", ".IconVrindavan", …), exactly one enabled. Applied when PlayBook goes to the
 * background, because switching launcher entries while the app is open can close it.
 */
object AppIcon {
    private fun alias(context: Context, theme: EpicTheme) =
        ComponentName(context.packageName, "com.sourabh.jarvistube.Icon" + theme.title)

    fun apply(context: Context) {
        val want = if (AppSettings.iconFollowsTheme) EpicTheme.of(AppSettings.themeKey) else EpicTheme.Agni
        val pm = context.packageManager
        runCatching {
            // Enable the new one first, so there is never a moment without a launcher entry.
            EpicTheme.entries.sortedBy { it != want }.forEach { t ->
                val c = alias(context, t)
                val target = if (t == want) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                val now = pm.getComponentEnabledSetting(c)
                val isOn = now == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                    (now == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && t == EpicTheme.Agni)
                val wantOn = target == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                if (isOn != wantOn) pm.setComponentEnabledSetting(c, target, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
