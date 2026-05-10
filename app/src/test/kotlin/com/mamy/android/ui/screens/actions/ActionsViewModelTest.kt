package com.mamy.android.ui.screens.actions

import com.mamy.android.data.db.dao.ActionDao
import com.mamy.android.data.db.dao.NoteDao
import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.entity.ActionEntity
import com.mamy.android.data.db.entity.PersonEntity
import com.mamy.android.data.tts.TtsService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class ActionsViewModelTest {

    private lateinit var actionDao: ActionDao
    private lateinit var personDao: PersonDao
    private lateinit var noteDao: NoteDao
    private lateinit var tts: TtsService

    private val personId = UUID.randomUUID()
    private val person = PersonEntity(
        id = personId,
        name = "Alice",
        email = null, roleHint = null, calendarAttendeeId = null,
        createdAt = Instant.now(), lastInteractionAt = null,
        interactionCount = 1, emotionalTrend = null,
        unmatched = false, archived = false,
    )

    private fun action(
        id: UUID = UUID.randomUUID(),
        status: String = "open",
        description: String = "task",
        deadline: Instant? = null,
        doneAt: Instant? = null,
        createdAt: Instant = Instant.now(),
        linkedPersonId: UUID? = null,
    ) = ActionEntity(
        id = id, description = description, assignee = "self",
        linkedPersonId = linkedPersonId, deadline = deadline,
        status = status, fromNoteId = UUID.randomUUID(),
        createdAt = createdAt, doneAt = doneAt,
    )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        actionDao = mockk(relaxed = true)
        personDao = mockk(relaxed = true)
        noteDao = mockk(relaxed = true)
        tts = mockk(relaxed = true)
        coEvery { actionDao.getAll() } returns emptyList()
        coEvery { personDao.getById(personId) } returns person
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `default filter is Open`() = runTest {
        val vm = ActionsViewModel(actionDao, personDao, noteDao, tts)
        advanceUntilIdle()
        assertEquals(ActionsFilter.Open, vm.state.value.filter)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `Open filter excludes done actions`() = runTest {
        val open = action(description = "open task")
        val done = action(status = "done", description = "done task", doneAt = Instant.now())
        coEvery { actionDao.getAll() } returns listOf(open, done)
        val vm = ActionsViewModel(actionDao, personDao, noteDao, tts)
        advanceUntilIdle()
        val descs = vm.state.value.actions.map { it.action.description }
        assertEquals(listOf("open task"), descs)
    }

    @Test
    fun `Done filter excludes open actions`() = runTest {
        val open = action(description = "open task")
        val done = action(status = "done", description = "done task", doneAt = Instant.now())
        coEvery { actionDao.getAll() } returns listOf(open, done)
        val vm = ActionsViewModel(actionDao, personDao, noteDao, tts)
        vm.setFilter(ActionsFilter.Done)
        advanceUntilIdle()
        val descs = vm.state.value.actions.map { it.action.description }
        assertEquals(listOf("done task"), descs)
    }

    @Test
    fun `All filter returns everything`() = runTest {
        val open = action(description = "open task")
        val done = action(status = "done", description = "done task", doneAt = Instant.now())
        coEvery { actionDao.getAll() } returns listOf(open, done)
        val vm = ActionsViewModel(actionDao, personDao, noteDao, tts)
        vm.setFilter(ActionsFilter.All)
        advanceUntilIdle()
        assertEquals(2, vm.state.value.actions.size)
    }

    @Test
    fun `linked person name is resolved client side`() = runTest {
        val a = action(description = "Email Alice", linkedPersonId = personId)
        coEvery { actionDao.getAll() } returns listOf(a)
        val vm = ActionsViewModel(actionDao, personDao, noteDao, tts)
        advanceUntilIdle()
        val row = vm.state.value.actions.single()
        assertEquals("Alice", row.linkedPersonName)
    }

    @Test
    fun `linked person name is null when not in db`() = runTest {
        val a = action(description = "Email", linkedPersonId = UUID.randomUUID())
        coEvery { actionDao.getAll() } returns listOf(a)
        coEvery { personDao.getById(any()) } returns null
        val vm = ActionsViewModel(actionDao, personDao, noteDao, tts)
        advanceUntilIdle()
        val row = vm.state.value.actions.single()
        assertEquals(null, row.linkedPersonName)
    }

    @Test
    fun `markDone calls dao with current instant`() = runTest {
        val vm = ActionsViewModel(actionDao, personDao, noteDao, tts)
        val id = UUID.randomUUID()
        vm.markDone(id)
        advanceUntilIdle()
        coVerify { actionDao.markDone(id, any()) }
    }

    @Test
    fun `setFilter updates state`() = runTest {
        val vm = ActionsViewModel(actionDao, personDao, noteDao, tts)
        advanceUntilIdle()
        vm.setFilter(ActionsFilter.Done)
        advanceUntilIdle()
        assertEquals(ActionsFilter.Done, vm.state.value.filter)
    }

    @Test
    fun `Open filter sorts by deadline ascending nulls last`() = runTest {
        val now = Instant.parse("2026-05-03T00:00:00Z")
        val noDeadline = action(description = "no deadline", deadline = null)
        val today = action(description = "today", deadline = now)
        val tomorrow = action(description = "tomorrow", deadline = now.plusSeconds(86_400))
        coEvery { actionDao.getAll() } returns listOf(tomorrow, noDeadline, today)
        val vm = ActionsViewModel(actionDao, personDao, noteDao, tts)
        advanceUntilIdle()
        assertEquals(
            listOf("today", "tomorrow", "no deadline"),
            vm.state.value.actions.map { it.action.description },
        )
    }

    @Test
    fun `loading state flips false after first emit`() = runTest {
        val vm = ActionsViewModel(actionDao, personDao, noteDao, tts)
        advanceUntilIdle()
        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.actions.isEmpty())
    }
}
