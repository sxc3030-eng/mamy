package com.mamy.android.data.sms

import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.test.core.app.ApplicationProvider
import com.mamy.android.data.db.dao.SentSmsDao
import com.mamy.android.data.db.entity.SentSmsEntry
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowApplication
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * P9 W1-E task 6 — Unit tests for [SmsSender].
 *
 * SmsManager is a system service that cannot be instantiated directly. We
 * inject a fake context whose ContextCompat.checkSelfPermission and
 * getSystemService(SmsManager) calls are interposed via Robolectric's
 * `grantPermissions` API for the permission gate, and via a relaxed mockk on
 * the Context for the SmsManager pathway.
 *
 * Coverage :
 *  - Permission denied -> SmsResult.PermissionDenied (no DAO insert).
 *  - Single segment success -> DAO has a `pending` row, sendTextMessage hit once.
 *  - Multi-segment (>160 chars) -> sendMultipartTextMessage with parts list.
 *  - SmsManager throws -> SmsResult.Failed + DB row updated to `failed`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SmsSenderTest {

    private lateinit var dao: FakeSentSmsDao
    private lateinit var clock: Clock
    private val now = Instant.parse("2026-05-03T18:00:00Z")

    @Before
    fun setUp() {
        dao = FakeSentSmsDao()
        clock = Clock.fixed(now, ZoneOffset.UTC)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `permission denied returns PermissionDenied without inserting`() = runTest {
        val context = mockk<Context>(relaxed = true)
        every { context.checkPermission(any(), any(), any()) } returns PackageManager.PERMISSION_DENIED
        every { context.checkSelfPermission(any()) } returns PackageManager.PERMISSION_DENIED

        val sender = SmsSender(context, dao, clock)
        val result = sender.send(
            phoneE164 = "+15145551234",
            body = "hello",
            recipientDisplayName = "Jimmy",
            contactId = null,
            linkedPersonId = null,
            rawIntentText = "MamY texte à Jimmy que hello",
            privacyMode = "standard",
        )

        assertEquals(SmsResult.PermissionDenied, result)
        assertEquals(0, dao.inserted.size)
    }

    @Test
    fun `single segment success records pending then dispatches sendTextMessage`() = runTest {
        val (sender, smsManager) = senderWithMockSmsManager()

        val result = sender.send(
            phoneE164 = "+15145551234",
            body = "short",
            recipientDisplayName = "Jimmy",
            contactId = "42",
            linkedPersonId = null,
            rawIntentText = "MamY texte à Jimmy que short",
            privacyMode = "standard",
        )

        assertTrue(result is SmsResult.Sending)
        result as SmsResult.Sending
        assertEquals(1, result.segments)

        // DAO insert happened with status pending
        assertEquals(1, dao.inserted.size)
        assertEquals("pending", dao.inserted[0].status)
        assertEquals("+15145551234", dao.inserted[0].recipientPhone)
        assertEquals("short", dao.inserted[0].body)

        verify(exactly = 1) {
            smsManager.sendTextMessage(
                "+15145551234", null, "short", any<PendingIntent>(), any<PendingIntent>(),
            )
        }
        verify(exactly = 0) {
            smsManager.sendMultipartTextMessage(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `multi-segment body uses sendMultipartTextMessage`() = runTest {
        val (sender, smsManager) = senderWithMockSmsManager(parts = arrayListOf("a", "b", "c"))

        val longBody = "x".repeat(420) // > 320 -> multi-part
        val result = sender.send(
            phoneE164 = "+15145551234",
            body = longBody,
            recipientDisplayName = "Marie",
            contactId = null,
            linkedPersonId = UUID.randomUUID(),
            rawIntentText = "MamY texte à Marie",
            privacyMode = "standard",
        )

        assertTrue(result is SmsResult.Sending)
        result as SmsResult.Sending
        assertEquals(3, result.segments)
        assertEquals(3, dao.inserted[0].segments)

        val partsCaptor = slot<ArrayList<String>>()
        verify(exactly = 1) {
            smsManager.sendMultipartTextMessage(
                "+15145551234", null,
                capture(partsCaptor),
                any(), any(),
            )
        }
        assertEquals(3, partsCaptor.captured.size)
    }

    @Test
    fun `SmsManager throw flips row to failed and returns Failed`() = runTest {
        val (sender, smsManager) = senderWithMockSmsManager()
        every {
            smsManager.sendTextMessage(any(), any(), any(), any(), any())
        } throws IllegalArgumentException("boom")

        val result = sender.send(
            phoneE164 = "+15145551234",
            body = "ping",
            recipientDisplayName = "Jimmy",
            contactId = null,
            linkedPersonId = null,
            rawIntentText = "MamY texte à Jimmy que ping",
            privacyMode = "standard",
        )

        assertTrue(result is SmsResult.Failed)
        assertEquals("boom", (result as SmsResult.Failed).reason)
        // status updated
        val updated = dao.updates.last()
        assertEquals("failed", updated.second)
        assertEquals("boom", updated.third)
    }

    /** Wires SmsSender against a real Robolectric context with SEND_SMS granted, and
     *  swaps `Context.getSystemService(SmsManager::class.java)` to return our mock. */
    private fun senderWithMockSmsManager(
        parts: ArrayList<String> = arrayListOf("short"),
    ): Pair<SmsSender, SmsManager> {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val shadowApp = Shadow.extract<ShadowApplication>(app)
        shadowApp.grantPermissions(android.Manifest.permission.SEND_SMS)

        val smsManager = mockk<SmsManager>(relaxed = true)
        every { smsManager.divideMessage(any()) } returns parts

        // Wrap context so getSystemService(SmsManager) returns our mock.
        val wrapped = object : android.content.ContextWrapper(app) {
            override fun <T : Any?> getSystemService(serviceClass: Class<T>): T {
                if (serviceClass == SmsManager::class.java) {
                    @Suppress("UNCHECKED_CAST")
                    return smsManager as T
                }
                return super.getSystemService(serviceClass)
            }
        }
        return SmsSender(wrapped, dao, clock) to smsManager
    }

    /** In-test fake DAO — captures inserts + status updates without spinning a Room DB. */
    private class FakeSentSmsDao : SentSmsDao {
        val inserted = mutableListOf<SentSmsEntry>()
        val updates = mutableListOf<Triple<UUID, String, String?>>()
        override suspend fun insert(entry: SentSmsEntry) { inserted += entry }
        override suspend fun updateStatus(id: UUID, status: String, failReason: String?) {
            updates += Triple(id, status, failReason)
        }
        override suspend fun findById(id: UUID): SentSmsEntry? = inserted.firstOrNull { it.id == id }
        override fun observeForPerson(personId: UUID) = flowOf(inserted.filter { it.recipientPersonId == personId })
        override fun observeAll() = flowOf(inserted.toList())
        override fun getPendingFlow() = MutableStateFlow(inserted.filter { it.status == "pending" })
    }
}
