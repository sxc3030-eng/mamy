package com.mamy.android.data.calendar

import com.mamy.android.data.db.dao.MeetingDao
import com.mamy.android.data.db.entity.MeetingEntity
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Read-only calendar facade for the briefing pipeline. Wraps [MeetingDao]
 * with the small-but-stable surface the briefing handlers need :
 *
 *  - [upcomingMeetings] for [com.mamy.android.domain.briefing.PreMeetingBriefHandler]
 *  - [todayMeetings]   for the daily-briefing pipeline (used by handlers, not by
 *                      the assembler which reads via DAO directly)
 */
@Singleton
class CalendarRepository @Inject constructor(
    private val meetingDao: MeetingDao,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun upcomingMeetings(within: Duration): List<MeetingEntity> {
        val now = Instant.now(clock)
        val end = now.plus(within.toJavaDuration())
        return meetingDao.between(now, end)
    }

    suspend fun todayMeetings(): List<MeetingEntity> {
        val now = Instant.now(clock)
        // 24h horizon is good enough for daily briefings; ZoneId-aware bounds
        // are computed in ContextAssembler when needed.
        return meetingDao.between(now.minusSeconds(60), now.plusSeconds(86_400))
    }
}
