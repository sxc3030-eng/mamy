package com.mamy.android.domain.briefing

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

class BriefingPromptBuilderTest {

    private val builder = BriefingPromptBuilder()
    private val ctx = """{"meetings":[{"person":"Marie","time":"10:00"}]}"""

    @Test
    fun `daily FR uses french system prompt`() {
        val p = builder.build(BriefingType.DAILY, ctx, Locale.FRENCH)
        assertTrue(p.system.contains("briefing"), "must mention briefing in french")
        assertTrue(p.system.contains("60 secondes"), "must cap at 60 seconds in french")
        assertTrue(p.user.contains(ctx), "user prompt must include the context JSON")
    }

    @Test
    fun `daily EN uses english system prompt`() {
        val p = builder.build(BriefingType.DAILY, ctx, Locale.ENGLISH)
        assertTrue(p.system.contains("morning briefing"), "must mention morning briefing")
        assertTrue(p.system.contains("60 seconds"), "must cap at 60 seconds")
    }

    @Test
    fun `pre meeting EN focuses on one person and 25 seconds`() {
        val p = builder.build(BriefingType.PRE_MEETING, ctx, Locale.ENGLISH)
        assertTrue(p.system.contains("ONE person"))
        assertTrue(p.system.contains("25 seconds"))
    }

    @Test
    fun `pre meeting FR focuses on one person and 25 secondes`() {
        val p = builder.build(BriefingType.PRE_MEETING, ctx, Locale.FRENCH)
        assertTrue(p.system.contains("UNE personne"))
        assertTrue(p.system.contains("25 secondes"))
    }

    @Test
    fun `person query FR is narrative 30 secondes`() {
        val p = builder.build(BriefingType.PERSON_QUERY, ctx, Locale.FRENCH)
        assertTrue(p.system.contains("narratif"))
        assertTrue(p.system.contains("30 secondes"))
    }

    @Test
    fun `eod EN mentions risk to watch tomorrow`() {
        val p = builder.build(BriefingType.EOD_SUMMARY, ctx, Locale.ENGLISH)
        assertTrue(p.system.contains("risk to watch tomorrow"))
    }

    @Test
    fun `eod FR mentions risque a surveiller demain`() {
        val p = builder.build(BriefingType.EOD_SUMMARY, ctx, Locale.FRENCH)
        assertTrue(p.system.contains("risque à surveiller demain"))
    }

    @Test
    fun `unknown locale falls back to english`() {
        val p = builder.build(BriefingType.DAILY, ctx, Locale("ja"))
        assertTrue(p.system.contains("morning briefing"), "non-FR/EN must fall back to EN")
    }

    @Test
    fun `cache ttl values match spec section 6`() {
        assertEquals(8 * 3600L, BriefingType.DAILY.cacheTtl.inWholeSeconds)
        assertEquals(3600L, BriefingType.PRE_MEETING.cacheTtl.inWholeSeconds)
        assertEquals(0L, BriefingType.PERSON_QUERY.cacheTtl.inWholeSeconds)
        assertEquals(0L, BriefingType.EOD_SUMMARY.cacheTtl.inWholeSeconds)
    }
}
