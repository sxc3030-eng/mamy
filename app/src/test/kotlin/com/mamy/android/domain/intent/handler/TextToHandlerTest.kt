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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale
import java.util.UUID

/**
 * P9 W1-E task 12 — Behavioural tests for [TextToHandler].
 *
 * Avoids spinning up Robolectric : every collaborator is mocked and the
 * SettingsRepository is faked via a tiny stub that returns the desired
 * privacyMode + language flow. Focuses on flow-control, not regex grammar
 * (covered in IntentGrammarTextToTest) or send mechanics (SmsSenderTest).
 */
class TextToHandlerTest {

    @Test
    fun `happy path - sends and returns Sent`() = runTest {
        val matcher = mockk<ContactMatcher>()
        val tts = mockk<TextToSpeechAdapter>(relaxed = true)
        val confirmer = mockk<VoiceConfirmListener>()
        val sender = mockk<SmsSender>()
        val settings = fakeSettings(SettingsRepository.PrivacyMode.STANDARD)

        coEvery { matcher.findByName("Jimmy") } returns MatchResult.Single(
            ContactCandidate(id = "42", displayName = "Jimmy Tremblay", phoneE164 = "+15145551234"),
        )
        coEvery { confirmer.listenOnce(any(), any()) } returns
            VoiceConfirmListener.ConfirmResult.Confirmed
        val entryId = UUID.randomUUID()
        coEvery {
            sender.send(any(), any(), any(), any(), any(), any(), any())
        } returns SmsResult.Sending(entryId, segments = 1)

        val handler = TextToHandler(matcher, tts, confirmer, sender, settings)
        val intent = Intent.TextTo(
            who = "Jimmy",
            body = "c'est bon pour ce soir",
            rawText = "MamY texte à Jimmy que c'est bon pour ce soir",
        )
        val result = handler.handle(intent)

        assertTrue(result is TextToResult.Sent)
        assertEquals(entryId, (result as TextToResult.Sent).entryId)
        coVerify(exactly = 1) {
            sender.send(
                phoneE164 = "+15145551234",
                body = "c'est bon pour ce soir",
                recipientDisplayName = "Jimmy Tremblay",
                contactId = "42",
                linkedPersonId = null,
                rawIntentText = intent.rawText,
                privacyMode = "standard",
            )
        }
    }

    @Test
    fun `none match - cancels with no_match`() = runTest {
        val matcher = mockk<ContactMatcher>()
        val tts = mockk<TextToSpeechAdapter>(relaxed = true)
        val confirmer = mockk<VoiceConfirmListener>()
        val sender = mockk<SmsSender>()
        val settings = fakeSettings(SettingsRepository.PrivacyMode.STANDARD)
        coEvery { matcher.findByName("Bobby") } returns MatchResult.None

        val handler = TextToHandler(matcher, tts, confirmer, sender, settings)
        val result = handler.handle(
            Intent.TextTo(who = "Bobby", body = "salut", rawText = "MamY texte à Bobby que salut"),
        )

        assertTrue(result is TextToResult.Cancelled)
        assertEquals("no_match", (result as TextToResult.Cancelled).reason)
        coVerify(exactly = 0) { sender.send(any(), any(), any(), any(), any(), any(), any()) }
        coVerify { tts.speak(match { it.contains("Bobby") }, Locale.FRENCH) }
    }

    @Test
    fun `homonyme - clarifies and continues`() = runTest {
        val matcher = mockk<ContactMatcher>()
        val tts = mockk<TextToSpeechAdapter>()
        val confirmer = mockk<VoiceConfirmListener>()
        val sender = mockk<SmsSender>()
        val settings = fakeSettings(SettingsRepository.PrivacyMode.STANDARD)

        coEvery { matcher.findByName("Jimmy") } returns MatchResult.Multiple(
            listOf(
                ContactCandidate(id = "1", displayName = "Jimmy Tremblay", phoneE164 = "+15145551111"),
                ContactCandidate(id = "2", displayName = "Jimmy Lebrun", phoneE164 = "+15145552222"),
            ),
        )
        coEvery { tts.speak(any(), any()) } returnsMany listOf(Unit, Unit, Unit)
        coEvery { tts.listenOnce(any()) } returns "Tremblay"
        coEvery { confirmer.listenOnce(any(), any()) } returns
            VoiceConfirmListener.ConfirmResult.Confirmed
        val entryId = UUID.randomUUID()
        coEvery {
            sender.send(any(), any(), any(), any(), any(), any(), any())
        } returns SmsResult.Sending(entryId, 1)

        val handler = TextToHandler(matcher, tts, confirmer, sender, settings)
        val result = handler.handle(
            Intent.TextTo(who = "Jimmy", body = "salut", rawText = "MamY texte à Jimmy que salut"),
        )

        assertTrue(result is TextToResult.Sent)
        coVerify(exactly = 1) {
            sender.send(
                phoneE164 = "+15145551111",
                body = any(), recipientDisplayName = any(), contactId = any(),
                linkedPersonId = any(), rawIntentText = any(), privacyMode = any(),
            )
        }
    }

