package com.mamy.android.domain.intent.handler

import com.mamy.android.data.settings.SettingsRepository
import com.mamy.android.data.sms.SmsResult
import com.mamy.android.data.sms.SmsSender
import com.mamy.android.data.sms.VoiceConfirmListener
import com.mamy.android.data.tts.TextToSpeechAdapter
import com.mamy.android.domain.contacts.ContactCandidate
import com.mamy.android.domain.contacts.ContactMatcher
import com.mamy.android.domain.contacts.MatchResult
import com.mamy.android.domain.intent.Intent
import com.mamy.android.domain.intent.IntentResult
import kotlinx.coroutines.flow.first
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P9 — Full orchestrator for [Intent.TextTo]. Implements the spec section 4
 * pipeline :
 *
 *  1. Resolve the recipient via [ContactMatcher.findByName].
 *  2. None -> TTS "Pas de \<X\> dans tes contacts" -> [TextToResult.Cancelled].
 *  3. Multiple -> simple TTS clarifier ("Tu parles de A ou B ?") -> recover
 *     a Single ; on miss -> Cancelled.
 *  4. Pick the MOBILE phone (priority MOBILE > WORK > HOME > OTHER) ; the
 *     [ContactCandidate] surfaced by [ContactMatcher] already encodes that
 *     priority in its `phoneE164` field — null means no number on file.
 *  5. Speak the confirmation : "Je texte à \<name\> au \<phone\> : '\<body\>'.
 *     Confirmé ?" via [TextToSpeechAdapter].
 *  6. Listen for confirmation via [VoiceConfirmListener] (3 sec window).
 *  7. Confirmed -> [SmsSender.send] ; Cancelled/Timeout -> abort + TTS "Annulé".
 *
 * Privacy mode STRICT short-circuits LLM fallback : if the regex grammar
 * already missed (caller would normally invoke an LLM extractor), entering
 * [handle] is the legitimate path because the regex matched. The privacy
 * check in this handler intentionally validates the rawText against the
 * grammar one more time so an attacker who synthesizes a TextTo intent
 * directly cannot bypass STRICT mode.
 */
