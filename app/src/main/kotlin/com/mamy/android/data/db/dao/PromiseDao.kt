package com.mamy.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mamy.android.data.db.entity.PromiseEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
interface PromiseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(promise: PromiseEntity)

    @Update
    suspend fun update(promise: PromiseEntity)

    @Query("SELECT * FROM promise WHERE id = :id LIMIT 1")
    suspend fun getById(id: UUID): PromiseEntity?

    @Query("SELECT * FROM promise WHERE to_id = 'self' AND status = 'active' ORDER BY due ASC, created_at ASC")
    suspend fun getOwedToMe(): List<PromiseEntity>

    @Query("SELECT * FROM promise WHERE from_id = 'self' AND status = 'active' ORDER BY due ASC, created_at ASC")
    suspend fun getOwedByMe(): List<PromiseEntity>

    @Query("SELECT * FROM promise WHERE (from_id = :personIdStr OR to_id = :personIdStr) ORDER BY created_at DESC")
    suspend fun getByPerson(personIdStr: String): List<PromiseEntity>

    @Query("SELECT * FROM promise WHERE status = 'active'")
    fun observeActive(): Flow<List<PromiseEntity>>

    @Query("UPDATE promise SET status = :status, resolved_at = :resolvedAt WHERE id = :id")
    suspend fun resolve(id: UUID, status: String, resolvedAt: Instant)

    @Query("DELETE FROM promise WHERE id = :id")
    suspend fun deleteById(id: UUID)
}
