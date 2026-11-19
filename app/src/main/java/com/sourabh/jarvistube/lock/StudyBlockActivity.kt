package com.sourabh.jarvistube.lock

import com.sourabh.jarvistube.R
import androidx.compose.ui.res.stringResource
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

/** Once in 24 hours, 3 times in 7 days, and deliberately slow: type the phrase and wait. */
@Composable
internal fun EmergencyExit(onEnd: () -> Unit, onCancel: () -> Unit) {
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
        TextButton(onClick = onCancel) { Text(stringResource(R.string.keep_studying)) }
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
    Text(stringResource(R.string.to_end_the_session_type), style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(4.dp))
    Text(EXIT_PHRASE, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(typed, { typed = it }, Modifier.fillMaxWidth(), singleLine = true)
    Spacer(Modifier.height(12.dp))
    Button(onClick = onEnd, Modifier.fillMaxWidth(), enabled = exitReady(typed, waited)) {
        Text(if (waited < EXIT_WAIT_SECONDS) "Wait ${EXIT_WAIT_SECONDS - waited} seconds" else "End session now")
    }
    TextButton(onClick = onCancel) { Text(stringResource(R.string.keep_studying)) }
}
