package com.mamy.android.domain.capture

import com.mamy.android.domain.intent.Intent

/** State events emitted by the capture pipeline. Drives notification icon + UI. */
sealed interface CaptureEvent {
    data object Idle : CaptureEvent
    data object WakeWordDetected : CaptureEvent
    data object Recording : CaptureEvent
    data object Transcribing : CaptureEvent
    data class TranscriptReady(val text: String, val durationSec: Int, val intent: Intent) : CaptureEvent
    data object NoSpeech : CaptureEvent
    data object MaxDurationHit : CaptureEvent
    data class Error(val cause: Throwable) : CaptureEvent
}
