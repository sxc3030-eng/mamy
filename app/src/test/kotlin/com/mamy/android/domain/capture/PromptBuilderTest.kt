package com.mamy.android.domain.capture

import com.mamy.android.util.Lang
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PromptBuilderTest {

    private val builder = PromptBuilder()

    @Test
    fun `FR prompt mentions french keywords`() {
        val p = builder.systemPrompt(Lang.FR)
        assertTrue(p.contains("manager"))
        assertTrue(p.contains("debrief vocal"))
        assertTrue(p.contains("JSON strict"))
        assertTrue(p.contains("emotional_state"))
        assertTrue(p.contains("meeting_meta"))
        assertTrue(p.contains("FR ou EN"))
    }

    @Test
    fun `EN prompt mentions english keywords`() {
        val p = builder.systemPrompt(Lang.EN)
        assertTrue(p.contains("team manager"))
        assertTrue(p.contains("voice debrief"))
        assertTrue(p.contains("strict JSON"))
        assertTrue(p.contains("FR or EN"))
    }

    @Test
    fun `both languages include the same JSON keys`() {
        val fr = builder.systemPrompt(Lang.FR)
        val en = builder.systemPrompt(Lang.EN)
        listOf("persons", "actions", "promises", "flags", "meeting_meta",
               "role_hint", "emotional_state", "deadline", "linked_person",
               "person_main", "date_inferred").forEach { key ->
            assertTrue(fr.contains(key), "FR prompt missing $key")
            assertTrue(en.contains(key), "EN prompt missing $key")
        }
    }
}