@Singleton
class TextToHandler @Inject constructor(
    private val matcher: ContactMatcher,
    private val tts: TextToSpeechAdapter,
    private val confirmListener: VoiceConfirmListener,
    private val smsSender: SmsSender,
    private val settings: SettingsRepository,
) {

    /**
     * Run the full flow. Returns a structured [TextToResult] for tests +
     * a spoken-text [IntentResult] hook for the dispatcher.
     */
    suspend fun handle(intent: Intent.TextTo): TextToResult {
        val locale = resolveLocale()
        val privacyMode = settings.privacyModeFlow.first()

        // Privacy STRICT belt-and-suspenders : if the rawText doesn't match the
        // local regex, refuse to continue ; this guards against an LLM-derived
        // Intent.TextTo bypassing the strict-mode contract.
        if (privacyMode == SettingsRepository.PrivacyMode.STRICT) {
            val regexHit = com.mamy.android.domain.intent.IntentGrammar.matchTextTo(intent.rawText)
            if (regexHit == null) {
                tts.speak(strictFallbackPrompt(locale), locale)
                return TextToResult.Cancelled("strict_mode_no_llm")
            }
        }

        // 1. Resolve recipient
        val resolved = when (val match = matcher.findByName(intent.who)) {
            is MatchResult.None -> {
                tts.speak(noContactPrompt(intent.who, locale), locale)
                return TextToResult.Cancelled("no_match")
            }
            is MatchResult.Single -> match.candidate
            is MatchResult.Multiple -> resolveAmbiguity(match.candidates, locale)
                ?: run {
                    tts.speak(unresolvedHomonymePrompt(locale), locale)
                    return TextToResult.Cancelled("homonyme_unresolved")
                }
        }

        // 2. Phone selection (already prioritized by ContactMatcher)
        val phone = resolved.phoneE164
        if (phone.isNullOrBlank()) {
            tts.speak(noPhonePrompt(resolved.displayName, locale), locale)
            return TextToResult.Cancelled("no_phone")
        }

        // 3. Confirmation prompt + listen
        tts.speak(confirmPrompt(resolved.displayName, phone, intent.body, locale), locale)
        val confirm = confirmListener.listenOnce(VoiceConfirmListener.DEFAULT_TIMEOUT_MS, locale)
        if (confirm !is VoiceConfirmListener.ConfirmResult.Confirmed) {
            tts.speak(cancelledPrompt(locale), locale)
            return TextToResult.Cancelled("user_declined")
        }

        // 4. Send
        val result = smsSender.send(
            phoneE164 = phone,
            body = intent.body,
            recipientDisplayName = resolved.displayName,
            contactId = resolved.id,
            linkedPersonId = resolved.linkedPersonId,
            rawIntentText = intent.rawText,
            privacyMode = privacyMode.name.lowercase(),
        )

        return when (result) {
            is SmsResult.Sending -> {
                tts.speak(sentPrompt(locale), locale)
                TextToResult.Sent(result.entryId)
            }
            is SmsResult.Sent -> TextToResult.Sent(result.entryId)
            is SmsResult.Delivered -> TextToResult.Sent(result.entryId)
            is SmsResult.Failed -> {
                tts.speak(failedPrompt(locale), locale)
                TextToResult.Failed(result.reason)
            }
            SmsResult.PermissionDenied -> {
                tts.speak(permissionDeniedPrompt(locale), locale)
                TextToResult.Failed("permission_denied")
            }
            SmsResult.NoCarrier -> {
                tts.speak(noCarrierPrompt(locale), locale)
                TextToResult.Failed("no_carrier")
            }
        }
    }

    /** IntentDispatcher adapter — speaks nothing extra (TTS already happened). */
    suspend fun dispatch(intent: Intent.TextTo): IntentResult = when (val r = handle(intent)) {
        is TextToResult.Sent -> IntentResult.silent()
        is TextToResult.Cancelled -> IntentResult.silent()
        is TextToResult.Failed -> IntentResult.failure(r.reason)
    }

    /** Ask user to disambiguate. Match the response against any candidate name token. */
    private suspend fun resolveAmbiguity(
        candidates: List<ContactCandidate>,
        locale: Locale,
    ): ContactCandidate? {
        if (candidates.size < 2) return candidates.firstOrNull()
        val names = candidates.joinToString(if (locale.language == "fr") " ou " else " or ") { it.displayName }
        val question = if (locale.language == "fr") "Tu parles de $names ?" else "Did you mean $names?"
        tts.speak(question, locale)
        val response = tts.listenOnce(5_000L)?.lowercase().orEmpty()
        if (response.isBlank()) return null
        return candidates.firstOrNull { c ->
            c.displayName.split(" ").any { tok -> tok.length >= 3 && response.contains(tok.lowercase()) }
        }
    }

    private suspend fun resolveLocale(): Locale =
        when (settings.languageFlow.first()) {
            SettingsRepository.Language.FR -> Locale.FRENCH
            SettingsRepository.Language.EN -> Locale.ENGLISH
            SettingsRepository.Language.SYSTEM -> Locale.getDefault()
        }

    // ----- Prompt builders (extracted so wording stays close to call-sites) -----

    private fun noContactPrompt(who: String, locale: Locale) =
        if (locale.language == "fr") "Pas de $who dans tes contacts."
        else "No $who in your contacts."

    private fun noPhonePrompt(name: String, locale: Locale) =
        if (locale.language == "fr") "$name n'a pas de numéro de téléphone."
        else "$name has no phone number on file."

    private fun confirmPrompt(name: String, phone: String, body: String, locale: Locale) =
        if (locale.language == "fr")
            "Je texte à $name au $phone : « $body ». Confirmé ?"
        else
            "I'll text $name at $phone: \"$body\". Confirm?"

    private fun cancelledPrompt(locale: Locale) =
        if (locale.language == "fr") "Annulé." else "Cancelled."

    private fun sentPrompt(locale: Locale) =
        if (locale.language == "fr") "Envoyé." else "Sent."

    private fun failedPrompt(locale: Locale) =
        if (locale.language == "fr") "Échec d'envoi." else "Send failed."

    private fun permissionDeniedPrompt(locale: Locale) =
        if (locale.language == "fr")
            "Permission SMS nécessaire — touche l'app pour activer."
        else "SMS permission required — tap the app to grant."

    private fun noCarrierPrompt(locale: Locale) =
        if (locale.language == "fr") "Pas de réseau cellulaire." else "No cellular service."

    private fun unresolvedHomonymePrompt(locale: Locale) =
        if (locale.language == "fr") "Pas compris, j'annule." else "Didn't catch that, cancelling."

    private fun strictFallbackPrompt(locale: Locale) =
        if (locale.language == "fr") "Reformule simplement, je n'ai pas compris."
        else "Could you rephrase simply?"
}

/** Outcome of [TextToHandler.handle] — finer-grained than [IntentResult] for tests. */
sealed class TextToResult {
    data class Sent(val entryId: java.util.UUID) : TextToResult()
    data class Cancelled(val reason: String) : TextToResult()
    data class Failed(val reason: String) : TextToResult()
}
