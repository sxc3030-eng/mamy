package com.mamy.android.ui.screens.reports

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mamy.android.R
import com.mamy.android.service.MamYListenerService
import java.time.Duration
import java.time.Instant

/**
 * Hilt-wired entry point. Resolves [ReportsListViewModel] and forwards the
 * person-tap callback (the host wires this to navigate to PersonDetail).
 */
@Composable
fun ReportsListRoute(
    viewModel: ReportsListViewModel = hiltViewModel(),
    todayViewModel: TodayAgendaViewModel = hiltViewModel(),
    onPersonClick: (PersonRow) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today by todayViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ReportsListScreen(
        state = state,
        todayState = today,
        onSortChange = viewModel::setSort,
        onQueryChange = viewModel::setQuery,
        onToggleHideUnmatched = viewModel::toggleHideUnmatched,
        onPersonClick = onPersonClick,
        onRecord = {
            val intent = Intent(context, MamYListenerService::class.java).apply {
                action = MamYListenerService.ACTION_TRIGGER_CAPTURE
            }
            // startForegroundService is required from a foreground activity on Android 12+
            context.startForegroundService(intent)
        },
    )
}

/**
 * Stateless reports list. Renders:
 * - search bar
 * - sort filter chips (Recent / Name / Flags)
 * - hide-unmatched toggle chip
 * - lazy list of [PersonRow]s with emotional-trend dot, name, role,
 *   interaction count, last-seen relative time, and an optional red flag dot
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsListScreen(
    state: ReportsListUiState,
    todayState: TodayAgendaState = TodayAgendaState(),
    onSortChange: (ReportsSort) -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleHideUnmatched: () -> Unit,
    onPersonClick: (PersonRow) -> Unit,
    onRecord: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.reports_title)) })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onRecord,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.reports_record_cd),
                    )
                },
                text = { Text(stringResource(R.string.reports_record_label)) },
                modifier = Modifier.testTag("reports-fab-record"),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .testTag("reports-root"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (todayState.hasContent) {
                TodayAgendaCard(todayState)
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(R.string.reports_search_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reports-search"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportsSort.values().forEach { s ->
                    FilterChip(
                        selected = state.sort == s,
                        onClick = { onSortChange(s) },
                        label = { Text(sortLabel(s)) },
                        modifier = Modifier.testTag("reports-sort-${s.name.lowercase()}"),
                    )
                }
                AssistChip(
                    onClick = onToggleHideUnmatched,
                    label = {
                        Text(
                            if (state.hideUnmatched) {
                                stringResource(R.string.reports_show_unmatched)
                            } else {
                                stringResource(R.string.reports_hide_unmatched)
                            },
                        )
                    },
                    modifier = Modifier.testTag("reports-toggle-unmatched"),
                )
            }

            if (state.persons.isEmpty()) {
                EmptyReportsState()
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.persons, key = { it.id }) { p ->
                        PersonRowCard(p, onClick = { onPersonClick(p) })
                    }
                }
            }
        }
    }
}

/**
 * Compact "today" agenda card shown above the search bar on the Reports screen.
 * Pulls from the device calendar via [TodayAgendaViewModel] — visible only when
 * the user has granted READ_CALENDAR and there is at least one event today.
 */
@Composable
private fun TodayAgendaCard(state: TodayAgendaState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reports-today-card"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.reports_today_title, state.count),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            state.previews.take(2).forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
            if (state.count > 2) {
                Text(
                    stringResource(R.string.reports_today_more, state.count - 2),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Inviting empty-state for fresh installs. Lists 3 example voice patterns
 * users can try with the FAB Record button so the screen does not feel
 * broken on first launch.
 */
@Composable
private fun EmptyReportsState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp, bottom = 16.dp)
            .testTag("reports-empty"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.reports_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.reports_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.reports_empty_examples_title),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    "• " + stringResource(R.string.reports_empty_example_1),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "• " + stringResource(R.string.reports_empty_example_2),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "• " + stringResource(R.string.reports_empty_example_3),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text(
            stringResource(R.string.reports_empty_footer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PersonRowCard(p: PersonRow, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("reports-row"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EmotionalTrendDot(trend = p.emotionalTrend)
            Column(modifier = Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium)
                val roleOrUnmatched = when {
                    p.unmatched -> stringResource(R.string.reports_unmatched_chip)
                    p.roleHint != null -> p.roleHint
                    else -> stringResource(R.string.reports_no_role)
                }
                Text(
                    roleOrUnmatched,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (p.unmatched) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.reports_interaction_count, p.interactionCount),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    p.lastInteractionAt?.let {
                        Text(
                            text = stringResource(R.string.reports_last_interaction, relativeTime(it)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (p.openFlagCount > 0) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .testTag("reports-flag-badge"),
                )
            }
        }
    }
}

@Composable
private fun EmotionalTrendDot(trend: String?) {
    val color = when (trend) {
        "happy", "engaged" -> Color(0xFF2E7D32)
        "ok", "neutral" -> Color(0xFF6E7891)
        "stressed" -> Color(0xFFE65100)
        "demotivated", "burnout", "conflict", "disengaged" ->
            MaterialTheme.colorScheme.error
        else -> Color(0xFFBDBDBD)
    }
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color)
            .testTag("reports-trend-dot"),
    )
}

@Composable
private fun sortLabel(s: ReportsSort): String = when (s) {
    ReportsSort.Recent -> stringResource(R.string.reports_sort_recent)
    ReportsSort.Name -> stringResource(R.string.reports_sort_name)
    ReportsSort.Flags -> stringResource(R.string.reports_sort_flags)
}

/**
 * Compact relative-time renderer for the row. JVM-only (no Android Context),
 * so the function is testable in unit tests.
 *
 * Buckets: "now", "5m", "2h", "3d", "Jan 12".
 */
private fun relativeTime(instant: Instant, now: Instant = Instant.now()): String {
    val d = Duration.between(instant, now)
    return when {
        d.toMinutes() < 1 -> "now"
        d.toMinutes() < 60 -> "${d.toMinutes()}m"
        d.toHours() < 24 -> "${d.toHours()}h"
        d.toDays() < 30 -> "${d.toDays()}d"
        else -> instant.toString().substring(0, 10)
    }
}
