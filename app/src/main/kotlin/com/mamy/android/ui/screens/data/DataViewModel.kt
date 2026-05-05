package com.mamy.android.ui.screens.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [DataScreen]: aggregates [DataStatsSource] and SMS counts into
 * a single state, drives export / wipe through [DataActions]. Stub
 * implementations of all three live in the data package until W1-E and P8 wire
 * the real ones via the same Hilt bindings.
 */
@HiltViewModel
class DataViewModel @Inject constructor(
    private val statsSource: DataStatsSource,
    private val actions: DataActions,
) : ViewModel() {

    private val transient: MutableStateFlow<DataUiState> = MutableStateFlow(DataUiState())

    val state: StateFlow<DataUiState> = combine(
        statsSource.observeStats(),
        transient.asStateFlow(),
    ) { stats, t ->
        t.copy(stats = stats)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DataUiState())

    fun exportAll(passphrase: String) = viewModelScope.launch {
        transient.update { it.copy(isExporting = true, errorMessage = null) }
        when (val result = actions.exportAll(passphrase)) {
            is ExportOutcome.Success -> transient.update {
                it.copy(isExporting = false, lastExportPath = result.path, errorMessage = null)
            }
            is ExportOutcome.Failure -> transient.update {
                it.copy(isExporting = false, errorMessage = result.reason)
            }
        }
    }

    fun wipeAll() = viewModelScope.launch { actions.wipeAll() }
    fun wipePerson(personId: String) = viewModelScope.launch { actions.wipePerson(personId) }
}
