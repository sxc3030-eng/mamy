package com.mamy.android.ui.screens.networklog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mamy.android.R
import com.mamy.android.data.network.NetworkLogEntry

@Composable
fun NetworkLogRoute(
    viewModel: NetworkLogViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NetworkLogScreen(
        state = state,
        onFilter = viewModel::setFilter,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkLogScreen(
    state: NetworkLogUiState,
    onFilter: (NetworkLogEntry.Category?) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.networklog_title)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .testTag("networklog-screen"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.networklog_disclaimer),
                style = MaterialTheme.typography.bodySmall,
            )
            FilterChipRow(current = state.filter, onFilter = onFilter)

            if (state.visible.isEmpty()) {
                Text(
                    stringResource(R.string.networklog_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .testTag("networklog-empty"),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("networklog-list"),
                ) {
                    items(
                        items = state.visible,
                        key = { e -> "${e.timestamp.toEpochMilli()}-${e.url}-${e.method}" },
                    ) { entry ->
                        EntryCard(entry)
                    }
                }
            }
        }
    }
    // onBack used by NavRoute; intentionally no top-bar back arrow because the
    // screen is reachable from a Settings button in the same task.
    @Suppress("UNUSED_EXPRESSION") onBack
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipRow(
    current: NetworkLogEntry.Category?,
    onFilter: (NetworkLogEntry.Category?) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("networklog-filters"),
    ) {
        FilterChip(
            selected = current == null,
            onClick = { onFilter(null) },
            label = { Text(stringResource(R.string.networklog_filter_all)) },
            modifier = Modifier.testTag("networklog-filter-all"),
        )
        FilterChip(
            selected = current == NetworkLogEntry.Category.CALENDAR,
            onClick = { onFilter(NetworkLogEntry.Category.CALENDAR) },
            label = { Text(stringResource(R.string.networklog_filter_calendar)) },
            modifier = Modifier.testTag("networklog-filter-calendar"),
        )
        FilterChip(
            selected = current == NetworkLogEntry.Category.LLM,
            onClick = { onFilter(NetworkLogEntry.Category.LLM) },
            label = { Text(stringResource(R.string.networklog_filter_llm)) },
            modifier = Modifier.testTag("networklog-filter-llm"),
        )
        FilterChip(
            selected = current == NetworkLogEntry.Category.STT,
            onClick = { onFilter(NetworkLogEntry.Category.STT) },
            label = { Text(stringResource(R.string.networklog_filter_stt)) },
            modifier = Modifier.testTag("networklog-filter-stt"),
        )
        FilterChip(
            selected = current == NetworkLogEntry.Category.OTHER,
            onClick = { onFilter(NetworkLogEntry.Category.OTHER) },
            label = { Text(stringResource(R.string.networklog_filter_other)) },
            modifier = Modifier.testTag("networklog-filter-other"),
        )
    }
}

@Composable
private fun EntryCard(entry: NetworkLogEntry) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .testTag("networklog-row"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stringResource(R.string.networklog_method_url, entry.method, entry.url),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "${entry.category} · ${entry.timestamp}",
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                stringResource(R.string.networklog_status, entry.statusCode, entry.durationMs),
                style = MaterialTheme.typography.bodySmall,
            )
            AnimatedVisibility(visible = expanded) {
                Column {
                    Text(
                        "URL: ${entry.url}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
