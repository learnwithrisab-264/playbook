package com.sourabh.jarvistube.lock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.MainActivity
import com.sourabh.jarvistube.data.timeLeftText
import com.sourabh.jarvistube.ui.theme.JarvisTubeTheme
import kotlinx.coroutines.delay

/** Shown over blocked apps: says how long is left, then sends you back to PlayBook. */
class StudyBlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JarvisTubeTheme {
                BackHandler { backToStudy() }
                BlockScreen(
                    onStudy = ::backToStudy,
                    onPhone = {
                        startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        finish()
                    }
                )
            }
        }
    }

    private fun backToStudy() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        fun intent(context: Context) = Intent(context, StudyBlockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

@Composable
private fun BlockScreen(onStudy: () -> Unit, onPhone: () -> Unit) {
    val study = Graph.study
    var left by remember { mutableLongStateOf(study.lockRemainingMs) }
    LaunchedEffect(Unit) {
        while (true) {
            study.tick()
            left = study.lockRemainingMs
            if (!study.lockActive) onStudy()
            delay(1000)
        }
    }
    var exiting by remember { mutableStateOf(false) }
    LaunchedEffect(exiting) {
        if (!exiting) {
            delay(5000)
            onStudy()
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().imePadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Rounded.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
            Text(
                "Study session locked, ${minutesLeftText(left)}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Only PlayBook and Phone work until the timer ends.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            if (exiting) {
                EmergencyExit(onEnd = { if (study.useEmergencyExit()) onStudy() }, onCancel = { exiting = false })
            } else {
                Button(onClick = onStudy, Modifier.fillMaxWidth()) { Text("Back to study video") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onPhone, Modifier.fillMaxWidth()) { Text("Open Phone") }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { exiting = true }) { Text("Emergency exit") }
            }        }
    }
}

/** Once in 24 hours, 3 times in 7 days, and deliberately slow: type the phrase and wait. */
@Composable
private fun EmergencyExit(onEnd: () -> Unit, onCancel: () -> Unit) {
    val study = Graph.study
    val wait = study.exitWaitMs
    if (study.exitNeedsRealTime || wait > 0L) {
        Text(
            if (study.exitNeedsRealTime) "The emergency exit needs internet to check the real time."
            else "Emergency exit not available for ${timeLeftText(wait)}.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Text(
            "It works once in 24 hours, $EXITS_PER_WEEK times in 7 days.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        TextButton(onClick = onCancel) { Text("Keep studying") }
        return
    }
    var typed by remember { mutableStateOf("") }
    var waited by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (waited < EXIT_WAIT_SECONDS) {
            delay(1000)
            waited++
        }
    }
    Text(
        "Use this only if it is really important. ${EXITS_PER_WEEK - study.exitsUsedThisWeek} of $EXITS_PER_WEEK left " +
            "in 7 days, and the next one comes after 24 hours.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(12.dp))
    Text("To end the session, type:", style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(4.dp))
    Text(EXIT_PHRASE, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(typed, { typed = it }, Modifier.fillMaxWidth(), singleLine = true)
    Spacer(Modifier.height(12.dp))
    Button(onClick = onEnd, Modifier.fillMaxWidth(), enabled = exitReady(typed, waited)) {
        Text(if (waited < EXIT_WAIT_SECONDS) "Wait ${EXIT_WAIT_SECONDS - waited} seconds" else "End session now")
    }
    TextButton(onClick = onCancel) { Text("Keep studying") }
}
