package com.mamy.android.ui.screens.actions

import com.mamy.android.data.db.entity.ActionEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ActionsUiStateTest {

    @Test
    fun `default state is Open filter empty list loading`() {
        val s = ActionsUiState()
        assertEquals(ActionsFilter.Open, s.filter)
        assertEquals(0, s.actions.size)
        assertEquals(true, s.isLoading)
    }

    @Test
    fun `ActionsFilter values include all three options`() {
        val names = ActionsFilter.values().map { it.name }
        assertEquals(listOf("Open", "Done", "All"), names)
    }

    @Test
    fun `ActionRow id delegates to action id`() {
        val id = UUID.randomUUID()
        val a = ActionEntity(
            id = id, description = "x", assignee = "self",
            linkedPersonId = null, deadline = null, status = "open",
            fromNoteId = UUID.randomUUID(), createdAt = Instant.now(),
            doneAt = null,
        )
        val row = ActionRow(action = a, linkedPersonName = null)
        assertEquals(id, row.id)
    }
}
