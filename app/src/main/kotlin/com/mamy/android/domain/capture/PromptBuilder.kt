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
        // Shared JSON shape spec injected into both locales. The schema keys
        // MUST stay English (they match the Kotlinx-serialized
        // [com.mamy.android.data.llm.model.StructuredNote] class) — a previous
        // version of this prompt listed the fields in French ("persons : nom,
        // role_hint, …"), which the LLM took literally and produced
        // `{"nom":"Marie"}`, failing strict parse and leaving every debrief
        // unstructured (v0.4.8 bug).
        private val SCHEMA = """
            JSON shape (use these EXACT English keys, do NOT translate the keys):
            {
              "persons": [
                {
                  "name": "string",
                  "role_hint": "string|null",
                  "emotional_state": "ok|stressed|demotivated|happy|conflict|engaged|disengaged",
                  "context_added": "string"
                }
              ],
              "actions": [
                {
                  "description": "string",
                  "assignee": "self|<name>",
                  "deadline": "ISO8601 datetime|null",
                  "linked_person": "<name>|null"
                }
              ],
              "promises": [
                { "from": "self|<name>", "to": "self|<name>", "what": "string", "due": "ISO8601 datetime|null" }
              ],
              "flags": [
                {
                  "person": "<name>",
                  "type": "demotivation|conflict|risk|opportunity|burnout|growth",
                  "source": "direct|indirect:<name>",
                  "severity": "low|medium|high",
                  "note": "string"
                }
              ],
              "meeting_meta": { "person_main": "<name>|null", "date_inferred": "ISO8601 datetime|null" }
            }

            Rules: Reply with JSON only, no markdown fences. Use the English keys
            above verbatim. If a field is unknown leave it null (or an empty array /
            empty string when typed as such), never invent. Deadlines and dates that
            are spoken relatively ("demain", "next Friday") must be resolved to an
            ISO8601 datetime when you can compute one — otherwise null.
        """.trimIndent()

        private val PROMPT_FR = """
            Tu es l'assistant secrétaire d'un manager d'équipe de 30 à 100 personnes.
            Tu reçois un debrief vocal libre post-meeting (en français ou anglais).
            Extrait les informations dans un objet JSON strict.

            $SCHEMA
        """.trimIndent()

        private val PROMPT_EN = """
            You are the secretary-assistant of a team manager (30-100 reports).
            You receive a free-form voice debrief from a post-meeting moment
            (French or English).
            Extract the information into a strict JSON object.

            $SCHEMA
        """.trimIndent()
    }
}
