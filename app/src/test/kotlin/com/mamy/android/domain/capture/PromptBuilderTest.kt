package com.mamy.android.domain.capture

import com.mamy.android.util.Lang
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PromptBuilderTest {

    private val builder = PromptBuilder()

    @Test
    fun `FR prompt mentions french framing`() {
        val p = builder.systemPrompt(Lang.FR)
        assertTrue(p.contains("manager"))
        assertTrue(p.contains("debrief vocal"))
        // v0.4.9 rewrote the prompt to push English JSON keys into both
        // locales, but the French framing line still mentions the FR/EN
        // dual-input contract.
        assertTrue(p.contains("français ou anglais"))
    }

    @Test
    fun `EN prompt mentions english framing`() {
        val p = builder.systemPrompt(Lang.EN)
        assertTrue(p.contains("team manager"))
        assertTrue(p.contains("voice debrief"))
        assertTrue(p.contains("French or English"))
    }

    @Test
    fun `both languages include the same English JSON keys`() {
        val fr = builder.systemPrompt(Lang.FR)
        val en = builder.systemPrompt(Lang.EN)
        listOf("persons", "actions", "promises", "flags", "meeting_meta",
               "name", "role_hint", "emotional_state", "context_added",
               "description", "assignee", "deadline", "linked_person",
               "from", "to", "what", "due",
               "person", "type", "source", "severity", "note",
               "person_main", "date_inferred").forEach { key ->
            assertTrue(fr.contains(key), "FR prompt missing $key")
            assertTrue(en.contains(key), "EN prompt missing $key")
        }
    }

    @Test
    fun `both prompts forbid translating keys`() {
        // Regression guard: v0.4.8 had the FR prompt list the fields with
        // French labels ("persons : nom, role_hint, …"), which Groq took
        // literally and produced `{"nom":"Marie"}`. The strict parser then
        // rejected the response, RawFallback fired, and TTS announced
        // "Noté: 0 actions, 0 personnes, 0 flagués".
        val fr = builder.systemPrompt(Lang.FR)
        val en = builder.systemPrompt(Lang.EN)
        listOf(fr, en).forEach { p ->
            assertTrue(p.contains("EXACT English keys"), "prompt missing English-key directive")
        }
    }
}
