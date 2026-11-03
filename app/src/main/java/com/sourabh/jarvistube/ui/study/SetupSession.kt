package com.sourabh.jarvistube.ui.study

import com.sourabh.jarvistube.R
import androidx.compose.ui.res.stringResource
import com.sourabh.jarvistube.ui.common.Avatar
import com.sourabh.jarvistube.ui.common.TopicChip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import com.sourabh.jarvistube.data.StudyChannels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.LibTab
import com.sourabh.jarvistube.LibraryViewModel
import com.sourabh.jarvistube.data.LibChannel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetupSession(lib: LibraryViewModel, bottomPadding: Dp) {
    val study = Graph.study
    val context = androidx.compose.ui.platform.LocalContext.current
    var newChannel by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }
    var lockDialog by remember { mutableStateOf(false) }
    var daysDialog by remember { mutableStateOf(false) }
    var filterLockDialog by remember { mutableStateOf(false) }
    val canStart = study.allChannelNames.isNotEmpty()
    fun needChannels() =
        android.widget.Toast.makeText(context, "Add a channel below first.", android.widget.Toast.LENGTH_SHORT).show()

    // Status-bar padding on the list (not the header) so scrolled cards never slide under the
    // clock; imePadding keeps the channel field above the keyboard (Android 15 edge-to-edge).
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().imePadding(),
        contentPadding = PaddingValues(bottom = bottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp)) {
                Text(stringResource(R.string.study), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Lock yourself in. Only your channels play until the timer ends.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. Focus: pick a length, press Start. Always locked: no way to stop early.
        item {
            SectionCard("Focus session") {
                Text(
                    "How long do you want to focus?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(25, 45, 60, 90, 120, 180)) { m ->
                        val label = if (m < 60) "$m min" else if (m % 60 == 0) "${m / 60} h" else "${m / 60}h ${m % 60}m"
                        TopicChip(label, study.minutes == m) { study.minutes = m; study.saveSettings() }
                    }
                    item { TopicChip("Days…", false) { if (canStart) daysDialog = true else needChannels() } }
                }
                Button(
                    onClick = {
                        if (!canStart) needChannels() else {
                            study.strictNext = true
                            study.saveSettings()
                            study.start()
                        }
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.start_lock), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                Text(
                    "Once started it can't be stopped early. Calls, alarms and settings still work.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Schedule: focus starts and locks by itself at a set time on chosen days.
        item {
            SectionCard("Auto-start focus") {
                val h = study.scheduleStartMin / 60
                val m = study.scheduleStartMin % 60
                val timeText = "%d:%02d %s".format(if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")
                val lenText = if (study.scheduleLenMin % 60 == 0) "${study.scheduleLenMin / 60} h" else "${study.scheduleLenMin} min"
                ToggleRow(
                    if (study.scheduleOn) "On · $timeText for $lenText" else "Off",
                    "Focus locks automatically at this time on the days you pick.",
                    study.scheduleOn
                ) { study.updateSchedule(on = it) }
                if (study.scheduleOn) {
                    OutlinedButton(
                        onClick = {
                            android.app.TimePickerDialog(context, { _, hh, mm -> study.updateSchedule(startMin = hh * 60 + mm) }, h, m, false).show()
                        },
                        shape = CircleShape, modifier = Modifier.fillMaxWidth()
                    ) { Text("Start time: $timeText") }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(60, 90, 120, 180, 240)) { len ->
                            TopicChip(if (len % 60 == 0) "${len / 60} h" else "${len / 60}h ${len % 60}m", study.scheduleLenMin == len) {
                                study.updateSchedule(lenMin = len)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, d ->
                            val on = study.scheduleDays and (1 shl i) != 0
                            Box(
                                Modifier.size(36.dp).clip(CircleShape)
                                    .background(if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { study.updateSchedule(days = study.scheduleDays xor (1 shl i)) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(d, color = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // 2. Music & entertainment filter, lockable for days.
        item {
            SectionCard("Block music & entertainment") {
                val forced = study.lockActive || study.filterLocked
                ToggleRow(
                    "Filter is ${if (study.entertainmentBlocked) "on" else "off"}",
                    when {
                        study.filterLocked -> "Locked on until ${lockDate(study.filterLockUntil)}."
                        study.lockActive -> "Always on while a lock is running."
                        else -> "Hides songs, films, gaming and suggestive videos. Always on during focus."
                    },
                    study.entertainmentBlocked,
                    enabled = !forced
                ) { study.updateBlockEntertainment(it) }
                OutlinedButton(onClick = { filterLockDialog = true }, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(if (study.filterLocked) "Lock for more days" else "Lock it on for days")
                }
            }
        }

        // 3. Daily limit for watching outside focus time.
        item {
            SectionCard("Daily time limit") {
                Text(
                    "Most you can watch per day outside focus sessions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(0, 15, 30, 60, 120)) { m ->
                        val allowed = !study.limitLocked || (m in 1..study.dailyLimit)
                        Box(Modifier.alpha(if (allowed) 1f else 0.35f)) {
                            TopicChip(if (m == 0) "No limit" else "$m min", study.dailyLimit == m) {
                                if (!study.updateDailyLimit(m)) {
                                    android.widget.Toast.makeText(
                                        context, "Limit is locked: you can only lower it.", android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                }
                if (study.dailyLimit > 0) {
                    val frac = (study.usageSec / (study.dailyLimit * 60f)).coerceIn(0f, 1f)
                    Text("Today: ${study.usageSec / 60} of ${study.dailyLimit} min", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(
                        progress = { frac },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = if (frac >= 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    OutlinedButton(onClick = { lockDialog = true }, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(if (study.limitLocked) "Locked until ${lockDate(study.limitLockUntil)}" else "Lock this limit")
                    }
                }
            }
        }

        // 4. Channels allowed during focus.
        item {
            SectionCard("Your channels") {
                Text(
                    "Only these play during focus.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StudyChannels.list.toList().forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(c.name, 32, c.avatar)
                        Spacer(Modifier.size(12.dp))
                        Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }
                study.allowed.toList().filterNot { StudyChannels.containsName(it) }.forEach { ch ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(ch, 32)
                        Spacer(Modifier.size(12.dp))
                        Text(ch, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { study.removeChannel(ch) }) {
                            Icon(Icons.Rounded.Close, "Remove $ch", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                TextField(
                    value = newChannel,
                    onValueChange = { newChannel = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.add_a_channel_by_name)) },
                    trailingIcon = {
                        IconButton(onClick = { study.addChannel(newChannel); newChannel = "" }) {
                            Icon(Icons.Rounded.Add, "Add channel")
                        }
                    },
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { study.addChannel(newChannel); newChannel = "" }),
                    modifier = Modifier.fillMaxWidth()
                )
                if (lib.signedIn) {
                    OutlinedButton(
                        onClick = { lib.ensure(LibTab.Channels); showImport = true },
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.pick_from_my_subscriptions)) }
                }
            }
        }
    }

    if (daysDialog) {
        LongLockDialog(
            base = com.sourabh.jarvistube.data.TrustedClock.now(),
            title = "Focus for days",
            confirmLabel = "Start & lock",
            onConfirm = { until -> study.startLongLock(until); daysDialog = false },
            onDismiss = { daysDialog = false }
        )
    }
    if (filterLockDialog) {
        DaysWheelDialog(
            title = if (study.filterLocked) "Lock for more days" else "Lock the filter on",
            from = maxOf(study.filterLockUntil, com.sourabh.jarvistube.data.TrustedClock.now()),
            onConfirm = { d -> study.lockFilter(d); filterLockDialog = false },
            onDismiss = { filterLockDialog = false }
        )
    }
    if (lockDialog) {
        AlertDialog(
            onDismissRequest = { lockDialog = false },
            title = { Text("Lock the ${study.dailyLimit}-min limit?") },
            text = { Text(stringResource(R.string.while_locked_you_can_lower_the_limit_but)) },
            confirmButton = {
                Row {
                    listOf(1, 7, 30).forEach { d ->
                        TextButton(onClick = { lockDialog = false; study.lockLimit(d) }) {
                            Text(if (d == 1) "1 day" else "$d days")
                        }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { lockDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showImport) {
        ModalBottomSheet(onDismissRequest = { showImport = false }) {
            val feed = lib.loader(LibTab.Channels).state
            val names = feed.items.filterIsInstance<LibChannel>()
            Text(
                "Your subscriptions",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            if (names.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    if (feed.loading) CircularProgressIndicator() else Text(stringResource(R.string.no_subscriptions_found))
                }
            } else {
                LazyColumn(Modifier.height(420.dp)) {
                    items(names, key = { it.id }) { c ->
                        val on = study.isAllowed(c.name)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(on, { if (it) study.addChannel(c.name) else study.removeChannel(c.name) })
                            Text(c.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
