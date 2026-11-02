package com.sourabh.jarvistube

import android.app.Application
import com.sourabh.jarvistube.data.AppDownloader
import com.sourabh.jarvistube.player.PlayerController
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import java.util.concurrent.TimeUnit

object Graph {
    lateinit var http: OkHttpClient
    lateinit var player: PlayerController
    lateinit var study: StudyState
}

class JarvisTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val client = OkHttpClient.Builder()
            // A black-holed IPv6 route otherwise costs a full connect timeout before IPv4 is tried.
            .dns(object : okhttp3.Dns {
                override fun lookup(hostname: String) =
                    okhttp3.Dns.SYSTEM.lookup(hostname).sortedBy { it is java.net.Inet6Address }
            })
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        Graph.http = client
        com.sourabh.jarvistube.data.TrustedClock.init(this)
        com.sourabh.jarvistube.data.Session.init(this)
        AppSettings.init(this)
        NewPipe.init(AppDownloader(client), AppSettings.localization(), AppSettings.contentCountry())
        Graph.player = PlayerController(this, client)
        Graph.study = StudyState(this)
        com.sourabh.jarvistube.data.Downloads.init(this)
        com.sourabh.jarvistube.data.StudyChannels.init(this)
        com.sourabh.jarvistube.data.WatchHistory.init(this)
        com.sourabh.jarvistube.data.SeenVideos.init(this)
        com.sourabh.jarvistube.data.FeedStore.init(this)
        com.sourabh.jarvistube.data.UploadAlerts.init(this)
        if (Graph.study.lockActive) com.sourabh.jarvistube.lock.StudyLockService.start(this)
    }
}
