package com.mamy.android.data.contacts

/**
 * Phone number type, matching the Android `ContactsContract.CommonDataKinds.Phone.TYPE_*`
 * constants but folded into a small enum convenient for matching/UI.
 *
 * Selection priority for SMS auto-pick = MOBILE > WORK > HOME > OTHER (see spec section 6).
 */
enum class PhoneType {
    MOBILE,
    WORK,
    HOME,
    OTHER,
}
