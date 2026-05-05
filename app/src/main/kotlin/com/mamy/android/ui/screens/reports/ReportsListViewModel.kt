package com.mamy.android.ui.screens.reports

import com.mamy.android.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Drives the reports list screen.
 *
 * Combines three user inputs (sort, query, hideUnmatched) with the repository
 * stream of persons; emits a sorted/filtered [ReportsListUiState] whenever any
 * input or the underlying data changes.
 *
 * The stream is **hot** ([BaseViewModel.asStateFlow]) so configuration changes
 * (rotation) don't refetch from Room.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsListViewModel @Inject constructor(
    private val repo: ReportsPersonRepository,
) : BaseViewModel() {

    private val sort = MutableStateFlow(ReportsSort.Recent)
    private val query = MutableStateFlow("")
    private val hideUnmatched = MutableStateFlow(true)

    val state: StateFlow<ReportsListUiState> =
        combine(sort, query, hideUnmatched) { s, q, h -> Triple(s, q, h) }
            .flatMapLatest { (s, q, h) ->
                repo.observeAll(filterUnmatched = h).map { list ->
                    val filtered = if (q.isBlank()) {
                        list
                    } else {
                        list.filter { it.name.contains(q, ignoreCase = true) }
                    }
                    val sorted = when (s) {
                        ReportsSort.Recent ->
                            filtered.sortedByDescending { it.lastInteractionAt }
                        ReportsSort.Name ->
                            filtered.sortedBy { it.name.lowercase() }
                        ReportsSort.Flags ->
                            filtered.sortedByDescending { it.openFlagCount }
                    }
                    ReportsListUiState(
                        persons = sorted,
                        sort = s,
                        query = q,
                        hideUnmatched = h,
                    )
                }
            }
            .asStateFlow(ReportsListUiState())

    fun setSort(s: ReportsSort) = sort.update { s }
    fun setQuery(q: String) = query.update { q }
    fun toggleHideUnmatched() = hideUnmatched.update { !it }
}
