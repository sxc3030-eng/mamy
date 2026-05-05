package com.mamy.android.ui.overlay

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coarse-grained UI state for the floating [VoiceIndicator] overlay. The
 * foreground service / SMS handler push updates here through [VoiceIndicatorBus].
 *
 * Five states tracked:
 * - [Idle]                   — wake-word listener is up but nothing is happening
 * - [Listening]              — wake-word fired, capturing audio (red pulsing dot)
 * - [Processing]             — STT/LLM working (orange spinning)
 * - [AwaitingSmsConfirm]     — P9 NEW: 3-sec window where user must say oui/non
 * - [Done]                   — brief flash green tick before returning to Idle
 */
sealed interface VoiceIndicatorState {
    object Idle : VoiceIndicatorState
    object Listening : VoiceIndicatorState
    object Processing : VoiceIndicatorState
    object AwaitingSmsConfirm : VoiceIndicatorState
    object Done : VoiceIndicatorState
}

/**
 * Process-wide singleton bus the foreground service / domain handlers push into,
 * and the [VoiceIndicatorOverlay] composable observes. Decoupling avoids a hard
 * dependency from UI on service lifecycle.
 */
@Singleton
class VoiceIndicatorBus @Inject constructor() {
    private val _state: MutableStateFlow<VoiceIndicatorState> =
        MutableStateFlow(VoiceIndicatorState.Idle)
    val state: Flow<VoiceIndicatorState> = _state.asStateFlow()

    fun emit(next: VoiceIndicatorState) {
        _state.value = next
    }
}
