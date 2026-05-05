package com.mamy.android.ui.screens.sms

import app.cash.turbine.test
import com.mamy.android.ui.screens.data.SmsHistoryRow
import com.mamy.android.ui.screens.data.SmsHistorySource
import com.mamy.android.ui.screens.data.SmsStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class SmsHistoryViewModelTest {

    private val source = mockk<SmsHistorySource>(relaxed = true)
    private val rowsFlow = MutableStateFlow<List<SmsHistoryRow>>(emptyList())

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { source.observeAll() } returns rowsFlow
    }

    @Test
    fun `query filter narrows visible rows by contact name`() = runTest {
        rowsFlow.value = listOf(
            SmsHistoryRow("1", "Jimmy Tremblay", "+15145551234", "OK ce soir", SmsStatus.SENT, Instant.parse("2026-05-03T18:00:00Z")),
            SmsHistoryRow("2", "Marie Dubois", "+15145557890", "RDV 14h", SmsStatus.SENT, Instant.parse("2026-05-03T14:00:00Z")),
        )
        val vm = SmsHistoryViewModel(source)
        vm.onQueryChange("jimmy")
        vm.state.test {
            var s = awaitItem()
            repeat(3) { if (s.visible.size > 1 || s.visible.isEmpty()) s = awaitItem() }
            assertEquals(1, s.visible.size)
            assertEquals("Jimmy Tremblay", s.visible.first().contactName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty query shows all rows`() = runTest {
        rowsFlow.value = listOf(
            SmsHistoryRow("1", "Jimmy", "+1234", "msg1", SmsStatus.SENT, Instant.now()),
            SmsHistoryRow("2", "Marie", "+5678", "msg2", SmsStatus.SENT, Instant.now()),
        )
        val vm = SmsHistoryViewModel(source)
        vm.state.test {
            var s = awaitItem()
            repeat(3) { if (s.visible.size != 2) s = awaitItem() }
            assertEquals(2, s.visible.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `rows sorted newest first`() = runTest {
        val older = SmsHistoryRow("1", "A", "+1", "old", SmsStatus.SENT, Instant.parse("2026-05-01T10:00:00Z"))
        val newer = SmsHistoryRow("2", "B", "+2", "new", SmsStatus.SENT, Instant.parse("2026-05-03T10:00:00Z"))
        rowsFlow.value = listOf(older, newer)
        val vm = SmsHistoryViewModel(source)
        vm.state.test {
            var s = awaitItem()
            repeat(3) { if (s.rows.firstOrNull()?.id != "2") s = awaitItem() }
            assertEquals("2", s.rows.first().id)
            assertTrue(s.rows.last().id == "1")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
