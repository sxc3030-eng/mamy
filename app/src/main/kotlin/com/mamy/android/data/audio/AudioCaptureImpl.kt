package com.mamy.android.data.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioCaptureImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : AudioCapture {

    override fun frames(): Flow<ShortArray> = callbackFlow {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("RECORD_AUDIO permission not granted")
        }

        val minBuf = AudioRecord.getMinBufferSize(
            AudioFormat.SAMPLE_RATE_HZ,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = maxOf(minBuf, AudioFormat.BYTES_PER_FRAME * 4)

        @SuppressLint("MissingPermission")
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            AudioFormat.SAMPLE_RATE_HZ,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("AudioRecord init failed (state=${record.state})")
        }

        val frame = ShortArray(AudioFormat.SAMPLES_PER_FRAME)
        val stop = AtomicBoolean(false)

        val thread = Thread({
            try {
                record.startRecording()
                while (!stop.get() && !Thread.currentThread().isInterrupted) {
                    var read = 0
                    while (read < frame.size) {
                        val n = record.read(frame, read, frame.size - read)
                        if (n <= 0) {
                            if (stop.get()) return@Thread
                            continue
                        }
                        read += n
                    }
                    val copy = frame.copyOf()
                    val r = trySend(copy)
                    if (r.isFailure) Log.w(TAG, "frame dropped (channel full)")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "AudioRecord thread crashed", t)
                close(t)
            } finally {
                try { record.stop() } catch (_: Throwable) {}
                record.release()
            }
        }, "MamY-AudioCapture").apply { isDaemon = true; start() }

        awaitClose {
            stop.set(true)
            thread.interrupt()
            thread.join(500)
        }
    }

    private companion object { const val TAG = "AudioCapture" }
}
