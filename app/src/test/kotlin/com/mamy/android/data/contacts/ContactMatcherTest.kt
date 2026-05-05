package com.mamy.android.data.contacts

import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.entity.PersonEntity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ContactMatcherTest {

    private val personDao: PersonDao = mockk()
    private val contactsRepo: ContactsRepository = mockk()
    private val matcher = ContactMatcher(personDao, contactsRepo)

    @Test
    fun `Person table exact match wins with fromTeam true`() = runTest {
        val jimmy = personEntity("Jimmy Tremblay")
        coEvery { personDao.findByName("Jimmy Tremblay") } returns listOf(jimmy)
        every { contactsRepo.loadContacts() } returns emptyList()

        val res = matcher.findByName("Jimmy Tremblay")

        assertTrue(res is MatchResult.Single)
        val single = res as MatchResult.Single
        assertEquals("Jimmy Tremblay", single.contact.displayName)
        assertTrue(single.fromTeam)
    }

    @Test
    fun `Contact exact match returns Single fromTeam false`() = runTest {
        coEvery { personDao.findByName(any()) } returns emptyList()
        every { contactsRepo.loadContacts() } returns listOf(
            contact("1", "Jimmy Tremblay", "Jimmy", "Tremblay"),
            contact("2", "Marie Dubois", "Marie", "Dubois"),
        )

        val res = matcher.findByName("Jimmy")

        assertTrue(res is MatchResult.Single)
        val single = res as MatchResult.Single
        assertEquals("Jimmy Tremblay", single.contact.displayName)
        assertFalse(single.fromTeam)
    }

    @Test
    fun `accent insensitive exact match`() = runTest {
        coEvery { personDao.findByName(any()) } returns emptyList()
        every { contactsRepo.loadContacts() } returns listOf(
            contact("1", "Hélène Côté", "Hélène", "Côté"),
        )

        val res = matcher.findByName("helene")

        assertTrue(res is MatchResult.Single)
    }

    @Test
    fun `multiple Marie returns Multiple`() = runTest {
        coEvery { personDao.findByName(any()) } returns emptyList()
        every { contactsRepo.loadContacts() } returns listOf(
            contact("1", "Marie Dubois", "Marie", "Dubois"),
            contact("2", "Marie Tremblay", "Marie", "Tremblay"),
        )

        val res = matcher.findByName("Marie")

        assertTrue(res is MatchResult.Multiple)
        assertEquals(2, (res as MatchResult.Multiple).contacts.size)
    }

    @Test
    fun `fuzzy Levenshtein matches Jimi to Jimmy`() = runTest {
        coEvery { personDao.findByName(any()) } returns emptyList()
        every { contactsRepo.loadContacts() } returns listOf(
            contact("1", "Jimmy Tremblay", "Jimmy", "Tremblay"),
        )

        val res = matcher.findByName("Jimi")

        assertTrue("Expected fuzzy match Jimi → Jimmy, got $res", res is MatchResult.Single)
    }

    @Test
    fun `empty contacts and no Person returns None`() = runTest {
        coEvery { personDao.findByName(any()) } returns emptyList()
        every { contactsRepo.loadContacts() } returns emptyList()

        val res = matcher.findByName("Jimmy")

        assertEquals(MatchResult.None, res)
    }

    @Test
    fun `empty query returns None`() = runTest {
        val res = matcher.findByName("   ")
        assertEquals(MatchResult.None, res)
    }

    @Test
    fun `substring match on display name`() = runTest {
        coEvery { personDao.findByName(any()) } returns emptyList()
        every { contactsRepo.loadContacts() } returns listOf(
            contact("1", "Jean-Philippe Tremblay", "Jean-Philippe", "Tremblay"),
        )

        val res = matcher.findByName("Tremblay")

        assertTrue(res is MatchResult.Single)
    }

    @Test
    fun `Levenshtein helper computes small edit distances`() {
        assertEquals(0, levenshtein("jimmy", "jimmy", 5))
        assertEquals(1, levenshtein("jimmy", "jimi", 5)) // remove 'm', swap 'y'->'i' = 2 — actually
        // Jimmy (5) vs Jimi (4) — subst y→i + delete m = 2 edits.
        assertEquals(2, levenshtein("jimmy", "jimi", 5))
        // Bound triggers early bail when length diff exceeds bound.
        assertEquals(3, levenshtein("a", "abcd", 2))
    }

    @Test
    fun `normalize lowercases and strips accents`() {
        assertEquals("helene", "Hélène".normalize())
        assertEquals("francois", "François".normalize())
        assertEquals("jimmy", "  Jimmy  ".normalize())
    }

    private fun personEntity(name: String) = PersonEntity(
        id = UUID.randomUUID(),
        name = name,
        email = null,
        roleHint = null,
        calendarAttendeeId = null,
        createdAt = Instant.now(),
        lastInteractionAt = null,
        interactionCount = 0,
        emotionalTrend = null,
        unmatched = false,
        archived = false,
        androidContactId = null,
    )

    private fun contact(
        id: String,
        display: String,
        first: String?,
        last: String?,
        phones: List<PhoneNumber> = listOf(PhoneNumber("+15145551234", PhoneType.MOBILE)),
    ) = Contact(
        id = id,
        displayName = display,
        firstName = first,
        lastName = last,
        phones = phones,
        emails = emptyList(),
    )
}
