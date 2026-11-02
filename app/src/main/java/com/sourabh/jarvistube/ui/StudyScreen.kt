package com.sourabh.jarvistube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import com.sourabh.jarvistube.data.StudyChannels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.LibTab
import com.sourabh.jarvistube.LibraryViewModel
import com.sourabh.jarvistube.TubeViewModel
import com.sourabh.jarvistube.data.LibChannel
import kotlinx.coroutines.delay

@Composable
fun StudyScreen(vm: TubeViewModel, lib: LibraryViewModel, bottomPadding: Dp) {
    val study = Graph.study
    if (study.justCompleted > 0) {
        AlertDialog(
            onDismissRequest = { study.justCompleted = 0 },
            title = { Text("Session complete") },
            text = { Text("You stayed focused for ${study.justCompleted} minutes. Nice work.") },
            confirmButton = { TextButton(onClick = { study.justCompleted = 0 }) { Text("Done") } }
        )
    }
    if (study.active) ActiveSession(vm, bottomPadding) else SetupSession(lib, bottomPadding)
}

// ---------------------------------------------------------------- setup

// PlayBook palette: yellow -> orange -> deep orange.
private val JarvisGradient = Brush.linearGradient(listOf(Color(0xFFFFC107), Color(0xFFFF8C00), Color(0xFFE64A19)))
private val JarvisViolet = Color(0xFFFF6D00)
private val JarvisCyan = Color(0xFFFFC107)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupSession(lib: LibraryViewModel, bottomPadding: Dp) {
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
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)) {
                Text("Study", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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
                    Text("Start & lock", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
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
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    placeholder = { Text("Add a channel by name") },
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
                    ) { Text("Pick from my subscriptions") }
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
            text = { Text("While locked you can lower the limit, but not raise it or turn it off.") },
            confirmButton = {
                Row {
                    listOf(1, 7, 30).forEach { d ->
                        TextButton(onClick = { lockDialog = false; study.lockLimit(d) }) {
                            Text(if (d == 1) "1 day" else "$d days")
                        }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { lockDialog = false }) { Text("Cancel") } }
        )
    }

    if (showImport) {
        ModalBottomSheet(onDismissRequest = { showImport = false }) {
            val feed = lib.loader(LibTab.Channels).state
            val names = feed.items.filterIsInstance<LibChannel>()
            Text(
                "Your subscriptions",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            if (names.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    if (feed.loading) CircularProgressIndicator() else Text("No subscriptions found.")
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

/**
 * Picks the end of a multi-day Study mode lock: quick day presets counted from [base] (now, or
 * the current end when extending), or an exact date and time.
 */
@Composable
private fun LongLockDialog(
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
private fun DaysWheelDialog(title: String, from: Long, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
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
private fun remainingText(sec: Long): String = when {
    sec >= 86_400 -> "%dd %02dh".format(sec / 86_400, sec % 86_400 / 3600)
    sec >= 3600 -> "%d:%02d:%02d".format(sec / 3600, sec % 3600 / 60, sec % 60)
    else -> "%02d:%02d".format(sec / 60, sec % 60)
}

private fun lockDate(ms: Long): String =
    java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(ms))

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = JarvisViolet, checkedThumbColor = Color.White)
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

// ---------------------------------------------------------------- active

/**
 * Focus mode home, laid out like YouTube's Subscriptions page: a slim header with the time left,
 * a strip of your channels to pick from (or All), a search box, then the videos.
 */
@Composable
private fun ActiveSession(vm: TubeViewModel, bottomPadding: Dp) {
    val study = Graph.study
    var remaining by remember { mutableLongStateOf(study.remainingMs) }
    var query by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<String?>(null) } // channel name; null = All
    val perChannel = remember { mutableStateMapOf<String, List<com.sourabh.jarvistube.data.VideoItem>>() }
    var channelLoading by remember { mutableStateOf(false) }
    var extendDialog by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }

    LaunchedEffect(study.endAt) {
        while (true) {
            remaining = study.remainingMs
            delay(1000)
        }
    }
    LaunchedEffect(study.active) {
        if (study.active) {
            if (vm.studyFeed.items.isEmpty()) vm.loadStudyFeed()
            if (study.feed.items.isEmpty()) study.loadFeed(null)
        }
    }

    // Every channel allowed during focus: "My channels" (with photos) plus names added in Study.
    val channels = remember(StudyChannels.list.size, study.allowed.size) {
        val known = StudyChannels.list.map { Triple(it.name, it.avatar, it.url) }
        val extra = study.allowed.filterNot { StudyChannels.containsName(it) }.map { Triple(it, null, null) }
        known + extra
    }
    // Picking a channel with a known page loads its newest uploads; others filter the search feed.
    LaunchedEffect(selected) {
        val name = selected ?: return@LaunchedEffect
        val url = channels.firstOrNull { it.first == name }?.third ?: return@LaunchedEffect
        if (perChannel[name] != null) return@LaunchedEffect
        channelLoading = true
        perChannel[name] = runCatching { com.sourabh.jarvistube.data.ChannelRepo.latestVideos(url) }.getOrDefault(emptyList())
        channelLoading = false
    }

    // Only videos that can actually play now: from one of the allowed channels.
    fun allowed(v: com.sourabh.jarvistube.data.VideoItem) = v.channel.isBlank() || study.isAllowed(v.channel, v.channelUrl)
    val allVideos = (vm.studyFeed.items + study.feed.items).filter(::allowed).distinctBy { it.url }
    val shown = when {
        searched != null -> study.feed.items.filter { allowed(it) && (selected == null || it.channel.equals(selected, true)) }
        selected != null -> perChannel[selected]?.takeIf { it.isNotEmpty() }
            ?: allVideos.filter { it.channel.equals(selected, true) }
        else -> allVideos
    }
    val loading = when {
        searched != null -> study.feed.loading
        selected != null -> channelLoading && shown.isEmpty()
        else -> allVideos.isEmpty() && (vm.studyFeed.loading || study.feed.loading)
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().imePadding(),
        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp)
    ) {
        // Header: title + countdown pill, a thin progress line, and the lock line.
        item {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, null, Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Focus mode", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(
                        "${remainingText(remaining / 1000)} left",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                val total = (study.sessionMinutes * 60_000f).coerceAtLeast(1f)
                val done = (1f - remaining / total).coerceIn(0f, 1f)
                // Plain YouTube-style progress line: grey track, red fill, no gaps or end dots.
                Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(done).fillMaxHeight().background(Color(0xFFFF6D00)))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(40.dp)) {
                    Text(
                        if (study.strict) "Locked until ${lockDate(study.endAt)}" else "Ends at ${lockDate(study.endAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (study.longLock) TextButton(onClick = { extendDialog = true }) { Text("Extend") }
                    if (!study.strict) TextButton(onClick = { confirmEnd = true }) { Text("End early") }
                }
            }
        }

        // Channel strip: All, then each channel. The picked one is ringed, the rest dimmed.
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ChannelBubble("All", null, selected == null) { selected = null }
                }
                items(channels, key = { it.first }) { (name, avatar, _) ->
                    ChannelBubble(name, avatar, selected == name) { selected = if (selected == name) null else name }
                }
            }
        }

        // Search inside the allowed channels.
        item {
            TextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(if (selected != null) "Search $selected" else "Search your channels") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (searched != null || query.isNotEmpty()) {
                        IconButton(onClick = { query = ""; searched = null }) { Icon(Icons.Rounded.Close, "Clear") }
                    }
                },
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (query.isNotBlank()) {
                        searched = query.trim()
                        study.loadFeed(listOfNotNull(searched, selected).joinToString(" "))
                    }
                }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        when {
            loading -> items(3) { VideoCardSkeleton() }
            searched != null && study.feed.error != null && shown.isEmpty() ->
                item { ErrorBox(study.feed.error!!, onRetry = { study.loadFeed(searched) }) }
            shown.isEmpty() -> item {
                Text(
                    if (searched != null) "Nothing found for \"$searched\"." else "No videos yet from ${selected ?: "your channels"}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp)
                )
            }
            else -> items(shown, key = { it.url }) { v -> VideoCard(v, onClick = { vm.play(v) }) }
        }
    }

    if (extendDialog) {
        LongLockDialog(
            base = study.endAt,
            title = "Extend Study lock",
            confirmLabel = "Extend",
            onConfirm = { until -> study.extendLongLock(until); extendDialog = false },
            onDismiss = { extendDialog = false }
        )
    }
    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End focus early?") },
            confirmButton = { TextButton(onClick = { confirmEnd = false; study.endEarly() }) { Text("End") } },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Keep going") } }
        )
    }
}

@Composable
private fun ChannelBubble(name: String, avatar: String?, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.width(68.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 4.dp)
            .alpha(if (selected) 1f else 0.6f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(60.dp).clip(CircleShape)
                .background(if (selected) cs.onSurface else Color.Transparent)
                .padding(2.dp).clip(CircleShape).background(cs.background).padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            if (avatar == null && name == "All") {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(cs.surfaceVariant), contentAlignment = Alignment.Center) {
                    Text("All", fontWeight = FontWeight.Bold)
                }
            } else {
                Avatar(name, 52, avatar)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            name, style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
