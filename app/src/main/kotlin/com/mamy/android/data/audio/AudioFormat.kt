package com.mamy.android.data.audio

/** 16-bit mono PCM @ 16 kHz — the format Whisper consumes natively. */
object AudioFormat {
    const val SAMPLE_RATE_HZ = 16_000
    const val FRAME_DURATION_MS = 30                  // 480 samples/frame, WebRTC VAD friendly
    const val SAMPLES_PER_FRAME = SAMPLE_RATE_HZ * FRAME_DURATION_MS / 1000  // 480
    const val BYTES_PER_FRAME = SAMPLES_PER_FRAME * 2 // 16-bit = 2 bytes
    const val MAX_DURATION_SEC = 90
    const val MAX_SAMPLES = SAMPLE_RATE_HZ * MAX_DURATION_SEC
}
