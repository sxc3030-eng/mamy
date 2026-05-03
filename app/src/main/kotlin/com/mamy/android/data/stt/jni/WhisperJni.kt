package com.mamy.android.data.stt.jni

/**
 * Thin JNI wrapper. Single context per WhisperJni instance; not thread-safe.
 * Lifecycle : [initContext] → many [transcribe] → [freeContext].
 */
class WhisperJni {
    private var nativePtr: Long = 0L

    fun initContext(modelPath: String) {
        require(nativePtr == 0L) { "Already initialized" }
        nativePtr = initContextNative(modelPath)
        require(nativePtr != 0L) { "whisper_init_from_file failed for $modelPath" }
    }

    /**
     * @param pcm 16-bit PCM @ 16 kHz mono
     * @param language ISO 639-1, e.g. "en", "fr", or "auto"
     */
    fun transcribe(pcm: ShortArray, language: String): String {
        require(nativePtr != 0L) { "Context not initialized" }
        return transcribeNative(nativePtr, pcm, language) ?: ""
    }

    fun freeContext() {
        if (nativePtr != 0L) {
            freeContextNative(nativePtr)
            nativePtr = 0L
        }
    }

    private external fun initContextNative(modelPath: String): Long
    private external fun transcribeNative(ctxPtr: Long, pcm: ShortArray, language: String): String?
    private external fun freeContextNative(ctxPtr: Long)

    external fun pingNative(): String

    companion object {
        init { System.loadLibrary("mamy_whisper") }
    }
}
