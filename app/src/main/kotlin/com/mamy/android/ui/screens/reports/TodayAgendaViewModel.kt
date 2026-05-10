package com.mamy.android.ui.screens.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamy.android.data.calendar.local.LocalCalendarReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Provides the "Today's agenda" card shown at the top of the Reports screen.
 *
 * Reads events for the current day from the device calendar (no Google API),
 * exposes a small [TodayAgendaState] with count + first 2 preview strings.
 * Empty state when:
 *   - permission not granted, or
 *   - zero events scheduled today (after now).
 *
 * [TodayAgendaState.hasContent] makes it easy for the screen to skip the card
 * entirely without UI gymnastics.
 */
@HiltViewModel
class TodayAgendaViewModel @Inject constructor(
    private val reader: LocalCalendarReader,
) : ViewModel() {

    private val _state = MutableStateFlow(TodayAgendaState())
    val state: StateFlow<TodayAgendaState> = _state.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            if (!reader.hasPermission()) {
                _state.value = TodayAgendaState()
                return@launch
            }
            val zone = ZoneId.systemDefault()
            val now = Instant.now()
            val endOfDay = LocalDateTime.of(LocalDate.now(zone), LocalTime.MAX)
                .atZone(zone).toInstant()
            val events = withContext(Dispatchers.IO) { reader.readEvents(now, endOfDay) }
            val previews = events.map { e ->
                val zdt = e.startsAt.atZone(zone)
                val time = zdt.format(TIME_FMT)
                "$time · ${e.title}"
            }
            _state.value = TodayAgendaState(
                count = events.size,
                previews = previews,
            )
        }
    }

    private companion object {
        val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

data class TodayAgendaState(
    val count: Int = 0,
    val previews: List<String> = emptyList(),
) {
    val hasContent: Boolean get() = count > 0
}
