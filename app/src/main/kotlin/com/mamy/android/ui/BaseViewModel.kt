package com.mamy.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Base ViewModel exposing a [Flow]→[StateFlow] helper scoped to the VM lifecycle.
 *
 * Used by P7 screens to convert repository flows into hot, lifecycle-aware state
 * that survives configuration changes and gets shared across multiple collectors.
 */
abstract class BaseViewModel : ViewModel() {

    /** Convert any [Flow] into a hot [StateFlow] scoped to this ViewModel. */
    protected fun <T> Flow<T>.asStateFlow(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), initial)
}
