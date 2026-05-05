package com.mamy.android.ui.screens.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mamy.android.R

@Composable
fun DataRoute(
    viewModel: DataViewModel = hiltViewModel(),
    onOpenSmsHistory: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DataScreen(
        state = state,
        onExport = viewModel::exportAll,
        onWipeAll = viewModel::wipeAll,
        onOpenSmsHistory = onOpenSmsHistory,
    )
}

/**
 * Tes données screen. Layout matches the wireframe in P9 spec section 8.4:
 *   1. Stats card (people, notes, open actions, SMS sent via MamY)
 *   2. SMS history button → SmsHistoryScreen
 *   3. Export passphrase + button (P8 wires real impl, stub for now)
 *   4. Wipe-all button with double-confirm dialog ("Type MamY")
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(
    state: DataUiState,
    onExport: (String) -> Unit,
    onWipeAll: () -> Unit,
    onOpenSmsHistory: () -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var wipeDialogVisible by remember { mutableStateOf(false) }
    var wipeConfirmInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.data_title)) }) },
        modifier = Modifier.testTag("data-screen"),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatsCard(state.stats)
            SmsHistoryCard(state.stats.smsSentCount, onOpenSmsHistory)

            // Export
            Text(
                stringResource(R.string.data_section_export),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text(stringResource(R.string.data_export_passphrase_label)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("data-passphrase"),
            )
            Button(
                onClick = { onExport(passphrase) },
                enabled = passphrase.length >= 8 && !state.isExporting,
                modifier = Modifier.testTag("data-btn-export"),
            ) { Text(stringResource(R.string.data_btn_export_all)) }
            state.lastExportPath?.let {
                Text(stringResource(R.string.data_export_done, it))
            }
            state.errorMessage?.let {
                Text(
                    stringResource(R.string.data_export_failure, it),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Wipe
            Text(
                stringResource(R.string.data_section_wipe),
                style = MaterialTheme.typography.titleMedium,
            )
            Button(
                onClick = { wipeDialogVisible = true; wipeConfirmInput = "" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.testTag("data-btn-wipe-all"),
            ) { Text(stringResource(R.string.data_btn_wipe_all)) }
        }
    }

    if (wipeDialogVisible) {
        AlertDialog(
            onDismissRequest = { wipeDialogVisible = false },
            title = { Text(stringResource(R.string.data_wipe_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.data_wipe_dialog_body))
                    OutlinedTextField(
                        value = wipeConfirmInput,
                        onValueChange = { wipeConfirmInput = it },
                        label = { Text(stringResource(R.string.data_wipe_confirm_input)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("data-wipe-confirm-input"),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = wipeConfirmInput.trim() == "MamY",
                    onClick = {
                        wipeDialogVisible = false
                        onWipeAll()
                    },
                    modifier = Modifier.testTag("data-btn-wipe-confirm"),
                ) { Text(stringResource(R.string.data_wipe_btn_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { wipeDialogVisible = false },
                    modifier = Modifier.testTag("data-btn-wipe-cancel"),
                ) { Text(stringResource(R.string.data_wipe_btn_cancel)) }
            },
        )
    }
}

@Composable
private fun StatsCard(stats: DataStats) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .testTag("data-stats-card")) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.data_section_stats),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(stringResource(R.string.data_stat_persons, stats.personCount))
            Text(stringResource(R.string.data_stat_notes, stats.noteCount))
            Text(stringResource(R.string.data_stat_actions_open, stats.openActionCount))
            // P9 NEW
            Text(
                stringResource(R.string.data_stat_sms_sent, stats.smsSentCount),
                modifier = Modifier.testTag("data-stat-sms-sent"),
            )
        }
    }
}

@Composable
private fun SmsHistoryCard(smsCount: Int, onOpen: () -> Unit) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .testTag("data-sms-history-card")) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.data_section_sms_history),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedButton(
                onClick = onOpen,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("data-btn-view-all-sms"),
            ) { Text(stringResource(R.string.data_btn_view_all_sms)) }
        }
    }
    @Suppress("UNUSED_EXPRESSION") smsCount
}
