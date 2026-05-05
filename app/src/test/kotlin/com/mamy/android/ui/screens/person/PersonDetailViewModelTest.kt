package com.mamy.android.ui.screens.person

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.mamy.android.data.db.dao.ActionDao
import com.mamy.android.data.db.dao.FlagDao
import com.mamy.android.data.db.dao.NoteDao
import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.dao.PromiseDao
import com.mamy.android.data.db.entity.ActionEntity
import com.mamy.android.data.db.entity.FlagEntity
import com.mamy.android.data.db.entity.NoteEntity
import com.mamy.android.data.db.entity.PersonEntity
import com.mamy.android.data.db.entity.PromiseEntity
import com.mamy.android.data.sms.EmptySentSmsRepository
import com.mamy.android.data.sms.SentSmsRepository
import com.mamy.android.data.sms.SentSmsRow
import com.mamy.android.data.sms.SmsStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class PersonDetailViewModelTest {

    private val personId: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")

    private lateinit var personDao: PersonDao
    private lateinit var noteDao: NoteDao
    private lateinit var promiseDao: PromiseDao
    private lateinit var actionDao: ActionDao
    private lateinit var flagDao: FlagDao
    private lateinit var smsRepo: SentSmsRepository

    private val person = PersonEntity(
        id = personId,
        name = "Alice",
        email = "alice@example.com",
        roleHint = "Lead",
        calendarAttendeeId = null,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        lastInteractionAt = Instant.parse("2026-04-30T00:00:00Z"),
        interactionCount = 7,
        emotionalTrend = "engaged",
        unmatched = false,
        archived = false,
    )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        personDao = mockk(relaxed = true)
        noteDao = mockk(relaxed = true)
        promiseDao = mockk(relaxed = true)
        actionDao = mockk(relaxed = true)
        flagDao = mockk(relaxed = true)
        smsRepo = EmptySentSmsRepository()

        coEvery { personDao.getById(personId) } returns person
        coEvery { noteDao.getByPersonOrderedDesc(personId) } returns emptyList()
        coEvery { promiseDao.getByPerson(personId.toString()) } returns emptyList()
        coEvery { actionDao.getByPerson(personId) } returns emptyList()
        coEvery { flagDao.getOpenByPerson(personId) } returns emptyList()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm(): PersonDetailViewModel {
        val saved = SavedStateHandle(mapOf(PersonDetailViewModel.ARG_PERSON_ID to personId.toString()))
        return PersonDetailViewModel(
            saved = saved,
            personDao = personDao,
            noteDao = noteDao,
            promiseDao = promiseDao,
            actionDao = actionDao,
            flagDao = flagDao,
            sentSmsRepository = smsRepo,
        )
    }

    @Test
    fun `loads person from saved state handle`() = runTest {
        val vm = newVm()
        advanceUntilIdle()
        val s = vm.state.value
        assertNotNull(s.person)
        assertEquals("Alice", s.person?.name)
    }

    @Test
    fun `combines notes promises actions flags and sms`() = runTest {
        val now = Instant.now()
        val noteOlder = NoteEntity(
            id = UUID.randomUUID(), personId = personId, meetingId = null,
            rawText = "older", structuredJson = null, nonStructured = false,
            createdAt = now.minusSeconds(60), audioDurationSec = 10,
            llmProvider = "claude", llmCostCents = null,
        )
        val noteNewer = noteOlder.copy(id = UUID.randomUUID(), rawText = "newer", createdAt = now)
        val promiseActive = PromiseEntity(
            id = UUID.randomUUID(), fromId = "self", toId = personId.toString(),
            what = "send doc", due = null, status = "active",
            fromNoteId = noteOlder.id, createdAt = now, resolvedAt = null,
        )
        val promiseDone = promiseActive.copy(id = UUID.randomUUID(), status = "done", what = "old")
        val actionOpen = ActionEntity(
            id = UUID.randomUUID(), description = "Email", assignee = "self",
            linkedPersonId = personId, deadline = null, status = "open",
            fromNoteId = noteOlder.id, createdAt = now, doneAt = null,
        )
        val actionDone = actionOpen.copy(id = UUID.randomUUID(), status = "done", description = "Old")
        val flag = FlagEntity(
            id = UUID.randomUUID(), personId = personId, type = "demotivation",
            source = "llm", severity = "high", note = "watch", resolved = false,
            fromNoteId = noteOlder.id, createdAt = now,
        )

        coEvery { noteDao.getByPersonOrderedDesc(personId) } returns listOf(noteOlder, noteNewer)
        coEvery { promiseDao.getByPerson(personId.toString()) } returns listOf(promiseActive, promiseDone)
        coEvery { actionDao.getByPerson(personId) } returns listOf(actionOpen, actionDone)
        coEvery { flagDao.getOpenByPerson(personId) } returns listOf(flag)

        val vm = newVm()
        advanceUntilIdle()
        val s = vm.state.value

        // notes sorted newest first
        assertEquals(listOf("newer", "older"), s.notes.map { it.rawText })
        // only active promise
        assertEquals(listOf(promiseActive.id), s.openPromises.map { it.id })
        // only open action
        assertEquals(listOf(actionOpen.id), s.openActions.map { it.id })
        assertEquals(1, s.openFlagCount)
        assertTrue(s.sentSms.isEmpty()) // empty stub repo
        assertFalse(s.isLoading)
    }

    @Test
    fun `surfaces sms rows from repository`() = runTest {
        val now = Instant.now()
        val smsRow = SentSmsRow(
            id = UUID.randomUUID(),
            recipientPersonId = personId,
            recipientPhone = "+15145551212",
            recipientDisplayName = "Alice",
            body = "running 5 min late",
            sentAt = now,
            status = SmsStatus.PENDING,
            failReason = null,
            segments = 1,
        )
        smsRepo = object : SentSmsRepository {
            override fun observeForPerson(personId: UUID) =
                MutableStateFlow(listOf(smsRow))
            override suspend fun retry(entryId: UUID) = true
        }
        val vm = newVm()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.sentSms.size)
        assertEquals(SmsStatus.PENDING, vm.state.value.sentSms[0].status)
    }

    @Test
    fun `rename calls dao update`() = runTest {
        val vm = newVm()
        advanceUntilIdle()
        vm.rename("Alice Bonjour")
        advanceUntilIdle()
        coVerify { personDao.update(match { it.name == "Alice Bonjour" }) }
    }

    @Test
    fun `archive calls dao update with archived true`() = runTest {
        val vm = newVm()
        advanceUntilIdle()
        vm.archive()
        advanceUntilIdle()
        coVerify { personDao.update(match { it.archived }) }
    }

    @Test
    fun `retrySms forwards to repo`() = runTest {
        val testSmsRepo = object : SentSmsRepository {
            override fun observeForPerson(personId: UUID) = flowOf(emptyList<SentSmsRow>())
            override suspend fun retry(entryId: UUID): Boolean { lastRetried = entryId; return true }
            var lastRetried: UUID? = null
        }
        smsRepo = testSmsRepo
        val vm = newVm()
        val entryId = UUID.randomUUID()
        var result: Boolean? = null
        vm.retrySms(entryId) { result = it }
        advanceUntilIdle()
        assertEquals(entryId, testSmsRepo.lastRetried)
        assertTrue(result == true)
    }

    @Test
    fun `missing personId throws`() {
        val saved = SavedStateHandle()
        try {
            PersonDetailViewModel(saved, personDao, noteDao, promiseDao, actionDao, flagDao, smsRepo)
            assertTrue(false, "expected IllegalStateException")
        } catch (_: IllegalStateException) {
            // ok
        }
    }
}
