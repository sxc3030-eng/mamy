package com.mamy.android.domain.capture

import com.mamy.android.data.audio.AudioCapture
import com.mamy.android.data.audio.VadProcessor
import com.mamy.android.data.audio.VadResult
import com.mamy.android.data.stt.WhisperEngine
import com.mamy.android.domain.intent.IntentRouter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-shot capture orchestrator. After a wake-word fire, the service calls
 * [runOneCapture] which : starts AudioCapture, hands the frame Flow to VadProcessor,
 * passes the resulting PCM to WhisperEngine, then routes the transcript through
 * IntentRouter. All intermediate states are emitted via [events].
 */
@Singleton
class CapturePipeline @Inject constructor(
    private val audioCapture: AudioCapture,
    private val vad: VadProcessor,
    private val whisper: WhisperEngine,
    private val router: IntentRouter,
) {
    private val _events = MutableSharedFlow<CaptureEvent>(replay = 1, extraBufferCapacity = 16)
    val events: Flow<CaptureEvent> = _events.asSharedFlow()

    init { _events.tryEmit(CaptureEvent.Idle) }

    suspend fun runOneCapture(language: String) {
        try {
            _events.emit(CaptureEvent.Recording)
            val frames = audioCapture.frames()
            val vadResult = vad.captureUntilSilence(frames)

            when (vadResult) {
                is VadResult.NoSpeech -> {
                    _events.emit(CaptureEvent.NoSpeech)
                    _events.emit(CaptureEvent.Idle)
                    return
                }
                is VadResult.MaxDuration -> {
                    _events.emit(CaptureEvent.MaxDurationHit)
                    // continue to transcription with the captured 90 s
                }
                is VadResult.Captured -> Unit
            }

            _events.emit(CaptureEvent.Transcribing)
            val sttResult = whisper.transcribe(vadResult.pcm, language)
            sttResult.fold(
                onSuccess = { text ->
                    val intent = router.route(text)
                    _events.emit(CaptureEvent.TranscriptReady(text, vadResult.durationSec, intent))
                },
                onFailure = { _events.emit(CaptureEvent.Error(it)) },
            )
            _events.emit(CaptureEvent.Idle)
        } catch (t: Throwable) {
            _events.emit(CaptureEvent.Error(t))
            _events.emit(CaptureEvent.Idle)
        }
    }

    /** Helper for service to await the next emission. */
    suspend fun nextEvent(): CaptureEvent = events.first()
}
