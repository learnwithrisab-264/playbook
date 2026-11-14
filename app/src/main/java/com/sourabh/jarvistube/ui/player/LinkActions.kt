package com.sourabh.jarvistube.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.sourabh.jarvistube.Graph
import com.sourabh.jarvistube.TubeViewModel
import com.sourabh.jarvistube.data.VideoItem
import com.sourabh.jarvistube.ui.common.linkStartSeconds
import com.sourabh.jarvistube.ui.common.youtubeVideoId

/** What tapping a link in a description or comment does. */
internal class LinkActions(val onUrl: (String) -> Unit, val onSeek: (Long) -> Unit)

/**
 * Times jump within this video. YouTube video links play inside PlayBook (through the usual study
 * checks); a link to this same video with a time jumps there. Other links open in the browser,
 * except during a study lock.
 */
@Composable
internal fun rememberLinkActions(vm: TubeViewModel, currentUrl: String): LinkActions {
    val context = LocalContext.current
    return remember(currentUrl) {
        val seek: (Long) -> Unit = { s -> Graph.player.player.seekTo(s * 1000) }
        LinkActions(onUrl = { url -> openLink(context, vm, currentUrl, url, seek) }, onSeek = seek)
    }
}

private fun openLink(context: Context, vm: TubeViewModel, currentUrl: String, url: String, seek: (Long) -> Unit) {
    val id = youtubeVideoId(url)
    if (id != null) {
        if (id == youtubeVideoId(currentUrl)) {
            linkStartSeconds(url)?.let(seek)
        } else {
            vm.play(VideoItem("https://www.youtube.com/watch?v=$id", "", "", null, 0, -1, null, false))
        }
        return
    }
    // Channel links (@handle, /channel/…) open the channel inside PlayBook.
    val channel = Regex("""^https?://(?:www\.|m\.)?youtube\.com/(@[^/?#]+|channel/[^/?#]+|c/[^/?#]+|user/[^/?#]+)""").find(url)
    if (channel != null && !Graph.study.lockActive) {
        val path = channel.groupValues[1]
        vm.openChannel("https://www.youtube.com/$path", if (path.startsWith("@")) path else path.substringAfter('/'))
        return
    }
    if (Graph.study.lockActive) {
        Toast.makeText(context, "Links open after the study lock ends", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show() }
}
