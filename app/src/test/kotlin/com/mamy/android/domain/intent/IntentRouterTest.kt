package com.mamy.android.domain.intent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IntentRouterTest {

    private val router = IntentRouter()

    @Test
    fun `stub always returns Capture`() {
        val result = router.route("MamY, ma journée")
        assertTrue(result is Intent.Capture)
        assertEquals("MamY, ma journée", (result as Intent.Capture).rawText)
    }

    @Test
    fun `stub preserves arbitrary input`() {
        val result = router.route("blah blah blah")
        assertEquals(Intent.Capture("blah blah blah"), result)
    }
}
