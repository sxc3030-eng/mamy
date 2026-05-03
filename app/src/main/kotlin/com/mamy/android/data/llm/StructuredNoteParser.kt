package com.mamy.android.data.llm

import com.mamy.android.data.llm.model.StructuredNote
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Singleton
class StructuredNoteParser @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Parse a raw LLM string into a [StructuredNote].
     * Strips ```json fences``` if present. Returns null on any failure.
     */
    fun parse(raw: String): StructuredNote? {
        val cleaned = stripFences(raw).trim()
        if (cleaned.isEmpty()) return null
        return try {
            json.decodeFromString(StructuredNote.serializer(), cleaned)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun stripFences(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        // Remove first fence line and last fence line
        val lines = trimmed.lines()
        val start = if (lines.first().startsWith("```")) 1 else 0
        val end = if (lines.last() == "```") lines.size - 1 else lines.size
        return lines.subList(start, end).joinToString("\n")
    }
}
