package com.mamy.android.data.contacts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhoneNumberTest {

    @Test
    fun `data class equality compares e164 and type`() {
        val a = PhoneNumber("+15145551234", PhoneType.MOBILE)
        val b = PhoneNumber("+15145551234", PhoneType.MOBILE)
        val c = PhoneNumber("+15145551234", PhoneType.WORK)
        val d = PhoneNumber("+15145559999", PhoneType.MOBILE)
        assertEquals(a, b)
        assertNotEquals(a, c)
        assertNotEquals(a, d)
    }

    @Test
    fun `e164 format starts with plus and digits only`() {
        val p = PhoneNumber("+15145551234", PhoneType.MOBILE)
        assertTrue(p.e164.startsWith("+"))
        assertTrue(p.e164.drop(1).all { it.isDigit() })
    }

    @Test
    fun `phone type enum covers all categories`() {
        assertEquals(4, PhoneType.entries.size)
        assertTrue(PhoneType.entries.containsAll(listOf(PhoneType.MOBILE, PhoneType.WORK, PhoneType.HOME, PhoneType.OTHER)))
    }

    @Test
    fun `contact data class wires phones and emails`() {
        val c = Contact(
            id = "42",
            displayName = "Jimmy Tremblay",
            firstName = "Jimmy",
            lastName = "Tremblay",
            phones = listOf(PhoneNumber("+15145551234", PhoneType.MOBILE)),
            emails = listOf("jimmy@example.com"),
        )
        assertEquals("Jimmy Tremblay", c.displayName)
        assertEquals(1, c.phones.size)
        assertEquals("jimmy@example.com", c.emails.first())
    }
}
