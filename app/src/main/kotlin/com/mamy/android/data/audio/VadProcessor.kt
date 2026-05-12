package com.mamy.android.data.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.takeWhile
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Consumes a [Flow] of PCM frames and applies VAD :
 *   - waits for first speech frame (otherwise NoSpeech after 8 s)
 *   - then accumulates until 3.5 s of trailing silence
 *   - hard cap at 90 s
 *
 * The 3.5 s trailing-silence threshold (was 1.5 s in v0.4.4) accommodates natural
 * thinking pauses mid-debrief — a manager reflecting between sentences should
 * not have the capture cut off after a brief hesitation. The 8 s no-speech
 * abort (was 5 s) gives the user time to mentally compose after tapping the
 * Record FAB.
 *
 * Uses a simple RMS-energy detector (no external lib). Good enough for our wedge :
 * detect "user stopped speaking after a few s of silence". WebRTC VAD would give
 * better voice/noise classification but the energy threshold is sufficient for
 * desk + corridor recordings. Threshold is tunable via [SimpleEnergyVad.rmsThreshold].
 */
@Singleton
class VadProcessor @Inject constructor() {

    /** Override-able for testing. */
    internal var vadFactory: () -> SimpleVad = ::createEnergyVad

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

    private fun createEnergyVad(): SimpleVad = SimpleEnergyVad()

    private companion object {
        const val SILENCE_CUT_MS = 3500
        const val NO_SPEECH_ABORT_MS = 8000
    }
}

/** Test-friendly facade over the VAD detector. */
internal interface SimpleVad {
    fun isSpeech(frame: ShortArray): Boolean
    fun close()
}

/**
 * RMS-energy-based VAD. Computes root-mean-square of the 16-bit PCM frame and compares
 * against a threshold. Tuned conservatively : threshold ~200 corresponds to roughly
 * "ambient room noise" being silence, "spoken words" being speech.
 *
 * The threshold can be tuned per-device via settings. We avoid an external WebRTC VAD lib
 * (JitPack reachability issues + unnecessary complexity for V1). If we need better
 * voice/noise classification, swap this for whisper.cpp's built-in VAD or a TFLite
 * model in V2.
 */
internal class SimpleEnergyVad(
    private val rmsThreshold: Double = DEFAULT_RMS_THRESHOLD,
) : SimpleVad {

    override fun isSpeech(frame: ShortArray): Boolean {
        if (frame.isEmpty()) return false
        var sumSq = 0.0
        for (sample in frame) {
            val v = sample.toDouble()
            sumSq += v * v
        }
        val rms = sqrt(sumSq / frame.size)
        return rms > rmsThreshold
    }

    override fun close() { /* no-op */ }

    companion object {
        // 16-bit PCM range : -32768..32767. Ambient room ~50-100 RMS, normal speech ~500-3000 RMS.
        const val DEFAULT_RMS_THRESHOLD = 200.0
    }
}
