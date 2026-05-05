package com.mamy.android.ui.screens.actions

import com.mamy.android.data.db.entity.ActionEntity
import java.util.UUID

/** Filter options exposed in the Actions screen filter chips. */
enum class ActionsFilter(val titleRes: Int) {
    Open(com.mamy.android.R.string.actions_filter_open),
    Done(com.mamy.android.R.string.actions_filter_done),
    All(com.mamy.android.R.string.actions_filter_all),
}

/**
 * View-layer row combining an [ActionEntity] with the resolved name of its
 * linked person (joined client-side because there is no Room view yet).
 */
data class ActionRow(
    val action: ActionEntity,
    val linkedPersonName: String?,
) {
    val id: UUID get() = action.id
}

data class ActionsUiState(
    val actions: List<ActionRow> = emptyList(),
    val filter: ActionsFilter = ActionsFilter.Open,
    val isLoading: Boolean = true,
)
