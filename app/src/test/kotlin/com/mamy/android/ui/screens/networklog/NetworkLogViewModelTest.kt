package com.mamy.android.ui.screens.networklog

import app.cash.turbine.test
import com.mamy.android.data.network.NetworkLogEntry
import com.mamy.android.data.network.NetworkLogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class NetworkLogViewModelTest {

    @BeforeEach
    fun setupMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @Test
    fun `state surfaces entries newest-first`() = runTest {
        val store = NetworkLogStore()
        val older = NetworkLogEntry(
            category = NetworkLogEntry.Category.CALENDAR,
            timestamp = Instant.parse("2026-05-01T08:00:00Z"),
            method = "GET",
            url = "https://www.googleapis.com/calendar/v3/calendars/primary/events",
            statusCode = 200,
            durationMs = 124,
        )
        val newer = NetworkLogEntry(
            category = NetworkLogEntry.Category.LLM,
            timestamp = Instant.parse("2026-05-03T08:00:00Z"),
            method = "POST",
            url = "https://api.anthropic.com/v1/messages",
            statusCode = 200,
            durationMs = 542,
        )
        store.append(older)
        store.append(newer)

        val vm = NetworkLogViewModel(store)
        vm.state.test {
            val s = awaitItem()
            assertEquals(2, s.entries.size)
            // Newest first.
            assertEquals(newer.url, s.entries.first().url)
            assertEquals(older.url, s.entries.last().url)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setFilter narrows visible to category`() = runTest {
        val store = NetworkLogStore()
        store.append(
            NetworkLogEntry(
                category = NetworkLogEntry.Category.CALENDAR,
                timestamp = Instant.now(),
                method = "GET",
                url = "https://www.googleapis.com/calendar/v3/calendars",
                statusCode = 200,
                durationMs = 90,
            )
        )
        store.append(
            NetworkLogEntry(
                category = NetworkLogEntry.Category.LLM,
                timestamp = Instant.now(),
                method = "POST",
                url = "https://api.anthropic.com/v1/messages",
                statusCode = 200,
                durationMs = 700,
            )
        )

        val vm = NetworkLogViewModel(store)
        vm.setFilter(NetworkLogEntry.Category.LLM)

        vm.state.test {
            val s = awaitItem()
            assertEquals(2, s.entries.size, "raw entries unchanged")
            assertEquals(1, s.visible.size, "filter narrows visible to LLM only")
            assertEquals(NetworkLogEntry.Category.LLM, s.visible.first().category)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setFilter null restores all entries`() = runTest {
        val store = NetworkLogStore()
        store.append(
            NetworkLogEntry(
                category = NetworkLogEntry.Category.CALENDAR,
                timestamp = Instant.now(),
                method = "GET",
                url = "https://x",
                statusCode = 200,
                durationMs = 1,
            )
        )
        val vm = NetworkLogViewModel(store)
        vm.setFilter(NetworkLogEntry.Category.STT)
        vm.setFilter(null)

        vm.state.test {
            val s = awaitItem()
            assertNull(s.filter)
            assertEquals(s.entries.size, s.visible.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
