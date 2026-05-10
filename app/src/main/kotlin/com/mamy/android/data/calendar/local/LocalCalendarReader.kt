package com.mamy.android.data.calendar.local

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads upcoming meetings directly from the device's calendar provider
 * ([CalendarContract]) — i.e., whatever is already synced on the phone (Google
 * Calendar, Outlook/Exchange, Samsung, iCloud-via-account, etc.). Replaces the
 * Google API + OAuth stack with a single permission (`READ_CALENDAR`).
 *
 * No background sync, no tokens, no network: query is direct against the
 * system content provider, instant, works offline.
 */
@Singleton
class LocalCalendarReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** True if the user granted READ_CALENDAR. */
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Return all events whose `dtstart` falls between [from] and [to],
     * sorted by start time. Recurring-event instances are expanded by the
     * provider via the `Instances` table so a weekly meeting shows up on
     * each occurrence, not once.
     *
     * Returns empty list when permission is missing — callers must check
     * [hasPermission] first to surface a rationale UI.
     */
    fun readEvents(from: Instant, to: Instant): List<LocalCalendarEvent> {
        if (!hasPermission()) return emptyList()
        val cr: ContentResolver = context.contentResolver
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(from.toEpochMilli().toString())
            .appendPath(to.toEpochMilli().toString())
            .build()

        val out = mutableListOf<LocalCalendarEvent>()
        cr.query(
            builder,
            INSTANCE_COLS,
            null,
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val eventId = c.getLong(0)
                val title = c.getString(1) ?: ""
                val begin = c.getLong(2)
                val end = c.getLong(3)
                val location = c.getString(4)
                val allDay = c.getInt(5) == 1
                val ownerAcct = c.getString(6)

                val attendees = readAttendees(eventId)
                out += LocalCalendarEvent(
                    eventId = eventId,
                    title = title.ifEmpty { "(untitled)" },
                    startsAt = Instant.ofEpochMilli(begin),
                    endsAt = Instant.ofEpochMilli(end),
                    location = location,
                    allDay = allDay,
                    organizer = ownerAcct,
                    attendees = attendees,
                )
            }
        }
        return out
    }

    private fun readAttendees(eventId: Long): List<LocalAttendee> {
        if (!hasPermission()) return emptyList()
        val cr = context.contentResolver
        val out = mutableListOf<LocalAttendee>()
        cr.query(
            CalendarContract.Attendees.CONTENT_URI,
            ATTENDEE_COLS,
            "${CalendarContract.Attendees.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0)
                val email = c.getString(1)
                val status = c.getInt(2)
                if (name.isNullOrBlank() && email.isNullOrBlank()) continue
                out += LocalAttendee(
                    name = name?.takeIf { it.isNotBlank() } ?: email.orEmpty(),
                    email = email,
                    isOrganizer = status == CalendarContract.Attendees.RELATIONSHIP_ORGANIZER,
                )
            }
        }
        return out
    }

    private companion object {
        val INSTANCE_COLS = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.OWNER_ACCOUNT,
        )
        val ATTENDEE_COLS = arrayOf(
            CalendarContract.Attendees.ATTENDEE_NAME,
            CalendarContract.Attendees.ATTENDEE_EMAIL,
            CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
        )
    }
}

data class LocalCalendarEvent(
    val eventId: Long,
    val title: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val location: String?,
    val allDay: Boolean,
    val organizer: String?,
    val attendees: List<LocalAttendee>,
)

data class LocalAttendee(
    val name: String,
    val email: String?,
    val isOrganizer: Boolean,
)
