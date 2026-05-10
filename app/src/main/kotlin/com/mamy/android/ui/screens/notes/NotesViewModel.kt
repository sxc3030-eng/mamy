package com.mamy.android.ui.screens.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamy.android.data.db.dao.NoteDao
import com.mamy.android.data.db.entity.NoteEntity
import com.mamy.android.data.tts.TtsService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * Drives the Notes tab.
 *
 * "Notes" here means free-form text notes the user types directly (NOT debrief
 * transcripts). They are stored as [NoteEntity] rows with `non_structured = true`
 * and `llm_provider = "manual"`, so they never run through the structurer
 * pipeline and never trigger LLM calls.
 *
 * Title and checklist are encoded inside [NoteEntity.rawText] using a simple
 * convention so we don't need a schema migration:
 *   line 0:    "# <title>"        (optional)
 *   line 1+:   body text          (any markdown — checkbox lines `- [ ] ...`)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotesViewModel @Inject constructor(
    private val noteDao: NoteDao,
    private val tts: TtsService,
) : ViewModel() {

    private val refresh = MutableStateFlow(0L)

    val state: StateFlow<NotesUiState> = refresh
        .flatMapLatest {
            flow { emit(noteDao.getAll()) }
        }
        .map { all ->
            // Show only manual notes (non-structured, llm_provider = "manual"),
            // sorted newest first.
            val manuals = all.filter {
                it.llmProvider == "manual" && it.nonStructured
            }.sortedByDescending { it.createdAt }
            NotesUiState(
                rows = manuals.map { e -> e.toRow() },
                isEmpty = manuals.isEmpty(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, NotesUiState())

    fun addNote(title: String, body: String) {
        val cleanTitle = title.trim()
        val cleanBody = body.trimEnd()
        if (cleanTitle.isEmpty() && cleanBody.isEmpty()) return
        val raw = buildString {
            if (cleanTitle.isNotEmpty()) {
                append("# ").append(cleanTitle).append('\n')
            }
            append(cleanBody)
        }
        viewModelScope.launch {
            noteDao.insert(
                NoteEntity(
                    id = UUID.randomUUID(),
                    personId = null,
                    meetingId = null,
                    rawText = raw,
                    structuredJson = null,
                    nonStructured = true,
                    createdAt = Instant.now(),
                    audioDurationSec = 0,
                    llmProvider = "manual",
                    llmCostCents = 0,
                ),
            )
            refresh.value = refresh.value + 1
        }
    }

    fun deleteNote(id: UUID) {
        viewModelScope.launch {
            noteDao.deleteById(id)
            refresh.value = refresh.value + 1
        }
    }

    /** Read the note aloud via TTS in the device's current locale. */
    fun speak(id: UUID) {
        viewModelScope.launch {
            val n = noteDao.getById(id) ?: return@launch
            // Strip leading "# title" markdown so TTS reads naturally.
            val text = n.rawText
                .lineSequence()
                .map { if (it.startsWith("# ")) it.removePrefix("# ") else it }
                .joinToString(". ") { it.trim() }
                .ifBlank { return@launch }
            tts.speak(text, Locale.getDefault(), interrupt = true)
        }
    }

    /** Stop any ongoing TTS playback. */
    fun stopSpeaking() {
        tts.interrupt()
    }
}

data class NotesUiState(
    val rows: List<NoteRow> = emptyList(),
    val isEmpty: Boolean = true,
)

data class NoteRow(
    val id: UUID,
    val title: String?,
    val preview: String,
    val createdAt: Instant,
)

private fun NoteEntity.toRow(): NoteRow {
    val lines = rawText.split('\n')
    val title: String? = lines.firstOrNull()
        ?.takeIf { it.startsWith("# ") }
        ?.removePrefix("# ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    val body = if (title != null) lines.drop(1).joinToString("\n").trim() else rawText.trim()
    val preview = body.take(120) + if (body.length > 120) "…" else ""
    return NoteRow(id = id, title = title, preview = preview, createdAt = createdAt)
}
