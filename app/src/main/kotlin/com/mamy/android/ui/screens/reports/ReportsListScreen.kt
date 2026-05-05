package com.mamy.android.ui.screens.reports

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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mamy.android.R
import java.time.Duration
import java.time.Instant

/**
 * Hilt-wired entry point. Resolves [ReportsListViewModel] and forwards the
 * person-tap callback (the host wires this to navigate to PersonDetail).
 */
@Composable
fun ReportsListRoute(
    viewModel: ReportsListViewModel = hiltViewModel(),
    onPersonClick: (PersonRow) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReportsListScreen(
        state = state,
        onSortChange = viewModel::setSort,
        onQueryChange = viewModel::setQuery,
        onToggleHideUnmatched = viewModel::toggleHideUnmatched,
        onPersonClick = onPersonClick,
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
    onSortChange: (ReportsSort) -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleHideUnmatched: () -> Unit,
    onPersonClick: (PersonRow) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.reports_title)) })
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
                Text(
                    stringResource(R.string.reports_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(vertical = 24.dp)
                        .testTag("reports-empty"),
                )
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
