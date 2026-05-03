package com.mamy.android.domain.intent.handler

import com.mamy.android.data.db.dao.FlagDao
import com.mamy.android.data.db.dao.PromiseDao
import com.mamy.android.data.db.entity.FlagEntity
import com.mamy.android.data.db.entity.PersonEntity
import com.mamy.android.data.db.entity.PromiseEntity
import com.mamy.android.domain.intent.Intent
import com.mamy.android.domain.memory.PersonMatcher
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertTrue

class TemplatedPersonBriefHandlerTest {

    private val matcher: PersonMatcher = mockk()
    private val promiseDao: PromiseDao = mockk()
    private val flagDao: FlagDao = mockk()
    private val handler = TemplatedPersonBriefHandler(matcher, promiseDao, flagDao)

    @Test
    fun `not found returns clean message`() = runTest {
        coEvery { matcher.match("Xyz") } returns PersonMatcher.MatchResult.NotFound
        val result = handler.handle(Intent.PersonBrief("Xyz", "MamY, briefe-moi sur Xyz"))
        assertTrue(result.spokenText!!.contains("inconnue") || result.spokenText!!.contains("not found"))
    }

    @Test
    fun `ambiguous returns clarification message`() = runTest {
        coEvery { matcher.match("Marie") } returns PersonMatcher.MatchResult.Ambiguous(
            listOf(personFixture("Marie Dubois"), personFixture("Marie Tremblay")),
        )
        val result = handler.handle(Intent.PersonBrief("Marie", "MamY, briefe-moi sur Marie"))
        assertTrue(result.spokenText!!.contains("Marie Dubois"))
        assertTrue(result.spokenText!!.contains("Marie Tremblay"))
    }

    @Test
    fun `single match assembles brief from promises and flags`() = runTest {
        val marie = personFixture("Marie Dubois")
        coEvery { matcher.match("Marie") } returns PersonMatcher.MatchResult.SingleMatch(marie)
        coEvery { promiseDao.findActiveBetween("self", marie.id.toString()) } returns listOf(
            promiseFixture(from = "self", to = marie.id.toString(), what = "review CV"),
        )
        coEvery { promiseDao.findActiveBetween(marie.id.toString(), "self") } returns listOf(
            promiseFixture(from = marie.id.toString(), to = "self", what = "envoyer rapport"),
        )
        coEvery { flagDao.findActiveByPerson(marie.id) } returns listOf(
            flagFixture(personId = marie.id, type = "demotivation", note = "perçue comme indirecte"),
        )

        val result = handler.handle(Intent.PersonBrief("Marie", "MamY, briefe-moi sur Marie"))

        assertTrue(result.spokenText!!.contains("Marie Dubois"))
        assertTrue(result.spokenText!!.contains("review CV"))
        assertTrue(result.spokenText!!.contains("envoyer rapport"))
        assertTrue(result.spokenText!!.contains("demotivation"))
    }

    private fun personFixture(name: String) = PersonEntity(
        id = UUID.randomUUID(), name = name,
        email = null, roleHint = null, calendarAttendeeId = null,
        createdAt = Instant.now(), lastInteractionAt = null,
        interactionCount = 0, emotionalTrend = null,
        unmatched = false, archived = false,
    )

    private fun promiseFixture(from: String, to: String, what: String) = PromiseEntity(
        id = UUID.randomUUID(), fromId = from, toId = to,
        what = what, due = null, status = "active",
        fromNoteId = UUID.randomUUID(), createdAt = Instant.now(), resolvedAt = null,
    )

    private fun flagFixture(personId: UUID, type: String, note: String) = FlagEntity(
        id = UUID.randomUUID(), personId = personId, type = type,
        source = "direct", severity = "medium", note = note,
        resolved = false, fromNoteId = UUID.randomUUID(), createdAt = Instant.now(),
    )
}