    @Test
    fun `no phone on candidate - cancels with no_phone`() = runTest {
        val matcher = mockk<ContactMatcher>()
        val tts = mockk<TextToSpeechAdapter>(relaxed = true)
        val confirmer = mockk<VoiceConfirmListener>()
        val sender = mockk<SmsSender>()
        val settings = fakeSettings(SettingsRepository.PrivacyMode.STANDARD)

        coEvery { matcher.findByName("Marie") } returns MatchResult.Single(
            ContactCandidate(id = "9", displayName = "Marie Dubois", phoneE164 = null),
        )

        val handler = TextToHandler(matcher, tts, confirmer, sender, settings)
        val result = handler.handle(
            Intent.TextTo(who = "Marie", body = "à demain", rawText = "MamY texte à Marie que à demain"),
        )

        assertTrue(result is TextToResult.Cancelled)
        assertEquals("no_phone", (result as TextToResult.Cancelled).reason)
        coVerify(exactly = 0) { sender.send(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `user says non - cancels and never sends`() = runTest {
        val matcher = mockk<ContactMatcher>()
        val tts = mockk<TextToSpeechAdapter>(relaxed = true)
        val confirmer = mockk<VoiceConfirmListener>()
        val sender = mockk<SmsSender>()
        val settings = fakeSettings(SettingsRepository.PrivacyMode.STANDARD)

        coEvery { matcher.findByName("Jimmy") } returns MatchResult.Single(
            ContactCandidate(id = "42", displayName = "Jimmy Tremblay", phoneE164 = "+15145551234"),
        )
        coEvery { confirmer.listenOnce(any(), any()) } returns
            VoiceConfirmListener.ConfirmResult.Cancelled

        val handler = TextToHandler(matcher, tts, confirmer, sender, settings)
        val result = handler.handle(
            Intent.TextTo(who = "Jimmy", body = "test test", rawText = "MamY texte à Jimmy que test test"),
        )

        assertTrue(result is TextToResult.Cancelled)
        assertEquals("user_declined", (result as TextToResult.Cancelled).reason)
        coVerify(exactly = 0) { sender.send(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `STRICT mode with regex miss returns strict_mode_no_llm`() = runTest {
        val matcher = mockk<ContactMatcher>()
        val tts = mockk<TextToSpeechAdapter>(relaxed = true)
        val confirmer = mockk<VoiceConfirmListener>()
        val sender = mockk<SmsSender>()
        val settings = fakeSettings(SettingsRepository.PrivacyMode.STRICT)

        // rawText does NOT match the local regex grammar -> STRICT must abort.
        val handler = TextToHandler(matcher, tts, confirmer, sender, settings)
        val result = handler.handle(
            Intent.TextTo(
                who = "Jimmy",
                body = "test test",
                rawText = "ehh peux-tu envoyer un mot rapide à Jimmy",
            ),
        )

        assertTrue(result is TextToResult.Cancelled)
        assertEquals("strict_mode_no_llm", (result as TextToResult.Cancelled).reason)
        coVerify(exactly = 0) { sender.send(any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { matcher.findByName(any()) }
    }

    @Test
    fun `STRICT mode with regex hit proceeds normally`() = runTest {
        val matcher = mockk<ContactMatcher>()
        val tts = mockk<TextToSpeechAdapter>(relaxed = true)
        val confirmer = mockk<VoiceConfirmListener>()
        val sender = mockk<SmsSender>()
        val settings = fakeSettings(SettingsRepository.PrivacyMode.STRICT)

        coEvery { matcher.findByName("Jimmy") } returns MatchResult.Single(
            ContactCandidate(id = "42", displayName = "Jimmy Tremblay", phoneE164 = "+15145551234"),
        )
        coEvery { confirmer.listenOnce(any(), any()) } returns
            VoiceConfirmListener.ConfirmResult.Confirmed
        val entryId = UUID.randomUUID()
        coEvery {
            sender.send(any(), any(), any(), any(), any(), any(), any())
        } returns SmsResult.Sending(entryId, 1)

        val handler = TextToHandler(matcher, tts, confirmer, sender, settings)
        val result = handler.handle(
            Intent.TextTo(
                who = "Jimmy",
                body = "c'est bon pour ce soir",
                rawText = "MamY texte à Jimmy que c'est bon pour ce soir",
            ),
        )

        assertTrue(result is TextToResult.Sent)
        coVerify(exactly = 1) {
            sender.send(any(), any(), any(), any(), any(), any(), privacyMode = "strict")
        }
    }

    @Test
    fun `permission denied - returns Failed and TTS guidance`() = runTest {
        val matcher = mockk<ContactMatcher>()
        val tts = mockk<TextToSpeechAdapter>(relaxed = true)
        val confirmer = mockk<VoiceConfirmListener>()
        val sender = mockk<SmsSender>()
        val settings = fakeSettings(SettingsRepository.PrivacyMode.STANDARD)

        coEvery { matcher.findByName("Jimmy") } returns MatchResult.Single(
            ContactCandidate(id = "42", displayName = "Jimmy", phoneE164 = "+15145551234"),
        )
        coEvery { confirmer.listenOnce(any(), any()) } returns
            VoiceConfirmListener.ConfirmResult.Confirmed
        coEvery { sender.send(any(), any(), any(), any(), any(), any(), any()) } returns
            SmsResult.PermissionDenied

        val handler = TextToHandler(matcher, tts, confirmer, sender, settings)
        val result = handler.handle(
            Intent.TextTo(who = "Jimmy", body = "test test", rawText = "MamY texte à Jimmy que test test"),
        )

        assertTrue(result is TextToResult.Failed)
        assertEquals("permission_denied", (result as TextToResult.Failed).reason)
    }

    private fun fakeSettings(mode: SettingsRepository.PrivacyMode): SettingsRepository {
        val repo = mockk<SettingsRepository>()
        io.mockk.every { repo.privacyModeFlow } returns flowOf(mode)
        io.mockk.every { repo.languageFlow } returns flowOf(SettingsRepository.Language.FR)
        return repo
    }
}
