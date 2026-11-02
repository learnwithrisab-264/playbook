package com.sourabh.jarvistube.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.AppSettings

/**
 * Edits which speeds the player's speed menus offer. Tap chips to switch several on or off, or
 * type several new speeds at once ("1.1, 2.2, 3.75x"). Nothing is saved until Save; 1x always stays.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun SpeedListDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Standard 0.25 steps up to 4x, plus any custom speeds already in the list.
    val standard = (1..16).map { it * 0.25f }
    var chosen by remember { mutableStateOf(AppSettings.speeds.toSet()) }
    var offered by remember { mutableStateOf((standard + AppSettings.speeds).distinct().sorted()) }
    var input by remember { mutableStateOf("") }

    fun addTyped() {
        val parts = input.split(',', ' ', ';').map { it.trim().removeSuffix("x").removeSuffix("X") }.filter { it.isNotEmpty() }
        val good = parts.mapNotNull { it.toFloatOrNull() }.map(AppSettings::roundSpeed)
            .filter { it in AppSettings.MIN_SPEED..AppSettings.MAX_SPEED }
        val bad = parts.size - good.size
        if (good.isNotEmpty()) {
            chosen = chosen + good
            offered = (offered + good).distinct().sorted()
            input = ""
        }
        if (bad > 0) Toast.makeText(
            context,
            "Speeds must be numbers from ${AppSettings.speedText(AppSettings.MIN_SPEED)} to ${AppSettings.speedText(AppSettings.MAX_SPEED)}",
            Toast.LENGTH_SHORT
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback speeds") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Tap to show or hide a speed in the speed menu. Normal (1x) always stays.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(12.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    offered.forEach { s ->
                        TopicChip(AppSettings.speedText(s), s in chosen) {
                            if (s != 1f) chosen = if (s in chosen) chosen - s else chosen + s
                        }
                    }
                }
                Spacer(Modifier.size(16.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    label = { Text("Add speeds") },
                    placeholder = { Text("e.g. 1.1, 2.2, 3.75") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { addTyped() }),
                    trailingIcon = { TextButton(onClick = { addTyped() }, enabled = input.isNotBlank()) { Text("Add") } },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(8.dp))
                Row {
                    TextButton(onClick = { chosen = offered.toSet() }) { Text("Select all") }
                    TextButton(onClick = { chosen = setOf(1f) }) { Text("Clear all") }
                    TextButton(onClick = {
                        chosen = AppSettings.defaultSpeeds.toSet()
                        offered = (standard + AppSettings.defaultSpeeds).distinct().sorted()
                    }) { Text("Reset") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                AppSettings.updateSpeeds(chosen)
                Toast.makeText(context, "${AppSettings.speeds.size} speeds in the menu", Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(options) { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(value) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = value == selected, onClick = { onPick(value) })
                        Spacer(Modifier.size(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
