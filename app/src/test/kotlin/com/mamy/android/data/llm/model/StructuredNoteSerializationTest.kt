package com.mamy.android.data.llm.model

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StructuredNoteSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `decodes a complete StructuredNote payload`() {
        val payload = """
            {
              "persons": [
                {"name":"Marie","role_hint":"team lead","emotional_state":"stressed","context_added":"projet X livrable vendredi"}
              ],
              "actions": [
                {"description":"parler à David","assignee":"self","deadline":null,"linked_person":"Marie"}
              ],
              "promises": [
                {"from":"self","to":"Marie","what":"30 min CV review","due":"2026-05-08T17:00:00Z"}
              ],
              "flags": [
                {"person":"Pierre","type":"demotivation","source":"indirect:Marie","severity":"medium","note":"traîne sur mockup"}
              ],
              "meeting_meta": {
                "person_main":"Marie",
                "date_inferred":"2026-05-02T10:30:00Z"
              }
            }
        """.trimIndent()

        val note = json.decodeFromString(StructuredNote.serializer(), payload)

        assertEquals(1, note.persons.size)
        assertEquals("Marie", note.persons[0].name)
        assertEquals(EmotionalState.STRESSED, note.persons[0].emotionalState)
        assertEquals(1, note.actions.size)
        assertEquals("self", note.actions[0].assignee)
        assertNull(note.actions[0].deadline)
        assertEquals("Marie", note.promises[0].to)
        assertEquals(FlagType.DEMOTIVATION, note.flags[0].type)
        assertEquals(Severity.MEDIUM, note.flags[0].severity)
        assertEquals("Marie", note.meetingMeta.personMain)
    }

    @Test
    fun `decodes empty arrays gracefully`() {
        val payload = """{"persons":[],"actions":[],"promises":[],"flags":[],"meeting_meta":{"person_main":null,"date_inferred":null}}"""

        val note = json.decodeFromString(StructuredNote.serializer(), payload)

        assertEquals(0, note.persons.size)
        assertNull(note.meetingMeta.personMain)
    }
}
