package com.mamy.android.domain.intent

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class IntentRouterTest {

    private val router = IntentRouter()

    @Test
    fun `capture FR routes to Capture`() {
        val intent = router.classify("MamY, prends note Marie va mieux")
        assertTrue(intent is Intent.Capture)
    }

    @Test
    fun `text_to FR routes to TextTo`() {
        val intent = router.classify("MamY texte à Jimmy que c'est bon pour ce soir")
        assertTrue(intent is Intent.TextTo, "expected TextTo but got $intent")
        intent as Intent.TextTo
        assertEquals("Jimmy", intent.who)
        assertEquals("c'est bon pour ce soir", intent.body)
    }

    @Test
    fun `text_to EN routes to TextTo`() {
        val intent = router.classify("MamY text Jimmy that I'm running late")
        assertTrue(intent is Intent.TextTo, "expected TextTo but got $intent")
        intent as Intent.TextTo
        assertEquals("Jimmy", intent.who)
        assertEquals("I'm running late", intent.body)
    }

    @Test
    fun `daily_brief FR routes`() {
        val intent = router.classify("MamY, ma journée")
        assertTrue(intent is Intent.DailyBrief)
    }

    @Test
    fun `daily_brief EN routes`() {
        val intent = router.classify("MamY, my day")
        assertTrue(intent is Intent.DailyBrief)
    }

    @Test
    fun `next_brief FR routes`() {
        val intent = router.classify("MamY, briefe")
        assertTrue(intent is Intent.NextBrief)
    }

    @Test
    fun `next_brief does NOT swallow person_brief`() {
        val intent = router.classify("MamY, briefe-moi sur Marie")
        assertTrue(intent is Intent.PersonBrief, "expected PersonBrief but got $intent")
        assertEquals("Marie", (intent as Intent.PersonBrief).personQuery)
    }

    @Test
    fun `person_brief alias routes`() {
        val intent = router.classify("MamY, c'est quoi avec Pierre")
        assertTrue(intent is Intent.PersonBrief)
        assertEquals("Pierre", (intent as Intent.PersonBrief).personQuery)
    }

    @Test
    fun `promises_owed_me routes`() {
        val intent = router.classify("MamY, qui me devait quoi")
        assertTrue(intent is Intent.PromisesOwedMe)
    }

    @Test
    fun `actions_open routes`() {
        val intent = router.classify("MamY, mes actions ouvertes")
        assertTrue(intent is Intent.ActionsOpen)
    }

    @Test
    fun `eod_summary routes`() {
        val intent = router.classify("MamY, résume ma journée")
        assertTrue(intent is Intent.EodSummary)
    }

    @Test
    fun `undo_last routes`() {
        val intent = router.classify("MamY, oublie ça")
        assertTrue(intent is Intent.UndoLast)
    }

    @Test
    fun `correct_last routes with correction text`() {
        val intent = router.classify("MamY, modifie : remplace Marie par Pierre")
        assertTrue(intent is Intent.CorrectLast)
        assertEquals("remplace Marie par Pierre", (intent as Intent.CorrectLast).correctedText)
    }

    @Test
    fun `unknown command falls back to Capture`() {
        val intent = router.classify("MamY blabla random text")
        assertTrue(intent is Intent.Capture)
        assertEquals("MamY blabla random text", (intent as Intent.Capture).rawText)
    }

    @Test
    fun `empty wake-word still falls back to Capture`() {
        val intent = router.classify("MamY")
        assertTrue(intent is Intent.Capture)
    }

    @Test
    fun `extra whitespace tolerated`() {
        val intent = router.classify("MamY,    ma journée   ")
        assertTrue(intent is Intent.DailyBrief)
    }
}
