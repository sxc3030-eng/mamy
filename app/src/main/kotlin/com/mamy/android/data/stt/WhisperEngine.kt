package com.mamy.android.data.stt

import kotlinx.coroutines.flow.Flow

/** STT operations exposed to the rest of the app. */
interface WhisperEngine {

    /** True if the model file is on disk and validated. */
    suspend fun isModelReady(): Boolean

    /**
     * Downloads the model if missing. Emits 0..100 progress percentages.
     * Last value is always 100 on success; flow completes after.
     * Throws [java.io.IOException] on network failure or hash mismatch.
     */
    fun downloadModel(): Flow<Int>

    /**
     * Transcribes 16-bit PCM @ 16 kHz mono. [language] is "en", "fr" or "auto".
     * Throws [IllegalStateException] if model not ready.
     */
    suspend fun transcribe(pcm: ShortArray, language: String): Result<String>
}
