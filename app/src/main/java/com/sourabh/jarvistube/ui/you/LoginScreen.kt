package com.sourabh.jarvistube.ui.you

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sourabh.jarvistube.data.WEB_UA

private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fwww.youtube.com%2F"

/**
 * Google sign-in inside a WebView. The login is the user's own, typed into Google's page;
 * the app only keeps the resulting YouTube session cookies.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onCookie: (String) -> Unit, onClose: () -> Unit) {
    var web by remember { mutableStateOf<WebView?>(null) }
    var done by remember { mutableStateOf(false) }

    BackHandler {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else onClose()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") }
            Text("Sign in to Google", style = MaterialTheme.typography.titleMedium)
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = WEB_UA
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) = Unit
                        override fun onPageFinished(view: WebView, url: String?) {
                            val host = android.net.Uri.parse(url ?: "").host.orEmpty()
                            if (done || !host.endsWith("youtube.com")) return
                            val cookies = CookieManager.getInstance().getCookie("https://www.youtube.com").orEmpty()
                            if (cookies.contains("SAPISID=")) {
                                done = true
                                CookieManager.getInstance().flush()
                                onCookie(cookies)
                            }
                        }
                    }
                    loadUrl(LOGIN_URL)
                    web = this
                }
            },
            onRelease = { it.destroy() }
        )
    }
}
