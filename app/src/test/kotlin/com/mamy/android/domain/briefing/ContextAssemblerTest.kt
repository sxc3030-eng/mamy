package com.mamy.android.domain.briefing

import com.mamy.android.data.db.entity.PersonEntity
import com.mamy.android.data.db.entity.NoteEntity
import com.mamy.android.data.db.entity.PromiseEntity
import com.mamy.android.data.db.entity.FlagEntity
import com.mamy.android.data.db.entity.MeetingEntity
import com.mamy.android.data.db.entity.ActionEntity
import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.dao.NoteDao
import com.mamy.android.data.db.dao.PromiseDao
import com.mamy.android.data.db.dao.FlagDao
import com.mamy.android.data.db.dao.MeetingDao
import com.mamy.android.data.db.dao.ActionDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ContextAssemblerTest {


    private val zone = ZoneId.of("America/Toronto")
    private val now = Instant.parse("2026-05-02T13:00:00Z") // 09:00 local

    private val personDao = mockk<PersonDao>()
    private val noteDao = mockk<NoteDao>()
    private val actionDao = mockk<ActionDao>()
    private val promiseDao = mockk<PromiseDao>()
    private val flagDao = mockk<FlagDao>()
    private val meetingDao = mockk<MeetingDao>()

    private val sut = ContextAssembler(personDao, noteDao, actionDao, promiseDao, flagDao, meetingDao, zone)

    private val pidMarie = UUID.randomUUID()
    private val midMorning = UUID.randomUUID()

    private val marie = PersonEntity(
        id = pidMarie, name = "Marie Dubois", email = null, roleHint = "team-lead",
        calendarAttendeeId = null, createdAt = now, lastInteractionAt = now,
        interactionCount = 12, emotionalTrend = "stressed→ok", unmatched = false, archived = false,
    )
    private val meetingMarie = MeetingEntity(
        id = midMorning, calendarEventId = "evt-1", title = "1:1 Marie",
        startsAt = Instant.parse("2026-05-02T14:00:00Z"),
        endsAt = Instant.parse("2026-05-02T14:30:00Z"),
        briefingText = null, postNoteId = null, createdAt = now,
    )

    @Test
    fun `daily assemble pulls each meetings attendees and last 3 notes`() = runTest {
        coEvery { meetingDao.between(any(), any()) } returns listOf(meetingMarie)
        coEvery { meetingDao.attendeesOf(midMorning) } returns listOf(pidMarie)
        coEvery { personDao.byId(pidMarie) } returns marie
        coEvery { noteDao.lastNForPerson(pidMarie, 3) } returns listOf(
            NoteEntity(UUID.randomUUID(), pidMarie, midMorning, "Stressée projet X", null, false, now, 30, "claude", 1),
        )
        coEvery { promiseDao.openFromTo("self", pidMarie.toString()) } returns emptyList()
        coEvery { promiseDao.openFromTo(pidMarie.toString(), "self") } returns emptyList()
        coEvery { flagDao.openForPerson(pidMarie) } returns emptyList()

        val req = BriefingRequest(BriefingType.DAILY, null, now, Locale.FRENCH)
        val json = JSONObject(sut.assemble(req))

        val meetings = json.getJSONArray("meetings")
        assertEquals(1, meetings.length())
        val first = meetings.getJSONObject(0)
        assertEquals("Marie Dubois", first.getString("person_name"))
        assertEquals(1, first.getJSONArray("recent_notes").length())
    }

    @Test
    fun `pre meeting assemble returns error when meeting missing`() = runTest {
        val missing = UUID.randomUUID()
        coEvery { meetingDao.byId(missing) } returns null
        val req = BriefingRequest(BriefingType.PRE_MEETING, missing.toString(), now, Locale.ENGLISH)
        val json = JSONObject(sut.assemble(req))
        assertEquals("meeting_not_found", json.getString("error"))
    }

    @Test
    fun `person query includes all promises and flags`() = runTest {
        coEvery { personDao.byId(pidMarie) } returns marie
        coEvery { noteDao.lastNForPerson(pidMarie, 10) } returns emptyList()
        coEvery { promiseDao.allFromTo("self", pidMarie.toString()) } returns listOf(
            PromiseEntity(UUID.randomUUID(), "self", pidMarie.toString(), "Reviewer CV", null, "active", UUID.randomUUID(), now, null),
        )
        coEvery { promiseDao.allFromTo(pidMarie.toString(), "self") } returns emptyList()
        coEvery { flagDao.allForPerson(pidMarie) } returns listOf(
            FlagEntity(UUID.randomUUID(), pidMarie, "growth", "direct", "low", "wants lead role", false, UUID.randomUUID(), now),
        )
        coEvery { actionDao.linkedTo(pidMarie) } returns emptyList()

        val req = BriefingRequest(BriefingType.PERSON_QUERY, pidMarie.toString(), now, Locale.FRENCH)
        val json = JSONObject(sut.assemble(req))
        assertEquals("Marie Dubois", json.getString("person_name"))
        assertEquals(1, json.getJSONArray("promises_from_me").length())
        assertEquals(1, json.getJSONArray("flags").length())
    }

    @Test
    fun `eod summary counts open vs done actions`() = runTest {
        val a1 = ActionEntity(UUID.randomUUID(), "Talk to David", "self", null, null, "open", UUID.randomUUID(), now, null)
        val a2 = ActionEntity(UUID.randomUUID(), "Send email", "self", null, null, "done", UUID.randomUUID(), now, now)
        coEvery { noteDao.between(any(), any()) } returns emptyList()
        coEvery { actionDao.createdBetween(any(), any()) } returns listOf(a1, a2)
        coEvery { promiseDao.updatedBetween(any(), any()) } returns emptyList()

        val req = BriefingRequest(BriefingType.EOD_SUMMARY, null, now, Locale.ENGLISH)
        val json = JSONObject(sut.assemble(req))
        assertEquals(1, json.getInt("actions_open"))
        assertEquals(1, json.getInt("actions_done"))
    }

    @Test
    fun `pre meeting requires non-null targetId`() = runTest {
        val req = BriefingRequest(BriefingType.PRE_MEETING, null, now, Locale.ENGLISH)
        val ex = runCatching { sut.assemble(req) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }
}
