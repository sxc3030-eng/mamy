package com.mamy.android.domain.intent.handler

import com.mamy.android.data.db.dao.ActionDao
import com.mamy.android.data.db.entity.ActionEntity
import com.mamy.android.domain.intent.Intent
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class ActionsOpenHandlerTest {

    private val actionDao: ActionDao = mockk()
    private val handler = ActionsOpenHandler(actionDao)

    @Test
    fun `empty returns clean message`() = runTest {
        coEvery { actionDao.findOpen() } returns emptyList()
        val result = handler.handle(Intent.ActionsOpen("MamY, mes actions ouvertes"))
        assertEquals("Aucune action ouverte. Tu es à jour.", result.spokenText)
    }

    @Test
    fun `formats list with deadline if present`() = runTest {
        val deadline = Instant.parse("2026-05-15T17:00:00Z")
        coEvery { actionDao.findOpen() } returns listOf(
            actionFixture("call David", null),
            actionFixture("review CV Marie", deadline),
        )
        val result = handler.handle(Intent.ActionsOpen("MamY, mes actions ouvertes"))
        assertTrue(result.spokenText!!.contains("call David"))
        assertTrue(result.spokenText!!.contains("review CV Marie"))
    }

    private fun actionFixture(desc: String, deadline: Instant?) = ActionEntity(
        id = UUID.randomUUID(),
        description = desc,
        assignee = "self",
        linkedPersonId = null,
        deadline = deadline,
        status = "open",
        fromNoteId = UUID.randomUUID(),
        createdAt = Instant.now(),
        doneAt = null,
    )
}
