package com.mamy.android.ui.screens.data

data class DataUiState(
    val stats: DataStats = DataStats(0, 0, 0, 0),
    val isExporting: Boolean = false,
    val lastExportPath: String? = null,
    val errorMessage: String? = null,
)
