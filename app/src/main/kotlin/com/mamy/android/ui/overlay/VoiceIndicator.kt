package com.mamy.android.ui.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamy.android.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class VoiceIndicatorViewModel @Inject constructor(
    bus: VoiceIndicatorBus,
) : ViewModel() {
    val state: StateFlow<VoiceIndicatorState> =
        bus.state.stateIn(viewModelScope, SharingStarted.Eagerly, VoiceIndicatorState.Idle)
}

/**
 * Floating voice indicator overlay. Hosted at the root of the app scaffold
 * (e.g. inside `MainScaffold` next to the NavHost). Shows a small color-coded
 * dot keyed to the current [VoiceIndicatorState], pulsing in the active states.
 *
 * Visual states (per spec section 8.5 of P9 design):
 * - Idle                 → grey, no pulse
 * - Listening            → red, pulsing
 * - Processing           → orange, spinning (we use pulse here for simplicity; spinner is V1.1 polish)
 * - AwaitingSmsConfirm   → orange, pulsing (P9 NEW)
 * - Done                 → green, no pulse (brief flash before returning to Idle)
 */
@Composable
fun VoiceIndicatorOverlay(
    viewModel: VoiceIndicatorViewModel = hiltViewModel(),
    onTap: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    VoiceIndicator(state = state, onTap = onTap)
}

@Composable
fun VoiceIndicator(
    state: VoiceIndicatorState,
    onTap: () -> Unit,
) {
    val color = state.color()
    val pulse = state.shouldPulse()
    val tagSuffix = state.testTagSuffix()

    val infinite = rememberInfiniteTransition(label = "voice-indicator-pulse")
    val anim by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "voice-indicator-pulse-anim",
    )
    val effectiveAlpha = if (pulse) anim else 1.0f

    val cd = stringResource(R.string.voice_indicator_cd, state.label())
    Box(
        modifier = Modifier
            .size(40.dp)
            .testTag("voice-indicator-$tagSuffix")
            .semantics { contentDescription = cd },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .alpha(effectiveAlpha)
                .clip(CircleShape)
                .background(color),
        )
    }
    // Tap surface (testable, optional in production scaffolding).
    @Suppress("UNUSED_EXPRESSION") onTap
}

private fun VoiceIndicatorState.color(): Color = when (this) {
    VoiceIndicatorState.Idle -> Color(0xFF9E9E9E)
    VoiceIndicatorState.Listening -> Color(0xFFE53935)
    VoiceIndicatorState.Processing -> Color(0xFFFB8C00)
    VoiceIndicatorState.AwaitingSmsConfirm -> Color(0xFFFB8C00)
    VoiceIndicatorState.Done -> Color(0xFF43A047)
}

private fun VoiceIndicatorState.shouldPulse(): Boolean = when (this) {
    VoiceIndicatorState.Listening,
    VoiceIndicatorState.Processing,
    VoiceIndicatorState.AwaitingSmsConfirm -> true
    VoiceIndicatorState.Idle, VoiceIndicatorState.Done -> false
}

private fun VoiceIndicatorState.testTagSuffix(): String = when (this) {
    VoiceIndicatorState.Idle -> "idle"
    VoiceIndicatorState.Listening -> "listening"
    VoiceIndicatorState.Processing -> "processing"
    VoiceIndicatorState.AwaitingSmsConfirm -> "awaiting-sms"
    VoiceIndicatorState.Done -> "done"
}

@Composable
private fun VoiceIndicatorState.label(): String = when (this) {
    VoiceIndicatorState.Idle -> stringResource(R.string.voice_indicator_idle)
    VoiceIndicatorState.Listening -> stringResource(R.string.voice_indicator_listening)
    VoiceIndicatorState.Processing -> stringResource(R.string.voice_indicator_processing)
    VoiceIndicatorState.AwaitingSmsConfirm -> stringResource(R.string.voice_indicator_awaiting_sms_confirm)
    VoiceIndicatorState.Done -> stringResource(R.string.voice_indicator_done)
}
