package com.mamy.android.ui.screens.person

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mamy.android.R
import com.mamy.android.data.db.entity.ActionEntity
import com.mamy.android.data.db.entity.FlagEntity
import com.mamy.android.data.db.entity.NoteEntity
import com.mamy.android.data.db.entity.PersonEntity
import com.mamy.android.data.db.entity.PromiseEntity
import com.mamy.android.data.sms.SentSmsRow
import com.mamy.android.data.sms.SmsStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Hilt entry point — instantiates [PersonDetailViewModel] from the nav graph.
 */
@Composable
fun PersonDetailRoute(
    onBack: () -> Unit,
    viewModel: PersonDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PersonDetailScreen(
        state = state,
        onBack = onBack,
        onRename = viewModel::rename,
        onArchive = {
            viewModel.archive()
            onBack()
        },
        onMerge = { /* V2 — merge dialog stub */ },
        onRetrySms = { id -> viewModel.retrySms(id) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    state: PersonDetailUiState,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onArchive: () -> Unit,
    onMerge: () -> Unit,
    onRetrySms: (java.util.UUID) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showArchiveConfirm by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.person?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("person-btn-back"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.person_detail_cd_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("person-btn-more"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.person_detail_cd_more),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.person_detail_btn_edit)) },
                            onClick = {
                                menuExpanded = false
                                showRename = true
                            },
                            modifier = Modifier.testTag("person-btn-edit"),
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.person_detail_btn_merge)) },
                            onClick = {
                                menuExpanded = false
                                onMerge()
                            },
                            modifier = Modifier.testTag("person-btn-merge"),
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.person_detail_btn_archive)) },
                            onClick = {
                                menuExpanded = false
                                showArchiveConfirm = true
                            },
                            modifier = Modifier.testTag("person-btn-archive"),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("person-loading"),
                )
            }
            state.person?.let { p ->
                PersonHeaderCard(person = p, openFlagCount = state.openFlagCount)
            }
            TabRow(selectedTabIndex = selectedTab, modifier = Modifier.testTag("person-tabs")) {
                PersonDetailTab.values().forEachIndexed { idx, tab ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(stringResource(tab.titleRes)) },
                        modifier = Modifier.testTag("person-tab-${tab.name.lowercase()}"),
                    )
                }
            }
            when (PersonDetailTab.values()[selectedTab]) {
                PersonDetailTab.Notes -> NotesTab(state.notes)
                PersonDetailTab.Promises -> PromisesTab(state.openPromises)
                PersonDetailTab.Actions -> ActionsTab(state.openActions)
                PersonDetailTab.Flags -> FlagsTab(state.openFlags)
                PersonDetailTab.Sms -> SmsTab(state.sentSms, onRetrySms)
            }
        }
    }

    if (showRename && state.person != null) {
        RenameDialog(
            currentName = state.person.name,
            onConfirm = {
                onRename(it)
                showRename = false
            },
            onDismiss = { showRename = false },
        )
    }
    if (showArchiveConfirm && state.person != null) {
        ArchiveDialog(
            personName = state.person.name,
            onConfirm = {
                onArchive()
                showArchiveConfirm = false
            },
            onDismiss = { showArchiveConfirm = false },
        )
    }
}

// ---------------- Header ----------------

@Composable
private fun PersonHeaderCard(person: PersonEntity, openFlagCount: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("person-header"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = person.roleHint ?: stringResource(R.string.person_detail_role_unknown),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.person_detail_interactions, person.interactionCount),
                style = MaterialTheme.typography.bodyMedium,
            )
            person.emotionalTrend?.let { trend ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .background(emotionalTrendColor(trend), CircleShape),
                    ) { Text("   ") }
                    Text(
                        text = stringResource(R.string.person_detail_trend, trend),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (openFlagCount > 0) {
                Text(
                    text = stringResource(R.string.person_detail_open_flags, openFlagCount),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.testTag("person-flag-badge"),
                )
            }
        }
    }
}

@Composable
private fun emotionalTrendColor(trend: String): Color {
    return when (trend.lowercase()) {
        "engaged" -> Color(0xFF2E7D32)        // green
        "disengaged", "burned_out" -> Color(0xFFC62828) // red
        else -> MaterialTheme.colorScheme.outline
    }
}

// ---------------- Notes ----------------

@Composable
private fun NotesTab(notes: List<NoteEntity>) {
    if (notes.isEmpty()) {
        EmptyState(stringResource(R.string.person_detail_empty_notes), tag = "person-notes-empty")
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("person-notes"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        items(notes, key = { it.id }) { NoteRow(it) }
    }
}

@Composable
private fun NoteRow(note: NoteEntity) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("person-note-row"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = formatInstant(note.createdAt),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = note.rawText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("person-note-text"),
            )
            if (note.rawText.length > 120) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.testTag("person-note-toggle"),
                ) {
                    Text(if (expanded) "▲" else "▼")
                }
            }
        }
    }
}

