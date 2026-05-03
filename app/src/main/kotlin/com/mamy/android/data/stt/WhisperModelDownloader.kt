package com.mamy.android.data.stt

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class WhisperModelDownloader(private val targetDir: File) {

    fun download(model: WhisperModel): Flow<Int> = flow {
        val target = File(targetDir, model.fileName)
        val tmp = File(targetDir, model.fileName + ".part")
        if (target.exists() && verifyHash(target, model.sha256)) {
            emit(100); return@flow
        }
        targetDir.mkdirs()
        tmp.delete()

        val conn = (URL(model.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode !in 200..299) {
                throw IOException("HTTP ${conn.responseCode} fetching ${model.downloadUrl}")
            }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: model.expectedBytes
            var emittedPct = -1

            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read: Long = 0
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        read += n
                        val pct = ((read * 100) / total).toInt().coerceIn(0, 99)
                        if (pct != emittedPct) {
                            emit(pct); emittedPct = pct
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }

        if (!verifyHash(tmp, model.sha256)) {
            tmp.delete()
            throw IOException("SHA256 mismatch for ${model.fileName}")
        }
        if (!tmp.renameTo(target)) {
            throw IOException("Could not rename ${tmp.absolutePath}")
        }
        emit(100)
    }.flowOn(Dispatchers.IO)

    private fun verifyHash(file: File, expected: String): Boolean {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        val hex = md.digest().joinToString("") { "%02x".format(it) }
        val ok = hex.equals(expected, ignoreCase = true)
        if (!ok) Log.w(TAG, "hash mismatch: got $hex expected $expected")
        return ok
    }

    private companion object { const val TAG = "WhisperDL" }
}
