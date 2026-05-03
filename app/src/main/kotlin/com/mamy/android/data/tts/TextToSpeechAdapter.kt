package com.mamy.android.data.tts

import java.util.Locale

/**
 * Two-way TTS contract used by [com.mamy.android.domain.intent.handler.HomonymeClarifier]
 * and any future P5/P6 handler that needs to ask a clarifying question and listen
 * for a vocal response.
 *
 * V1 stub impl is shipped in [TextToSpeechAdapterImpl]. The real engine wraps an
 * Android [android.speech.tts.TextToSpeech] for [speak] and the same Whisper STT
 * pipeline that drives capture for [listenOnce].
 */
interface TextToSpeechAdapter {
    suspend fun speak(text: String, lang: Locale)
    suspend fun listenOnce(timeoutMs: Long): String?
}
