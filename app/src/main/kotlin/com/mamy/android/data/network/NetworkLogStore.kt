package com.mamy.android.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory ring buffer of [NetworkLogEntry]. UI layer can `collect(stream)` to render the
 * NetworkLogScreen list. Capped at [MAX_ENTRIES] to keep memory bounded on long sessions.
 */
@Singleton
class NetworkLogStore @Inject constructor() {

    private val mutex = Mutex()
    private val state = MutableStateFlow<List<NetworkLogEntry>>(emptyList())
    val stream: StateFlow<List<NetworkLogEntry>> = state.asStateFlow()

    suspend fun append(entry: NetworkLogEntry) {
        mutex.withLock {
            val list = state.value
            val updated = if (list.size >= MAX_ENTRIES) {
                list.drop(list.size - MAX_ENTRIES + 1) + entry
            } else {
                list + entry
            }
            state.value = updated
        }
    }

    suspend fun clear() {
        mutex.withLock { state.value = emptyList() }
    }

    private companion object {
        const val MAX_ENTRIES = 500
    }
}
