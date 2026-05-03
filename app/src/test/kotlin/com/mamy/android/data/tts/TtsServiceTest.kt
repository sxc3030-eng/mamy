package com.mamy.android.data.tts

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TtsServiceTest {

    /**
     * NOTE: Robolectric's TextToSpeech shadow does NOT auto-fire onInit/onDone callbacks
     * on the main looper unless we idle it explicitly. We don't *block* on speak() in
     * these tests — the goal is only to ensure construction + setRate + interrupt don't
     * crash. End-to-end TTS playback is exercised in the instrumented test (Task 22,
     * skipped here per scope).
     */

    @Test
    fun `construction does not throw`() {
        val sut = TtsService(ApplicationProvider.getApplicationContext())
        shadowOf(Looper.getMainLooper()).idle()
        sut.interrupt() // does not throw before-or-after init
    }

    @Test
    fun `setRate clamps to 0_5-2_0 without crash`() {
        val sut = TtsService(ApplicationProvider.getApplicationContext())
        shadowOf(Looper.getMainLooper()).idle()
        sut.setRate(0.1f)  // clamped to 0.5
        sut.setRate(5f)    // clamped to 2.0
    }

    @Test
    fun `interrupt does not throw before init`() {
        val sut = TtsService(ApplicationProvider.getApplicationContext())
        sut.interrupt()
    }

    @Test
    fun `shutdown after construction does not crash`() {
        val sut = TtsService(ApplicationProvider.getApplicationContext())
        shadowOf(Looper.getMainLooper()).idle()
        sut.shutdown()
    }
}
