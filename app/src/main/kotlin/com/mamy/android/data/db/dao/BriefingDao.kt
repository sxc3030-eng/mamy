package com.mamy.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mamy.android.data.db.entity.BriefingEntity
import java.time.Instant

@Dao
interface BriefingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(briefing: BriefingEntity)

    @Query(
        "SELECT * FROM briefing WHERE type = :type AND ((:targetId IS NULL AND target_id IS NULL) OR target_id = :targetId) " +
            "AND expires_at > :now ORDER BY generated_at DESC LIMIT 1"
    )
    suspend fun getValidByTypeAndTarget(type: String, targetId: String?, now: Instant): BriefingEntity?

    @Query("DELETE FROM briefing WHERE expires_at <= :now")
    suspend fun deleteExpired(now: Instant)

    @Query("SELECT COUNT(*) FROM briefing")
    suspend fun countAll(): Int

    // ----- P6 Briefing cache (plan-spec names) -----

    @Query("SELECT * FROM briefing WHERE type = :type AND target_id = :targetId AND expires_at > :now ORDER BY generated_at DESC LIMIT 1")
    suspend fun fresh(type: String, targetId: String, now: Instant): BriefingEntity?

    @Query("DELETE FROM briefing WHERE type = :type AND target_id = :targetId")
    suspend fun deleteFor(type: String, targetId: String)
}
