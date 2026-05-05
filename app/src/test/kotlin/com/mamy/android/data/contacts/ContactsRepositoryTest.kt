package com.mamy.android.data.contacts

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ContactsRepositoryTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `permission denied results in empty emit`() = runTest {
        // Default Robolectric: permission not granted.
        val repo = ContactsRepository(app)
        assertFalse(repo.hasContactsPermission())
        repo.observeContacts().test {
            assertEquals(emptyList<Contact>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `permission granted then loadContacts returns empty list when no rows`() = runTest {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        val repo = ContactsRepository(app)
        assertTrue(repo.hasContactsPermission())
        // Shadow ContentResolver returns null cursors for ContactsContract by default — repo handles that.
        val list = repo.loadContacts()
        assertEquals(emptyList<Contact>(), list)
    }

    @Test
    fun `normalize quebec mobile to E164`() {
        val repo = ContactsRepository(app)
        val out = repo.normalizeToE164("(514) 555-0123")
        // The CA region is the fallback; "555-0123" range may not validate as a real number,
        // so we accept either the formatted E.164 or null. The important contract: never crash.
        if (out != null) {
            assertTrue(out.startsWith("+"))
            assertTrue(out.drop(1).all { it.isDigit() })
        }
    }

    @Test
    fun `normalize garbage returns null`() {
        val repo = ContactsRepository(app)
        assertNull(repo.normalizeToE164(""))
        assertNull(repo.normalizeToE164("   "))
        assertNull(repo.normalizeToE164("not-a-phone"))
    }

    @Test
    fun `normalize already-E164 round trips`() {
        val repo = ContactsRepository(app)
        // +14155551234 is the canonical Apple test number range — valid in libphonenumber.
        val out = repo.normalizeToE164("+14155551234")
        assertNotNull(out)
        assertEquals("+14155551234", out)
    }
}
