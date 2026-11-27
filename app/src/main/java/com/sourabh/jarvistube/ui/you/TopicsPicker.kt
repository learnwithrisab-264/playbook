package com.sourabh.jarvistube.ui.you

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.AppSettings
import com.sourabh.jarvistube.TubeViewModel
import com.sourabh.jarvistube.ui.theme.Corners

/** Suggested topics; you can add any of your own as well. */
private val SUGGESTED = listOf(
    "Science", "Physics", "Chemistry", "Biology", "Mathematics", "Space",
    "Programming", "AI", "Technology", "Startups", "Business", "Finance", "Economics",
    "History", "Geography", "Current affairs", "Psychology", "Philosophy",
    "Health and fitness", "Cooking", "Art and design", "Filmmaking", "Photography",
    "Music theory", "Languages", "Exam prep", "Productivity", "Self improvement", "Cricket", "Football"
)

/**
 * Pick what you want to watch. Recommendations on Home and the Shorts tab then come only from
 * these topics. Saving with none chosen goes back to your YouTube home feed.
 * With [forShorts] it picks "Your topics Shorts": topics only for the Shorts tab, or "Same as
 * Your topics". None picked = Shorts from your YouTube account.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopicsDialog(vm: TubeViewModel, onDismiss: () -> Unit, forShorts: Boolean = false) {
    val chosen = remember {
        mutableStateListOf<String>().apply { addAll(if (forShorts) AppSettings.shortsTopics else AppSettings.userTopics) }
    }
    var same by remember { mutableStateOf(forShorts && AppSettings.shortsTopicsSame) }
    var custom by remember { mutableStateOf("") }
    val touch = com.sourabh.jarvistube.ui.theme.rememberTouch()
    fun addCustom() {
        val t = custom.trim()
        if (t.isNotEmpty() && chosen.none { it.equals(t, true) }) chosen += t
        custom = ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Interests, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(if (forShorts) "Your topics Shorts" else "Your topics") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text(
                    if (forShorts) "The Shorts tab will show Shorts only from what you pick. Pick none for Shorts from your YouTube account."
                    else "Recommendations and Shorts will come only from what you pick.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (forShorts) {
                    // Shorts can simply follow Your topics.
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp).clip(Corners.medium)
                            .clickable { same = !same }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Same as Your topics", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (AppSettings.userTopics.isEmpty()) "No Your topics picked yet"
                                else AppSettings.userTopics.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        androidx.compose.material3.Switch(checked = same, onCheckedChange = { same = it })
                    }
                }
                Spacer(Modifier.size(14.dp))
                val shown = if (same) AppSettings.userTopics.toList() else chosen.toList()
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = if (same) Modifier.alpha(0.5f) else Modifier
                ) {
                    // Your own topics first, then the suggestions.
                    (shown.filter { c -> SUGGESTED.none { it.equals(c, true) } } + SUGGESTED).forEach { t ->
                        val on = shown.any { it.equals(t, true) }
                        TopicToggle(t, on) {
                            if (!same) {
                                touch()
                                if (on) chosen.removeAll { it.equals(t, true) } else chosen += t
                            }
                        }
                    }
                }
                Spacer(Modifier.size(14.dp))
                if (!same) Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        custom, { custom = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Add your own, e.g. UPSC") },
                        shape = Corners.pill,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addCustom() })
                    )
                    IconButton(onClick = ::addCustom, enabled = custom.isNotBlank()) { Icon(Icons.Rounded.Add, "Add topic") }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (forShorts) {
                    AppSettings.updateShortsTopics(chosen.toList(), same)
                    vm.shortsTopicsChanged()
                } else {
                    AppSettings.updateUserTopics(chosen.toList())
                    vm.topicsChanged()
                }
                onDismiss()
            }) {
                Text(
                    when {
                        same -> "Save"
                        chosen.isEmpty() -> "Save (no topics)"
                        else -> "Save ${chosen.size} topics"
                    }
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun TopicToggle(label: String, on: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    // Unpicked topics: the page colour with a thin outline, so they stand out on the dialog.
    val bg by animateColorAsState(if (on) cs.primary else cs.background, label = "topicBg")
    val edge by animateColorAsState(if (on) cs.primary else cs.outline.copy(alpha = 0.6f), label = "topicEdge")
    val fg by animateColorAsState(if (on) cs.onPrimary else cs.onSurface, label = "topicFg")
    Row(
        Modifier.clip(Corners.pill).background(bg).border(1.dp, edge, Corners.pill).clickable(onClick = onClick)
            .padding(start = if (on) 10.dp else 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (on) {
            Icon(Icons.Rounded.Check, null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(4.dp))
        }
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
    }
}

/** On Home, while no topics are chosen: one tap to pick them. */
@Composable
fun TopicsPrompt(onPick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(Corners.large)
            .background(cs.primary.copy(alpha = 0.10f))
            .clickable(onClick = onPick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Interests, null, tint = cs.primary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text("What do you want to watch?", style = MaterialTheme.typography.titleSmall)
            Text(
                "Pick your topics and recommendations and Shorts will follow them.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )
        }
        Spacer(Modifier.size(8.dp))
        Text("Pick", color = cs.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}
