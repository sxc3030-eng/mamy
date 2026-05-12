package com.mamy.android.data.wakeword

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

/**
 * Locates a per-locale custom Porcupine keyword (.ppn) shipped under
 * `app/src/main/assets/wakeword/`, copies it to internal storage (Porcupine
 * needs a real path on disk, not an asset stream) and returns the absolute
 * path.
 *
 * Returns `null` when no asset and no pre-copied file exists for the requested
 * locale — callers should fall back to a built-in keyword in that case. This
 * is the path used by V1.5 alpha builds that ship without custom `MamY.ppn`
 * files: the engine falls back to `BuiltInKeyword.JARVIS`.
 *
 * Drop `mamy_en.ppn` and `mamy_fr.ppn` next to `README.md` to switch the
 * wake-word back to "MamY" at build time — no code change needed.
 */
class WakeWordModelResolver(private val context: Context) {

    /** Returns the on-disk path, or null when no asset exists for the locale. */
    fun resolveKeywordPathOrNull(locale: Locale): String? {
        val fileName = when (locale.language.lowercase()) {
            "fr" -> "mamy_fr.ppn"
            else -> "mamy_en.ppn"
        }
        val assetName = "wakeword/$fileName"
        val outFile = File(context.filesDir, fileName)

        // Fast path: already copied previously (and non-empty).
        if (outFile.exists() && outFile.length() > 0L) {
            return outFile.absolutePath
        }

        // Try to copy from APK assets.
        return try {
            context.assets.open(assetName).use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
            outFile.absolutePath
        } catch (_: IOException) {
            // No asset bundled for this locale — caller should fall back to a
            // built-in keyword.
            null
        }
    }
}
