package com.mamy.android.data.sms

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class SentSmsRepositoryTest {

    @Test
    fun `SmsStatus_fromRaw maps known values`() {
        assertEquals(SmsStatus.PENDING, SmsStatus.fromRaw("pending"))
        assertEquals(SmsStatus.SENT, SmsStatus.fromRaw("sent"))
        assertEquals(SmsStatus.DELIVERED, SmsStatus.fromRaw("delivered"))
        assertEquals(SmsStatus.FAILED, SmsStatus.fromRaw("failed"))
        assertEquals(SmsStatus.CANCELLED, SmsStatus.fromRaw("cancelled"))
        assertEquals(SmsStatus.CANCELLED, SmsStatus.fromRaw("canceled"))
    }

    @Test
    fun `SmsStatus_fromRaw is case insensitive`() {
        assertEquals(SmsStatus.SENT, SmsStatus.fromRaw("SENT"))
        assertEquals(SmsStatus.FAILED, SmsStatus.fromRaw("Failed"))
    }

    @Test
    fun `SmsStatus_fromRaw treats null and unknown as PENDING`() {
        assertEquals(SmsStatus.PENDING, SmsStatus.fromRaw(null))
        assertEquals(SmsStatus.PENDING, SmsStatus.fromRaw("garbage"))
    }

    @Test
    fun `SmsStatus_retryable is true for pending and failed only`() {
        assertTrue(SmsStatus.retryable(SmsStatus.PENDING))
        assertTrue(SmsStatus.retryable(SmsStatus.FAILED))
        assertFalse(SmsStatus.retryable(SmsStatus.SENT))
        assertFalse(SmsStatus.retryable(SmsStatus.DELIVERED))
        assertFalse(SmsStatus.retryable(SmsStatus.CANCELLED))
    }

    @Test
    fun `EmptySentSmsRepository observeForPerson emits empty list`() = runTest {
        val repo = EmptySentSmsRepository()
        val result = repo.observeForPerson(UUID.randomUUID()).first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `EmptySentSmsRepository retry returns false`() = runTest {
        val repo = EmptySentSmsRepository()
        assertFalse(repo.retry(UUID.randomUUID()))
    }
}
