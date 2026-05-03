package com.mamy.android.data.calendar

import com.mamy.android.data.calendar.google.CalendarApiClient
import com.mamy.android.data.calendar.google.CalendarEvent
import com.mamy.android.data.db.dao.MeetingAttendeeDao
import com.mamy.android.data.db.dao.MeetingDao
import com.mamy.android.data.db.entity.MeetingAttendeeEntity
import com.mamy.android.data.db.entity.MeetingEntity
import com.mamy.android.data.settings.CalendarSyncStateStore
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject

class InitialCalendarSyncUseCase @Inject constructor(
    private val api: CalendarApiClient,
    private val meetingDao: MeetingDao,
    private val attendeeDao: MeetingAttendeeDao,
    private val personMatcher: PersonMatcher,
    private val state: CalendarSyncStateStore,
    private val clock: Clock = Clock.systemUTC()
) {

    suspend fun execute(
        calendarId: String = "primary",
        pastDays: Long = 30,
        futureDays: Long = 30
    ): Result<Unit> {
        val now = clock.instant()
        val timeMin = now.minusSeconds(pastDays * 86_400)
        val timeMax = now.plusSeconds(futureDays * 86_400)

        var pageToken: String? = null
        var lastSyncToken: String? = null

        do {
            val resp = api.listEvents(
                calendarId = calendarId,
                timeMin = timeMin,
                timeMax = timeMax,
                syncToken = null,
                pageToken = pageToken
            )
            val list = resp.getOrElse { return Result.failure(it) }
            list.items.forEach { applyEvent(it) }
            pageToken = list.nextPageToken
            lastSyncToken = list.nextSyncToken ?: lastSyncToken
        } while (pageToken != null)

        if (lastSyncToken != null) state.saveSyncToken(calendarId, lastSyncToken)
        return Result.success(Unit)
    }

    private suspend fun applyEvent(ev: CalendarEvent) {
        if (ev.status == "cancelled") {
            meetingDao.deleteByCalendarEventId(ev.id)
            return
        }
        val startsAt = parseInstant(ev.start?.dateTime ?: ev.start?.date) ?: return
        val endsAt = parseInstant(ev.end?.dateTime ?: ev.end?.date) ?: startsAt.plusSeconds(1800)

        val existing = meetingDao.findByCalendarEventId(ev.id)
        val meetingId = existing?.id ?: UUID.randomUUID()
        val meeting = MeetingEntity(
            id = meetingId,
            calendarEventId = ev.id,
            title = ev.summary ?: "(untitled)",
            startsAt = startsAt,
            endsAt = endsAt,
            briefingText = existing?.briefingText,
            postNoteId = existing?.postNoteId,
            createdAt = existing?.createdAt ?: clock.instant()
        )
        meetingDao.upsert(meeting)
        attendeeDao.deleteForMeeting(meetingId)

        val attendeeRows = ev.attendees.orEmpty().mapNotNull { att ->
            val person = personMatcher.matchOrCreate(att) ?: return@mapNotNull null
            MeetingAttendeeEntity(meetingId = meetingId, personId = person.id)
        }
        if (attendeeRows.isNotEmpty()) attendeeDao.insertAll(attendeeRows)
    }

    private fun parseInstant(value: String?): Instant? {
        if (value == null) return null
        return runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
            ?: runCatching {
                // all-day "YYYY-MM-DD" fallback : interpret as local midnight UTC
                Instant.parse(value + "T00:00:00Z")
            }.getOrNull()
    }
}
