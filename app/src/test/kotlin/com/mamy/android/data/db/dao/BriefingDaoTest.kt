package com.mamy.android.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mamy.android.data.db.MamYDatabase
import com.mamy.android.data.db.entity.BriefingEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
class BriefingDaoTest {

    private lateinit var db: MamYDatabase
    private lateinit var dao: BriefingDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MamYDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.briefingDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `getValidByTypeAndTarget returns non-expired briefing`() = runTest {
        val now = Instant.parse("2026-05-02T08:00:00Z")
        dao.insert(sampleBriefing(
            type = "daily",
            targetId = null,
            generatedAt = now,
            expiresAt = now.plusSeconds(8 * 3600),
        ))
        val fetched = dao.getValidByTypeAndTarget("daily", null, now.plusSeconds(60))
        assertEquals("daily", fetched!!.type)
    }

    @Test
    fun `getValidByTypeAndTarget returns null for expired`() = runTest {
        val now = Instant.parse("2026-05-02T08:00:00Z")
        dao.insert(sampleBriefing(
            type = "daily",
            targetId = null,
            generatedAt = now.minusSeconds(10 * 3600),
            expiresAt = now.minusSeconds(2 * 3600),
        ))
        assertNull(dao.getValidByTypeAndTarget("daily", null, now))
    }

    @Test
    fun `deleteExpired removes only expired entries`() = runTest {
        val now = Instant.parse("2026-05-02T08:00:00Z")
        dao.insert(sampleBriefing("a", null, now, now.plusSeconds(3600)))
        dao.insert(sampleBriefing("b", null, now.minusSeconds(7200), now.minusSeconds(3600)))
        dao.deleteExpired(now)
        assertEquals(1, dao.countAll())
    }

    private fun sampleBriefing(
        type: String,
        targetId: String?,
        generatedAt: Instant,
        expiresAt: Instant,
    ) = BriefingEntity(
        id = UUID.randomUUID(),
        type = type,
        targetId = targetId,
        generatedAt = generatedAt,
        expiresAt = expiresAt,
        text = "briefing text",
        llmProvider = "claude",
        llmCostCents = 2,
    )
}
