package com.mamy.android.domain.intent.handler

import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the most recently created Note for the undo window.
 * Updated by [com.mamy.android.domain.intent.handler.CaptureHandler] after a successful
 * structuration. Read by [UndoLastHandler].
 *
 * Thread-safe via `@Volatile` on the single mutable field.
 */
@Singleton
class LastNoteTracker @Inject constructor() {

    @Volatile
    private var slot: Slot? = null

    data class Slot(val noteId: UUID, val createdAt: Instant)

    fun record(noteId: UUID, createdAt: Instant = Instant.now()) {
        slot = Slot(noteId, createdAt)
    }

    /** Returns the slot if still within [windowMs] ms of recording, else null. */
    fun snapshot(now: Instant = Instant.now(), windowMs: Long = WINDOW_MS): Slot? {
        val s = slot ?: return null
        return if (now.toEpochMilli() - s.createdAt.toEpochMilli() <= windowMs) s else null
    }

    fun clear() {
        slot = null
    }

    companion object {
        const val WINDOW_MS: Long = 30_000L
    }
}
