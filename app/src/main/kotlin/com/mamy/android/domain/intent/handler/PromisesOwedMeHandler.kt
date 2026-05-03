package com.mamy.android.domain.intent.handler

import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.dao.PromiseDao
import com.mamy.android.domain.intent.Intent
import com.mamy.android.domain.intent.IntentResult
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads active promises where `to=self` from Room, formats vocally.
 * Pure DB query — no LLM call.
 */
@Singleton
class PromisesOwedMeHandler @Inject constructor(
    private val promiseDao: PromiseDao,
    private val personDao: PersonDao,
) : IntentHandler<Intent.PromisesOwedMe> {

    override suspend fun handle(intent: Intent.PromisesOwedMe): IntentResult {
        val promises = promiseDao.findActiveOwedToSelf()
        if (promises.isEmpty()) {
            return IntentResult.spoken("Personne ne te doit rien actuellement.")
        }
        val sb = StringBuilder()
        sb.append("Tu as ${promises.size} promesse${if (promises.size > 1) "s" else ""} ouverte${if (promises.size > 1) "s" else ""} envers toi. ")
        promises.forEachIndexed { idx, p ->
            val who = resolveName(p.fromId)
            sb.append("${idx + 1}. ").append(who).append(" doit ").append(p.what).append(". ")
        }
        return IntentResult.spoken(sb.toString().trim())
    }

    private suspend fun resolveName(fromId: String): String {
        if (fromId == "self") return "toi-même"
        return runCatching { UUID.fromString(fromId) }
            .getOrNull()
            ?.let { personDao.getById(it)?.name }
            ?: "Quelqu'un"
    }
}
