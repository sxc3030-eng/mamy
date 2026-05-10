package com.mamy.android.ui.screens.actions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mamy.android.R
import com.mamy.android.ui.common.MicButton
import com.mamy.android.ui.common.rememberSpeechToTextLauncher
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun ActionsRoute(viewModel: ActionsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ActionsScreen(
        state = state,
        onFilterChange = viewModel::setFilter,
        onMarkDone = viewModel::markDone,
        onAddAction = { desc, assignee, deadline ->
            viewModel.addAction(desc, assignee, deadline)
        },
        onSpeakAction = viewModel::speak,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionsScreen(
    state: ActionsUiState,
    onFilterChange: (ActionsFilter) -> Unit,
    onMarkDone: (UUID) -> Unit,
    onAddAction: (description: String, assignee: String, deadline: Instant?) -> Unit = { _, _, _ -> },
    onSpeakAction: (UUID) -> Unit = {},
) {
    var showAddDialog by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.actions_title)) })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.actions_btn_add)) },
                modifier = Modifier.testTag("actions-fab-add"),
            )
        },
    ) { padding ->
        if (showAddDialog) {
            AddActionDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { d, a, dl ->
                    onAddAction(d, a, dl)
                    showAddDialog = false
                },
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("actions-loading"),
                )
            }
            FilterChipsRow(
                current = state.filter,
                onChange = onFilterChange,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("actions-filters"),
            )
            if (state.actions.isEmpty() && !state.isLoading) {
                EmptyActionsState()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .testTag("actions-list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    items(state.actions, key = { it.id }) { row ->
                        ActionRowItem(
                            row = row,
                            allowSwipe = state.filter == ActionsFilter.Open,
                            onMarkDone = onMarkDone,
                            onSpeak = { onSpeakAction(row.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dialog to manually add an Action without going through a debrief.
 * Description is required. Assignee defaults to "Me". Deadline is optional and
 * exposed via 4 quick presets (None / Today / Tomorrow / Next week) — full
 * date-time picker comes in V1.1 to keep this dialog small on phones.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddActionDialog(
    onDismiss: () -> Unit,
    onConfirm: (description: String, assignee: String, deadline: Instant?) -> Unit,
) {
    var description by remember { mutableStateOf("") }
    var assignee by remember { mutableStateOf("Me") }
    var deadlinePreset by remember { mutableStateOf(DeadlinePreset.None) }
    val descStt = rememberSpeechToTextLauncher { recognized ->
        description = if (description.isBlank()) recognized else "$description $recognized"
    }
    val assigneeStt = rememberSpeechToTextLauncher { recognized ->
        assignee = recognized
    }
    val now = remember { Instant.now() }
    val computedDeadline = when (deadlinePreset) {
        DeadlinePreset.None -> null
        DeadlinePreset.Today -> now.plus(Duration.ofHours(8))
        DeadlinePreset.Tomorrow -> now.plus(Duration.ofDays(1))
        DeadlinePreset.NextWeek -> now.plus(Duration.ofDays(7))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(description, assignee, computedDeadline) },
                enabled = description.isNotBlank(),
                modifier = Modifier.testTag("actions-add-dialog-confirm"),
            ) { Text(stringResource(R.string.actions_btn_add_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.actions_btn_add_cancel))
            }
        },
        title = { Text(stringResource(R.string.actions_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.actions_add_desc)) },
                    singleLine = false,
                    minLines = 2,
                    trailingIcon = { MicButton(onClick = { descStt.launch() }) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("actions-add-desc"),
                )
                OutlinedTextField(
                    value = assignee,
                    onValueChange = { assignee = it },
                    label = { Text(stringResource(R.string.actions_add_assignee)) },
                    singleLine = true,
                    trailingIcon = { MicButton(onClick = { assigneeStt.launch() }) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("actions-add-assignee"),
                )
                Text(
                    stringResource(R.string.actions_add_deadline_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    DeadlinePreset.values().forEach { preset ->
                        FilterChip(
                            selected = deadlinePreset == preset,
                            onClick = { deadlinePreset = preset },
                            label = { Text(stringResource(preset.labelRes)) },
                            modifier = Modifier.testTag("actions-add-deadline-${preset.name.lowercase()}"),
                        )
                    }
                }
            }
        },
    )
}

private enum class DeadlinePreset(val labelRes: Int) {
    None(R.string.actions_add_deadline_none),
    Today(R.string.actions_add_deadline_today),
    Tomorrow(R.string.actions_add_deadline_tomorrow),
    NextWeek(R.string.actions_add_deadline_next_week),
}

/**
 * Inviting empty-state for the Actions screen.
 * Explains where actions come from and shows an example.
 */
@Composable
private fun EmptyActionsState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .testTag("actions-empty"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.actions_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.actions_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(R.string.actions_empty_example_title),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    stringResource(R.string.actions_empty_example_quote),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.actions_empty_example_result),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipsRow(
    current: ActionsFilter,
    onChange: (ActionsFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionsFilter.values().forEach { f ->
            FilterChip(
                selected = current == f,
                onClick = { onChange(f) },
                label = { Text(stringResource(f.titleRes)) },
                modifier = Modifier.testTag("actions-filter-${f.name.lowercase()}"),
            )
        }
    }
}

/**
 * One row of the actions list. Wrapped in [SwipeToDismissBox] so the user
 * can swipe right to mark it done. The swipe is only enabled when the filter
 * is [ActionsFilter.Open] — swiping a `done` action to "done" makes no sense.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionRowItem(
    row: ActionRow,
    allowSwipe: Boolean,
    onMarkDone: (UUID) -> Unit,
    onSpeak: () -> Unit = {},
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (allowSwipe && value == SwipeToDismissBoxValue.StartToEnd) {
                onMarkDone(row.id)
                true
            } else {
                false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.5f },
    )
    LaunchedEffect(row.id) {
        // Reset dismiss state if recycled with a new id.
        dismissState.reset()
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("actions-row"),
        backgroundContent = {
            if (allowSwipe) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF2E7D32))
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = stringResource(R.string.actions_cd_done),
                        tint = Color.White,
                    )
                }
            }
        },
        enableDismissFromStartToEnd = allowSwipe,
        enableDismissFromEndToStart = false,
    ) {
        ActionCard(row = row, onMarkDone = onMarkDone, allowMarkDone = allowSwipe, onSpeak = onSpeak)
    }
}

@Composable
private fun ActionCard(
    row: ActionRow,
    onMarkDone: (UUID) -> Unit,
    allowMarkDone: Boolean,
    onSpeak: () -> Unit = {},
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.action.description,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("actions-row-desc"),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.action.deadline?.let {
                            stringResource(R.string.actions_deadline, formatRelative(it))
                        } ?: stringResource(R.string.actions_no_deadline),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    if (row.linkedPersonName != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                modifier = Modifier
                                    .testTag("actions-row-person-icon")
                                    .padding(end = 2.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = row.linkedPersonName,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
            IconButton(
                onClick = onSpeak,
                modifier = Modifier.testTag("actions-row-speak"),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.read_aloud_cd),
                )
            }
            if (allowMarkDone) {
                IconButton(
                    onClick = { onMarkDone(row.id) },
                    modifier = Modifier.testTag("actions-btn-done"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = stringResource(R.string.actions_cd_done),
                    )
                }
            } else {
                Text(
                    text = if (row.action.status == "done") {
                        stringResource(R.string.actions_status_done)
                    } else {
                        stringResource(R.string.actions_status_open)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("actions-row-status"),
                )
            }
        }
    }
}

private val DATE_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd")
        .withZone(ZoneId.systemDefault())

/**
 * Tiny relative-time formatter — for now we surface a calendar date.
 * Full "in 2 days" / "yesterday" formatter is V1.1 (TECH_DEBT).
 */
private fun formatRelative(t: Instant): String = DATE_FMT.format(t)
