package com.sourabh.jarvistube.data

import android.content.Context
import android.os.SystemClock
import java.io.File

/** Tiny file logger (logcat is unreliable on some OEM builds). Read with `adb shell cat`. */
object Dbg {
    private var file: File? = null
    private val t0 = SystemClock.elapsedRealtime()

    fun init(ctx: Context) {
        file = File(ctx.getExternalFilesDir(null), "jt_timing.txt").also { it.writeText("") }
    }

    fun log(msg: String) {
        runCatching { file?.appendText("${SystemClock.elapsedRealtime() - t0}ms  $msg\n") }
    }
}
