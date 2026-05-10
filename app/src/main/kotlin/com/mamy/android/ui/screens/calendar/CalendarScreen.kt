package com.mamy.android.ui.screens.calendar

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mamy.android.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarRoute(
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.onPermissionGranted()
    }
    CalendarScreen(
        state = state,
        onRequestPermission = {
            permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onRequestPermission: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.calendar_title)) })
        },
        modifier = Modifier.testTag("calendar-screen"),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                !state.connected -> CalendarPermissionNeededState(onRequestPermission)
                state.isEmpty -> CalendarEmptyState()
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .testTag("calendar-list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.rows) { row ->
                        when (row) {
                            is CalendarRow.Header -> HeaderRow(row)
                            is CalendarRow.Meeting -> MeetingRow(row)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(header: CalendarRow.Header) {
    val text = when (header) {
        CalendarRow.Header.Today -> stringResource(R.string.calendar_header_today)
        CalendarRow.Header.Tomorrow -> stringResource(R.string.calendar_header_tomorrow)
        is CalendarRow.Header.Date -> formatHeader(header.day)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp)
            .testTag("calendar-header"),
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun MeetingRow(m: CalendarRow.Meeting) {
    Card(modifier = Modifier.fillMaxWidth().testTag("calendar-meeting")) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(m.title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = formatTimeRange(m.startsAt, m.endsAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (m.attendeeCount > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 2.dp),
                    )
                    val txt = if (m.attendeeNames.isNotEmpty()) {
                        m.attendeeNames.joinToString(", ").let {
                            if (it.length > 60) it.take(57) + "…" else it
                        }
                    } else {
                        stringResource(R.string.calendar_attendees_count, m.attendeeCount)
                    }
                    Text(text = txt, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (m.hasBriefing || m.hasNote) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (m.hasBriefing) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(end = 2.dp),
                        )
                        Text(
                            stringResource(R.string.calendar_has_briefing),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    if (m.hasNote) {
                        Text(
                            stringResource(R.string.calendar_has_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarPermissionNeededState(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("calendar-permission-needed"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.calendar_permission_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.calendar_permission_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.testTag("calendar-grant-cta"),
        ) {
            Text(stringResource(R.string.calendar_btn_grant_permission))
        }
        Text(
            stringResource(R.string.calendar_permission_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CalendarEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("calendar-empty"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.calendar_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.calendar_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val DAY_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())

private val TIME_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

private fun formatHeader(day: LocalDate): String =
    day.format(DAY_FMT).replaceFirstChar { it.uppercase() }

private fun formatTimeRange(start: Instant, end: Instant): String {
    val z = ZoneId.systemDefault()
    return "${start.atZone(z).format(TIME_FMT)} – ${end.atZone(z).format(TIME_FMT)}"
}
