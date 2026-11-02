package com.sourabh.jarvistube.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException
import kotlin.math.min

/**
 * googlevideo throttles one long request to a trickle. Fetching the file as consecutive
 * ~10 MB `range=` chunks (what NewPipe does) keeps full speed. Non-googlevideo URLs
 * (HLS, local) pass straight through.
 */
@UnstableApi
class ChunkedHttpDataSource(private val upstream: DataSource) : DataSource {
    private var spec: DataSpec? = null
    private var passthrough = false
    private var nextPos = 0L
    private var endPos = 0L // exclusive end of the whole requested range
    private var emptyReopens = 0

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        val clen = dataSpec.uri.getQueryParameter("clen")?.toLongOrNull()
        val host = dataSpec.uri.host.orEmpty()
        if (clen == null || !host.contains("googlevideo")) {
            passthrough = true
            return upstream.open(dataSpec)
        }
        passthrough = false
        spec = dataSpec
        nextPos = dataSpec.position
        endPos = if (dataSpec.length == C.LENGTH_UNSET.toLong()) clen else min(clen, dataSpec.position + dataSpec.length)
        emptyReopens = 0
        openChunk()
        return endPos - dataSpec.position
    }

    private fun openChunk() {
        val s = spec ?: throw IOException("Not opened")
        val chunkEnd = min(endPos, nextPos + CHUNK)
        val uri: Uri = s.uri.buildUpon().appendQueryParameter("range", "$nextPos-${chunkEnd - 1}").build()
        upstream.open(s.buildUpon().setUri(uri).setPosition(0).setLength(C.LENGTH_UNSET.toLong()).build())
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (passthrough) return upstream.read(buffer, offset, length)
        if (length == 0) return 0
        while (true) {
            if (nextPos >= endPos) return C.RESULT_END_OF_INPUT
            val n = upstream.read(buffer, offset, length)
            if (n == C.RESULT_END_OF_INPUT) {
                if (nextPos >= endPos) return C.RESULT_END_OF_INPUT
                upstream.close()
                if (++emptyReopens > 3) throw IOException("Chunk request returned no data")
                openChunk()
                continue
            }
            emptyReopens = 0
            nextPos += n
            return n
        }
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() = upstream.close()

    private companion object {
        const val CHUNK = 10L * 1024 * 1024
    }
}
