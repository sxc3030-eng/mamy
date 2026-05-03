package com.mamy.android.domain.memory

import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.entity.PersonEntity
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ConfirmPersonStubUseCaseTest {

    private val dao = mockk<PersonDao>(relaxed = true)
    private val useCase = ConfirmPersonStubUseCase(dao)

    private fun stub(name: String = "Marc Tremblay") = PersonEntity(
        id = UUID.randomUUID(),
        name = name,
        email = "marc@x.com",
        roleHint = null,
        calendarAttendeeId = "marc@x.com",
        createdAt = Instant.parse("2026-05-02T12:00:00Z"),
        lastInteractionAt = null,
        interactionCount = 0,
        emotionalTrend = null,
        unmatched = true,
        archived = false
    )

    @Test
    fun `confirm flips unmatched flag to false`() = runTest {
        val s = stub()
        val captured = slot<PersonEntity>()
        coEvery { dao.findById(s.id) } returns s
        coEvery { dao.update(capture(captured)) } returns Unit

        useCase.confirm(s.id)

        assertEquals(false, captured.captured.unmatched)
        assertEquals(s.id, captured.captured.id)
    }

    @Test
    fun `editName updates name and confirms`() = runTest {
        val s = stub()
        val captured = slot<PersonEntity>()
        coEvery { dao.findById(s.id) } returns s
        coEvery { dao.update(capture(captured)) } returns Unit

        useCase.editName(s.id, "Marc-Andre Tremblay")

        assertEquals("Marc-Andre Tremblay", captured.captured.name)
        assertEquals(false, captured.captured.unmatched)
    }

    @Test
    fun `mergeInto archives stub and re-attaches attendee id to target`() = runTest {
        val stubPerson = stub()
        val target = stub(name = "Marc Already").copy(unmatched = false, calendarAttendeeId = null)
        coEvery { dao.findById(stubPerson.id) } returns stubPerson
        coEvery { dao.findById(target.id) } returns target
        val captures = mutableListOf<PersonEntity>()
        coEvery { dao.update(capture(captures)) } returns Unit

        useCase.mergeInto(stubId = stubPerson.id, targetId = target.id)

        // Target gets the attendee id
        val targetUpdate = captures.first { it.id == target.id }
        assertEquals("marc@x.com", targetUpdate.calendarAttendeeId)
        // Stub gets archived
        val stubUpdate = captures.first { it.id == stubPerson.id }
        assertEquals(true, stubUpdate.archived)
    }
}
