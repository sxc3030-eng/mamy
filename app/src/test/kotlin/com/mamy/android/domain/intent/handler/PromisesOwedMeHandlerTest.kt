package com.mamy.android.domain.intent.handler

import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.dao.PromiseDao
import com.mamy.android.data.db.entity.PersonEntity
import com.mamy.android.data.db.entity.PromiseEntity
import com.mamy.android.domain.intent.Intent
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromisesOwedMeHandlerTest {

    private val promiseDao: PromiseDao = mockk()
    private val personDao: PersonDao = mockk()
    private val handler = PromisesOwedMeHandler(promiseDao, personDao)

    @Test
    fun `empty list returns friendly empty message`() = runTest {
        coEvery { promiseDao.findActiveOwedToSelf() } returns emptyList()

        val result = handler.handle(Intent.PromisesOwedMe("MamY, qui me devait quoi"))

        assertTrue(result.success)
        assertEquals("Personne ne te doit rien actuellement.", result.spokenText)
    }

    @Test
    fun `list with one promise formats with name`() = runTest {
        val marieId = UUID.randomUUID()
        coEvery { promiseDao.findActiveOwedToSelf() } returns listOf(
            PromiseEntity(
                id = UUID.randomUUID(),
                fromId = marieId.toString(),
                toId = "self",
                what = "envoyer le rapport projet X",
                due = null,
                status = "active",
                fromNoteId = UUID.randomUUID(),
                createdAt = Instant.now(),
                resolvedAt = null,
            ),
        )
        coEvery { personDao.getById(marieId) } returns PersonEntity(
            id = marieId, name = "Marie",
            email = null, roleHint = null, calendarAttendeeId = null,
            createdAt = Instant.now(), lastInteractionAt = null,
            interactionCount = 0, emotionalTrend = null,
            unmatched = false, archived = false,
        )

        val result = handler.handle(Intent.PromisesOwedMe("MamY, qui me devait quoi"))

        assertTrue(result.success)
        assertTrue(result.spokenText!!.contains("Marie"))
        assertTrue(result.spokenText!!.contains("envoyer le rapport projet X"))
    }

    @Test
    fun `unknown person id falls back to generic label`() = runTest {
        coEvery { promiseDao.findActiveOwedToSelf() } returns listOf(
            PromiseEntity(
                id = UUID.randomUUID(),
                fromId = "self",
                toId = "self",
                what = "test",
                due = null,
                status = "active",
                fromNoteId = UUID.randomUUID(),
                createdAt = Instant.now(),
                resolvedAt = null,
            ),
        )

        val result = handler.handle(Intent.PromisesOwedMe("MamY, qui me devait quoi"))
        assertTrue(result.success)
        assertTrue(result.spokenText!!.contains("Quelqu'un") || result.spokenText!!.contains("toi-même"))
    }
}
