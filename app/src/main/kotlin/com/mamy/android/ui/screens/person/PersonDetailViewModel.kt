package com.mamy.android.ui.screens.person

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamy.android.data.db.dao.ActionDao
import com.mamy.android.data.db.dao.FlagDao
import com.mamy.android.data.db.dao.NoteDao
import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.dao.PromiseDao
import com.mamy.android.data.sms.SentSmsRepository
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
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for [PersonDetailScreen].
 *
 * The DAOs the screen depends on (Person/Note/Promise/Action/Flag) only
 * expose `suspend` query methods today — they have no Flow<…> by-id
 * variants. To keep the screen reactive, this ViewModel:
 *
 *  - Drives all DAO calls from a single [refreshTrigger] [MutableStateFlow].
 *    Every time [refresh] (or a mutation like [rename] / [archive]) bumps the
 *    trigger, the upstream re-runs each suspend DAO method and rebuilds the
 *    state.
 *  - Composes the trigger with the (already reactive) [SentSmsRepository]
 *    flow via [combine].
 *
 * That keeps the wiring deterministic and test-friendly: pushing a value to
 * [refreshTrigger] (or simply observing the initial value) triggers exactly
 * one DAO round-trip, no infinite-loop polling. The screen layer is expected
 * to call [refresh] from a `LaunchedEffect` (e.g. on navigation result) — V1.1
 * will swap the suspend queries for proper `Flow<>` once the DAOs grow them
 * (TECH_DEBT).
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

    /** Bumped to trigger a DAO re-fetch (initial load, after rename/archive, manual refresh). */
    private val refreshTrigger = MutableStateFlow(0L)

    /**
     * For each [refreshTrigger] value we run all 5 suspend DAO queries once
     * (in parallel via [flow] emit) and merge with the SMS [Flow]. The result
     * is collapsed into [PersonDetailUiState].
     */
    val state: StateFlow<PersonDetailUiState> = combine(
        refreshTrigger.flatMapLatest { _ ->
            flow {
                emit(
                    Snapshot(
                        person = personDao.getById(personId),
                        notes = noteDao.getByPersonOrderedDesc(personId),
                        promises = promiseDao.getByPerson(personId.toString()),
                        actions = actionDao.getByPerson(personId),
                        flags = flagDao.getOpenByPerson(personId),
                    )
                )
            }
        },
        sentSmsRepository.observeForPerson(personId),
    ) { snap, sms ->
        PersonDetailUiState(
            person = snap.person,
            notes = snap.notes.sortedByDescending { it.createdAt },
            openPromises = snap.promises.filter { it.status == "active" },
            openActions = snap.actions.filter { it.status == "open" },
            openFlags = snap.flags,
            sentSms = sms,
            isLoading = false,
        )
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PersonDetailUiState())

    /** Force a re-fetch (e.g. after returning from a child screen). */
    fun refresh() { refreshTrigger.value = refreshTrigger.value + 1 }

    /** Persist a renamed [com.mamy.android.data.db.entity.PersonEntity]. */
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
     * Retry sending a previously-failed/pending SMS. The empty stub repo
     * (W1-B default binding) returns false; W1-E lands the real impl.
     */
    fun retrySms(entryId: UUID, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = sentSmsRepository.retry(entryId)
            onResult(ok)
        }
    }

    /** Internal grouping of the 5 DAO results before they are mapped to [PersonDetailUiState]. */
    private data class Snapshot(
        val person: com.mamy.android.data.db.entity.PersonEntity?,
        val notes: List<com.mamy.android.data.db.entity.NoteEntity>,
        val promises: List<com.mamy.android.data.db.entity.PromiseEntity>,
        val actions: List<com.mamy.android.data.db.entity.ActionEntity>,
        val flags: List<com.mamy.android.data.db.entity.FlagEntity>,
    )

    companion object {
        const val ARG_PERSON_ID = "personId"
    }
}
