package com.mamy.android.util

/**
 * Detects ≥1000 ms hold on the same key. Pure logic, no Android imports → unit-testable.
 */
class VolumeLongPressDetector(
    private val thresholdMs: Long = 1000L,
    private val onLongPress: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var pressStartMs: Long = 0L
    private var fired: Boolean = false
    private var trackedKey: Int = -1

    /** @return true if the event was consumed (long-press fired). */
    fun onKeyDown(keyCode: Int): Boolean {
        if (keyCode != trackedKey) {
            trackedKey = keyCode
            pressStartMs = now()
            fired = false
            return false
        }
        // Repeat events while held
        if (!fired && now() - pressStartMs >= thresholdMs) {
            fired = true
            onLongPress()
            return true
        }
        return false
    }

    fun onKeyUp(keyCode: Int) {
        if (keyCode == trackedKey) {
            trackedKey = -1
            pressStartMs = 0L
            fired = false
        }
    }
}
