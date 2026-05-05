package com.mamy.android.domain.intent

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull

/**
 * P9 W1-E task 10 — Golden inputs for `text_to` regex grammar (spec section 5).
 *
 * Covers FR + EN happy paths from the spec corpus, plus edge cases :
 *  - empty body / body too short -> miss
 *  - who too long -> miss
 *  - non-text-to phrase -> miss (router falls back to Capture)
 */
class IntentGrammarTextToTest {

    @Test
    fun `FR texte à Jimmy que ___ matches`() {
        val r = IntentGrammar.matchTextTo("MamY texte à Jimmy que c'est bon pour ce soir")
        assertEquals("Jimmy", r?.first)
        assertEquals("c'est bon pour ce soir", r?.second)
    }

    @Test
    fun `FR texte Jimmy colon body matches`() {
        val r = IntentGrammar.matchTextTo("MamY texte Jimmy : c'est bon pour ce soir")
        assertEquals("Jimmy", r?.first)
        assertEquals("c'est bon pour ce soir", r?.second)
    }

    @Test
    fun `FR envoie un texto à first-last name matches`() {
        val r = IntentGrammar.matchTextTo("MamY envoie un texto à Marie Dubois que je serai en retard")
        assertEquals("Marie Dubois", r?.first)
        assertEquals("je serai en retard", r?.second)
    }

    @Test
    fun `FR dis à Pierre que ___ matches`() {
        val r = IntentGrammar.matchTextTo("MamY dis à Pierre que la réunion est déplacée")
        assertEquals("Pierre", r?.first)
        assertEquals("la réunion est déplacée", r?.second)
    }

    @Test
    fun `FR with comma after wake-word matches`() {
        val r = IntentGrammar.matchTextTo("MamY, texte à Jimmy que c'est bon")
        assertEquals("Jimmy", r?.first)
        assertEquals("c'est bon", r?.second)
    }

    @Test
    fun `EN text Jimmy that ___ matches`() {
        val r = IntentGrammar.matchTextTo("MamY text Jimmy that I'm running late")
        assertEquals("Jimmy", r?.first)
        assertEquals("I'm running late", r?.second)
    }

    @Test
    fun `EN send Marie a text saying matches`() {
        val r = IntentGrammar.matchTextTo("MamY send Marie a text saying meeting at 3")
        assertEquals("Marie", r?.first)
        assertEquals("meeting at 3", r?.second)
    }

    @Test
    fun `EN send sms to firstlast that body matches`() {
        val r = IntentGrammar.matchTextTo("MamY send sms to John Smith that hello there")
        assertEquals("John Smith", r?.first)
        assertEquals("hello there", r?.second)
    }

    @Test
    fun `body too short fails validation`() {
        val r = IntentGrammar.matchTextTo("MamY texte à Jimmy que ok")
        assertNull(r) // body "ok" is 2 chars, < 3 minimum
    }

    @Test
    fun `body too long fails validation`() {
        val veryLong = "x".repeat(400)
        val r = IntentGrammar.matchTextTo("MamY texte à Jimmy que $veryLong")
        assertNull(r)
    }

    @Test
    fun `non text-to phrase yields null`() {
        assertNull(IntentGrammar.matchTextTo("MamY ma journée"))
        assertNull(IntentGrammar.matchTextTo("MamY prends note Jimmy va bien"))
        assertNull(IntentGrammar.matchTextTo("MamY"))
    }

    @Test
    fun `accent in name preserved`() {
        val r = IntentGrammar.matchTextTo("MamY texte à Élise que coucou ma puce")
        assertEquals("Élise", r?.first)
        assertEquals("coucou ma puce", r?.second)
    }

    @Test
    fun `hyphenated firstname preserved`() {
        val r = IntentGrammar.matchTextTo("MamY texte à Jean-François que à demain")
        assertEquals("Jean-François", r?.first)
        assertEquals("à demain", r?.second)
    }
}
