package com.mamy.android.ui.screens.networklog

import com.mamy.android.data.network.NetworkLogEntry

data class NetworkLogUiState(
    val entries: List<NetworkLogEntry> = emptyList(),
    val filter: NetworkLogEntry.Category? = null,
) {
    val visible: List<NetworkLogEntry>
        get() = if (filter == null) entries else entries.filter { it.category == filter }
}
