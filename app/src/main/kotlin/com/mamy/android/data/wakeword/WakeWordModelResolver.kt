package com.mamy.android.data.wakeword

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Copies the appropriate `.ppn` asset to internal storage (Porcupine needs a real path)
 * and returns the absolute path.
 */
class WakeWordModelResolver(private val context: Context) {

    fun resolveKeywordPath(locale: Locale): String {
        val assetName = when (locale.language.lowercase()) {
            "fr" -> "wakeword/mamy_fr.ppn"
            else -> "wakeword/mamy_en.ppn"
        }
        val outFile = File(context.filesDir, assetName.substringAfterLast('/'))
        if (!outFile.exists() || outFile.length() == 0L) {
            context.assets.open(assetName).use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
        }
        return outFile.absolutePath
    }
}
