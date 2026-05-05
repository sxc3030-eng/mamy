package com.mamy.android.ui.screens.data

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DataViewModelTest {

    private val statsSource = mockk<DataStatsSource>(relaxed = true)
    private val actions = mockk<DataActions>(relaxed = true)
    private val statsFlow = MutableStateFlow(DataStats(0, 0, 0, 0))

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        io.mockk.every { statsSource.observeStats() } returns statsFlow
    }

    @Test
    fun `state surfaces stats from source`() = runTest {
        statsFlow.value = DataStats(personCount = 47, noteCount = 192, openActionCount = 28, smsSentCount = 156)
        val vm = DataViewModel(statsSource, actions)
        vm.state.test {
            // Drain initial empty state
            var s = awaitItem()
            if (s.stats.personCount == 0) s = awaitItem()
            assertEquals(47, s.stats.personCount)
            assertEquals(156, s.stats.smsSentCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `exportAll success sets lastExportPath and clears error`() = runTest {
        coEvery { actions.exportAll(any()) } returns ExportOutcome.Success("/tmp/x.tar.aes")
        val vm = DataViewModel(statsSource, actions)
        vm.exportAll("password1234")

        vm.state.test {
            // The viewmodel emits initial then updated; drain until path appears.
            var s = awaitItem()
            repeat(5) {
                if (s.lastExportPath == null) s = awaitItem()
            }
            assertEquals("/tmp/x.tar.aes", s.lastExportPath)
            assertFalse(s.isExporting)
            assertNull(s.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { actions.exportAll("password1234") }
    }

    @Test
    fun `exportAll failure surfaces errorMessage`() = runTest {
        coEvery { actions.exportAll(any()) } returns ExportOutcome.Failure("disk full")
        val vm = DataViewModel(statsSource, actions)
        vm.exportAll("password1234")

        vm.state.test {
            var s = awaitItem()
            repeat(5) {
                if (s.errorMessage == null) s = awaitItem()
            }
            assertEquals("disk full", s.errorMessage)
            assertNull(s.lastExportPath)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `wipeAll delegates to actions`() = runTest {
        val vm = DataViewModel(statsSource, actions)
        vm.wipeAll()
        coVerify { actions.wipeAll() }
    }

    @Test
    fun `wipePerson delegates to actions`() = runTest {
        val vm = DataViewModel(statsSource, actions)
        vm.wipePerson("person-42")
        coVerify { actions.wipePerson("person-42") }
    }
}
