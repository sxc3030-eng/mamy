package com.mamy.android.data.audio

/**
 * VAD-driven capture outcome.
 * - [Captured] : VAD detected silence > 1.5 s after speech started.
 * - [MaxDuration] : reached 90 s hard cap.
 * - [NoSpeech] : 5 s elapsed with no speech detected → abort.
 */
sealed interface VadResult {
    val pcm: ShortArray
    val durationSec: Int

    data class Captured(override val pcm: ShortArray, override val durationSec: Int) : VadResult
    data class MaxDuration(override val pcm: ShortArray, override val durationSec: Int) : VadResult
    data class NoSpeech(override val pcm: ShortArray, override val durationSec: Int) : VadResult
}
