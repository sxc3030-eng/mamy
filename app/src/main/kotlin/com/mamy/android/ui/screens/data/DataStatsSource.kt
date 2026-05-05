package com.mamy.android.ui.screens.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggregate counters surfaced in the DataScreen stats header.
 * Non-SMS counts come from existing Room DAOs (PersonDao, NoteDao, ActionDao);
 * SMS count comes from [SmsHistorySource].
 */
data class DataStats(
    val personCount: Int,
    val noteCount: Int,
    val openActionCount: Int,
    val smsSentCount: Int,
)

interface DataStatsSource {
    fun observeStats(): Flow<DataStats>
}

/**
 * Stub returning zeros so the DataScreen renders before W1-E / merge wires the
 * real DAO-backed implementation. Real impl will combine PersonDao.count(),
 * NoteDao.count(), ActionDao.countOpen(), SmsHistorySource.observeSentCount().
 */
@Singleton
class NoOpDataStatsSource @Inject constructor() : DataStatsSource {
    private val zeros: MutableStateFlow<DataStats> = MutableStateFlow(DataStats(0, 0, 0, 0))
    override fun observeStats(): Flow<DataStats> = zeros.asStateFlow()
}
