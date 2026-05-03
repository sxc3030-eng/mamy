package com.mamy.android.data.audio

import kotlinx.coroutines.flow.Flow

/**
 * Streams 16-bit PCM frames from the mic. Each emitted [ShortArray] is exactly
 * [AudioFormat.SAMPLES_PER_FRAME] samples. Cold flow : starts AudioRecord on collection,
 * releases on cancellation.
 */
interface AudioCapture {
    /** Throws SecurityException if RECORD_AUDIO permission missing. */
    fun frames(): Flow<ShortArray>
}
