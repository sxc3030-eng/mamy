package com.mamy.android.data.tts

import com.mamy.android.data.llm.model.FlagType
import com.mamy.android.data.llm.model.StructuredAction
import com.mamy.android.data.llm.model.StructuredFlag
import com.mamy.android.data.llm.model.StructuredNote
import com.mamy.android.util.Lang
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TtsConfirmerTextTest {

    private val builder = TtsConfirmer.MessageBuilder()

    @Test
    fun `FR singular forms`() {
        val note = StructuredNote(
            actions = listOf(StructuredAction("x", "self")),
            flags = listOf(StructuredFlag(person = "Marie", type = FlagType.RISK, source = "direct")),
        )
        assertEquals("Noté. 1 action, 1 personne flaggée.", builder.confirmation(note, Lang.FR))
    }

    @Test
    fun `FR plural forms`() {
        val note = StructuredNote(
            actions = listOf(StructuredAction("x", "self"), StructuredAction("y", "self")),
            flags = listOf(
                StructuredFlag("a", FlagType.RISK, "direct"),
                StructuredFlag("b", FlagType.RISK, "direct"),
                StructuredFlag("c", FlagType.RISK, "direct"),
            ),
        )
        assertEquals("Noté. 2 actions, 3 personnes flaggées.", builder.confirmation(note, Lang.FR))
    }

    @Test
    fun `EN singular forms`() {
        val note = StructuredNote(
            actions = listOf(StructuredAction("x", "self")),
            flags = listOf(StructuredFlag("M", FlagType.RISK, "direct")),
        )
        assertEquals("Noted. 1 action, 1 person flagged.", builder.confirmation(note, Lang.EN))
    }

    @Test
    fun `EN plural forms`() {
        val note = StructuredNote(
            actions = listOf(StructuredAction("x", "self"), StructuredAction("y", "self")),
            flags = listOf(
                StructuredFlag("a", FlagType.RISK, "direct"),
                StructuredFlag("b", FlagType.RISK, "direct"),
            ),
        )
        assertEquals("Noted. 2 actions, 2 people flagged.", builder.confirmation(note, Lang.EN))
    }

    @Test
    fun `zero items`() {
        val note = StructuredNote()
        assertEquals("Noté. 0 action, 0 personne flaggée.", builder.confirmation(note, Lang.FR))
        assertEquals("Noted. 0 actions, 0 people flagged.", builder.confirmation(note, Lang.EN))
    }
}
