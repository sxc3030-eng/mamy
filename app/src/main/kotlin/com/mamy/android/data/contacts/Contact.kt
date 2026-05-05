package com.mamy.android.data.contacts

/**
 * In-memory projection of an Android contact (`ContactsContract.Contacts`).
 *
 * Re-built from the `ContentResolver` on each [ContactsRepository.observeContacts] emission ;
 * never persisted to Room (the system Contacts DB is canonical).
 */
data class Contact(
    /** `ContactsContract.Contacts._ID` as String (kept stringly-typed because Long IDs round-trip badly across providers). */
    val id: String,
    /** `Contacts.DISPLAY_NAME_PRIMARY` — e.g. "Jimmy Tremblay". */
    val displayName: String,
    /** `StructuredName.GIVEN_NAME` if available — e.g. "Jimmy". */
    val firstName: String? = null,
    /** `StructuredName.FAMILY_NAME` if available — e.g. "Tremblay". */
    val lastName: String? = null,
    /** All phone rows attached to this contact, normalized to E.164. */
    val phones: List<PhoneNumber> = emptyList(),
    /** All email rows attached to this contact, lowercased. */
    val emails: List<String> = emptyList(),
)
