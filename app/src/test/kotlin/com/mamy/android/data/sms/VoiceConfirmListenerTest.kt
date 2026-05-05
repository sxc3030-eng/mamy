package com.mamy.android.data.sms

import android.content.Context
import com.mamy.android.data.sms.VoiceConfirmListener.ConfirmResult
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * P9 W1-E task 8 — Unit tests for [VoiceConfirmListener].
 *
 * The recognizer round-trip is too involved to fake faithfully under
 * Robolectric, so we drive a thin subclass [FakeListener] that overrides
 * [VoiceConfirmListener.recognizeOnce] to return a canned utterance and
 * exercise both the regex matcher (via [VoiceConfirmListener.classify]) and
 * the suspend dispatch path.
 *
 * Cases :
 *  - "oui" -> Confirmed
 *  - "yes" -> Confirmed
 *  - "non" -> Cancelled
 *  - "blah" -> Cancelled
 *  - null (recognizer error / timeout) -> Cancelled
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VoiceConfirmListenerTest {

    @Test
    fun `classify oui returns Confirmed`() {
        val listener = VoiceConfirmListener(mockk(relaxed = true))
        assertEquals(ConfirmResult.Confirmed, listener.classify("oui"))
    }

    @Test
    fun `classify yes returns Confirmed`() {
        val listener = VoiceConfirmListener(mockk(relaxed = true))
        assertEquals(ConfirmResult.Confirmed, listener.classify("yes"))
    }

    @Test
    fun `classify confirme returns Confirmed`() {
        val listener = VoiceConfirmListener(mockk(relaxed = true))
        assertEquals(ConfirmResult.Confirmed, listener.classify("confirme"))
    }

    @Test
    fun `classify non returns Cancelled`() {
        val listener = VoiceConfirmListener(mockk(relaxed = true))
        assertEquals(ConfirmResult.Cancelled, listener.classify("non"))
    }

    @Test
    fun `classify cancel returns Cancelled`() {
        val listener = VoiceConfirmListener(mockk(relaxed = true))
        assertEquals(ConfirmResult.Cancelled, listener.classify("cancel"))
    }

    @Test
    fun `classify blah falls through to Cancelled`() {
        val listener = VoiceConfirmListener(mockk(relaxed = true))
        assertEquals(ConfirmResult.Cancelled, listener.classify("blah"))
    }

    @Test
    fun `classify empty string is Cancelled`() {
        val listener = VoiceConfirmListener(mockk(relaxed = true))
        assertEquals(ConfirmResult.Cancelled, listener.classify(""))
    }

    @Test
    fun `listenOnce returns Confirmed when recognizer yields oui`() = runTest {
        val listener = FakeListener(mockk(relaxed = true), canned = "oui")
        val result = listener.listenOnce(timeoutMs = 100L, locale = Locale.FRENCH)
        assertEquals(ConfirmResult.Confirmed, result)
    }

    @Test
    fun `listenOnce returns Cancelled when recognizer yields non`() = runTest {
        val listener = FakeListener(mockk(relaxed = true), canned = "non")
        val result = listener.listenOnce(timeoutMs = 100L, locale = Locale.FRENCH)
        assertEquals(ConfirmResult.Cancelled, result)
    }

    @Test
    fun `listenOnce returns Cancelled when recognizer yields gibberish`() = runTest {
        val listener = FakeListener(mockk(relaxed = true), canned = "blah blah")
        val result = listener.listenOnce(timeoutMs = 100L, locale = Locale.FRENCH)
        assertEquals(ConfirmResult.Cancelled, result)
    }

    @Test
    fun `listenOnce returns Cancelled when recognizer errors out`() = runTest {
        val listener = FakeListener(mockk(relaxed = true), canned = null)
        val result = listener.listenOnce(timeoutMs = 100L, locale = Locale.FRENCH)
        assertEquals(ConfirmResult.Cancelled, result)
    }

    /** Subclass that bypasses the real SpeechRecognizer with a canned utterance. */
    private class FakeListener(
        context: Context,
        private val canned: String?,
    ) : VoiceConfirmListener(context) {
        override suspend fun recognizeOnce(locale: Locale): String? = canned
    }
}
