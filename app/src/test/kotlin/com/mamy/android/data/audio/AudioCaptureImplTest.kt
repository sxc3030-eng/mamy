package com.mamy.android.data.audio

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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
