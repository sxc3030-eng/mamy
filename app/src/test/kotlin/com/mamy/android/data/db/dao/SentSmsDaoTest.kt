package com.mamy.android.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.mamy.android.data.db.MamYDatabase
import com.mamy.android.data.db.entity.SentSmsEntry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

/**
 * P9 W1-E task 3 — Robolectric in-memory DAO tests for SentSmsDao.
 *
 * Covers the V1 surface :
 *  - insert + observeForPerson : 2 entries same person -> flow emits both
 *  - updateStatus pending -> sent : observed change downstream
 *  - observeAll cross-person : everything visible regardless of person
 *  - getPendingFlow filters status=pending
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SentSmsDaoTest {

    private lateinit var db: MamYDatabase
    private lateinit var dao: SentSmsDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MamYDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.sentSmsDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `insert and findById round-trip`() = runTest {
        val entry = sample()
        dao.insert(entry)
        val loaded = dao.findById(entry.id)
        assertNotNull(loaded)
        assertEquals(entry.recipientPhone, loaded!!.recipientPhone)
        assertEquals("pending", loaded.status)
    }

    @Test
    fun `observeForPerson emits both entries for same person`() = runTest {
        val personA = UUID.randomUUID()
        val a1 = sample(personId = personA, body = "first")
        val a2 = sample(personId = personA, body = "second")
        dao.insert(a1)
        dao.insert(a2)

        dao.observeForPerson(personA).test {
            val emitted = awaitItem()
            assertEquals(2, emitted.size)
            assertTrue(emitted.any { it.body == "first" })
            assertTrue(emitted.any { it.body == "second" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeForPerson does not emit other persons`() = runTest {
        val personA = UUID.randomUUID()
        val personB = UUID.randomUUID()
        dao.insert(sample(personId = personA, body = "for A"))
        dao.insert(sample(personId = personB, body = "for B"))

        dao.observeForPerson(personA).test {
            val emitted = awaitItem()
            assertEquals(1, emitted.size)
            assertEquals("for A", emitted[0].body)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateStatus from pending to sent is observable`() = runTest {
        val entry = sample()
        dao.insert(entry)
        dao.updateStatus(entry.id, "sent", null)
        val loaded = dao.findById(entry.id)
        assertEquals("sent", loaded!!.status)
        assertEquals(null, loaded.failReason)
    }

    @Test
    fun `updateStatus to failed records failReason`() = runTest {
        val entry = sample()
        dao.insert(entry)
        dao.updateStatus(entry.id, "failed", "no_service")
        val loaded = dao.findById(entry.id)
        assertEquals("failed", loaded!!.status)
        assertEquals("no_service", loaded.failReason)
    }

    @Test
    fun `observeAll emits across persons`() = runTest {
        val personA = UUID.randomUUID()
        val personB = UUID.randomUUID()
        dao.insert(sample(personId = personA, body = "A1"))
        dao.insert(sample(personId = personB, body = "B1"))
        dao.insert(sample(personId = null, body = "no-person"))

        dao.observeAll().test {
            val emitted = awaitItem()
            assertEquals(3, emitted.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getPendingFlow only emits pending status`() = runTest {
        val pending = sample(body = "pending one")
        val sent = sample(body = "sent one")
        dao.insert(pending)
        dao.insert(sent)
        dao.updateStatus(sent.id, "sent", null)

        dao.getPendingFlow().test {
            val emitted = awaitItem()
            assertEquals(1, emitted.size)
            assertEquals("pending one", emitted[0].body)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun sample(
        id: UUID = UUID.randomUUID(),
        personId: UUID? = UUID.randomUUID(),
        body: String = "hello",
        status: String = "pending",
    ) = SentSmsEntry(
        id = id,
        recipientContactId = "100",
        recipientPersonId = personId,
        recipientPhone = "+15145551234",
        recipientDisplayName = "Jimmy Tremblay",
        body = body,
        sentAt = Instant.parse("2026-05-03T18:00:00Z"),
        status = status,
        failReason = null,
        rawIntentText = "MamY texte à Jimmy que $body",
        segments = 1,
        privacyMode = "standard",
    )
}
