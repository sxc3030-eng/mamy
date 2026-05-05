package com.mamy.android.ui.screens.reports

import com.mamy.android.data.db.entity.PersonEntity
import java.time.Instant
import java.util.UUID

/**
 * UI projection of a Person used by the Reports list.
 *
 * Wraps [PersonEntity] and adds derived/joined fields the row displays:
 * - [openFlagCount] : number of open flags for that person (joined from [com.mamy.android.data.db.dao.FlagDao])
 *
 * Created as a separate UI model so the list can be rendered without direct
 * Room entity coupling and so future changes (eg. role hint enrichment) don't
 * leak into the entity layer.
 */
data class PersonRow(
    val id: UUID,
    val name: String,
    val roleHint: String?,
    val email: String?,
    val emotionalTrend: String?,
    val unmatched: Boolean,
    val lastInteractionAt: Instant?,
    val interactionCount: Int,
    val openFlagCount: Int,
) {
    companion object {
        fun fromEntity(entity: PersonEntity, openFlagCount: Int = 0) = PersonRow(
            id = entity.id,
            name = entity.name,
            roleHint = entity.roleHint,
            email = entity.email,
            emotionalTrend = entity.emotionalTrend,
            unmatched = entity.unmatched,
            lastInteractionAt = entity.lastInteractionAt,
            interactionCount = entity.interactionCount,
            openFlagCount = openFlagCount,
        )
    }
}
