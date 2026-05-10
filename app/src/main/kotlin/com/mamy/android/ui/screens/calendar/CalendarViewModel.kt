package com.mamy.android.ui.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamy.android.data.calendar.local.LocalCalendarReader
import com.mamy.android.data.calendar.local.MeetingReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

/**
 * Drives the Calendar screen using the device's [LocalCalendarReader] (no
 * OAuth, no Google account, just READ_CALENDAR permission). Events are loaded
 * synchronously on permission grant and on [refresh].
 */
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val reader: LocalCalendarReader,
    private val reminderScheduler: MeetingReminderScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    init { reload() }

    /** Called from the screen after the user grants READ_CALENDAR at runtime. */
    fun onPermissionGranted() {
        reload()
    }

    /** Pull-to-refresh / nav re-entry. */
    fun refresh() {
        reload()
    }

    private fun reload() {
        viewModelScope.launch {
            val granted = reader.hasPermission()
            if (!granted) {
                _state.value = CalendarUiState(connected = false)
                return@launch
            }
            val now = Instant.now()
            val end = now.plus(30, ChronoUnit.DAYS)
            val events = withContext(Dispatchers.IO) { reader.readEvents(now, end) }
            _state.value = CalendarUiState(
                connected = true,
                rows = buildRows(events),
                isEmpty = events.isEmpty(),
            )
            // Re-schedule T-24h + T-1h reminders for every event in the window.
            // REPLACE policy means existing work for the same event is updated
            // — never duplicated.
            withContext(Dispatchers.IO) {
                runCatching { reminderScheduler.scheduleFor(events) }
            }
        }
    }

    private fun buildRows(events: List<com.mamy.android.data.calendar.local.LocalCalendarEvent>): List<CalendarRow> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val tomorrow = today.plusDays(1)
        val grouped = events.groupBy { it.startsAt.atZone(zone).toLocalDate() }.toSortedMap()

        val rows = mutableListOf<CalendarRow>()
        for ((date, items) in grouped) {
            val header = when (date) {
                today -> CalendarRow.Header.Today
                tomorrow -> CalendarRow.Header.Tomorrow
                else -> CalendarRow.Header.Date(date)
            }
            rows += header
            for (e in items) {
                rows += CalendarRow.Meeting(
                    id = UUID.nameUUIDFromBytes("local-${e.eventId}".toByteArray()),
                    title = e.title,
                    startsAt = e.startsAt,
                    endsAt = e.endsAt,
                    attendeeCount = e.attendees.size,
                    attendeeNames = e.attendees.map { it.name },
                    hasBriefing = false,
                    hasNote = false,
                    location = e.location,
                    allDay = e.allDay,
                )
            }
        }
        return rows
    }

    private fun initialState() = CalendarUiState(
        connected = reader.hasPermission(),
    )
}

/** Composable-friendly UI rows: date headers + meeting entries. */
sealed interface CalendarRow {
    sealed interface Header : CalendarRow {
        data object Today : Header
        data object Tomorrow : Header
        data class Date(val day: LocalDate) : Header
    }

    data class Meeting(
        val id: UUID,
        val title: String,
        val startsAt: Instant,
        val endsAt: Instant,
        val attendeeCount: Int,
        val attendeeNames: List<String>,
        val hasBriefing: Boolean,
        val hasNote: Boolean,
        val location: String? = null,
        val allDay: Boolean = false,
    ) : CalendarRow
}

data class CalendarUiState(
    val connected: Boolean,
    val rows: List<CalendarRow> = emptyList(),
    val isEmpty: Boolean = true,
)
