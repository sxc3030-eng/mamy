package com.mamy.android.data.llm

import com.mamy.android.data.llm.model.FlagType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StructuredNoteParserTest {

    private val parser = StructuredNoteParser()

    @Test
    fun `parses valid JSON`() {
        val raw = """
            {"persons":[{"name":"Marie","emotional_state":"happy","context_added":""}],
             "actions":[],"promises":[],"flags":[],
             "meeting_meta":{"person_main":"Marie","date_inferred":null}}
        """.trimIndent()

        val parsed = parser.parse(raw)

        assertEquals("Marie", parsed?.persons?.firstOrNull()?.name)
    }

    @Test
    fun `strips markdown code fences before parsing`() {
        val raw = """
            ```json
            {"persons":[],"actions":[],"promises":[],"flags":[{"person":"Pierre","type":"risk","source":"direct"}],"meeting_meta":{"person_main":null,"date_inferred":null}}
            ```
        """.trimIndent()

        val parsed = parser.parse(raw)

        assertEquals(FlagType.RISK, parsed?.flags?.firstOrNull()?.type)
    }

    @Test
    fun `returns null on malformed JSON`() {
        assertNull(parser.parse("this is not json"))
        assertNull(parser.parse(""))
        assertNull(parser.parse("{not closed"))
    }

    @Test
    fun `returns null on missing required fields`() {
        // "name" is required on StructuredPerson
        val raw = """{"persons":[{"role_hint":"x"}],"actions":[],"promises":[],"flags":[],"meeting_meta":{}}"""
        assertNull(parser.parse(raw))
    }
}
