package com.mamy.android.ui.screens.reports

/**
 * Sort options for the reports list (P7 plan §8).
 *
 * - [Recent] : descending by `last_interaction_at` (default)
 * - [Name] : ascending by name (case-insensitive)
 * - [Flags] : descending by open flag count (most flags at the top)
 */
enum class ReportsSort { Recent, Name, Flags }

/**
 * Reports list UI state.
 *
 * [persons] is already sorted + filtered by the ViewModel; the screen renders
 * it directly. [hideUnmatched] defaults to true (the noisy unmatched bucket is
 * collapsed by default, exposed by the toggle chip).
 */
data class ReportsListUiState(
    val persons: List<PersonRow> = emptyList(),
    val sort: ReportsSort = ReportsSort.Recent,
    val query: String = "",
    val hideUnmatched: Boolean = true,
    val isLoading: Boolean = false,
)
