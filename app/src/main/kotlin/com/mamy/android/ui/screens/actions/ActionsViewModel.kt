package com.mamy.android.ui.screens.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamy.android.data.db.dao.ActionDao
import com.mamy.android.data.db.dao.PersonDao
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
import java.util.UUID
import javax.inject.Inject

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
}
