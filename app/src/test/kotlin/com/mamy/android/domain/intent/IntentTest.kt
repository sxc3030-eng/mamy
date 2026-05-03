package com.mamy.android.domain.intent

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class IntentTest {
    @Test
    fun `Intent_Capture has rawText`() {
        val intent: Intent = Intent.Capture(rawText = "MamY, prends note bla bla")
        assertEquals("MamY, prends note bla bla", (intent as Intent.Capture).rawText)
    }

    @Test
    fun `Intent_PersonBrief has personQuery`() {
        val intent: Intent = Intent.PersonBrief(personQuery = "Marie", rawText = "MamY, briefe-moi sur Marie")
        assertEquals("Marie", (intent as Intent.PersonBrief).personQuery)
    }

    @Test
    fun `Intent_CorrectLast has correctedText`() {
        val intent: Intent = Intent.CorrectLast(correctedText = "remplace projet X par projet Y", rawText = "MamY, modifie : remplace projet X par projet Y")
        assertEquals("remplace projet X par projet Y", (intent as Intent.CorrectLast).correctedText)
    }

    @Test
    fun `simple intents only carry rawText`() {
        val intents: List<Intent> = listOf(
            Intent.DailyBrief(rawText = "x"),
            Intent.NextBrief(rawText = "x"),
            Intent.PromisesOwedMe(rawText = "x"),
            Intent.ActionsOpen(rawText = "x"),
            Intent.EodSummary(rawText = "x"),
            Intent.UndoLast(rawText = "x"),
        )
        assertEquals(6, intents.size)
    }
}
