package com.mamy.android.ui.screens.sms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamy.android.ui.screens.data.SmsHistoryRow
import com.mamy.android.ui.screens.data.SmsHistorySource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SmsHistoryUiState(
    val rows: List<SmsHistoryRow> = emptyList(),
    val query: String = "",
) {
    val visible: List<SmsHistoryRow>
        get() = if (query.isBlank()) rows else {
            val q = query.trim().lowercase()
            rows.filter { row ->
                row.contactName.lowercase().contains(q) ||
                    row.body.lowercase().contains(q) ||
                    row.phoneNumber.contains(q) ||
                    row.sentAt.toString().contains(q)
            }
        }
}

@HiltViewModel
class SmsHistoryViewModel @Inject constructor(
    source: SmsHistorySource,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<SmsHistoryUiState> = combine(
        source.observeAll(),
        query,
    ) { rows, q ->
        SmsHistoryUiState(rows = rows.sortedByDescending { it.sentAt }, query = q)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SmsHistoryUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }
}
