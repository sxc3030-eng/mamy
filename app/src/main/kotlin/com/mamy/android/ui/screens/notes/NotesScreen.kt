package com.mamy.android.ui.screens.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.Alignment
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mamy.android.R
import com.mamy.android.ui.common.MicButton
import com.mamy.android.ui.common.rememberSpeechToTextLauncher
import java.time.Duration
import java.time.Instant

@Composable
fun NotesRoute(viewModel: NotesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NotesScreen(
        state = state,
        onAddNote = { title, body -> viewModel.addNote(title, body) },
        onSpeakNote = viewModel::speak,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    state: NotesUiState,
    onAddNote: (title: String, body: String) -> Unit = { _, _ -> },
    onSpeakNote: (java.util.UUID) -> Unit = {},
) {
    var showAddDialog by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.notes_title)) })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.notes_btn_add)) },
                modifier = Modifier.testTag("notes-fab-add"),
            )
        },
        modifier = Modifier.testTag("notes-screen"),
    ) { padding ->
        if (showAddDialog) {
            AddNoteDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { title, body ->
                    onAddNote(title, body)
                    showAddDialog = false
                },
            )
        }
        if (state.isEmpty) {
            NotesEmptyState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .testTag("notes-list"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
            ) {
                items(state.rows, key = { it.id }) { row ->
                    NoteCard(row, onSpeak = { onSpeakNote(row.id) })
                }
            }
        }
    }
}

@Composable
private fun NoteCard(row: NoteRow, onSpeak: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().testTag("notes-row")) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                row.title?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (row.preview.isNotEmpty()) {
                    Text(
                        row.preview,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    relativeTime(row.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onSpeak,
                modifier = Modifier.testTag("notes-row-speak"),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.read_aloud_cd),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddNoteDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, body: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val titleStt = rememberSpeechToTextLauncher { recognized ->
        title = if (title.isBlank()) recognized else "$title $recognized"
    }
    val bodyStt = rememberSpeechToTextLauncher { recognized ->
        body = if (body.isBlank()) recognized else "$body\n$recognized"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title, body) },
                enabled = title.isNotBlank() || body.isNotBlank(),
                modifier = Modifier.testTag("notes-add-dialog-confirm"),
            ) { Text(stringResource(R.string.notes_btn_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.notes_btn_cancel)) }
        },
        title = { Text(stringResource(R.string.notes_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.notes_field_title)) },
                    singleLine = true,
                    trailingIcon = { MicButton(onClick = { titleStt.launch() }) },
                    modifier = Modifier.fillMaxWidth().testTag("notes-add-title"),
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(stringResource(R.string.notes_field_body)) },
                    minLines = 4,
                    trailingIcon = { MicButton(onClick = { bodyStt.launch() }) },
                    modifier = Modifier.fillMaxWidth().testTag("notes-add-body"),
                )
                Text(
                    stringResource(R.string.notes_field_body_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun NotesEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("notes-empty"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.notes_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.notes_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(R.string.notes_empty_examples_title),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text("• " + stringResource(R.string.notes_empty_example_1), style = MaterialTheme.typography.bodySmall)
                Text("• " + stringResource(R.string.notes_empty_example_2), style = MaterialTheme.typography.bodySmall)
                Text("• " + stringResource(R.string.notes_empty_example_3), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun relativeTime(t: Instant, now: Instant = Instant.now()): String {
    val d = Duration.between(t, now)
    return when {
        d.toMinutes() < 1 -> "now"
        d.toMinutes() < 60 -> "${d.toMinutes()}m ago"
        d.toHours() < 24 -> "${d.toHours()}h ago"
        d.toDays() < 30 -> "${d.toDays()}d ago"
        else -> t.toString().substring(0, 10)
    }
}
