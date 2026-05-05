package com.mamy.android.ui.screens.networklog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamy.android.data.network.NetworkLogEntry
import com.mamy.android.data.network.NetworkLogStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NetworkLogViewModel @Inject constructor(
    store: NetworkLogStore,
) : ViewModel() {

    private val _filter: MutableStateFlow<NetworkLogEntry.Category?> = MutableStateFlow(null)

    val state: StateFlow<NetworkLogUiState> = combine(
        store.stream,
        _filter,
    ) { entries, filter ->
        NetworkLogUiState(
            // Sort newest-first for display.
            entries = entries.sortedByDescending { it.timestamp },
            filter = filter,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NetworkLogUiState())

    fun setFilter(category: NetworkLogEntry.Category?) {
        _filter.value = category
    }
}