// ---------------- Promises ----------------

@Composable
private fun PromisesTab(promises: List<PromiseEntity>) {
    if (promises.isEmpty()) {
        EmptyState(stringResource(R.string.person_detail_empty_promises), tag = "person-promises-empty")
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("person-promises"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        items(promises, key = { it.id }) { PromiseRow(it) }
    }
}

@Composable
private fun PromiseRow(p: PromiseEntity) {
    val selfLabel = stringResource(R.string.person_detail_promise_self)
    val from = if (p.fromId == "self") selfLabel else "→"
    val to = if (p.toId == "self") selfLabel else "→"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("person-promise-row"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "$from → $to",
                style = MaterialTheme.typography.labelSmall,
            )
            Text(p.what, style = MaterialTheme.typography.bodyMedium)
            p.due?.let {
                Text(
                    text = stringResource(R.string.person_detail_promise_due, formatInstant(it)),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

// ---------------- Actions ----------------

@Composable
private fun ActionsTab(actions: List<ActionEntity>) {
    if (actions.isEmpty()) {
        EmptyState(stringResource(R.string.person_detail_empty_actions), tag = "person-actions-empty")
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("person-actions"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        items(actions, key = { it.id }) { ActionRowCompact(it) }
    }
}

@Composable
private fun ActionRowCompact(a: ActionEntity) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("person-action-row"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(a.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = a.deadline?.let {
                    stringResource(R.string.person_detail_action_deadline, formatInstant(it))
                } ?: stringResource(R.string.actions_no_deadline),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

// ---------------- Flags ----------------

@Composable
private fun FlagsTab(flags: List<FlagEntity>) {
    if (flags.isEmpty()) {
        EmptyState(stringResource(R.string.person_detail_empty_flags), tag = "person-flags-empty")
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("person-flags"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        items(flags, key = { it.id }) { FlagRow(it) }
    }
}

@Composable
private fun FlagRow(f: FlagEntity) {
    val severityColor = when (f.severity.lowercase()) {
        "high" -> Color(0xFFC62828)
        "medium" -> Color(0xFFEF6C00)
        else -> Color(0xFF616161)
    }
    val severityLabel = when (f.severity.lowercase()) {
        "high" -> stringResource(R.string.person_detail_severity_high)
        "medium" -> stringResource(R.string.person_detail_severity_medium)
        else -> stringResource(R.string.person_detail_severity_low)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("person-flag-row"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .background(severityColor, CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("person-flag-severity"),
                ) {
                    Text(
                        severityLabel.uppercase(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(
                    text = f.type,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Text(f.note, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---------------- SMS (P9) ----------------

@Composable
private fun SmsTab(rows: List<SentSmsRow>, onRetry: (java.util.UUID) -> Unit) {
    if (rows.isEmpty()) {
        EmptyState(stringResource(R.string.person_detail_empty_sms), tag = "person-sms-empty")
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("person-sms"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        items(rows, key = { it.id }) { SmsRow(it, onRetry) }
    }
}

@Composable
private fun SmsRow(row: SentSmsRow, onRetry: (java.util.UUID) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("person-sms-row"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = formatInstant(row.sentAt),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = smsStatusLabel(row.status),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.testTag("person-sms-status-${row.status.raw}"),
                )
            }
            Text(
                text = row.body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            row.failReason?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (SmsStatus.retryable(row.status)) {
                TextButton(
                    onClick = { onRetry(row.id) },
                    modifier = Modifier.testTag("person-sms-retry"),
                ) {
                    Text(stringResource(R.string.person_detail_sms_retry))
                }
            }
        }
    }
}

@Composable
private fun smsStatusLabel(status: SmsStatus): String = stringResource(
    when (status) {
        SmsStatus.PENDING -> R.string.person_detail_sms_status_pending
        SmsStatus.SENT -> R.string.person_detail_sms_status_sent
        SmsStatus.DELIVERED -> R.string.person_detail_sms_status_delivered
        SmsStatus.FAILED -> R.string.person_detail_sms_status_failed
        SmsStatus.CANCELLED -> R.string.person_detail_sms_status_cancelled
    }
)

// ---------------- Dialogs ----------------

@Composable
private fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.person_detail_dialog_edit_title, currentName)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.person_detail_dialog_edit_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("person-rename-field"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                modifier = Modifier.testTag("person-rename-confirm"),
                enabled = text.isNotBlank() && text.trim() != currentName,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        modifier = Modifier.testTag("person-rename-dialog"),
    )
}

@Composable
private fun ArchiveDialog(
    personName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.person_detail_dialog_archive_title, personName)) },
        text = { Text(stringResource(R.string.person_detail_dialog_archive_msg)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("person-archive-confirm"),
            ) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        modifier = Modifier.testTag("person-archive-dialog"),
    )
}

// ---------------- Helpers ----------------

@Composable
private fun EmptyState(text: String, tag: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

private val DATE_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

private fun formatInstant(t: Instant): String = DATE_FMT.format(t)
