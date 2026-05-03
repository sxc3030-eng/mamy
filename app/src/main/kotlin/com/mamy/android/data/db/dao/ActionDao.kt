package com.mamy.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mamy.android.data.db.entity.ActionEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
interface ActionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(action: ActionEntity)

    @Update
    suspend fun update(action: ActionEntity)

    @Query("SELECT * FROM action WHERE id = :id LIMIT 1")
    suspend fun getById(id: UUID): ActionEntity?

    @Query("SELECT * FROM action WHERE status = 'open' ORDER BY deadline ASC, created_at ASC")
    suspend fun getOpen(): List<ActionEntity>

    @Query("SELECT * FROM action WHERE status = 'open' ORDER BY deadline ASC, created_at ASC")
    fun observeOpen(): Flow<List<ActionEntity>>

    @Query("SELECT * FROM action WHERE linked_person_id = :personId ORDER BY created_at DESC")
    suspend fun getByPerson(personId: UUID): List<ActionEntity>

    @Query("UPDATE action SET status = 'done', done_at = :doneAt WHERE id = :id")
    suspend fun markDone(id: UUID, doneAt: Instant)

    @Query("DELETE FROM action WHERE id = :id")
    suspend fun deleteById(id: UUID)

    @Query("SELECT * FROM action ORDER BY created_at DESC")
    suspend fun getAll(): List<ActionEntity>

    @Query("SELECT * FROM Action WHERE status = 'open' ORDER BY deadline IS NULL, deadline ASC, created_at DESC")
    suspend fun findOpen(): List<ActionEntity>

    @Query("SELECT * FROM Action WHERE from_note_id = :noteId")
    suspend fun findByNoteId(noteId: UUID): List<ActionEntity>

    @Query("DELETE FROM Action WHERE from_note_id = :noteId")
    suspend fun deleteByNoteId(noteId: UUID): Int

    // ----- P6 Briefing aliases (plan-spec names) -----

    @Query("SELECT * FROM action WHERE linked_person_id = :personId ORDER BY created_at DESC")
    suspend fun linkedTo(personId: UUID): List<ActionEntity>

    @Query("SELECT * FROM action WHERE created_at >= :from AND created_at < :to ORDER BY created_at DESC")
    suspend fun createdBetween(from: Instant, to: Instant): List<ActionEntity>
}
