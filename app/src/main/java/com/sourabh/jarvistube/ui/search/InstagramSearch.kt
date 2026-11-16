package com.sourabh.jarvistube.ui.search

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.net.URLEncoder

/** While on, every search also goes straight to Instagram (kept until Videos or Shorts is picked). */
internal object InstagramMode {
    var on by mutableStateOf(false)
}

/**
 * Instagram mode: the search runs on Instagram automatically, in the Instagram app when it's
 * installed (already signed in), otherwise in a Chrome tab. Instagram doesn't allow other apps to
 * show it inside them, so this is the reliable way; nothing is scraped and PlayBook never sees
 * the login. Back returns here. Hidden during study sessions and locks, like Shorts.
 */
@Composable
internal fun InstagramSearch(query: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val tabColor = MaterialTheme.colorScheme.background.toArgb()
    val q = query.trim()
    // Every new search (and switching to this chip) opens Instagram's search right away.
    LaunchedEffect(q) { if (q.isNotEmpty()) openInstagramSearch(context, q, tabColor) }
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Searching Instagram for “$q”", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Back brings you here. New searches go to Instagram too, until you pick Videos or Shorts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = { openInstagramSearch(context, q, tabColor) }) { Text("Open again") }
    }
}

/** Instagram's keyword search for [query]: its app when installed, else a Chrome tab (no extra library). */
internal fun openInstagramSearch(context: Context, query: String, toolbarColor: Int) {
    com.sourabh.jarvistube.Graph.player.player.pause()
    val url = "https://www.instagram.com/explore/search/keyword/?q=" + URLEncoder.encode(query, "UTF-8")
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        putExtras(Bundle().apply { putBinder("android.support.customtabs.extra.SESSION", null) })
        putExtra("android.support.customtabs.extra.TOOLBAR_COLOR", toolbarColor)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val chrome = runCatching { context.packageManager.getPackageInfo("com.android.chrome", 0) }.isSuccess
    if (chrome) intent.setPackage("com.android.chrome")
    runCatching { context.startActivity(intent) }.onFailure {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
