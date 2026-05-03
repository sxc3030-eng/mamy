package com.mamy.android.data.wakeword

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class WakeWordModelResolverTest {

    @Test
    fun `resolves english model to filesDir absolute path`() {
        // Robolectric requires a fake asset; we write one before the call
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // shadowOf the assets manager : drop a fake file
        File(ctx.filesDir, "mamy_en.ppn").writeBytes(ByteArray(16) { it.toByte() })

        val resolver = WakeWordModelResolver(ctx)
        val path = resolver.resolveKeywordPath(Locale.ENGLISH)

        assertTrue("path should be absolute", File(path).isAbsolute)
        assertTrue("file must exist", File(path).exists())
        assertTrue("path under filesDir", path.startsWith(ctx.filesDir.absolutePath))
    }

    @Test
    fun `resolves french model when locale=fr`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(ctx.filesDir, "mamy_fr.ppn").writeBytes(ByteArray(16) { it.toByte() })

        val resolver = WakeWordModelResolver(ctx)
        val path = resolver.resolveKeywordPath(Locale.FRENCH)

        assertTrue(path.endsWith("mamy_fr.ppn"))
    }
}
