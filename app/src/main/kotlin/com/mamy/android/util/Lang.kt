package com.mamy.android.util

/**
 * Two-letter language enum used by LLM/STT/UI layers. Minimal and explicit
 * (no relying on java.util.Locale at the domain boundary).
 */
enum class Lang(val tag: String) {
    FR("fr"),
    EN("en");

    companion object {
        fun fromTag(tag: String): Lang = when (tag.lowercase().take(2)) {
            "fr" -> FR
            "en" -> EN
            else -> EN
        }
    }
}
