package com.mamy.android.data.calendar.google

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CalendarTokenStoreTest {

    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }
    private val store = CalendarTokenStore(prefs, json)

    @Test
    fun `load returns null when key missing`() = runTest {
        every { prefs.getString("calendar_tokens", null) } returns null
        assertNull(store.load())
    }

    @Test
    fun `load returns parsed tokens when present`() = runTest {
        val tokens = CalendarTokens("a", "r", 1234L, "s", "e@x.com")
        every { prefs.getString("calendar_tokens", null) } returns
            json.encodeToString(CalendarTokens.serializer(), tokens)
        assertEquals(tokens, store.load())
    }

    @Test
    fun `save serializes and commits`() = runTest {
        val tokens = CalendarTokens("a", "r", 1234L, "s", "e@x.com")
        every { prefs.edit() } returns editor
        val captured = slot<String>()
        every { editor.putString("calendar_tokens", capture(captured)) } returns editor

        store.save(tokens)

        val decoded = json.decodeFromString(CalendarTokens.serializer(), captured.captured)
        assertEquals(tokens, decoded)
        verify { editor.apply() }
    }

    @Test
    fun `clear removes key`() = runTest {
        every { prefs.edit() } returns editor
        store.clear()
        verify { editor.remove("calendar_tokens") }
        verify { editor.apply() }
    }
}
