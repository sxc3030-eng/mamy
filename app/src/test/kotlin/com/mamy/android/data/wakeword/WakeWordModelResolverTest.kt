package com.mamy.android.data.wakeword

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WakeWordModelResolverTest {

    @Test
    fun `resolves english model to filesDir absolute path when pre-copied`() {
        // The resolver's fast path picks up an already-copied file in filesDir
        // and skips the assets-open call entirely — that's what we exercise here.
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(ctx.filesDir, "mamy_en.ppn").writeBytes(ByteArray(16) { it.toByte() })

        val resolver = WakeWordModelResolver(ctx)
        val path = resolver.resolveKeywordPathOrNull(Locale.ENGLISH)

        assertNotNull("path should be non-null when file exists", path)
        assertTrue("path should be absolute", File(path!!).isAbsolute)
        assertTrue("file must exist", File(path).exists())
        assertTrue("path under filesDir", path.startsWith(ctx.filesDir.absolutePath))
    }

    @Test
    fun `resolves french model when locale=fr and file present`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(ctx.filesDir, "mamy_fr.ppn").writeBytes(ByteArray(16) { it.toByte() })

        val resolver = WakeWordModelResolver(ctx)
        val path = resolver.resolveKeywordPathOrNull(Locale.FRENCH)

        assertNotNull(path)
        assertTrue(path!!.endsWith("mamy_fr.ppn"))
    }

    @Test
    fun `returns null when no asset and no pre-copied file exists`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Make sure no leftover from a previous test (Robolectric reuses filesDir
        // between tests within the same class).
        File(ctx.filesDir, "mamy_en.ppn").delete()

        val resolver = WakeWordModelResolver(ctx)
        val path = resolver.resolveKeywordPathOrNull(Locale.ENGLISH)

        assertNull("caller must fall back to a built-in keyword", path)
    }
}
