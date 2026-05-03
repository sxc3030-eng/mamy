package com.mamy.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mamy.android.data.db.entity.PersonEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface PersonDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(person: PersonEntity)

    @Update
    suspend fun update(person: PersonEntity)

    @Query("SELECT * FROM person WHERE id = :id LIMIT 1")
    suspend fun getById(id: UUID): PersonEntity?

    @Query("SELECT * FROM person WHERE email = :email LIMIT 1")
    suspend fun getByEmail(email: String): PersonEntity?

    @Query("SELECT * FROM person WHERE id = :id LIMIT 1")
    suspend fun findById(id: UUID): PersonEntity?

    @Query("SELECT * FROM person WHERE calendar_attendee_id = :email LIMIT 1")
    suspend fun findByCalendarEmail(email: String): PersonEntity?

    @Query("SELECT * FROM person WHERE unmatched = 1 AND archived = 0 ORDER BY created_at DESC")
    fun observeUnmatched(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM person WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByExactName(name: String): PersonEntity?

    @Query("SELECT * FROM person WHERE archived = 0 ORDER BY last_interaction_at DESC")
    suspend fun getActiveOrderedByLastInteraction(): List<PersonEntity>

    @Query("SELECT * FROM person WHERE archived = 0 ORDER BY last_interaction_at DESC")
    fun observeActive(): Flow<List<PersonEntity>>

    @Query("DELETE FROM person WHERE id = :id")
    suspend fun deleteById(id: UUID)

    @Query("SELECT * FROM person ORDER BY name ASC")
    suspend fun getAll(): List<PersonEntity>

    @Query("SELECT * FROM Person WHERE LOWER(name) LIKE '%' || LOWER(:query) || '%' AND archived = 0 ORDER BY last_interaction_at DESC")
    suspend fun findByName(query: String): List<PersonEntity>

    // ----- P6 Briefing aliases (plan-spec names) -----

    @Query("SELECT * FROM person WHERE id = :id LIMIT 1")
    suspend fun byId(id: UUID): PersonEntity?

    @Query("SELECT * FROM person WHERE archived = 0 ORDER BY last_interaction_at DESC")
    suspend fun allActive(): List<PersonEntity>
}
