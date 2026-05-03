package com.mamy.android.domain.intent

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull

class IntentGrammarTest {

    @Test
    fun `capture FR matches`() {
        val match = IntentGrammar.CAPTURE.find("MamY, prends note Marie va mieux")
        assertNotNull(match)
    }

    @Test
    fun `capture EN matches`() {
        val match = IntentGrammar.CAPTURE.find("MamY take a note Marie is doing better")
        assertNotNull(match)
    }

    @Test
    fun `daily_brief FR matches`() {
        val match = IntentGrammar.DAILY_BRIEF.find("MamY, ma journée")
        assertNotNull(match)
    }

    @Test
    fun `daily_brief EN matches`() {
        val match = IntentGrammar.DAILY_BRIEF.find("MamY, my day")
        assertNotNull(match)
    }

    @Test
    fun `next_brief FR matches without name`() {
        val match = IntentGrammar.NEXT_BRIEF.find("MamY, briefe")
        assertNotNull(match)
    }

    @Test
    fun `next_brief FR does NOT match when followed by name`() {
        val match = IntentGrammar.NEXT_BRIEF.find("MamY, briefe-moi sur Marie")
        assertNull(match)
    }

    @Test
    fun `next_brief EN matches`() {
        val match = IntentGrammar.NEXT_BRIEF.find("MamY, brief me")
        assertNotNull(match)
    }

    @Test
    fun `person_brief FR captures name`() {
        val match = IntentGrammar.PERSON_BRIEF_DIRECT.find("MamY, briefe-moi sur Marie Tremblay")
        assertNotNull(match)
        assertEquals("Marie Tremblay", match!!.groupValues[2])
    }

    @Test
    fun `person_brief alias FR captures name`() {
        val match = IntentGrammar.PERSON_BRIEF_ALIAS.find("MamY, c'est quoi avec Pierre")
        assertNotNull(match)
        assertEquals("Pierre", match!!.groupValues[2])
    }

    @Test
    fun `person_brief EN captures name`() {
        val match = IntentGrammar.PERSON_BRIEF_DIRECT.find("MamY, brief me on Sarah")
        assertNotNull(match)
        assertEquals("Sarah", match!!.groupValues[2])
    }

    @Test
    fun `promises_owed_me FR matches`() {
        val match = IntentGrammar.PROMISES_OWED_ME.find("MamY, qui me devait quoi")
        assertNotNull(match)
    }

    @Test
    fun `promises_owed_me EN matches`() {
        val match = IntentGrammar.PROMISES_OWED_ME.find("MamY, what's owed to me")
        assertNotNull(match)
    }

    @Test
    fun `actions_open FR matches`() {
        val match = IntentGrammar.ACTIONS_OPEN.find("MamY, mes actions ouvertes")
        assertNotNull(match)
    }

    @Test
    fun `actions_open EN matches`() {
        val match = IntentGrammar.ACTIONS_OPEN.find("MamY, my open actions")
        assertNotNull(match)
    }

    @Test
    fun `eod_summary FR matches`() {
        val match = IntentGrammar.EOD_SUMMARY.find("MamY, résume ma journée")
        assertNotNull(match)
    }

    @Test
    fun `eod_summary EN matches`() {
        val match = IntentGrammar.EOD_SUMMARY.find("MamY, summarize my day")
        assertNotNull(match)
    }

    @Test
    fun `undo_last FR matches`() {
        val match = IntentGrammar.UNDO_LAST.find("MamY, oublie ça")
        assertNotNull(match)
    }

    @Test
    fun `undo_last EN matches`() {
        val match = IntentGrammar.UNDO_LAST.find("MamY, forget that")
        assertNotNull(match)
    }

    @Test
    fun `correct_last FR captures correction`() {
        val match = IntentGrammar.CORRECT_LAST.find("MamY, modifie : remplace Marie par Pierre")
        assertNotNull(match)
        assertEquals("remplace Marie par Pierre", match!!.groupValues[2].trim())
    }

    @Test
    fun `correct_last EN captures correction`() {
        val match = IntentGrammar.CORRECT_LAST.find("MamY, edit: change Marie to Pierre")
        assertNotNull(match)
        assertEquals("change Marie to Pierre", match!!.groupValues[2].trim())
    }

    @Test
    fun `case insensitive`() {
        val match = IntentGrammar.DAILY_BRIEF.find("mamy, MA JOURNÉE")
        assertNotNull(match)
    }
}
