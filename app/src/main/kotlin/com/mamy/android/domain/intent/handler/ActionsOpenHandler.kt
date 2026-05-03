package com.mamy.android.domain.intent.handler

import com.mamy.android.data.db.dao.ActionDao
import com.mamy.android.domain.intent.Intent
import com.mamy.android.domain.intent.IntentResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActionsOpenHandler @Inject constructor(
    private val actionDao: ActionDao,
) : IntentHandler<Intent.ActionsOpen> {

    private val formatter = DateTimeFormatter.ofPattern("d MMMM", Locale.FRANCE)

    override suspend fun handle(intent: Intent.ActionsOpen): IntentResult {
        val actions = actionDao.findOpen()
        if (actions.isEmpty()) {
            return IntentResult.spoken("Aucune action ouverte. Tu es à jour.")
        }
        val sb = StringBuilder()
        sb.append("Tu as ${actions.size} action${if (actions.size > 1) "s" else ""} ouverte${if (actions.size > 1) "s" else ""}. ")
        actions.forEachIndexed { idx, a ->
            sb.append("${idx + 1}. ").append(a.description)
            a.deadline?.let { sb.append(", deadline ").append(formatDeadline(it)) }
            sb.append(". ")
        }
        return IntentResult.spoken(sb.toString().trim())
    }

    private fun formatDeadline(d: Instant): String {
        val date = LocalDate.ofInstant(d, ZoneId.systemDefault())
        return when (date) {
            LocalDate.now() -> "aujourd'hui"
            LocalDate.now().plusDays(1) -> "demain"
            else -> formatter.format(date)
        }
    }
}
