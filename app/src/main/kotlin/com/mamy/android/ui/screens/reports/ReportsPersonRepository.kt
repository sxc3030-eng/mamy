package com.mamy.android.ui.screens.reports

import kotlinx.coroutines.flow.Flow

/**
 * Repository contract used by [ReportsListViewModel] and [PersonDetailViewModel] (W1-B).
 *
 * Kept in the UI/reports package so this branch (W1-A) can declare what it needs
 * without modifying P1-P6 sources. The orchestrator will re-home this contract to
 * the data layer and provide a Room-backed implementation in a follow-up.
 */
interface ReportsPersonRepository {
    /**
     * Observes all active (non-archived) persons.
     *
     * @param filterUnmatched when true, also excludes [com.mamy.android.data.db.entity.PersonEntity.unmatched] = true
     */
    fun observeAll(filterUnmatched: Boolean): Flow<List<PersonRow>>
}
