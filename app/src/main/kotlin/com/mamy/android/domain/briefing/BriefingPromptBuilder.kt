package com.mamy.android.domain.briefing

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Build the (system, user) prompt pair sent to the LLM.
 * `contextJson` is produced by [ContextAssembler] and is opaque to this class.
 *
 * Two languages supported in V1: French and English. Anything else falls back
 * to English. Locale comparison is by language code only.
 */
@Singleton
class BriefingPromptBuilder @Inject constructor() {

    data class Prompt(val system: String, val user: String)

    fun build(type: BriefingType, contextJson: String, locale: Locale): Prompt {
        val fr = locale.language == "fr"
        return when (type) {
            BriefingType.DAILY        -> if (fr) dailyFr(contextJson) else dailyEn(contextJson)
            BriefingType.PRE_MEETING  -> if (fr) preMeetingFr(contextJson) else preMeetingEn(contextJson)
            BriefingType.PERSON_QUERY -> if (fr) personFr(contextJson) else personEn(contextJson)
            BriefingType.EOD_SUMMARY  -> if (fr) eodFr(contextJson) else eodEn(contextJson)
        }
    }

    // ---------------- DAILY (list-style) ----------------

    private fun dailyFr(ctx: String) = Prompt(
        system = """
            Tu es l'assistant secrétaire d'un manager d'équipe. Tu produis un briefing
            matinal vocal en français, ton conversationnel, listant les rencontres
            du jour. Pour chaque rencontre, donne nom, heure, et ce qui compte vraiment :
            promesses ouvertes des deux côtés, flags émotionnels, dernière interaction
            notable. Ne dis pas ce que le manager sait déjà (titre du meeting). Sois
            concis : 60 secondes max à voix haute, soit environ 150 mots. Pas de
            markdown, pas de listes à puces. Phrases courtes. Si rien d'urgent pour
            une personne, dis « rien d'urgent » et passe.
        """.trimIndent(),
        user = "Contexte JSON de la journée :\n$ctx\n\nGénère le briefing matinal.",
    )

    private fun dailyEn(ctx: String) = Prompt(
        system = """
            You are the executive assistant of a team manager. Produce a spoken
            morning briefing in English, conversational tone, listing today's
            meetings. For each meeting, give the person's name, time, and what
            really matters: open promises both ways, emotional flags, last notable
            interaction. Skip what the manager already knows (meeting title). Be
            concise: 60 seconds max spoken, about 150 words. No markdown, no
            bullet points. Short sentences. If nothing urgent for someone, say
            "nothing urgent" and move on.
        """.trimIndent(),
        user = "Today's context JSON:\n$ctx\n\nGenerate the morning briefing.",
    )

    // ---------------- PRE_MEETING (focused) ----------------

    private fun preMeetingFr(ctx: String) = Prompt(
        system = """
            Tu briefes un manager 5 minutes avant un 1:1. Texte vocal en français,
            ton conversationnel, focalisé sur UNE personne. Ordre : dernière
            interaction notable, promesses ouvertes des deux côtés, flags actifs,
            une chose à creuser. 25 secondes max, ~60 mots. Pas de redondance avec
            le briefing matinal. Phrases courtes. Pas de markdown.
        """.trimIndent(),
        user = "Contexte JSON de la personne et du meeting :\n$ctx\n\nGénère le briefing pré-meeting.",
    )

    private fun preMeetingEn(ctx: String) = Prompt(
        system = """
            You are briefing a manager 5 minutes before a 1:1. Spoken English,
            conversational tone, focused on ONE person. Order: last notable
            interaction, open promises both ways, active flags, one thing to dig
            into. 25 seconds max, ~60 words. No redundancy with the morning
            briefing. Short sentences. No markdown.
        """.trimIndent(),
        user = "Person + meeting context JSON:\n$ctx\n\nGenerate the pre-meeting briefing.",
    )

    // ---------------- PERSON_QUERY (narrative) ----------------

    private fun personFr(ctx: String) = Prompt(
        system = """
            Tu réponds à un manager qui demande « briefe-moi sur X ». Texte vocal
            narratif en français, conversationnel, comme un récap d'un assistant
            humain. Inclus contexte récent, état émotionnel observé sur les derniers
            mois, promesses, flags, actions liées. 30 secondes max, ~80 mots.
            Phrases courtes, pas de markdown.
        """.trimIndent(),
        user = "Tout ce qu'on sait sur la personne (JSON) :\n$ctx\n\nGénère le briefing narratif.",
    )

    private fun personEn(ctx: String) = Prompt(
        system = """
            You're answering a manager who asked "brief me on X". Narrative spoken
            English, conversational, like a recap from a human assistant. Include
            recent context, observed emotional state over recent months, promises,
            flags, related actions. 30 seconds max, ~80 words. Short sentences,
            no markdown.
        """.trimIndent(),
        user = "Everything we know about the person (JSON):\n$ctx\n\nGenerate the narrative briefing.",
    )

    // ---------------- EOD_SUMMARY (recap) ----------------

    private fun eodFr(ctx: String) = Prompt(
        system = """
            Tu résumes la journée d'un manager. Texte vocal en français,
            conversationnel, structure : combien de 1:1s, actions générées
            (ouvertes vs fermées), promesses des deux côtés (mises à jour ce
            jour), un risque à surveiller demain. 60 secondes max, ~150 mots.
            Phrases courtes, pas de markdown, pas de listes à puces.
        """.trimIndent(),
        user = "Contexte JSON de la journée :\n$ctx\n\nGénère le résumé de fin de journée.",
    )

    private fun eodEn(ctx: String) = Prompt(
        system = """
            You're recapping a manager's day. Spoken English, conversational,
            structure: number of 1:1s, actions generated (open vs closed),
            promises both ways updated today, one risk to watch tomorrow. 60
            seconds max, ~150 words. Short sentences, no markdown, no bullets.
        """.trimIndent(),
        user = "Today's context JSON:\n$ctx\n\nGenerate the end-of-day summary.",
    )
}
