package com.mamy.android.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mamy.android.data.db.MamYDatabase
import com.mamy.android.data.db.entity.MeetingEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MeetingDaoTest {

    private lateinit var db: MamYDatabase
    private lateinit var dao: MeetingDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MamYDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.meetingDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `getInRange returns meetings whose start is between bounds`() = runTest {
        dao.insert(sampleMeeting("morning", Instant.parse("2026-05-02T09:00:00Z")))
        dao.insert(sampleMeeting("afternoon", Instant.parse("2026-05-02T14:00:00Z")))
        dao.insert(sampleMeeting("tomorrow", Instant.parse("2026-05-03T09:00:00Z")))
        val today = dao.getInRange(
            Instant.parse("2026-05-02T00:00:00Z"),
            Instant.parse("2026-05-02T23:59:59Z"),
        )
        assertEquals(2, today.size)
    }

    @Test
    fun `getByCalendarEventId returns matching event`() = runTest {
        dao.insert(sampleMeeting("ev", Instant.now(), calendarEventId = "ev-123"))
        val fetched = dao.getByCalendarEventId("ev-123")
        assertEquals("ev", fetched!!.title)
    }

    private fun sampleMeeting(title: String, startsAt: Instant, calendarEventId: String? = null) =
        MeetingEntity(
            id = UUID.randomUUID(),
            calendarEventId = calendarEventId,
            title = title,
            startsAt = startsAt,
            endsAt = startsAt.plusSeconds(1800),
            briefingText = null,
            postNoteId = null,
            createdAt = Instant.now(),
        )
}
