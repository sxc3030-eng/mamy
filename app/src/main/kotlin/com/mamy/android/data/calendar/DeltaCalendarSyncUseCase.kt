package com.mamy.android.data.calendar

import com.mamy.android.data.calendar.google.CalendarApiClient
import com.mamy.android.data.calendar.google.CalendarSyncTokenInvalidException
import com.mamy.android.data.db.dao.MeetingAttendeeDao
import com.mamy.android.data.db.dao.MeetingDao
import com.mamy.android.data.db.entity.MeetingAttendeeEntity
import com.mamy.android.data.db.entity.MeetingEntity
import com.mamy.android.data.settings.CalendarSyncStateStore
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject

class DeltaCalendarSyncUseCase @Inject constructor(
    private val api: CalendarApiClient,
    private val state: CalendarSyncStateStore,
    private val initialSync: InitialCalendarSyncUseCase,
    private val meetingDao: MeetingDao,
    private val attendeeDao: MeetingAttendeeDao,
    private val personMatcher: PersonMatcher,
    private val clock: Clock = Clock.systemUTC()
) {

    suspend fun execute(calendarId: String = "primary"): Result<Unit> {
        val token = state.loadSyncToken(calendarId)
            ?: return initialSync.execute(calendarId)

        var pageToken: String? = null
        var nextSync: String? = null
        do {
            val resp = api.listEvents(
                calendarId = calendarId,
                timeMin = null,
                timeMax = null,
                syncToken = if (pageToken == null) token else null,
                pageToken = pageToken
            )
            val list = resp.getOrElse { err ->
                if (err is CalendarSyncTokenInvalidException) {
                    state.clearSyncToken(calendarId)
                    return initialSync.execute(calendarId)
                }
                return Result.failure(err)
            }
            list.items.forEach { ev ->
                if (ev.status == "cancelled") {
                    meetingDao.deleteByCalendarEventId(ev.id)
                    return@forEach
                }
                val startsAt = ev.start?.dateTime?.let { OffsetDateTime.parse(it).toInstant() } ?: return@forEach
                val endsAt = ev.end?.dateTime?.let { OffsetDateTime.parse(it).toInstant() } ?: startsAt.plusSeconds(1800)
                val existing = meetingDao.findByCalendarEventId(ev.id)
                val mid = existing?.id ?: UUID.randomUUID()
                meetingDao.upsert(
                    MeetingEntity(
                        id = mid,
                        calendarEventId = ev.id,
                        title = ev.summary ?: "(untitled)",
                        startsAt = startsAt,
                        endsAt = endsAt,
                        briefingText = existing?.briefingText,
                        postNoteId = existing?.postNoteId,
                        createdAt = existing?.createdAt ?: clock.instant()
                    )
                )
                attendeeDao.deleteForMeeting(mid)
                val rows = ev.attendees.orEmpty().mapNotNull { att ->
                    val p = personMatcher.matchOrCreate(att) ?: return@mapNotNull null
                    MeetingAttendeeEntity(meetingId = mid, personId = p.id)
                }
                if (rows.isNotEmpty()) attendeeDao.insertAll(rows)
            }
            pageToken = list.nextPageToken
            nextSync = list.nextSyncToken ?: nextSync
        } while (pageToken != null)

        if (nextSync != null) state.saveSyncToken(calendarId, nextSync)
        return Result.success(Unit)
    }
}
