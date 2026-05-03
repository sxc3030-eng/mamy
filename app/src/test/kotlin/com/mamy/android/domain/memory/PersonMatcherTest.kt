package com.mamy.android.domain.memory

import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.entity.PersonEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class PersonMatcherTest {

    private val dao: PersonDao = mockk()
    private val matcher = PersonMatcher(dao)

    @Test
    fun `single match returns SingleMatch`() = runTest {
        coEvery { dao.findByName("Marie") } returns listOf(person("Marie Dubois"))
        val r = matcher.match("Marie")
        assertTrue(r is PersonMatcher.MatchResult.SingleMatch)
    }

    @Test
    fun `multiple matches returns Ambiguous`() = runTest {
        coEvery { dao.findByName("Marie") } returns listOf(
            person("Marie Dubois"),
            person("Marie Tremblay"),
        )
        val r = matcher.match("Marie")
        assertEquals(2, (r as PersonMatcher.MatchResult.Ambiguous).candidates.size)
    }

    @Test
    fun `no matches returns NotFound`() = runTest {
        coEvery { dao.findByName("Xyz") } returns emptyList()
        val r = matcher.match("Xyz")
        assertEquals(PersonMatcher.MatchResult.NotFound, r)
    }

    private fun person(name: String) = PersonEntity(
        id = UUID.randomUUID(),
        name = name,
        email = null, roleHint = null, calendarAttendeeId = null,
        createdAt = Instant.now(), lastInteractionAt = null,
        interactionCount = 0, emotionalTrend = null,
        unmatched = false, archived = false,
    )
}
