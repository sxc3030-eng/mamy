package com.mamy.android.domain.intent.handler

import com.mamy.android.data.db.dao.ActionDao
import com.mamy.android.data.db.dao.FlagDao
import com.mamy.android.data.db.dao.NoteDao
import com.mamy.android.data.db.dao.PromiseDao
import com.mamy.android.domain.intent.Intent
import com.mamy.android.domain.intent.IntentResult
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deletes the most recent Note + its cascading Actions / Promises / Flags
 * if [LastNoteTracker.WINDOW_MS] has not elapsed.
 *
 * The Room schema does NOT use ON DELETE CASCADE (P1 chose to manage cascades
 * in code so it stays explicit). We delete children first then the Note.
 */
@Singleton
class UndoLastHandler @Inject constructor(
    private val noteDao: NoteDao,
    private val actionDao: ActionDao,
    private val promiseDao: PromiseDao,
    private val flagDao: FlagDao,
    private val tracker: LastNoteTracker,
) : IntentHandler<Intent.UndoLast> {

    override suspend fun handle(intent: Intent.UndoLast): IntentResult = handle(intent, Instant.now())

    // Test-friendly overload: caller can inject `now` for deterministic window checks.
    suspend fun handle(intent: Intent.UndoLast, now: Instant): IntentResult {
        val slot = tracker.snapshot(now = now)
            ?: return if (tracker.snapshot(now = now, windowMs = Long.MAX_VALUE) != null) {
                IntentResult.spoken("Trop tard, fenêtre d'annulation expirée. Too late, undo window expired.")
            } else {
                IntentResult.spoken("Rien à annuler récemment.")
            }

        actionDao.deleteByNoteId(slot.noteId)
        promiseDao.deleteByNoteId(slot.noteId)
        flagDao.deleteByNoteId(slot.noteId)
        noteDao.deleteById(slot.noteId)
        tracker.clear()

        return IntentResult.spoken("Annulé.")
    }
}
