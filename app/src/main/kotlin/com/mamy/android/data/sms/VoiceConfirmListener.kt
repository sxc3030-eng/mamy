package com.mamy.android.data.sms

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * P9 — Listens for a single yes/no utterance via Android's [SpeechRecognizer]
 * and matches it against the CONFIRM/CANCEL regexes from spec annex A.
 *
 * Default policy : **anything that isn't an explicit confirmation cancels**.
 * Background noise, silence, recognizer errors, "blah" — all fall through to
 * [ConfirmResult.Cancelled]. This is the safety property that keeps an SMS
 * from being sent on a wake-word false-positive.
 *
 * The recognizer is created and torn down per [listenOnce] call ; we never
 * leak a long-lived instance.
 */
@Singleton
open class VoiceConfirmListener @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Listen once for up to [timeoutMs] milliseconds, run the recognized
     * utterance through CONFIRM/CANCEL/Timeout matching, and return.
     *
     * @param locale language-tag the recognizer should expect ; the orchestrator
     *   resolves this from the user's app-language preference.
     */
    open suspend fun listenOnce(
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        locale: Locale = Locale.FRENCH,
    ): ConfirmResult {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return ConfirmResult.Cancelled
        }
        val raw = withTimeoutOrNullSafe(timeoutMs) {
            recognizeOnce(locale)
        } ?: return ConfirmResult.Cancelled

        return classify(raw)
    }

    /** Pure classifier exposed for tests : matches against the CONFIRM / CANCEL regexes. */
    fun classify(utterance: String): ConfirmResult {
        val trimmed = utterance.trim()
        if (CONFIRM.containsMatchIn(trimmed)) return ConfirmResult.Confirmed
        if (CANCEL.containsMatchIn(trimmed)) return ConfirmResult.Cancelled
        return ConfirmResult.Cancelled
    }

    /** Bridges [SpeechRecognizer]'s callback API to a single suspending result. */
    protected open suspend fun recognizeOnce(locale: Locale): String? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}

                override fun onError(error: Int) {
                    runCatching { recognizer.destroy() }
                    if (cont.isActive) cont.resume(null)
                }

                override fun onResults(results: Bundle?) {
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val first = list?.firstOrNull()
                    runCatching { recognizer.destroy() }
                    if (cont.isActive) cont.resume(first)
                }
            })

            cont.invokeOnCancellation { runCatching { recognizer.destroy() } }
            recognizer.startListening(intent)
        }
    }

    private suspend fun <T> withTimeoutOrNullSafe(timeoutMs: Long, block: suspend () -> T): T? =
        try {
            withTimeoutOrNull(timeoutMs) { block() }
        } catch (_: TimeoutCancellationException) {
            null
        }

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 3_000L

        /** Spec annex A — confirm regex, FR + EN. */
        val CONFIRM = Regex(
            "^\\s*(oui|yes|ok|envoie|envoyer|confirm[éée]?|vas-?y|go)\\s*\$",
            RegexOption.IGNORE_CASE,
        )

        /** Spec annex A — cancel regex, FR + EN. */
        val CANCEL = Regex(
            "^\\s*(non|no|annule(?:r)?|stop|arr[êe]te|cancel)\\s*\$",
            RegexOption.IGNORE_CASE,
        )
    }

    sealed class ConfirmResult {
        data object Confirmed : ConfirmResult()
        data object Cancelled : ConfirmResult()
        data object Timeout : ConfirmResult()
    }
}
