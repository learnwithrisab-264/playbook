package com.sourabh.jarvistube.ui.search

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.net.URLEncoder

/**
 * Instagram for a search, the way Instagram allows: it refuses to draw inside other apps (it
 * serves an empty page to embedded browsers), so its own site opens in a Chrome tab over PlayBook,
 * straight on the keyword search for what you typed (e.g. "drone making"). Your Instagram login
 * stays in Chrome, PlayBook never sees it, nothing is scraped, and Back returns here.
 * Hidden during study sessions and locks, like Shorts.
 */
@Composable
internal fun InstagramSearch(query: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val tabColor = MaterialTheme.colorScheme.background.toArgb()
    val q = query.trim()
    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Instagram reels for “$q”", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Opens Instagram's search for these words. Tap any reel there and swipe up for more.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = {
                openInstagram(context, "https://www.instagram.com/explore/search/keyword/?q=" + URLEncoder.encode(q, "UTF-8"), tabColor)
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Rounded.Search, null)
            Spacer(Modifier.width(8.dp))
            Text("Search “$q” on Instagram")
        }
        OutlinedButton(
            onClick = { openInstagram(context, "https://www.instagram.com/reels/", tabColor) },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Rounded.SmartDisplay, null)
            Spacer(Modifier.width(8.dp))
            Text("Instagram reels feed")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(
                "Sign in once on Instagram's own page in Chrome; PlayBook never sees your password. " +
                    "Instagram doesn't let other apps show it inside them, so it opens in a Chrome tab. Back brings you here.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A Chrome Custom Tab without the extra library: the standard intent extras do it. */
private fun openInstagram(context: Context, url: String, toolbarColor: Int) {
    com.sourabh.jarvistube.Graph.player.player.pause()
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        putExtras(Bundle().apply { putBinder("android.support.customtabs.extra.SESSION", null) })
        putExtra("android.support.customtabs.extra.TOOLBAR_COLOR", toolbarColor)
        putExtra("android.support.customtabs.extra.TITLE_VISIBILITY", 1)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    // Chrome when it's there (a real browser tab, so Instagram works); otherwise any browser.
    val chrome = runCatching { context.packageManager.getPackageInfo("com.android.chrome", 0) }.isSuccess
    if (chrome) intent.setPackage("com.android.chrome")
    runCatching { context.startActivity(intent) }.onFailure {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
