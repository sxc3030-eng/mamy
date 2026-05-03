package com.mamy.android.domain.intent.handler

import com.mamy.android.data.db.entity.PersonEntity
import com.mamy.android.data.tts.TextToSpeechAdapter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Locale
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull

class HomonymeClarifierTest {

    private val tts: TextToSpeechAdapter = mockk(relaxed = true)
    private val clarifier = HomonymeClarifier(tts)

    @Test
    fun `picks candidate matching response`() = runTest {
        val dubois = personFixture("Marie Dubois")
        val tremblay = personFixture("Marie Tremblay")
        coEvery { tts.listenOnce(any()) } returns "Tremblay"

        val choice = clarifier.disambiguate(listOf(dubois, tremblay), Locale.FRENCH)

        assertEquals(tremblay.id, choice?.id)
        coVerify { tts.speak(match { it.contains("Marie Dubois") && it.contains("Marie Tremblay") }, Locale.FRENCH) }
    }

    @Test
    fun `no audible response returns null`() = runTest {
        val a = personFixture("Marie Dubois")
        val b = personFixture("Marie Tremblay")
        coEvery { tts.listenOnce(any()) } returns null

        val choice = clarifier.disambiguate(listOf(a, b), Locale.FRENCH)
        assertNull(choice)
    }

    @Test
    fun `unmatched response returns null`() = runTest {
        val a = personFixture("Marie Dubois")
        val b = personFixture("Marie Tremblay")
        coEvery { tts.listenOnce(any()) } returns "personne d'autre"

        val choice = clarifier.disambiguate(listOf(a, b), Locale.FRENCH)
        assertNull(choice)
    }

    private fun personFixture(name: String) = PersonEntity(
        id = UUID.randomUUID(), name = name,
        email = null, roleHint = null, calendarAttendeeId = null,
        createdAt = Instant.now(), lastInteractionAt = null,
        interactionCount = 0, emotionalTrend = null,
        unmatched = false, archived = false,
    )
}
