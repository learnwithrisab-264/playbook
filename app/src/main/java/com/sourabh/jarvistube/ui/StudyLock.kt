package com.sourabh.jarvistube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.lock.MAX_LOCK_MINUTES
import com.sourabh.jarvistube.lock.StudyGuardService
import com.sourabh.jarvistube.lock.StudyLockService
import com.sourabh.jarvistube.player.NowPlaying
import kotlinx.coroutines.delay
import java.util.Locale

fun formatLeft(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

/**
 * Player top-bar control: the ⋮ settings sheet button, plus a countdown badge while locked.
 */
@Composable
fun StudyLockButton(now: NowPlaying) {
    val study = Graph.study
    if (study.lockActive) {
        var left by remember { mutableLongStateOf(study.lockRemainingMs) }
        LaunchedEffect(study.lockEndAt) {
            while (true) {
                left = study.lockRemainingMs
                delay(1000)
            }
        }
        Row(
            Modifier.padding(end = 8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Lock, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text(formatLeft(left), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
    // ⋮ opens the YouTube-style settings sheet (PlayerMoreSheetHost), also during a lock.
    IconButton(onClick = { PlayerPopups.moreSheet = true }) { Icon(Icons.Rounded.MoreVert, "More", tint = Color.White) }
}

/**
 * Menus opened from the auto-hiding player controls. While any is open the controls stay up,
 * and the lock dialog is hosted outside the controls so hiding them can't discard it.
 */
object PlayerPopups {
    var lockMenu by mutableStateOf(false)
    var lockPicker by mutableStateOf(false)
    var ccMenu by mutableStateOf(false)
    var qualityMenu by mutableStateOf(false)
    var moreSheet by mutableStateOf(false)
    val anyOpen: Boolean get() = lockMenu || lockPicker || ccMenu || qualityMenu || moreSheet
}

/** Always composed by the player screen; shows the duration picker when requested. */
@Composable
fun StudyLockDialogHost(now: NowPlaying) {
    if (!PlayerPopups.lockPicker) return
    val context = LocalContext.current
    LockDurationDialog(
        onConfirm = { minutes ->
            PlayerPopups.lockPicker = false
            Graph.study.startLock(now.video, minutes)
            StudyLockService.start(context)
        },
        onDismiss = { PlayerPopups.lockPicker = false }
    )
}

@Composable
private fun LockDurationDialog(onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var guardOn by remember { mutableStateOf(StudyGuardService.isEnabled(context)) }
    var overAppsOn by remember { mutableStateOf(StudyGuardService.canShowOverApps(context)) }
    // Coming back from Settings updates this by itself.
    LaunchedEffect(Unit) {
        while (true) {
            guardOn = StudyGuardService.isEnabled(context)
            overAppsOn = StudyGuardService.canShowOverApps(context)
            delay(1000)
        }
    }
    val ready = guardOn && overAppsOn
    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(2) }
    val total = hours * 60 + minutes
    var confirming by remember { mutableStateOf(false) }
    if (confirming) {
        ConfirmLockDialog(total, onConfirm = { onConfirm(total) }, onBack = { confirming = false })
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lock study session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Until the timer ends, only this video and the Phone app work. Other apps, Home, Recents " +
                        "Settings and Shorts are blocked. Calls and alarms still work.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!guardOn) {
                    TextButton(onClick = {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }) { Text("Step 1: turn on PlayBook Study Lock") }
                }
                if (!overAppsOn) {
                    TextButton(onClick = { context.startActivity(StudyGuardService.overlaySettings(context)) }) {
                        Text("Step 2: allow Display over other apps")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TopicChip("2 min", hours == 0 && minutes == 2) { hours = 0; minutes = 2 }
                    TopicChip("25 min", hours == 0 && minutes == 25) { hours = 0; minutes = 25 }
                    TopicChip("1 hour", hours == 1 && minutes == 0) { hours = 1; minutes = 0 }
                    TopicChip("3 hours", hours == 3 && minutes == 0) { hours = 3; minutes = 0 }
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stepper("Hours", hours, onMinus = { if (hours > 0) hours-- }, onPlus = { if (total + 60 <= MAX_LOCK_MINUTES) hours++ })
                    Stepper(
                        "Minutes", minutes,
                        onMinus = { minutes = (minutes - 1).coerceAtLeast(0) },
                        onPlus = { if (minutes < 59 && total < MAX_LOCK_MINUTES) minutes++ }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { confirming = true }, enabled = total > 0 && ready) {
                Text(if (!ready) "Finish the steps first" else if (total > 0) "Continue" else "Pick a duration")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** The last check before locking: shows the exact end time, because nothing can end it early. */
@Composable
private fun ConfirmLockDialog(minutes: Int, onConfirm: () -> Unit, onBack: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val end = java.time.Instant.ofEpochMilli(now + minutes * 60_000L).atZone(java.time.ZoneId.systemDefault())
    val endText = end.format(java.time.format.DateTimeFormatter.ofPattern("h:mm:ss a, d MMM", Locale.getDefault()))
    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("Lock until $endText?") },
        text = {
            Text(
                "Your phone stays locked for ${minutes / 60}h ${minutes % 60}m. Only the emergency exit can end it early, " +
                    "once in 24 hours and 3 times in 7 days. Phone calls and emergency calls keep working.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Lock now") } },
        dismissButton = { TextButton(onClick = onBack) { Text("Go back") } }
    )
}

@Composable
private fun Stepper(label: String, value: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onMinus) { Icon(Icons.Rounded.Remove, "Less $label") }
            Text(
                value.toString().padStart(2, '0'),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(56.dp)
            )
            FilledTonalIconButton(onClick = onPlus) { Icon(Icons.Rounded.Add, "More $label") }
        }
    }
}
