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
import com.sourabh.jarvistube.ui.theme.JarvisTubeTheme
import kotlinx.coroutines.delay

/** Shown over blocked apps: says how long is left, then sends you back to PlayBook. */
class StudyBlockActivity : ComponentActivity() {
    private var emergency by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readIntent(intent)
        setContent {
            JarvisTubeTheme {
                BackHandler { backToStudy() }
                BlockScreen(
                    emergency = emergency,
                    onEmergency = { emergency = it },
                    onStudy = ::backToStudy,
                    onPhone = {
                        startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        finish()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent) {
        if (intent.getBooleanExtra(EMERGENCY, false)) emergency = true
    }

    private fun backToStudy() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        private const val EMERGENCY = "emergency"

        fun intent(context: Context) = Intent(context, StudyBlockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun emergencyIntent(context: Context) = intent(context).putExtra(EMERGENCY, true)
    }
}

@Composable
private fun BlockScreen(
    emergency: Boolean,
    onEmergency: (Boolean) -> Unit,
    onStudy: () -> Unit,
    onPhone: () -> Unit
) {
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
    LaunchedEffect(emergency) {
        if (!emergency) {
            delay(5000)
            onStudy()
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().imePadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Rounded.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(20.dp))
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
            Spacer(Modifier.height(28.dp))
            if (emergency) {
                EmergencyExit(
                    onEnd = { study.endLockEarly(); onStudy() },
                    onCancel = { onEmergency(false) }
                )
            } else {
                Button(onClick = onStudy, Modifier.fillMaxWidth()) { Text("Back to study video") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onPhone, Modifier.fillMaxWidth()) { Text("Open Phone") }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { onEmergency(true) }) { Text("Emergency exit") }
            }
        }
    }
}

/** Deliberately slow: you must type the phrase and wait, so it can't be an impulse. */
@Composable
private fun EmergencyExit(onEnd: () -> Unit, onCancel: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    var waited by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (waited < EXIT_WAIT_SECONDS) {
            delay(1000)
            waited++
        }
    }
    Text("To end the session early, type:", style = MaterialTheme.typography.bodyMedium)
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
