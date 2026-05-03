package com.mamy.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mamy.android.data.db.entity.NoteEntity
import java.util.UUID

@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    @Update
    suspend fun update(note: NoteEntity)

    @Query("SELECT * FROM note WHERE id = :id LIMIT 1")
    suspend fun getById(id: UUID): NoteEntity?

    @Query("SELECT * FROM note WHERE person_id = :personId ORDER BY created_at DESC")
    suspend fun getByPersonOrderedDesc(personId: UUID): List<NoteEntity>

    @Query("SELECT * FROM note WHERE non_structured = 1 ORDER BY created_at DESC")
    suspend fun getNonStructured(): List<NoteEntity>

    @Query("DELETE FROM note WHERE id = :id")
    suspend fun deleteById(id: UUID)

    @Query("SELECT * FROM note ORDER BY created_at DESC")
    suspend fun getAll(): List<NoteEntity>
}
