package com.mamy.android.domain.briefing

import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.entity.PersonEntity
import com.mamy.android.data.tts.TtsService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID

class PersonQueryBriefHandlerTest {

    private val now = Instant.parse("2026-05-02T13:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val personDao = mockk<PersonDao>()
    private val gen = mockk<BriefingGenerator>()
    private val tts = mockk<TtsService>(relaxed = true)
    private val sut = PersonQueryBriefHandler(personDao, gen, tts, clock)

    private fun person(name: String): PersonEntity = PersonEntity(
        id = UUID.randomUUID(), name = name, email = null, roleHint = null,
        calendarAttendeeId = null, createdAt = now, lastInteractionAt = now,
        interactionCount = 1, emotionalTrend = null, unmatched = false, archived = false,
    )

    @Test
    fun `single match generates briefing`() = runTest {
        val marie = person("Marie Dubois")
        coEvery { personDao.allActive() } returns listOf(marie, person("Pierre"))
        coEvery { gen.generate(any()) } returns BriefingResult("Marie OK", now, now, false, "claude", 4)

        val res = sut.run("Marie", Locale.FRENCH)

        assertEquals("Marie OK", res.spokenText)
        coVerify { tts.speak("Marie OK", Locale.FRENCH, interrupt = true) }
    }

    @Test
    fun `accent insensitive match - resume hits resume`() = runTest {
        val anais = person("Anaïs")
        coEvery { personDao.allActive() } returns listOf(anais)
        coEvery { gen.generate(any()) } returns BriefingResult("Anais context", now, now, false, "claude", 1)

        val res = sut.run("anais", Locale.FRENCH)
        assertEquals("Anais context", res.spokenText)
    }

    @Test
    fun `zero match speaks unknown FR`() = runTest {
        coEvery { personDao.allActive() } returns emptyList()
        val res = sut.run("Inconnu", Locale.FRENCH)
        val text = res.spokenText!!
        assertTrue(text.contains("Je ne trouve"))
    }

    @Test
    fun `multiple matches speak clarification`() = runTest {
        coEvery { personDao.allActive() } returns listOf(
            person("Marie Dubois"), person("Marie Tremblay"),
        )
        val res = sut.run("Marie", Locale.FRENCH)
        val text = res.spokenText!!
        assertTrue(text.contains("Marie Dubois"))
        assertTrue(text.contains("Marie Tremblay"))
    }
}
