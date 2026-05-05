package com.mamy.android.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import androidx.core.content.ContextCompat
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reactive read-only access to the Android system Contacts.
 *
 * - [observeContacts] emits the full list once and re-emits whenever
 *   `ContactsContract.Contacts.CONTENT_URI` notifies a change (insert/update/delete).
 * - When [hasContactsPermission] is false the flow emits an empty list and never observes —
 *   this lets callers reuse the same flow across permission grants without rebuilding.
 *
 * Phone numbers are normalized to E.164 via Google `libphonenumber` against the device's
 * default region (or `CA` as a Quebec-friendly fallback). Numbers that fail to parse are
 * dropped silently — they aren't useful for `SmsManager.sendTextMessage` anyway.
 */
@Singleton
class ContactsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val phoneUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance()

    /** Live list of contacts. Empty when [hasContactsPermission] is false. */
    fun observeContacts(): Flow<List<Contact>> = callbackFlow {
        if (!hasContactsPermission()) {
            trySend(emptyList())
            awaitClose { /* no observer was registered */ }
            return@callbackFlow
        }

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(loadContacts())
            }
        }
        val resolver = context.contentResolver
        resolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI,
            true,
            observer,
        )
        trySend(loadContacts())
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    /** True if `READ_CONTACTS` is currently granted. */
    fun hasContactsPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Single-shot read of the system Contacts. Visible for testing.
     *
     * Loads `Contacts` as the spine, then attaches phones & emails by `CONTACT_ID`.
     */
    internal fun loadContacts(): List<Contact> {
        if (!hasContactsPermission()) return emptyList()
        val resolver = context.contentResolver
        val phonesById = mutableMapOf<String, MutableList<PhoneNumber>>()
        val emailsById = mutableMapOf<String, MutableList<String>>()
        val firstByDisplay = mutableMapOf<String, Pair<String?, String?>>()

        // Phones
        resolver.query(
            CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                CommonDataKinds.Phone.CONTACT_ID,
                CommonDataKinds.Phone.NUMBER,
                CommonDataKinds.Phone.TYPE,
            ),
            null, null, null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(CommonDataKinds.Phone.CONTACT_ID)
            val numCol = cursor.getColumnIndexOrThrow(CommonDataKinds.Phone.NUMBER)
            val typeCol = cursor.getColumnIndexOrThrow(CommonDataKinds.Phone.TYPE)
            while (cursor.moveToNext()) {
                val cid = cursor.getLong(idCol).toString()
                val raw = cursor.getString(numCol) ?: continue
                val androidType = cursor.getInt(typeCol)
                val e164 = normalizeToE164(raw) ?: continue
                phonesById.getOrPut(cid) { mutableListOf() }
                    .add(PhoneNumber(e164, mapPhoneType(androidType)))
            }
        }

        // Emails
        resolver.query(
            CommonDataKinds.Email.CONTENT_URI,
            arrayOf(
                CommonDataKinds.Email.CONTACT_ID,
                CommonDataKinds.Email.ADDRESS,
            ),
            null, null, null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(CommonDataKinds.Email.CONTACT_ID)
            val addrCol = cursor.getColumnIndexOrThrow(CommonDataKinds.Email.ADDRESS)
            while (cursor.moveToNext()) {
                val cid = cursor.getLong(idCol).toString()
                val addr = cursor.getString(addrCol)?.lowercase(Locale.ROOT) ?: continue
                emailsById.getOrPut(cid) { mutableListOf() }.add(addr)
            }
        }

        // Structured names — best-effort firstName/lastName
        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                CommonDataKinds.StructuredName.CONTACT_ID,
                CommonDataKinds.StructuredName.GIVEN_NAME,
                CommonDataKinds.StructuredName.FAMILY_NAME,
            ),
            "${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE),
            null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(CommonDataKinds.StructuredName.CONTACT_ID)
            val firstCol = cursor.getColumnIndexOrThrow(CommonDataKinds.StructuredName.GIVEN_NAME)
            val lastCol = cursor.getColumnIndexOrThrow(CommonDataKinds.StructuredName.FAMILY_NAME)
            while (cursor.moveToNext()) {
                val cid = cursor.getLong(idCol).toString()
                if (firstByDisplay.containsKey(cid)) continue
                firstByDisplay[cid] = cursor.getString(firstCol) to cursor.getString(lastCol)
            }
        }

        // Spine: Contacts table
        return resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ),
            null, null,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val out = mutableListOf<Contact>()
            while (cursor.moveToNext()) {
                val cid = cursor.getLong(idCol).toString()
                val display = cursor.getString(nameCol) ?: continue
                val (first, last) = firstByDisplay[cid] ?: (null to null)
                out += Contact(
                    id = cid,
                    displayName = display,
                    firstName = first,
                    lastName = last,
                    phones = phonesById[cid].orEmpty(),
                    emails = emailsById[cid].orEmpty(),
                )
            }
            out.toList()
        } ?: emptyList()
    }

    /** Public for [ContactMatcher] / unit tests to share the same normalization. */
    internal fun normalizeToE164(raw: String): String? {
        if (raw.isBlank()) return null
        val region = defaultRegion()
        return try {
            val parsed = phoneUtil.parse(raw, region)
            if (!phoneUtil.isValidNumber(parsed)) null
            else phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
        } catch (_: NumberParseException) {
            null
        }
    }

    private fun defaultRegion(): String {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE)
        // Avoid a hard reflective dep — fall back to locale country which is Pixel-7-friendly.
        @Suppress("UNUSED_VARIABLE")
        val unused = tm
        val locale = Locale.getDefault()
        val cc = locale.country?.takeIf { it.length == 2 }?.uppercase(Locale.ROOT)
        return cc ?: "CA"
    }

    private fun mapPhoneType(androidType: Int): PhoneType = when (androidType) {
        CommonDataKinds.Phone.TYPE_MOBILE -> PhoneType.MOBILE
        CommonDataKinds.Phone.TYPE_WORK,
        CommonDataKinds.Phone.TYPE_WORK_MOBILE -> PhoneType.WORK
        CommonDataKinds.Phone.TYPE_HOME -> PhoneType.HOME
        else -> PhoneType.OTHER
    }
}
