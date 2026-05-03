package com.mamy.android.domain.briefing

import com.mamy.android.data.db.dao.BriefingDao
import com.mamy.android.data.db.entity.BriefingEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class BriefingCacheTest {

    private val now = Instant.parse("2026-05-02T13:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val dao = mockk<BriefingDao>(relaxed = true)
    private val sut = BriefingCache(dao, clock)

    @Test
    fun `get returns null when type is not cached (PERSON_QUERY)`() = runTest {
        val out = sut.get(BriefingType.PERSON_QUERY, "anything")
        assertNull(out)
        coVerify(exactly = 0) { dao.fresh(any(), any(), any()) }
    }

    @Test
    fun `get returns null when DAO finds no fresh row`() = runTest {
        coEvery { dao.fresh("DAILY", "", now) } returns null
        val out = sut.get(BriefingType.DAILY, null)
        assertNull(out)
    }

    @Test
    fun `get returns BriefingResult with cached=true when row fresh`() = runTest {
        val row = BriefingEntity(
            id = UUID.randomUUID(), type = "DAILY", targetId = "",
            generatedAt = now.minusSeconds(60), expiresAt = now.plusSeconds(3600),
            text = "Bonjour Marc", llmProvider = "claude", llmCostCents = 4,
        )
        coEvery { dao.fresh("DAILY", "", now) } returns row
        val out = sut.get(BriefingType.DAILY, null)
        assertNotNull(out)
        assertEquals("Bonjour Marc", out!!.text)
        assertTrue(out.cached)
        assertEquals("claude", out.providerName)
        assertEquals(0, out.costCents) // cached hit costs 0 this run
    }

    @Test
    fun `put writes row with computed expiresAt for DAILY (8h)`() = runTest {
        val slot = slot<BriefingEntity>()
        coEvery { dao.insert(capture(slot)) } returns Unit
        val out = sut.put(BriefingType.DAILY, null, "Hello", "claude", costCents = 7)
        assertEquals(now.plusSeconds(8 * 3600), out.expiresAt)
        assertEquals(now.plusSeconds(8 * 3600), slot.captured.expiresAt)
        assertEquals("DAILY", slot.captured.type)
        assertEquals("", slot.captured.targetId)
        assertEquals(false, out.cached)
    }

    @Test
    fun `put for PRE_MEETING uses 1h ttl`() = runTest {
        val slot = slot<BriefingEntity>()
        coEvery { dao.insert(capture(slot)) } returns Unit
        sut.put(BriefingType.PRE_MEETING, "meeting-id", "Hi", "gpt", 5)
        assertEquals(now.plusSeconds(3600), slot.captured.expiresAt)
        assertEquals("meeting-id", slot.captured.targetId)
    }

    @Test
    fun `put for non-cached type does not touch DAO`() = runTest {
        val out = sut.put(BriefingType.EOD_SUMMARY, null, "Bye", "gemini", 3)
        coVerify(exactly = 0) { dao.insert(any()) }
        assertEquals("Bye", out.text)
        assertEquals(false, out.cached)
    }

    @Test
    fun `put deletes prior entries before insert (idempotency)`() = runTest {
        sut.put(BriefingType.DAILY, null, "x", "claude", 1)
        coVerify { dao.deleteFor("DAILY", "") }
        coVerify { dao.insert(any()) }
    }

    @Test
    fun `evictExpired delegates to DAO with current time`() = runTest {
        sut.evictExpired()
        coVerify { dao.deleteExpired(now) }
    }
}
