package com.mamy.android.data.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import com.mamy.android.data.llm.model.StructuredNote
import com.mamy.android.util.Lang
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class TtsConfirmer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val builder: MessageBuilder = MessageBuilder(),
) {

    @Volatile private var tts: TextToSpeech? = null

    suspend fun confirm(note: StructuredNote, language: Lang) {
        val message = builder.confirmation(note, language)
        val engine = ensureInitialized() ?: return
        engine.language = if (language == Lang.FR) Locale.FRENCH else Locale.ENGLISH
        engine.speak(message, TextToSpeech.QUEUE_ADD, null, "mamy-confirm")
    }

    private suspend fun ensureInitialized(): TextToSpeech? {
        tts?.let { return it }
        return suspendCancellableCoroutine { cont ->
            lateinit var engine: TextToSpeech
            engine = TextToSpeech(context) { status ->
                if (cont.isActive) {
                    if (status == TextToSpeech.SUCCESS) {
                        tts = engine
                        cont.resume(engine)
                    } else {
                        cont.resume(null)
                    }
                }
            }
        }
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
    }

    /** Pure text builder — kept separate so it's unit-testable without a real TTS engine. */
    class MessageBuilder {
        fun confirmation(note: StructuredNote, language: Lang): String {
            val actions = note.actions.size
            val flags = note.flags.size
            return when (language) {
                Lang.FR -> {
                    val pers = if (flags <= 1) "personne flaggée" else "personnes flaggées"
                    val act = if (actions <= 1) "action" else "actions"
                    "Noté. $actions $act, $flags $pers."
                }
                Lang.EN -> {
                    val pers = if (flags == 1) "person flagged" else "people flagged"
                    val act = if (actions == 1) "action" else "actions"
                    "Noted. $actions $act, $flags $pers."
                }
            }
        }
    }
}
