package com.mamy.android.ui.screens.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamy.android.data.db.dao.ActionDao
import com.mamy.android.data.db.dao.NoteDao
import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.entity.ActionEntity
import com.mamy.android.data.db.entity.NoteEntity
import com.mamy.android.data.tts.TtsService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/** Stable id for the synthetic note that owns all manually-entered actions. */
private val MANUAL_NOTE_ID: UUID =
    UUID.nameUUIDFromBytes("mamy-manual-actions".toByteArray())

/**
 * ViewModel for [ActionsScreen].
 *
 * Like [com.mamy.android.ui.screens.person.PersonDetailViewModel], this
 * drives DAO queries from a [refreshTrigger] [MutableStateFlow] because
 * [ActionDao] only exposes `suspend` queries for the All / by-Person view —
 * `observeOpen` covers only the `open` filter.
 *
 * The trigger fires once on init and again on each [markDone] / [refresh].
 * Linked-person names are resolved client-side via [PersonDao.getById] so
 * each [ActionRow] carries the data the Composable needs without joining
 * two flows in the UI.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActionsViewModel @Inject constructor(
    private val actionDao: ActionDao,
    private val personDao: PersonDao,
    private val noteDao: NoteDao,
    private val tts: TtsService,
) : ViewModel() {

    private val filterFlow = MutableStateFlow(ActionsFilter.Open)
    private val refreshTrigger = MutableStateFlow(0L)

    val state: StateFlow<ActionsUiState> = combine(
        filterFlow,
        refreshTrigger.flatMapLatest { _ ->
            flow { emit(actionDao.getAll()) }
        },
    ) { filter, all ->
        val filtered = when (filter) {
            ActionsFilter.Open -> all.filter { it.status == "open" }
            ActionsFilter.Done -> all.filter { it.status == "done" }
            ActionsFilter.All -> all
        }
        val sorted = when (filter) {
            ActionsFilter.Open -> filtered.sortedWith(
                compareBy(nullsLast()) { it.deadline }
            )
            ActionsFilter.Done -> filtered.sortedByDescending { it.doneAt ?: it.createdAt }
            ActionsFilter.All -> filtered.sortedByDescending { it.createdAt }
        }
        val rows = sorted.map { a ->
            val name = a.linkedPersonId?.let {
                runCatching { personDao.getById(it)?.name }.getOrNull()
            }
            ActionRow(action = a, linkedPersonName = name)
        }
        ActionsUiState(actions = rows, filter = filter, isLoading = false)
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ActionsUiState())

    fun setFilter(f: ActionsFilter) { filterFlow.value = f }

    /** Force a re-fetch. */
    fun refresh() { refreshTrigger.value = refreshTrigger.value + 1 }

    /** Mark an action as done; bumps the refresh trigger so the row drops out (Open filter). */
    fun markDone(id: UUID) {
        viewModelScope.launch {
            actionDao.markDone(id, Instant.now())
            refresh()
        }
    }

    /**
     * Manually add an action without going through a debrief. Stored against a
     * single sentinel "manual" [NoteEntity] so the existing FK chain
     * (action.from_note_id → note.id) stays intact without a schema migration.
     */
    fun addAction(
        description: String,
        assignee: String,
        deadline: Instant?,
        linkedPersonId: UUID? = null,
    ) {
        val desc = description.trim()
        if (desc.isEmpty()) return
        viewModelScope.launch {
            ensureManualNote()
            actionDao.insert(
                ActionEntity(
                    id = UUID.randomUUID(),
                    description = desc,
                    assignee = assignee.trim().ifBlank { "Me" },
                    linkedPersonId = linkedPersonId,
                    deadline = deadline,
                    status = "open",
                    fromNoteId = MANUAL_NOTE_ID,
                    createdAt = Instant.now(),
                    doneAt = null,
                ),
            )
            refresh()
        }
    }

    /** Read an action aloud — description + deadline if set + linked person. */
    fun speak(id: UUID) {
        viewModelScope.launch {
            val a = actionDao.getById(id) ?: return@launch
            val parts = mutableListOf<String>()
            parts += a.description
            a.deadline?.let { dl ->
                val zone = ZoneId.systemDefault()
                val fmt = DateTimeFormatter.ofPattern("d MMMM HH:mm", Locale.getDefault())
                parts += "due " + dl.atZone(zone).format(fmt)
            }
            if (a.assignee.isNotBlank()) parts += "assignee ${a.assignee}"
            tts.speak(parts.joinToString(". "), Locale.getDefault(), interrupt = true)
        }
    }

    private suspend fun ensureManualNote() {
        if (noteDao.getById(MANUAL_NOTE_ID) != null) return
        noteDao.insert(
            NoteEntity(
                id = MANUAL_NOTE_ID,
                personId = null,
                meetingId = null,
                rawText = "[manual entries]",
                structuredJson = null,
                nonStructured = true,
                createdAt = Instant.now(),
                audioDurationSec = 0,
                llmProvider = "manual",
                llmCostCents = 0,
            ),
        )
    }
}
