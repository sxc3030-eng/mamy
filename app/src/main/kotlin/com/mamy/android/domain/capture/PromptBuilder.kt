package com.mamy.android.domain.capture

import com.mamy.android.util.Lang
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptBuilder @Inject constructor() {

    fun systemPrompt(language: Lang): String = when (language) {
        Lang.FR -> PROMPT_FR
        Lang.EN -> PROMPT_EN
    }

    companion object {
        private val PROMPT_FR = """
            Tu es l'assistant secrétaire d'un manager d'équipe 30-100 personnes. Tu reçois
            un debrief vocal libre post-meeting (FR ou EN). Extrait en JSON strict :

            - persons : nom, role_hint, emotional_state (ok|stressed|demotivated|happy|conflict|engaged|disengaged), context_added
            - actions : description, assignee (self ou nom), deadline ISO8601 ou null, linked_person
            - promises : from, to, what, due
            - flags : person, type (demotivation|conflict|risk|opportunity|burnout|growth), source (direct|indirect:X), severity, note
            - meeting_meta : person_main (avec qui était le 1:1, déduit), date_inferred

            Si une info est ambiguë, mets null plutôt que d'inventer. Réponds JSON brut, sans markdown.
        """.trimIndent()

        private val PROMPT_EN = """
            You are the secretary-assistant of a team manager (30-100 reports). You receive
            a free-form voice debrief from a post-meeting moment (FR or EN). Extract strict JSON:

            - persons: name, role_hint, emotional_state (ok|stressed|demotivated|happy|conflict|engaged|disengaged), context_added
            - actions: description, assignee (self or name), deadline ISO8601 or null, linked_person
            - promises: from, to, what, due
            - flags: person, type (demotivation|conflict|risk|opportunity|burnout|growth), source (direct|indirect:X), severity, note
            - meeting_meta: person_main (whom the 1:1 was with, inferred), date_inferred

            If anything is ambiguous, put null rather than inventing. Reply with raw JSON, no markdown.
        """.trimIndent()
    }
}
