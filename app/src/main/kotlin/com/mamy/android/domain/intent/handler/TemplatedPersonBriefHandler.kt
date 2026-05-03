package com.mamy.android.domain.intent.handler

import com.mamy.android.data.db.dao.FlagDao
import com.mamy.android.data.db.dao.PromiseDao
import com.mamy.android.data.db.entity.PersonEntity
import com.mamy.android.domain.intent.Intent
import com.mamy.android.domain.intent.IntentResult
import com.mamy.android.domain.memory.PersonMatcher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * V1 templated person brief : pulls open promises both ways + active flags + assembles
 * a deterministic vocal text. P6 will replace with an LLM-generated brief that uses
 * the same DB context but produces conversational tone.
 */
@Singleton
class TemplatedPersonBriefHandler @Inject constructor(
    private val matcher: PersonMatcher,
    private val promiseDao: PromiseDao,
    private val flagDao: FlagDao,
) : PersonBriefHandler {

    override suspend fun handle(intent: Intent.PersonBrief): IntentResult {
        return when (val r = matcher.match(intent.personQuery)) {
            PersonMatcher.MatchResult.NotFound ->
                IntentResult.spoken("Personne inconnue : ${intent.personQuery}. Person not found.")
            is PersonMatcher.MatchResult.Ambiguous -> {
                val names = r.candidates.joinToString(" ou ") { it.name }
                IntentResult.spoken("Plusieurs personnes correspondent : $names. Précise.")
            }
            is PersonMatcher.MatchResult.SingleMatch -> assemble(r.person)
        }
    }

    private suspend fun assemble(p: PersonEntity): IntentResult {
        val owedToPerson = promiseDao.findActiveBetween("self", p.id.toString())
        val owedFromPerson = promiseDao.findActiveBetween(p.id.toString(), "self")
        val flags = flagDao.findActiveByPerson(p.id)

        val sb = StringBuilder()
        sb.append("Brief sur ${p.name}. ")
        if (owedToPerson.isEmpty() && owedFromPerson.isEmpty() && flags.isEmpty()) {
            sb.append("Rien d'ouvert actuellement.")
            return IntentResult.spoken(sb.toString())
        }
        if (owedToPerson.isNotEmpty()) {
            sb.append("Tu lui dois : ")
            sb.append(owedToPerson.joinToString("; ") { it.what })
            sb.append(". ")
        }
        if (owedFromPerson.isNotEmpty()) {
            sb.append("Elle/il te doit : ")
            sb.append(owedFromPerson.joinToString("; ") { it.what })
            sb.append(". ")
        }
        if (flags.isNotEmpty()) {
            sb.append("Flags actifs : ")
            sb.append(flags.joinToString("; ") { "${it.type} (${it.note})" })
            sb.append(".")
        }
        return IntentResult.spoken(sb.toString().trim())
    }
}
