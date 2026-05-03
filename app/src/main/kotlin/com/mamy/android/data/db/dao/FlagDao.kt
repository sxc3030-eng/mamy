package com.mamy.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mamy.android.data.db.entity.FlagEntity
import java.util.UUID

@Dao
interface FlagDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(flag: FlagEntity)

    @Update
    suspend fun update(flag: FlagEntity)

    @Query("SELECT * FROM flag WHERE id = :id LIMIT 1")
    suspend fun getById(id: UUID): FlagEntity?

    @Query("SELECT * FROM flag WHERE person_id = :personId AND resolved = 0 ORDER BY severity DESC, created_at DESC")
    suspend fun getOpenByPerson(personId: UUID): List<FlagEntity>

    @Query("SELECT * FROM flag WHERE resolved = 0 ORDER BY severity DESC, created_at DESC")
    suspend fun getAllOpen(): List<FlagEntity>

    @Query("UPDATE flag SET resolved = 1 WHERE id = :id")
    suspend fun markResolved(id: UUID)

    @Query("DELETE FROM flag WHERE id = :id")
    suspend fun deleteById(id: UUID)

    @Query("SELECT * FROM flag ORDER BY created_at DESC")
    suspend fun getAll(): List<FlagEntity>
}
