package com.mamy.android.domain.contacts

import com.mamy.android.data.contacts.Contact
import com.mamy.android.data.contacts.ContactsRepository
import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.entity.PersonEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class AutoLinkPersonContactsUseCaseTest {

    private val personDao: PersonDao = mockk(relaxed = true)
    private val contactsRepository: ContactsRepository = mockk()
    private val useCase = AutoLinkPersonContactsUseCase(personDao, contactsRepository)

    @Test
    fun `links Person by calendar attendee email matching Contact email`() = runTest {
        every { contactsRepository.hasContactsPermission() } returns true
        every { contactsRepository.loadContacts() } returns listOf(
            contact("c1", "Jimmy Tremblay", emails = listOf("jimmy@example.com")),
            contact("c2", "Marie Dubois", emails = listOf("marie@example.com")),
        )
        val jimmy = person("Jimmy Tremblay", calendarEmail = "jimmy@example.com", androidId = null)
        val marie = person("Marie Dubois", calendarEmail = "marie@example.com", androidId = null)
        coEvery { personDao.getAll() } returns listOf(jimmy, marie)

        val linked = useCase()

        assertEquals(2, linked)
        coVerify { personDao.update(jimmy.copy(androidContactId = "c1")) }
        coVerify { personDao.update(marie.copy(androidContactId = "c2")) }
    }

    @Test
    fun `idempotent — already-linked Person is skipped`() = runTest {
        every { contactsRepository.hasContactsPermission() } returns true
        every { contactsRepository.loadContacts() } returns listOf(
            contact("c1", "Jimmy", emails = listOf("jimmy@example.com")),
        )
        val jimmy = person("Jimmy", calendarEmail = "jimmy@example.com", androidId = "c1")
        coEvery { personDao.getAll() } returns listOf(jimmy)

        val linked = useCase()

        assertEquals(0, linked)
        coVerify(exactly = 0) { personDao.update(any()) }
    }

    @Test
    fun `falls back to email when calendar attendee id is null`() = runTest {
        every { contactsRepository.hasContactsPermission() } returns true
        every { contactsRepository.loadContacts() } returns listOf(
            contact("c1", "Pierre", emails = listOf("pierre@example.com")),
        )
        val pierre = person("Pierre", calendarEmail = null, email = "pierre@example.com", androidId = null)
        coEvery { personDao.getAll() } returns listOf(pierre)

        val linked = useCase()

        assertEquals(1, linked)
    }

    @Test
    fun `permission denied → no-op`() = runTest {
        every { contactsRepository.hasContactsPermission() } returns false
        coEvery { personDao.getAll() } returns listOf(person("X"))

        val linked = useCase()

        assertEquals(0, linked)
        coVerify(exactly = 0) { personDao.getAll() }
    }

    @Test
    fun `ambiguous email shared by two Contacts is skipped`() = runTest {
        every { contactsRepository.hasContactsPermission() } returns true
        every { contactsRepository.loadContacts() } returns listOf(
            contact("c1", "Shared 1", emails = listOf("shared@example.com")),
            contact("c2", "Shared 2", emails = listOf("shared@example.com")),
        )
        val p = person("X", calendarEmail = "shared@example.com", androidId = null)
        coEvery { personDao.getAll() } returns listOf(p)

        val linked = useCase()

        assertEquals(0, linked)
        coVerify(exactly = 0) { personDao.update(any()) }
    }

    private fun person(
        name: String,
        calendarEmail: String? = null,
        email: String? = null,
        androidId: String? = null,
    ) = PersonEntity(
        id = UUID.randomUUID(),
        name = name,
        email = email,
        roleHint = null,
        calendarAttendeeId = calendarEmail,
        createdAt = Instant.now(),
        lastInteractionAt = null,
        interactionCount = 0,
        emotionalTrend = null,
        unmatched = false,
        archived = false,
        androidContactId = androidId,
    )

    private fun contact(id: String, name: String, emails: List<String>) = Contact(
        id = id,
        displayName = name,
        firstName = name.substringBefore(' '),
        lastName = null,
        phones = emptyList(),
        emails = emails,
    )
}
