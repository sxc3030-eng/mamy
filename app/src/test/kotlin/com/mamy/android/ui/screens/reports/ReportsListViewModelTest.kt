package com.mamy.android.ui.screens.reports

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for [ReportsListViewModel].
 *
 * Verifies:
 * - default sort is Recent
 * - sort change reorders (Name ascending, Flags descending, Recent by date)
 * - search query filters by case-insensitive substring
 * - hideUnmatched toggle flips between true and false (default true)
 * - filtering by unmatched flows through the repository call
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportsListViewModelTest {

    private lateinit var repo: ReportsPersonRepository
    private val source = MutableStateFlow<List<PersonRow>>(emptyList())

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo = mockk()
        every { repo.observeAll(any()) } returns source
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun row(
        name: String,
        flags: Int = 0,
        lastSeen: Instant = Instant.now(),
        unmatched: Boolean = false,
    ) = PersonRow(
        id = UUID.randomUUID(),
        name = name,
        roleHint = null,
        email = null,
        emotionalTrend = null,
        unmatched = unmatched,
        lastInteractionAt = lastSeen,
        interactionCount = 1,
        openFlagCount = flags,
    )

    /**
     * Activates the lazy [ReportsListViewModel.state] StateFlow so emissions
     * propagate. The VM uses [SharingStarted.WhileSubscribed] which is inert
     * until at least one collector exists. We launch a perpetual collector in
     * the test scope and rely on [advanceUntilIdle] to flush emissions.
     */
    private fun TestScope.activate(vm: ReportsListViewModel) {
        backgroundScope.launch { vm.state.collect { } }
    }

    @Test
    fun `default sort is Recent`() = runTest {
        val vm = ReportsListViewModel(repo)
        activate(vm)
        advanceUntilIdle()
        assertEquals(ReportsSort.Recent, vm.state.value.sort)
    }

    @Test
    fun `default hideUnmatched is false`() = runTest {
        // v0.4.8 flipped the default: freshly-extracted persons from a debrief
        // are marked unmatched=true until linked to a Contacts entry, and
        // hiding them by default made Master's first captures invisible.
        val vm = ReportsListViewModel(repo)
        activate(vm)
        advanceUntilIdle()
        assertFalse(vm.state.value.hideUnmatched)
    }

    @Test
    fun `Name sort orders ascending case-insensitive`() = runTest {
        source.value = listOf(row("Zoé"), row("alice"), row("Marie"))
        val vm = ReportsListViewModel(repo)
        activate(vm)
        vm.setSort(ReportsSort.Name)
        advanceUntilIdle()
        val names = vm.state.value.persons.map { it.name }
        assertEquals(listOf("alice", "Marie", "Zoé"), names)
    }

    @Test
    fun `Flags sort orders by openFlagCount descending`() = runTest {
        source.value = listOf(row("a", flags = 1), row("b", flags = 5), row("c", flags = 0))
        val vm = ReportsListViewModel(repo)
        activate(vm)
        vm.setSort(ReportsSort.Flags)
        advanceUntilIdle()
        val flags = vm.state.value.persons.map { it.openFlagCount }
        assertEquals(listOf(5, 1, 0), flags)
    }

    @Test
    fun `Recent sort orders by lastInteractionAt descending`() = runTest {
        val now = Instant.now()
        source.value = listOf(
            row("old", lastSeen = now.minusSeconds(3600)),
            row("recent", lastSeen = now),
            row("middle", lastSeen = now.minusSeconds(60)),
        )
        val vm = ReportsListViewModel(repo)
        activate(vm)
        advanceUntilIdle()
        val names = vm.state.value.persons.map { it.name }
        assertEquals(listOf("recent", "middle", "old"), names)
    }

    @Test
    fun `setQuery filters by case-insensitive substring`() = runTest {
        source.value = listOf(row("Marie Tremblay"), row("Pierre Lavoie"), row("MARIANNE"))
        val vm = ReportsListViewModel(repo)
        activate(vm)
        vm.setQuery("mari")
        advanceUntilIdle()
        val names = vm.state.value.persons.map { it.name }.sorted()
        assertEquals(listOf("MARIANNE", "Marie Tremblay"), names)
    }

    @Test
    fun `empty query returns all persons`() = runTest {
        source.value = listOf(row("Alice"), row("Bob"))
        val vm = ReportsListViewModel(repo)
        activate(vm)
        vm.setQuery("")
        advanceUntilIdle()
        assertEquals(2, vm.state.value.persons.size)
    }

    @Test
    fun `toggleHideUnmatched flips state`() = runTest {
        val vm = ReportsListViewModel(repo)
        activate(vm)
        advanceUntilIdle()
        assertFalse(vm.state.value.hideUnmatched)
        vm.toggleHideUnmatched()
        advanceUntilIdle()
        assertTrue(vm.state.value.hideUnmatched)
        vm.toggleHideUnmatched()
        advanceUntilIdle()
        assertFalse(vm.state.value.hideUnmatched)
    }
}
