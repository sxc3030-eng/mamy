package com.mamy.android.ui.screens.person

import com.mamy.android.data.db.entity.FlagEntity
import com.mamy.android.data.db.entity.NoteEntity
import com.mamy.android.data.db.entity.PersonEntity
import com.mamy.android.data.sms.SentSmsRow
import com.mamy.android.data.sms.SmsStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Pure-data tests for [PersonDetailUiState] — no coroutines / mocks. These
 * cover invariants the screen relies on (e.g. `openFlagCount` matches the
 * size of `openFlags`).
 */
class PersonDetailUiStateTest {

    private val pid = UUID.randomUUID()

    private fun person() = PersonEntity(
        id = pid, name = "Alice", email = null, roleHint = "Lead",
        calendarAttendeeId = null, createdAt = Instant.now(),
        lastInteractionAt = null, interactionCount = 1, emotionalTrend = null,
        unmatched = false, archived = false,
    )

    private fun flag(severity: String) = FlagEntity(
        id = UUID.randomUUID(), personId = pid, type = "demotivation",
        source = "llm", severity = severity, note = "n", resolved = false,
        fromNoteId = UUID.randomUUID(), createdAt = Instant.now(),
    )

    private fun note(text: String, createdAt: Instant) = NoteEntity(
        id = UUID.randomUUID(), personId = pid, meetingId = null,
        rawText = text, structuredJson = null, nonStructured = false,
        createdAt = createdAt, audioDurationSec = 5,
        llmProvider = "claude", llmCostCents = null,
    )

    @Test
    fun `default state is loading with no data`() {
        val s = PersonDetailUiState()
        assertEquals(true, s.isLoading)
        assertEquals(null, s.person)
        assertEquals(0, s.openFlagCount)
        assertEquals(0, s.notes.size)
    }

    @Test
    fun `openFlagCount equals openFlags size`() {
        val s = PersonDetailUiState(
            person = person(),
            openFlags = listOf(flag("high"), flag("medium"), flag("low")),
            isLoading = false,
        )
        assertEquals(3, s.openFlagCount)
    }

    @Test
    fun `PersonDetailTab order matches declaration`() {
        // Declared order is the source of truth for the TabRow indexing.
        assertEquals(
            listOf("Notes", "Promises", "Actions", "Flags", "Sms"),
            PersonDetailTab.values().map { it.name },
        )
    }

    @Test
    fun `state surfaces sms rows from repository`() {
        val sms = SentSmsRow(
            id = UUID.randomUUID(),
            recipientPersonId = pid,
            recipientPhone = "+15145551212",
            recipientDisplayName = "Alice",
            body = "running late",
            sentAt = Instant.now(),
            status = SmsStatus.PENDING,
            failReason = null,
            segments = 1,
        )
        val s = PersonDetailUiState(
            person = person(),
            sentSms = listOf(sms),
            isLoading = false,
        )
        assertEquals(1, s.sentSms.size)
        assertEquals(SmsStatus.PENDING, s.sentSms[0].status)
    }
}
