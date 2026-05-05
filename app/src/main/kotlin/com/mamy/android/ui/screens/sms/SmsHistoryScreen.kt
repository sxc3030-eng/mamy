package com.mamy.android.ui.screens.sms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mamy.android.R
import com.mamy.android.ui.screens.data.SmsHistoryRow
import com.mamy.android.ui.screens.data.SmsStatus

@Composable
fun SmsHistoryRoute(viewModel: SmsHistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SmsHistoryScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsHistoryScreen(
    state: SmsHistoryUiState,
    onQueryChange: (String) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.sms_history_title)) }) },
        modifier = Modifier.testTag("sms-history-screen"),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(R.string.sms_history_search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sms-history-search"),
            )
            if (state.visible.isEmpty()) {
                Text(
                    stringResource(R.string.sms_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .testTag("sms-history-empty"),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("sms-history-list"),
                ) {
                    items(state.visible, key = { it.id }) { SmsRow(it) }
                }
            }
        }
    }
}

@Composable
private fun SmsRow(row: SmsHistoryRow) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sms-history-row"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(row.contactName, style = MaterialTheme.typography.titleSmall)
            Text(row.body, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${row.phoneNumber} · ${row.sentAt}",
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                row.status.label(),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun SmsStatus.label(): String = when (this) {
    SmsStatus.PENDING -> stringResource(R.string.sms_history_status_pending)
    SmsStatus.SENT -> stringResource(R.string.sms_history_status_sent)
    SmsStatus.DELIVERED -> stringResource(R.string.sms_history_status_delivered)
    SmsStatus.FAILED -> stringResource(R.string.sms_history_status_failed)
    SmsStatus.CANCELLED -> stringResource(R.string.sms_history_status_cancelled)
}
