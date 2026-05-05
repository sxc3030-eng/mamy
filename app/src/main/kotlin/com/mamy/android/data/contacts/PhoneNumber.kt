package com.mamy.android.data.contacts

/**
 * Phone number normalized to E.164 (+ country code, no separators), with type metadata.
 *
 * Example: « (514) 555-1234 » in CA → `PhoneNumber("+15145551234", PhoneType.MOBILE)`.
 */
data class PhoneNumber(
    val e164: String,
    val type: PhoneType,
)
