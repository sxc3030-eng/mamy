package com.mamy.android.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mamy.android.data.db.MamYDatabase
import com.mamy.android.data.db.entity.PersonEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PersonDaoTest {

    private lateinit var db: MamYDatabase
    private lateinit var dao: PersonDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MamYDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.personDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `insert and getById round-trips`() = runTest {
        val p = samplePerson("Marie Dubois", "marie@example.com")
        dao.insert(p)
        val fetched = dao.getById(p.id)
        assertNotNull(fetched)
        assertEquals("Marie Dubois", fetched!!.name)
    }

    @Test
    fun `getByEmail returns matching person`() = runTest {
        dao.insert(samplePerson("Pierre Martin", "pierre@example.com"))
        val fetched = dao.getByEmail("pierre@example.com")
        assertNotNull(fetched)
        assertEquals("Pierre Martin", fetched!!.name)
    }

    @Test
    fun `getByEmail returns null for unknown email`() = runTest {
        assertNull(dao.getByEmail("nobody@x.com"))
    }

    @Test
    fun `getActiveOrderedByLastInteraction lists non-archived sorted desc`() = runTest {
        dao.insert(samplePerson("A", "a@x.com", lastInteraction = Instant.parse("2026-05-01T10:00:00Z")))
        dao.insert(samplePerson("B", "b@x.com", lastInteraction = Instant.parse("2026-05-02T10:00:00Z")))
        dao.insert(samplePerson("C", "c@x.com", archived = true))
        val list = dao.getActiveOrderedByLastInteraction()
        assertEquals(2, list.size)
        assertEquals("B", list[0].name)
        assertEquals("A", list[1].name)
    }

    @Test
    fun `update changes fields`() = runTest {
        val p = samplePerson("X", "x@x.com")
        dao.insert(p)
        dao.update(p.copy(roleHint = "Lead"))
        assertEquals("Lead", dao.getById(p.id)!!.roleHint)
    }

    @Test
    fun `deleteById removes row`() = runTest {
        val p = samplePerson("Y", "y@x.com")
        dao.insert(p)
        dao.deleteById(p.id)
        assertNull(dao.getById(p.id))
    }

    private fun samplePerson(
        name: String,
        email: String,
        lastInteraction: Instant? = null,
        archived: Boolean = false,
    ) = PersonEntity(
        id = UUID.randomUUID(),
        name = name,
        email = email,
        roleHint = null,
        calendarAttendeeId = email,
        createdAt = Instant.now(),
        lastInteractionAt = lastInteraction,
        interactionCount = 0,
        emotionalTrend = null,
        unmatched = true,
        archived = archived,
    )
}
