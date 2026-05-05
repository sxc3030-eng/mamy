package com.mamy.android.ui.screens.person

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamy.android.data.db.dao.ActionDao
import com.mamy.android.data.db.dao.FlagDao
import com.mamy.android.data.db.dao.NoteDao
import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.dao.PromiseDao
import com.mamy.android.data.db.entity.PersonEntity
import com.mamy.android.data.sms.SentSmsRepository
import com.mamy.android.data.sms.SentSmsRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for [PersonDetailScreen].
 *
 * Combines five reactive sources:
 *  - [PersonDao] for the person row (polled — DAO does not expose Flow<PersonEntity?> by id)
 *  - [NoteDao] for notes (polled, sorted by createdAt desc)
 *  - [PromiseDao] for promises (polled, both directions self ↔ person)
 *  - [ActionDao] for actions linked to this person
 *  - [FlagDao] for active flags
 *  - [SentSmsRepository] for sent SMS rows (already a Flow)
 *
 * Most DAOs only expose `suspend` methods today — we wrap each in a tiny
 * polling Flow so [combine] can treat them uniformly. The polling cadence is
 * 1.5 s which is plenty for a passive consultation screen and avoids
 * sprinkling new DAO methods on each entity (kept on hold for V1.1 — see
 * `TECH_DEBT.md`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val personDao: PersonDao,
    private val noteDao: NoteDao,
    private val promiseDao: PromiseDao,
    private val actionDao: ActionDao,
    private val flagDao: FlagDao,
    private val sentSmsRepository: SentSmsRepository,
) : ViewModel() {

    /** Person ID extracted from the nav route argument. */
    private val personId: UUID = UUID.fromString(
        checkNotNull(saved.get<String>(ARG_PERSON_ID)) {
            "Missing personId nav arg"
        }
    )

    /** Triggered by [refresh] / mutation calls to force a re-poll. */
    private val refreshTrigger = MutableStateFlow(0L)

    val state: StateFlow<PersonDetailUiState> = combine(
        observePerson(personId),
        observeNotes(personId),
        observePromises(personId),
        observeActions(personId),
        combine(
            observeFlags(personId),
            sentSmsRepository.observeForPerson(personId),
        ) { flags, sms -> flags to sms },
    ) { person, notes, promises, actions, flagsAndSms ->
        val (flags, sms) = flagsAndSms
        PersonDetailUiState(
            person = person,
            notes = notes.sortedByDescending { it.createdAt },
            openPromises = promises.filter { it.status == "active" },
            openActions = actions.filter { it.status == "open" },
            openFlags = flags,
            sentSms = sms,
            isLoading = false,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonDetailUiState())

    /** Force a re-poll (e.g. after rename, archive). */
    fun refresh() { refreshTrigger.value += 1 }

    /** Persist a renamed [PersonEntity]; reactive flows pick the change up automatically. */
    fun rename(newName: String) {
        val current = state.value.person ?: return
        viewModelScope.launch {
            personDao.update(current.copy(name = newName.trim()))
            refresh()
        }
    }

    /** Mark this person as archived. UI is expected to navigate back. */
    fun archive() {
        val current = state.value.person ?: return
        viewModelScope.launch {
            personDao.update(current.copy(archived = true))
            refresh()
        }
    }

    /**
     * Retry sending a previously-failed/pending SMS. Returns true if the
     * underlying repo accepts the retry. Currently no-op when
     * [com.mamy.android.data.sms.EmptySentSmsRepository] is bound (W1-E
     * pending).
     */
    fun retrySms(entryId: UUID, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = sentSmsRepository.retry(entryId)
            onResult(ok)
        }
    }

    // ---- Internal: poll-based Flow adapters around suspend DAO calls ----

    private fun observePerson(id: UUID): Flow<PersonEntity?> = flow {
        while (true) {
            emit(personDao.getById(id))
            kotlinx.coroutines.delay(POLL_MS)
        }
    }

    private fun observeNotes(id: UUID) = flow {
        while (true) {
            emit(noteDao.getByPersonOrderedDesc(id))
            kotlinx.coroutines.delay(POLL_MS)
        }
    }

    private fun observePromises(id: UUID) = flow {
        while (true) {
            // Both directions : self ↔ person. P6 alias `openFromTo` exists but
            // requires two queries; `getByPerson` already returns both sides.
            emit(promiseDao.getByPerson(id.toString()))
            kotlinx.coroutines.delay(POLL_MS)
        }
    }

    private fun observeActions(id: UUID) = flow {
        while (true) {
            emit(actionDao.getByPerson(id))
            kotlinx.coroutines.delay(POLL_MS)
        }
    }

    private fun observeFlags(id: UUID) = flow {
        while (true) {
            emit(flagDao.getOpenByPerson(id))
            kotlinx.coroutines.delay(POLL_MS)
        }
    }

    companion object {
        const val ARG_PERSON_ID = "personId"
        private const val POLL_MS = 1_500L
    }
}
