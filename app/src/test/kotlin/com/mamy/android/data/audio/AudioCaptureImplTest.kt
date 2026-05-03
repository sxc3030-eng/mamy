package com.mamy.android.data.audio

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AudioCaptureImplTest {

    @Test
    fun `frames throws SecurityException without RECORD_AUDIO permission`() {
        // Robolectric default : no runtime permissions granted
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val capture = AudioCaptureImpl(ctx)

        assertThrows(SecurityException::class.java) {
            runBlocking { capture.frames().first() }
        }
    }
}
