package com.mamy.android.data.audio

import com.konovalov.vad.webrtc.Vad
import com.konovalov.vad.webrtc.config.FrameSize
import com.konovalov.vad.webrtc.config.Mode
import com.konovalov.vad.webrtc.config.SampleRate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Consumes a [Flow] of PCM frames and applies VAD :
 *   - waits for first speech frame (otherwise NoSpeech after 5 s)
 *   - then accumulates until 1.5 s of trailing silence
 *   - hard cap at 90 s
 */
@Singleton
class VadProcessor @Inject constructor() {

    /** Override-able for testing. */
    internal var vadFactory: () -> SimpleVad = ::createWebRtcVad

    suspend fun captureUntilSilence(frames: Flow<ShortArray>): VadResult {
        val vad = vadFactory()
        try {
            val pcmBuffer = ShortArray(AudioFormat.MAX_SAMPLES)
            var pcmOffset = 0
            var speechSeen = false
            var trailingSilenceFrames = 0
            var noSpeechFrames = 0

            val silenceFramesThreshold =
                SILENCE_CUT_MS / AudioFormat.FRAME_DURATION_MS  // 1500/30 = 50
            val noSpeechAbortFrames =
                NO_SPEECH_ABORT_MS / AudioFormat.FRAME_DURATION_MS // 5000/30 = ~166

            var resultMarker: VadResult? = null

            frames.takeWhile { frame ->
                // Append frame
                val toCopy = minOf(frame.size, pcmBuffer.size - pcmOffset)
                System.arraycopy(frame, 0, pcmBuffer, pcmOffset, toCopy)
                pcmOffset += toCopy

                // Hard cap
                if (pcmOffset >= AudioFormat.MAX_SAMPLES) {
                    resultMarker = VadResult.MaxDuration(
                        pcmBuffer.copyOf(pcmOffset), pcmOffset / AudioFormat.SAMPLE_RATE_HZ)
                    return@takeWhile false
                }

                val isSpeech = vad.isSpeech(frame)
                if (isSpeech) {
                    speechSeen = true
                    trailingSilenceFrames = 0
                    noSpeechFrames = 0
                } else if (speechSeen) {
                    trailingSilenceFrames++
                    if (trailingSilenceFrames >= silenceFramesThreshold) {
                        val sampleCount = pcmOffset - trailingSilenceFrames * AudioFormat.SAMPLES_PER_FRAME
                        val trimmed = pcmBuffer.copyOf(maxOf(sampleCount, 0))
                        resultMarker = VadResult.Captured(trimmed, trimmed.size / AudioFormat.SAMPLE_RATE_HZ)
                        return@takeWhile false
                    }
                } else {
                    noSpeechFrames++
                    if (noSpeechFrames >= noSpeechAbortFrames) {
                        resultMarker = VadResult.NoSpeech(ShortArray(0), 0)
                        return@takeWhile false
                    }
                }
                true
            }.collect { /* drain */ }

            return resultMarker ?: VadResult.Captured(
                pcmBuffer.copyOf(pcmOffset),
                pcmOffset / AudioFormat.SAMPLE_RATE_HZ,
            )
        } finally {
            vad.close()
        }
    }

    private fun createWebRtcVad(): SimpleVad {
        val v = Vad.builder()
            .setSampleRate(SampleRate.SAMPLE_RATE_16K)
            .setFrameSize(FrameSize.FRAME_SIZE_480)
            .setMode(Mode.AGGRESSIVE)
            .build()
        return object : SimpleVad {
            override fun isSpeech(frame: ShortArray): Boolean = v.isSpeech(frame)
            override fun close() = v.close()
        }
    }

    private companion object {
        const val SILENCE_CUT_MS = 1500
        const val NO_SPEECH_ABORT_MS = 5000
    }
}

/** Test-friendly facade over the WebRTC VAD library. */
internal interface SimpleVad {
    fun isSpeech(frame: ShortArray): Boolean
    fun close()
}
