package com.sourabh.jarvistube.ui

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Picks the end of a multi-day Study mode lock: quick day presets counted from [base] (now, or
 * the current end when extending), or an exact date and time.
 */
@Composable
internal fun LongLockDialog(
    base: Long,
    title: String,
    confirmLabel: String,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var until by remember { mutableLongStateOf(base + 86_400_000L) }
    var days by remember { mutableIntStateOf(1) }
    val valid = until > base + 60_000L && until > com.sourabh.jarvistube.data.TrustedClock.now() + 60_000L

    fun pickExact() {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = until }
        android.app.DatePickerDialog(context, { _, y, m, d ->
            android.app.TimePickerDialog(context, { _, h, min ->
                cal.set(y, m, d, h, min, 0)
                until = cal.timeInMillis
                days = 0
            }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), false).show()
        }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH))
            .apply { datePicker.minDate = maxOf(base, System.currentTimeMillis()) }
            .show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Lock, null, tint = JarvisCyan) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(1, 3, 7, 14, 30)) { d ->
                        TopicChip(if (d == 1) "1 day" else "$d days", days == d) {
                            days = d
                            until = base + d * 86_400_000L
                        }
                    }
                }
                OutlinedButton(onClick = { pickExact() }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (days == 0) "Until ${lockDate(until)}" else "Pick exact date & time")
                }
                Text(
                    if (valid) "Locked until ${lockDate(until)}. It can't be ended early." else "Choose a time later than now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (valid) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(until) }, enabled = valid,
                colors = ButtonDefaults.buttonColors(containerColor = JarvisViolet, contentColor = Color.White)
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Scroll wheel of 1–365 days, counted from [from] (now, or the current end when extending). */
@Composable
internal fun DaysWheelDialog(title: String, from: Long, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var days by remember { mutableIntStateOf(7) }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Lock, null, tint = JarvisCyan) },
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            android.widget.NumberPicker(ctx).apply {
                                minValue = 1
                                maxValue = 365
                                value = days
                                wrapSelectorWheel = false
                                setTextColor(textColor)
                                textSize = 64f
                                descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                                setOnValueChangedListener { _, _, v -> days = v }
                            }
                        },
                        modifier = Modifier.width(96.dp)
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(if (days == 1) "day" else "days", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Music & entertainment stay blocked until ${lockDate(from + days * 86_400_000L)}. " +
                        "The switch can't be turned off before then.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(days) },
                colors = ButtonDefaults.buttonColors(containerColor = JarvisViolet, contentColor = Color.White)
            ) { Text("Lock") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Remaining time: "3d 04h", "5:07:09" or "07:09". */
internal fun remainingText(sec: Long): String = when {
    sec >= 86_400 -> "%dd %02dh".format(sec / 86_400, sec % 86_400 / 3600)
    sec >= 3600 -> "%d:%02d:%02d".format(sec / 3600, sec % 3600 / 60, sec % 60)
    else -> "%02d:%02d".format(sec / 60, sec % 60)
}

internal fun lockDate(ms: Long): String =
    java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(ms))
