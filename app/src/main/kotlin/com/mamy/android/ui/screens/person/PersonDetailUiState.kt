package com.mamy.android.ui.screens.person

import com.mamy.android.data.db.entity.ActionEntity
import com.mamy.android.data.db.entity.FlagEntity
import com.mamy.android.data.db.entity.NoteEntity
import com.mamy.android.data.db.entity.PersonEntity
import com.mamy.android.data.db.entity.PromiseEntity
import com.mamy.android.data.sms.SentSmsRow

/**
 * Immutable snapshot of everything PersonDetailScreen renders.
 *
 * Combines outputs of 5 DAOs (Person + Note + Promise + Action + Flag) and 1
 * SMS bridge (SentSmsRepository) into a single state class. The screen
 * derives all tab contents from this — no extra fetching at the Compose
 * layer.
 */
data class PersonDetailUiState(
    val person: PersonEntity? = null,
    val notes: List<NoteEntity> = emptyList(),
    val openPromises: List<PromiseEntity> = emptyList(),
    val openActions: List<ActionEntity> = emptyList(),
    val openFlags: List<FlagEntity> = emptyList(),
    val sentSms: List<SentSmsRow> = emptyList(),
    val isLoading: Boolean = true,
) {
    /** Number of unresolved flags — surfaced as a badge in the header. */
    val openFlagCount: Int get() = openFlags.size
}

/** Tab index used by the Compose layer. Order matches [PersonDetailTab.values]. */
enum class PersonDetailTab(val titleRes: Int) {
    Notes(com.mamy.android.R.string.person_detail_tab_notes),
    Promises(com.mamy.android.R.string.person_detail_tab_promises),
    Actions(com.mamy.android.R.string.person_detail_tab_actions),
    Flags(com.mamy.android.R.string.person_detail_tab_flags),
    Sms(com.mamy.android.R.string.person_detail_tab_sms),
}
