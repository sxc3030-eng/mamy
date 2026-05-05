package com.mamy.android.ui.screens.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamy.android.data.db.dao.ActionDao
import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.entity.ActionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for [ActionsScreen]. Surfaces actions filtered by status (open /
 * done / all) and resolves the linked-person name client-side so each row
 * carries everything the Composable needs.
 *
 * Like [com.mamy.android.ui.screens.person.PersonDetailViewModel], this wraps
 * suspend DAO calls in a 1.5s polling Flow because [ActionDao.observeOpen]
 * only covers the `open` filter — V1.1 will add `observeAll(status)`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActionsViewModel @Inject constructor(
    private val actionDao: ActionDao,
    private val personDao: PersonDao,
) : ViewModel() {

    private val filterFlow = MutableStateFlow(ActionsFilter.Open)

    val state: StateFlow<ActionsUiState> = combine(
        filterFlow,
        observeActions(),
    ) { filter, actions ->
        val filtered = when (filter) {
            ActionsFilter.Open -> actions.filter { it.status == "open" }
            ActionsFilter.Done -> actions.filter { it.status == "done" }
            ActionsFilter.All -> actions
        }
        // Sort: open first by deadline asc (nulls last), done by doneAt desc, all by createdAt desc.
        val sorted = when (filter) {
            ActionsFilter.Open -> filtered.sortedWith(
                compareBy(nullsLast()) { it.deadline }
            )
            ActionsFilter.Done -> filtered.sortedByDescending { it.doneAt ?: it.createdAt }
            ActionsFilter.All -> filtered.sortedByDescending { it.createdAt }
        }
        val rows = sorted.map { a ->
            val name = a.linkedPersonId?.let { runCatching { personDao.getById(it)?.name }.getOrNull() }
            ActionRow(action = a, linkedPersonName = name)
        }
        ActionsUiState(actions = rows, filter = filter, isLoading = false)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActionsUiState())

    fun setFilter(f: ActionsFilter) { filterFlow.value = f }

    /** Mark an action as done. Reactive flow will surface the change. */
    fun markDone(id: UUID) {
        viewModelScope.launch {
            actionDao.markDone(id, Instant.now())
        }
    }

    private fun observeActions() = flow<List<ActionEntity>> {
        while (true) {
            emit(actionDao.getAll())
            kotlinx.coroutines.delay(POLL_MS)
        }
    }

    companion object {
        private const val POLL_MS = 1_500L
    }
}
