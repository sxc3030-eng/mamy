# MamY P3 — LLM Structurer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement BYOK LLM abstraction (Claude + OpenAI + Gemini), structuration of voice transcripts into typed JSON saved to the local DB, cost tracking per provider, and TTS confirmation. After P3, a full debrief flow works end-to-end : wake-word → audio → STT → LLM → structured note in DB → vocal confirmation.

**Architecture:** Sealed interface `LlmProvider` decouples app from concrete LLM. Each provider impl handles its API quirks (auth header, request schema, response parsing) but exposes a unified `structure(text)` returning `StructuredNote`. Cost tracker logs every call. Capture flow : transcript → IntentRouter (CAPTURE) → LlmStructurer → DAOs → TTS confirmation.

**Tech Stack:** Kotlin 2.0.21 · OkHttp 4.12 · kotlinx.serialization 1.7 · Hilt 2.52 · Coroutines 1.9 · Android TextToSpeech (native)
---

## Pre-flight

### Assumptions about P1 (Foundation) and P2 (Voice Capture)

P1 ships :
- `MamYDatabase` (Room + SQLCipher) with entities + DAOs : `PersonDao`, `NoteDao`, `ActionDao`, `PromiseDao`, `FlagDao`
- `data/secrets/SecretsVault.kt` with API : `suspend fun getKey(provider: String): String?` and `suspend fun setKey(provider: String, key: String)` (Android Keystore-backed)
- `data/settings/SettingsRepository.kt` exposing `Flow<Settings>` with field `llmProvider: String` (one of `"claude"`, `"openai"`, `"gemini"`) and `uiLanguage: String` (`"fr"` | `"en"`)
- Hilt setup with `@HiltAndroidApp` + `data/db/DatabaseModule.kt` + `data/secrets/SecretsModule.kt`
- TypeConverter for `Instant` ↔ `Long` already wired
- `util/Lang.kt` enum `Lang { FR, EN }` with helper `fun fromTag(tag: String): Lang`
- `util/Result.kt` typealias if not std `Result<T>`

P2 ships :
- `domain/intent/Intent.kt` enum + `domain/intent/IntentRouter.kt` with `fun route(text: String): Intent`
- `domain/capture/CaptureSession.kt` exposing `Flow<TranscriptResult>` where `TranscriptResult(text: String, language: Lang, durationSec: Int)` is emitted at end of each capture
- Service `MamYListenerService` with injected `CaptureSessionPipeline` interface

### Dependencies to add (`gradle/libs.versions.toml`)

```toml
[versions]
okhttp = "4.12.0"
serialization = "1.7.3"

[libraries]
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "okhttp" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }

[plugins]
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

In `app/build.gradle.kts` :
```kotlin
plugins {
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.okhttp.mockwebserver)
}
```

---

## Task 1: kotlinx.serialization setup + StructuredNote data classes

- [ ] **Add dependencies + plugin**

  Edit `gradle/libs.versions.toml` to include `okhttp`, `okhttp-mockwebserver`, `kotlinx-serialization-json` and the `kotlin-serialization` plugin (versions above).

  Edit `app/build.gradle.kts` to apply `alias(libs.plugins.kotlin.serialization)` in plugins block and add the three deps.

- [ ] **Write failing test** : `app/src/test/kotlin/com/mamy/android/data/llm/model/StructuredNoteSerializationTest.kt`

```kotlin
package com.mamy.android.data.llm.model

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StructuredNoteSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `decodes a complete StructuredNote payload`() {
        val payload = """
            {
              "persons": [
                {"name":"Marie","role_hint":"team lead","emotional_state":"stressed","context_added":"projet X livrable vendredi"}
              ],
              "actions": [
                {"description":"parler à David","assignee":"self","deadline":null,"linked_person":"Marie"}
              ],
              "promises": [
                {"from":"self","to":"Marie","what":"30 min CV review","due":"2026-05-08T17:00:00Z"}
              ],
              "flags": [
                {"person":"Pierre","type":"demotivation","source":"indirect:Marie","severity":"medium","note":"traîne sur mockup"}
              ],
              "meeting_meta": {
                "person_main":"Marie",
                "date_inferred":"2026-05-02T10:30:00Z"
              }
            }
        """.trimIndent()

        val note = json.decodeFromString(StructuredNote.serializer(), payload)

        assertEquals(1, note.persons.size)
        assertEquals("Marie", note.persons[0].name)
        assertEquals(EmotionalState.STRESSED, note.persons[0].emotionalState)
        assertEquals(1, note.actions.size)
        assertEquals("self", note.actions[0].assignee)
        assertNull(note.actions[0].deadline)
        assertEquals("Marie", note.promises[0].to)
        assertEquals(FlagType.DEMOTIVATION, note.flags[0].type)
        assertEquals(Severity.MEDIUM, note.flags[0].severity)
        assertEquals("Marie", note.meetingMeta.personMain)
    }

    @Test
    fun `decodes empty arrays gracefully`() {
        val payload = """{"persons":[],"actions":[],"promises":[],"flags":[],"meeting_meta":{"person_main":null,"date_inferred":null}}"""

        val note = json.decodeFromString(StructuredNote.serializer(), payload)

        assertEquals(0, note.persons.size)
        assertNull(note.meetingMeta.personMain)
    }
}
```

- [ ] **Run test** : `./gradlew :app:testDebugUnitTest --tests "com.mamy.android.data.llm.model.StructuredNoteSerializationTest"` → **expect FAIL** (classes don't exist).

- [ ] **Implement** : `app/src/main/kotlin/com/mamy/android/data/llm/model/StructuredNote.kt`

```kotlin
package com.mamy.android.data.llm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class EmotionalState {
    @SerialName("ok") OK,
    @SerialName("stressed") STRESSED,
    @SerialName("demotivated") DEMOTIVATED,
    @SerialName("happy") HAPPY,
    @SerialName("conflict") CONFLICT,
    @SerialName("engaged") ENGAGED,
    @SerialName("disengaged") DISENGAGED,
}

@Serializable
enum class FlagType {
    @SerialName("demotivation") DEMOTIVATION,
    @SerialName("conflict") CONFLICT,
    @SerialName("risk") RISK,
    @SerialName("opportunity") OPPORTUNITY,
    @SerialName("burnout") BURNOUT,
    @SerialName("growth") GROWTH,
}

@Serializable
enum class Severity {
    @SerialName("low") LOW,
    @SerialName("medium") MEDIUM,
    @SerialName("high") HIGH,
}

@Serializable
data class StructuredPerson(
    val name: String,
    @SerialName("role_hint") val roleHint: String? = null,
    @SerialName("emotional_state") val emotionalState: EmotionalState = EmotionalState.OK,
    @SerialName("context_added") val contextAdded: String = "",
)

@Serializable
data class StructuredAction(
    val description: String,
    val assignee: String,
    val deadline: String? = null,            // ISO8601 string, parsed by caller
    @SerialName("linked_person") val linkedPerson: String? = null,
)

@Serializable
data class StructuredPromise(
    val from: String,
    val to: String,
    val what: String,
    val due: String? = null,                 // ISO8601 string
)

@Serializable
data class StructuredFlag(
    val person: String,
    val type: FlagType,
    val source: String,                      // "direct" or "indirect:<name>"
    val severity: Severity = Severity.MEDIUM,
    val note: String = "",
)

@Serializable
data class MeetingMeta(
    @SerialName("person_main") val personMain: String? = null,
    @SerialName("date_inferred") val dateInferred: String? = null,
)

@Serializable
data class StructuredNote(
    val persons: List<StructuredPerson> = emptyList(),
    val actions: List<StructuredAction> = emptyList(),
    val promises: List<StructuredPromise> = emptyList(),
    val flags: List<StructuredFlag> = emptyList(),
    @SerialName("meeting_meta") val meetingMeta: MeetingMeta = MeetingMeta(),
)
```

- [ ] **Run test** : `./gradlew :app:testDebugUnitTest --tests "com.mamy.android.data.llm.model.StructuredNoteSerializationTest"` → **expect PASS**.

- [ ] **Commit** : `feat: add StructuredNote model with kotlinx.serialization`

---

## Task 2: JSON parser with malformed-input fallback

- [ ] **Write failing test** : `app/src/test/kotlin/com/mamy/android/data/llm/StructuredNoteParserTest.kt`

```kotlin
package com.mamy.android.data.llm

import com.mamy.android.data.llm.model.FlagType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StructuredNoteParserTest {

    private val parser = StructuredNoteParser()

    @Test
    fun `parses valid JSON`() {
        val raw = """
            {"persons":[{"name":"Marie","emotional_state":"happy","context_added":""}],
             "actions":[],"promises":[],"flags":[],
             "meeting_meta":{"person_main":"Marie","date_inferred":null}}
        """.trimIndent()

        val parsed = parser.parse(raw)

        assertEquals("Marie", parsed?.persons?.firstOrNull()?.name)
    }

    @Test
    fun `strips markdown code fences before parsing`() {
        val raw = """
            ```json
            {"persons":[],"actions":[],"promises":[],"flags":[{"person":"Pierre","type":"risk","source":"direct"}],"meeting_meta":{"person_main":null,"date_inferred":null}}
            ```
        """.trimIndent()

        val parsed = parser.parse(raw)

        assertEquals(FlagType.RISK, parsed?.flags?.firstOrNull()?.type)
    }

    @Test
    fun `returns null on malformed JSON`() {
        assertNull(parser.parse("this is not json"))
        assertNull(parser.parse(""))
        assertNull(parser.parse("{not closed"))
    }

    @Test
    fun `returns null on missing required fields`() {
        // "name" is required on StructuredPerson
        val raw = """{"persons":[{"role_hint":"x"}],"actions":[],"promises":[],"flags":[],"meeting_meta":{}}"""
        assertNull(parser.parse(raw))
    }
}
```

- [ ] **Run test** → **expect FAIL**.

- [ ] **Implement** : `app/src/main/kotlin/com/mamy/android/data/llm/StructuredNoteParser.kt`

```kotlin
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
```

- [ ] **Run test** → **expect PASS**.

- [ ] **Commit** : `feat: add StructuredNoteParser with markdown-fence stripping and malformed-input fallback`

---

## Task 3: LlmProvider sealed interface + LlmRequest/LlmResponse data classes

- [ ] **Write failing test** : `app/src/test/kotlin/com/mamy/android/data/llm/LlmProviderContractTest.kt`

```kotlin
package com.mamy.android.data.llm

import com.mamy.android.data.llm.model.StructuredNote
import com.mamy.android.util.Lang
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LlmProviderContractTest {

    private val fake = object : LlmProvider {
        override val id = "fake"
        override val displayName = "Fake"
        override suspend fun structure(req: LlmRequest): Result<LlmResponse> {
            return Result.success(
                LlmResponse(
                    note = StructuredNote(),
                    rawText = "{}",
                    tokensIn = 100,
                    tokensOut = 20,
                )
            )
        }
        override suspend fun testKey(): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun `provider returns a parsed StructuredNote and token usage`() = runTest {
        val req = LlmRequest(
            transcript = "hello",
            language = Lang.EN,
            systemPrompt = "test",
        )

        val resp = fake.structure(req).getOrThrow()

        assertEquals(100, resp.tokensIn)
        assertEquals(20, resp.tokensOut)
        assertTrue(resp.note.persons.isEmpty())
    }
}
```

- [ ] **Run test** → **expect FAIL**.

- [ ] **Implement** : `app/src/main/kotlin/com/mamy/android/data/llm/LlmProvider.kt`

```kotlin
package com.mamy.android.data.llm

import com.mamy.android.data.llm.model.StructuredNote
import com.mamy.android.util.Lang

/**
 * Identifies a BYOK provider. Used as DB column value and DataStore key.
 */
object LlmProviderId {
    const val CLAUDE = "claude"
    const val OPENAI = "openai"
    const val GEMINI = "gemini"
}

data class LlmRequest(
    val transcript: String,
    val language: Lang,
    val systemPrompt: String,
)

data class LlmResponse(
    val note: StructuredNote,
    val rawText: String,
    val tokensIn: Int,
    val tokensOut: Int,
)

/**
 * BYOK LLM provider abstraction. Each impl handles auth + request shape + response parsing.
 *
 * Implementations are stateless and resolve their API key on each call via [SecretsVault].
 */
interface LlmProvider {
    val id: String                 // matches LlmProviderId.*
    val displayName: String

    suspend fun structure(req: LlmRequest): Result<LlmResponse>

    /** 1-token call to verify the API key works. ~$0.0001. */
    suspend fun testKey(): Result<Unit>
}
```

- [ ] **Run test** → **expect PASS**.

- [ ] **Commit** : `feat: add LlmProvider interface with LlmRequest/LlmResponse model`

---

## Task 4: ClaudeProvider impl

- [ ] **Write failing test** : `app/src/test/kotlin/com/mamy/android/data/llm/claude/ClaudeProviderTest.kt`

```kotlin
package com.mamy.android.data.llm.claude

import com.mamy.android.data.llm.LlmRequest
import com.mamy.android.data.secrets.SecretsVault
import com.mamy.android.util.Lang
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ClaudeProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var vault: SecretsVault
    private lateinit var provider: ClaudeProvider

    @BeforeEach
    fun setup() {
        server = MockWebServer().also { it.start() }
        vault = mockk()
        coEvery { vault.getKey("claude") } returns "test-key-claude-1234"
        provider = ClaudeProvider(
            client = OkHttpClient(),
            vault = vault,
            baseUrl = server.url("/").toString().trimEnd('/'),
        )
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `structure sends correct headers and body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """
            {
              "content":[{"type":"text","text":"{\"persons\":[],\"actions\":[],\"promises\":[],\"flags\":[],\"meeting_meta\":{\"person_main\":null,\"date_inferred\":null}}"}],
              "usage":{"input_tokens":150,"output_tokens":42}
            }
            """.trimIndent()
        ))

        val req = LlmRequest(transcript = "Marie va mieux", language = Lang.FR, systemPrompt = "system")
        val resp = provider.structure(req).getOrThrow()

        assertEquals(150, resp.tokensIn)
        assertEquals(42, resp.tokensOut)
        assertEquals(0, resp.note.persons.size)

        val recorded = server.takeRequest()
        assertEquals("/v1/messages", recorded.path)
        assertEquals("test-key-claude-1234", recorded.getHeader("x-api-key"))
        assertEquals("2023-06-01", recorded.getHeader("anthropic-version"))
        assertEquals("application/json", recorded.getHeader("content-type"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"model\":\"claude-3-5-haiku-20241022\""))
        assertTrue(body.contains("Marie va mieux"))
        assertTrue(body.contains("\"max_tokens\":1024"))
    }

    @Test
    fun `returns failure when API key missing`() = runTest {
        coEvery { vault.getKey("claude") } returns null
        val req = LlmRequest(transcript = "x", language = Lang.EN, systemPrompt = "s")
        val result = provider.structure(req)
        assertTrue(result.isFailure)
        assertEquals("Claude API key not set", result.exceptionOrNull()?.message)
    }

    @Test
    fun `returns failure on 401`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"invalid"}"""))
        val req = LlmRequest(transcript = "x", language = Lang.EN, systemPrompt = "s")
        val result = provider.structure(req)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("401") == true)
    }

    @Test
    fun `testKey makes minimal call`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"content":[{"type":"text","text":"ok"}],"usage":{"input_tokens":1,"output_tokens":1}}"""
        ))
        val result = provider.testKey()
        assertTrue(result.isSuccess)
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"max_tokens\":1"))
    }
}
```

- [ ] **Run test** → **expect FAIL**.

- [ ] **Implement** : `app/src/main/kotlin/com/mamy/android/data/llm/claude/ClaudeProvider.kt`

```kotlin
package com.mamy.android.data.llm.claude

import com.mamy.android.data.llm.LlmProvider
import com.mamy.android.data.llm.LlmProviderId
import com.mamy.android.data.llm.LlmRequest
import com.mamy.android.data.llm.LlmResponse
import com.mamy.android.data.llm.StructuredNoteParser
import com.mamy.android.data.secrets.SecretsVault
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class ClaudeProvider @Inject constructor(
    private val client: OkHttpClient,
    private val vault: SecretsVault,
    private val parser: StructuredNoteParser = StructuredNoteParser(),
    private val baseUrl: String = "https://api.anthropic.com",
) : LlmProvider {

    override val id = LlmProviderId.CLAUDE
    override val displayName = "Anthropic Claude"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonMedia = "application/json".toMediaType()

    override suspend fun structure(req: LlmRequest): Result<LlmResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val key = vault.getKey(LlmProviderId.CLAUDE)
                ?: throw IllegalStateException("Claude API key not set")

            val body = buildRequestBody(
                model = MODEL,
                maxTokens = 1024,
                system = req.systemPrompt,
                userText = req.transcript,
            )

            val resp = client.newCall(
                Request.Builder()
                    .url("$baseUrl/v1/messages")
                    .header("x-api-key", key)
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .post(body.toString().toRequestBody(jsonMedia))
                    .build()
            ).execute()

            resp.use { r ->
                if (!r.isSuccessful) {
                    throw IllegalStateException("Claude API HTTP ${r.code}: ${r.body?.string().orEmpty().take(200)}")
                }
                val payload = json.parseToJsonElement(r.body!!.string()).jsonObject
                val text = payload["content"]?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                    ?: throw IllegalStateException("Claude API: missing content[0].text")
                val usage = payload["usage"]?.jsonObject
                val tokensIn = usage?.get("input_tokens")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val tokensOut = usage?.get("output_tokens")?.jsonPrimitive?.content?.toIntOrNull() ?: 0

                val parsed = parser.parse(text) ?: throw ParseFailedException(text)
                LlmResponse(note = parsed, rawText = text, tokensIn = tokensIn, tokensOut = tokensOut)
            }
        }
    }

    override suspend fun testKey(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val key = vault.getKey(LlmProviderId.CLAUDE)
                ?: throw IllegalStateException("Claude API key not set")

            val body = buildRequestBody(
                model = MODEL,
                maxTokens = 1,
                system = "Reply with exactly: ok",
                userText = "ping",
            )

            client.newCall(
                Request.Builder()
                    .url("$baseUrl/v1/messages")
                    .header("x-api-key", key)
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .post(body.toString().toRequestBody(jsonMedia))
                    .build()
            ).execute().use { r ->
                if (!r.isSuccessful) {
                    throw IllegalStateException("Claude key test failed: HTTP ${r.code}")
                }
            }
        }
    }

    private fun buildRequestBody(
        model: String,
        maxTokens: Int,
        system: String,
        userText: String,
    ): JsonObject = buildJsonObject {
        put("model", model)
        put("max_tokens", maxTokens)
        put("system", system)
        put("messages", buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("content", userText)
            })
        })
    }

    companion object {
        const val MODEL = "claude-3-5-haiku-20241022"
    }
}

class ParseFailedException(val rawText: String) : RuntimeException("LLM returned non-JSON or malformed JSON")
```

- [ ] **Run test** → **expect PASS**.

- [ ] **Commit** : `feat: add ClaudeProvider with MockWebServer-tested /v1/messages flow`

---

## Task 5: OpenAIProvider impl

- [ ] **Write failing test** : `app/src/test/kotlin/com/mamy/android/data/llm/openai/OpenAIProviderTest.kt`

```kotlin
package com.mamy.android.data.llm.openai

import com.mamy.android.data.llm.LlmRequest
import com.mamy.android.data.secrets.SecretsVault
import com.mamy.android.util.Lang
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OpenAIProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var vault: SecretsVault
    private lateinit var provider: OpenAIProvider

    @BeforeEach
    fun setup() {
        server = MockWebServer().also { it.start() }
        vault = mockk()
        coEvery { vault.getKey("openai") } returns "sk-test-openai-1234"
        provider = OpenAIProvider(
            client = OkHttpClient(),
            vault = vault,
            baseUrl = server.url("/").toString().trimEnd('/'),
        )
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `structure posts correct payload`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """
            {
              "choices":[{
                "message":{"role":"assistant","content":"{\"persons\":[],\"actions\":[],\"promises\":[],\"flags\":[],\"meeting_meta\":{\"person_main\":null,\"date_inferred\":null}}"},
                "finish_reason":"stop"
              }],
              "usage":{"prompt_tokens":120,"completion_tokens":30,"total_tokens":150}
            }
            """.trimIndent()
        ))

        val req = LlmRequest(transcript = "Pierre est démotivé", language = Lang.FR, systemPrompt = "system")
        val resp = provider.structure(req).getOrThrow()

        assertEquals(120, resp.tokensIn)
        assertEquals(30, resp.tokensOut)

        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer sk-test-openai-1234", recorded.getHeader("authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"model\":\"gpt-4o-mini\""))
        assertTrue(body.contains("\"response_format\":{\"type\":\"json_object\"}"))
        assertTrue(body.contains("Pierre est démotivé") || body.contains("Pierre est d\\u00e9motiv\\u00e9"))
        assertTrue(body.contains("\"role\":\"system\""))
        assertTrue(body.contains("\"role\":\"user\""))
    }

    @Test
    fun `returns failure when key missing`() = runTest {
        coEvery { vault.getKey("openai") } returns null
        val result = provider.structure(LlmRequest("x", Lang.EN, "s"))
        assertTrue(result.isFailure)
    }

    @Test
    fun `returns failure on 429 rate limit`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("rate limited"))
        val result = provider.structure(LlmRequest("x", Lang.EN, "s"))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("429") == true)
    }
}
```

- [ ] **Run test** → **expect FAIL**.

- [ ] **Implement** : `app/src/main/kotlin/com/mamy/android/data/llm/openai/OpenAIProvider.kt`

```kotlin
package com.mamy.android.data.llm.openai

import com.mamy.android.data.llm.LlmProvider
import com.mamy.android.data.llm.LlmProviderId
import com.mamy.android.data.llm.LlmRequest
import com.mamy.android.data.llm.LlmResponse
import com.mamy.android.data.llm.ParseFailedException
import com.mamy.android.data.llm.StructuredNoteParser
import com.mamy.android.data.secrets.SecretsVault
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class OpenAIProvider @Inject constructor(
    private val client: OkHttpClient,
    private val vault: SecretsVault,
    private val parser: StructuredNoteParser = StructuredNoteParser(),
    private val baseUrl: String = "https://api.openai.com",
) : LlmProvider {

    override val id = LlmProviderId.OPENAI
    override val displayName = "OpenAI GPT-4o"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonMedia = "application/json".toMediaType()

    override suspend fun structure(req: LlmRequest): Result<LlmResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val key = vault.getKey(LlmProviderId.OPENAI)
                ?: throw IllegalStateException("OpenAI API key not set")

            val body = buildJsonObject {
                put("model", MODEL)
                put("response_format", buildJsonObject { put("type", "json_object") })
                put("temperature", 0.0)
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", req.systemPrompt)
                    })
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", req.transcript)
                    })
                })
            }

            client.newCall(
                Request.Builder()
                    .url("$baseUrl/v1/chat/completions")
                    .header("authorization", "Bearer $key")
                    .header("content-type", "application/json")
                    .post(body.toString().toRequestBody(jsonMedia))
                    .build()
            ).execute().use { r ->
                if (!r.isSuccessful) {
                    throw IllegalStateException("OpenAI API HTTP ${r.code}: ${r.body?.string().orEmpty().take(200)}")
                }
                val payload = json.parseToJsonElement(r.body!!.string()).jsonObject
                val content = payload["choices"]?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")
                    ?.jsonPrimitive?.content
                    ?: throw IllegalStateException("OpenAI API: missing choices[0].message.content")
                val usage = payload["usage"]?.jsonObject
                val tokensIn = usage?.get("prompt_tokens")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val tokensOut = usage?.get("completion_tokens")?.jsonPrimitive?.content?.toIntOrNull() ?: 0

                val parsed = parser.parse(content) ?: throw ParseFailedException(content)
                LlmResponse(note = parsed, rawText = content, tokensIn = tokensIn, tokensOut = tokensOut)
            }
        }
    }

    override suspend fun testKey(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val key = vault.getKey(LlmProviderId.OPENAI)
                ?: throw IllegalStateException("OpenAI API key not set")

            val body = buildJsonObject {
                put("model", MODEL)
                put("max_tokens", 1)
                put("messages", buildJsonArray {
                    add(buildJsonObject { put("role", "user"); put("content", "ping") })
                })
            }

            client.newCall(
                Request.Builder()
                    .url("$baseUrl/v1/chat/completions")
                    .header("authorization", "Bearer $key")
                    .header("content-type", "application/json")
                    .post(body.toString().toRequestBody(jsonMedia))
                    .build()
            ).execute().use { r ->
                if (!r.isSuccessful) throw IllegalStateException("OpenAI key test failed: HTTP ${r.code}")
            }
        }
    }

    companion object {
        const val MODEL = "gpt-4o-mini"
    }
}
```

- [ ] **Run test** → **expect PASS**.

- [ ] **Commit** : `feat: add OpenAIProvider with response_format json_object`

---

## Task 6: GeminiProvider stub (V1.1)

- [ ] **Write failing test** : `app/src/test/kotlin/com/mamy/android/data/llm/gemini/GeminiProviderTest.kt`

```kotlin
package com.mamy.android.data.llm.gemini

import com.mamy.android.data.llm.LlmRequest
import com.mamy.android.util.Lang
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeminiProviderTest {

    private val provider = GeminiProvider()

    @Test
    fun `structure returns NotImplementedV1 failure`() = runTest {
        val r = provider.structure(LlmRequest("x", Lang.EN, "s"))
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull() is GeminiNotImplementedException)
    }

    @Test
    fun `testKey returns failure`() = runTest {
        val r = provider.testKey()
        assertTrue(r.isFailure)
    }

    @Test
    fun `id and displayName are stable`() {
        assertEquals("gemini", provider.id)
        assertEquals("Google Gemini", provider.displayName)
    }
}
```

- [ ] **Run test** → **expect FAIL**.

- [ ] **Implement** : `app/src/main/kotlin/com/mamy/android/data/llm/gemini/GeminiProvider.kt`

```kotlin
package com.mamy.android.data.llm.gemini

import com.mamy.android.data.llm.LlmProvider
import com.mamy.android.data.llm.LlmProviderId
import com.mamy.android.data.llm.LlmRequest
import com.mamy.android.data.llm.LlmResponse
import javax.inject.Inject
import javax.inject.Singleton

class GeminiNotImplementedException :
    UnsupportedOperationException("Gemini provider arrives in MamY V1.1.")

@Singleton
class GeminiProvider @Inject constructor() : LlmProvider {

    override val id = LlmProviderId.GEMINI
    override val displayName = "Google Gemini"

    override suspend fun structure(req: LlmRequest): Result<LlmResponse> =
        Result.failure(GeminiNotImplementedException())

    override suspend fun testKey(): Result<Unit> =
        Result.failure(GeminiNotImplementedException())
}
```

- [ ] **Run test** → **expect PASS**.

- [ ] **Commit** : `feat: add GeminiProvider stub for V1.1`

---

## Task 7: ProviderFactory chooses provider from settings

- [ ] **Write failing test** : `app/src/test/kotlin/com/mamy/android/data/llm/LlmProviderFactoryTest.kt`

```kotlin
package com.mamy.android.data.llm

import com.mamy.android.data.llm.claude.ClaudeProvider
import com.mamy.android.data.llm.gemini.GeminiProvider
import com.mamy.android.data.llm.openai.OpenAIProvider
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class LlmProviderFactoryTest {

    private val claude = mockk<ClaudeProvider>(relaxed = true) { /* id default */ }
    private val openai = mockk<OpenAIProvider>(relaxed = true)
    private val gemini = mockk<GeminiProvider>(relaxed = true)

    private val factory = LlmProviderFactory(
        claude = lazy { claude.also { io.mockk.every { it.id } returns "claude" } },
        openai = lazy { openai.also { io.mockk.every { it.id } returns "openai" } },
        gemini = lazy { gemini.also { io.mockk.every { it.id } returns "gemini" } },
    )

    @Test
    fun `selects claude provider`() {
        assertSame(claude, factory.byId("claude"))
    }

    @Test
    fun `selects openai provider`() {
        assertSame(openai, factory.byId("openai"))
    }

    @Test
    fun `selects gemini provider`() {
        assertSame(gemini, factory.byId("gemini"))
    }

    @Test
    fun `throws on unknown id`() {
        val ex = assertThrows(IllegalArgumentException::class.java) { factory.byId("local") }
        assertEquals("Unknown LLM provider id: local", ex.message)
    }

    @Test
    fun `lists providers in stable order`() {
        val ids = factory.all().map { it.id }
        assertEquals(listOf("claude", "openai", "gemini"), ids)
    }
}
```

- [ ] **Run test** → **expect FAIL**.

- [ ] **Implement** : `app/src/main/kotlin/com/mamy/android/data/llm/LlmProviderFactory.kt`

```kotlin
package com.mamy.android.data.llm

import com.mamy.android.data.llm.claude.ClaudeProvider
import com.mamy.android.data.llm.gemini.GeminiProvider
import com.mamy.android.data.llm.openai.OpenAIProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmProviderFactory @Inject constructor(
    private val claude: Lazy<ClaudeProvider>,
    private val openai: Lazy<OpenAIProvider>,
    private val gemini: Lazy<GeminiProvider>,
) {
    fun byId(id: String): LlmProvider = when (id) {
        LlmProviderId.CLAUDE -> claude.value
        LlmProviderId.OPENAI -> openai.value
        LlmProviderId.GEMINI -> gemini.value
        else -> throw IllegalArgumentException("Unknown LLM provider id: $id")
    }

    fun all(): List<LlmProvider> = listOf(claude.value, openai.value, gemini.value)
}
```

- [ ] **Run test** → **expect PASS**.

- [ ] **Commit** : `feat: add LlmProviderFactory for runtime selection`

---

## Task 8: Prompt builder (FR + EN)

- [ ] **Write failing test** : `app/src/test/kotlin/com/mamy/android/domain/capture/PromptBuilderTest.kt`

```kotlin
package com.mamy.android.domain.capture

import com.mamy.android.util.Lang
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PromptBuilderTest {

    private val builder = PromptBuilder()

    @Test
    fun `FR prompt mentions french keywords`() {
        val p = builder.systemPrompt(Lang.FR)
        assertTrue(p.contains("manager"))
        assertTrue(p.contains("debrief vocal"))
        assertTrue(p.contains("JSON strict"))
        assertTrue(p.contains("emotional_state"))
        assertTrue(p.contains("meeting_meta"))
        assertTrue(p.contains("FR ou EN"))
    }

    @Test
    fun `EN prompt mentions english keywords`() {
        val p = builder.systemPrompt(Lang.EN)
        assertTrue(p.contains("team manager"))
        assertTrue(p.contains("voice debrief"))
        assertTrue(p.contains("strict JSON"))
        assertTrue(p.contains("FR or EN"))
    }

    @Test
    fun `both languages include the same JSON keys`() {
        val fr = builder.systemPrompt(Lang.FR)
        val en = builder.systemPrompt(Lang.EN)
        listOf("persons", "actions", "promises", "flags", "meeting_meta",
               "role_hint", "emotional_state", "deadline", "linked_person",
               "person_main", "date_inferred").forEach { key ->
            assertTrue(fr.contains(key), "FR prompt missing $key")
            assertTrue(en.contains(key), "EN prompt missing $key")
        }
    }
}
```

- [ ] **Run test** → **expect FAIL**.

- [ ] **Implement** : `app/src/main/kotlin/com/mamy/android/domain/capture/PromptBuilder.kt`

```kotlin
package com.mamy.android.domain.capture

import com.mamy.android.util.Lang
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptBuilder @Inject constructor() {

    fun systemPrompt(language: Lang): String = when (language) {
        Lang.FR -> PROMPT_FR
        Lang.EN -> PROMPT_EN
    }

    companion object {
        private val PROMPT_FR = """
            Tu es l'assistant secrétaire d'un manager d'équipe 30-100 personnes. Tu reçois
            un debrief vocal libre post-meeting (FR ou EN). Extrait en JSON strict :

            - persons : nom, role_hint, emotional_state (ok|stressed|demotivated|happy|conflict|engaged|disengaged), context_added
            - actions : description, assignee (self ou nom), deadline ISO8601 ou null, linked_person
            - promises : from, to, what, due
            - flags : person, type (demotivation|conflict|risk|opportunity|burnout|growth), source (direct|indirect:X), severity, note
            - meeting_meta : person_main (avec qui était le 1:1, déduit), date_inferred

            Si une info est ambiguë, mets null plutôt que d'inventer. Réponds JSON brut, sans markdown.
        """.trimIndent()

        private val PROMPT_EN = """
            You are the secretary-assistant of a team manager (30-100 reports). You receive
            a free-form voice debrief from a post-meeting moment (FR or EN). Extract strict JSON:

            - persons: name, role_hint, emotional_state (ok|stressed|demotivated|happy|conflict|engaged|disengaged), context_added
            - actions: description, assignee (self or name), deadline ISO8601 or null, linked_person
            - promises: from, to, what, due
            - flags: person, type (demotivation|conflict|risk|opportunity|burnout|growth), source (direct|indirect:X), severity, note
            - meeting_meta: person_main (whom the 1:1 was with, inferred), date_inferred

            If anything is ambiguous, put null rather than inventing. Reply with raw JSON, no markdown.
        """.trimIndent()
    }
}
```

- [ ] **Run test** → **expect PASS**.

- [ ] **Commit** : `feat: add PromptBuilder with FR + EN system prompts`

---

## Task 9: LlmCostTracker — Room entity + DAO + repository

- [ ] **Write failing test** : `app/src/androidTest/kotlin/com/mamy/android/data/llm/cost/LlmCostDaoInstrumentedTest.kt`

```kotlin
package com.mamy.android.data.llm.cost

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LlmCostDaoInstrumentedTest {

    private lateinit var db: LlmCostTestDatabase
    private lateinit var dao: LlmCostDao

    @Before
    fun setup() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, LlmCostTestDatabase::class.java).build()
        dao = db.llmCostDao()
    }

    @After
    fun teardown() { db.close() }

    @Test
    fun aggregatesByProviderAndMonth() = runTest {
        val ym = YearMonth.of(2026, 5)
        val day1 = ym.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        val day15 = ym.atDay(15).atStartOfDay().toInstant(ZoneOffset.UTC)

        dao.insert(LlmCostEntry(provider = "claude", tokensIn = 1000, tokensOut = 500, costMicroCents = 4_000, createdAt = day1))
        dao.insert(LlmCostEntry(provider = "claude", tokensIn = 2000, tokensOut = 100, costMicroCents = 2_400, createdAt = day15))
        dao.insert(LlmCostEntry(provider = "openai", tokensIn = 500, tokensOut = 200, costMicroCents = 8_000, createdAt = day15))

        val rows = dao.aggregateForMonth(
            from = ym.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            to = ym.atEndOfMonth().atTime(23, 59, 59).toInstant(ZoneOffset.UTC).toEpochMilli(),
        ).first()

        assertEquals(2, rows.size)
        val claude = rows.first { it.provider == "claude" }
        assertEquals(3000, claude.tokensIn)
        assertEquals(600, claude.tokensOut)
        assertEquals(6_400L, claude.costMicroCents)
        val openai = rows.first { it.provider == "openai" }
        assertEquals(8_000L, openai.costMicroCents)
    }
}
```

Plus a tiny test-only DB (`app/src/androidTest/kotlin/com/mamy/android/data/llm/cost/LlmCostTestDatabase.kt`) :

```kotlin
package com.mamy.android.data.llm.cost

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mamy.android.data.db.converter.InstantConverter

@Database(entities = [LlmCostEntry::class], version = 1, exportSchema = false)
@TypeConverters(InstantConverter::class)
abstract class LlmCostTestDatabase : RoomDatabase() {
    abstract fun llmCostDao(): LlmCostDao
}
```

- [ ] **Run test** : `./gradlew :app:connectedDebugAndroidTest --tests "com.mamy.android.data.llm.cost.LlmCostDaoInstrumentedTest"` → **expect FAIL**.

- [ ] **Implement** entity : `app/src/main/kotlin/com/mamy/android/data/llm/cost/LlmCostEntry.kt`

```kotlin
package com.mamy.android.data.llm.cost

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "llm_cost")
data class LlmCostEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val provider: String,                 // matches LlmProviderId.*
    val tokensIn: Int,
    val tokensOut: Int,
    val costMicroCents: Long,             // 1 USD = 100_000_000 microcents (precision)
    val createdAt: Instant,
)
```

- [ ] **Implement** DAO : `app/src/main/kotlin/com/mamy/android/data/llm/cost/LlmCostDao.kt`

```kotlin
package com.mamy.android.data.llm.cost

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class CostAggregate(
    val provider: String,
    val tokensIn: Int,
    val tokensOut: Int,
    val costMicroCents: Long,
)

@Dao
interface LlmCostDao {

    @Insert
    suspend fun insert(entry: LlmCostEntry): Long

    @Query("""
        SELECT provider AS provider,
               SUM(tokensIn) AS tokensIn,
               SUM(tokensOut) AS tokensOut,
               SUM(costMicroCents) AS costMicroCents
        FROM llm_cost
        WHERE createdAt BETWEEN :from AND :to
        GROUP BY provider
        ORDER BY provider ASC
    """)
    fun aggregateForMonth(from: Long, to: Long): Flow<List<CostAggregate>>

    @Query("DELETE FROM llm_cost") suspend fun clear()
}
```

(`InstantConverter` from P1 must already exist — entity uses it via the Database `@TypeConverters` annotation.)

- [ ] **Wire into MamYDatabase** (P1) : add `LlmCostEntry::class` to `entities = [...]` and bump DB version + provide a destructive migration for now (V1).

  Append in `app/src/main/kotlin/com/mamy/android/data/db/MamYDatabase.kt` :

  ```kotlin
  abstract fun llmCostDao(): LlmCostDao
  ```

  Add `LlmCostEntry::class` to entities array, increment `version = 2`, set `fallbackToDestructiveMigration()` in builder.

- [ ] **Run test** → **expect PASS**.

- [ ] **Commit** : `feat: add LlmCostEntry + LlmCostDao with monthly aggregate query`

---

## Task 10: Cost calculator per provider

- [ ] **Write failing test** : `app/src/test/kotlin/com/mamy/android/data/llm/cost/LlmCostCalculatorTest.kt`

```kotlin
package com.mamy.android.data.llm.cost

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LlmCostCalculatorTest {

    private val calc = LlmCostCalculator()

    @Test
    fun `claude haiku 1k in 500 out`() {
        // claude-3-5-haiku: $1.00/M input, $5.00/M output
        // 1000 in = $0.001 = 1000 microcents · 500 out = $0.0025 = 2500 microcents
        assertEquals(3500L, calc.microCents("claude", tokensIn = 1000, tokensOut = 500))
    }

    @Test
    fun `openai gpt-4o-mini`() {
        // gpt-4o-mini: $0.15/M input, $0.60/M output
        // 1000 in = $0.00015 = 150 microcents · 500 out = $0.0003 = 300 microcents
        assertEquals(450L, calc.microCents("openai", tokensIn = 1000, tokensOut = 500))
    }

    @Test
    fun `gemini returns 0 for stub`() {
        assertEquals(0L, calc.microCents("gemini", tokensIn = 1000, tokensOut = 500))
    }

    @Test
    fun `unknown provider returns 0`() {
        assertEquals(0L, calc.microCents("local", tokensIn = 1000, tokensOut = 500))
    }

    @Test
    fun `formats microcents to dollars`() {
        assertEquals("$0.0035", calc.formatUsd(3500))
        assertEquals("$1.23", calc.formatUsd(1_230_000))
        assertEquals("$0.00", calc.formatUsd(0))
    }
}
```

- [ ] **Run test** → **expect FAIL**.

- [ ] **Implement** : `app/src/main/kotlin/com/mamy/android/data/llm/cost/LlmCostCalculator.kt`

```kotlin
package com.mamy.android.data.llm.cost

import com.mamy.android.data.llm.LlmProviderId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-provider token pricing. Stored as microcents-per-million-tokens to avoid floats.
 *
 *   1 USD = 100 cents = 100_000_000 microcents
 *   $1.00/M tokens   = 100_000_000 microcents per 1M tokens = 100 microcents per token
 */
@Singleton
class LlmCostCalculator @Inject constructor() {

    fun microCents(providerId: String, tokensIn: Int, tokensOut: Int): Long {
        val rates = when (providerId) {
            LlmProviderId.CLAUDE -> Rates(inMicroPerM = 100_000_000, outMicroPerM = 500_000_000) // $1.00 / $5.00
            LlmProviderId.OPENAI -> Rates(inMicroPerM = 15_000_000, outMicroPerM = 60_000_000)  // $0.15 / $0.60
            LlmProviderId.GEMINI -> Rates(inMicroPerM = 0, outMicroPerM = 0)
            else -> Rates(0, 0)
        }
        val inCost = (tokensIn.toLong() * rates.inMicroPerM) / 1_000_000L
        val outCost = (tokensOut.toLong() * rates.outMicroPerM) / 1_000_000L
        return inCost + outCost
    }

    /** Formats microcents to display dollars. Picks 2 or 4 decimals based on size. */
    fun formatUsd(microCents: Long): String {
        val cents = microCents / 10_000L                 // microcents → cents (truncate)
        val microRemainder = microCents % 10_000L
        val dollars = cents / 100
        val centRemainder = cents % 100
        return if (microCents in 1L..9_999L) {
            // sub-cent : show 4 decimals
            val total = microCents.toDouble() / 1_000_000.0
            String.format("$%.4f", total)
        } else {
            String.format("$%d.%02d", dollars, centRemainder)
        }
    }

    private data class Rates(val inMicroPerM: Long, val outMicroPerM: Long)
}
```

- [ ] **Run test** → **expect PASS**.

- [ ] **Commit** : `feat: add LlmCostCalculator with claude+openai pricing`

---

## Task 11: LlmCostTracker repository + Flow<MonthlyCost>

- [ ] **Write failing test** : `app/src/test/kotlin/com/mamy/android/data/llm/cost/LlmCostTrackerTest.kt`

```kotlin
package com.mamy.android.data.llm.cost

import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LlmCostTrackerTest {

    @Test
    fun `record inserts entry with computed cost`() = runTest {
        val dao = mockk<LlmCostDao>(relaxed = true)
        val fixedNow = Instant.parse("2026-05-15T12:00:00Z")
        val tracker = LlmCostTracker(
            dao = dao,
            calculator = LlmCostCalculator(),
            clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
        )

        tracker.record(provider = "claude", tokensIn = 1000, tokensOut = 500)

        val captured = slot<LlmCostEntry>()
        coVerify { dao.insert(capture(captured)) }
        assertEquals("claude", captured.captured.provider)
        assertEquals(1000, captured.captured.tokensIn)
        assertEquals(500, captured.captured.tokensOut)
        assertEquals(3500L, captured.captured.costMicroCents)
        assertEquals(fixedNow, captured.captured.createdAt)
    }
}
```

- [ ] **Run test** → **expect FAIL**.

- [ ] **Implement** : `app/src/main/kotlin/com/mamy/android/data/llm/cost/LlmCostTracker.kt`

```kotlin
package com.mamy.android.data.llm.cost

import java.time.Clock
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

data class MonthlyCost(
    val provider: String,
    val tokensIn: Int,
    val tokensOut: Int,
    val microCents: Long,
)

@Singleton
class LlmCostTracker @Inject constructor(
    private val dao: LlmCostDao,
    private val calculator: LlmCostCalculator,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    suspend fun record(provider: String, tokensIn: Int, tokensOut: Int) {
        val cost = calculator.microCents(provider, tokensIn, tokensOut)
        dao.insert(
            LlmCostEntry(
                provider = provider,
                tokensIn = tokensIn,
                tokensOut = tokensOut,
                costMicroCents = cost,
                createdAt = clock.instant(),
            )
        )
    }

    fun monthlyCosts(yearMonth: YearMonth = YearMonth.now(clock)): Flow<List<MonthlyCost>> {
        val zone = ZoneId.systemDefault()
        val from = yearMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = yearMonth.atEndOfMonth().atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
        return kotlinx.coroutines.flow.map(dao.aggregateForMonth(from, to)) { rows ->
            rows.map {
                MonthlyCost(
                    provider = it.provider,
                    tokensIn = it.tokensIn,
                    tokensOut = it.tokensOut,
                    microCents = it.costMicroCents,
                )
            }
        }
    }

    private fun <T, R> kotlinx.coroutines.flow.map(
        upstream: Flow<T>,
        transform: suspend (T) -> R,
    ): Flow<R> = kotlinx.coroutines.flow.flow {
        upstream.collect { emit(transform(it)) }
    }
}
```

- [ ] **Run test** → **expect PASS**.

- [ ] **Commit** : `feat: add LlmCostTracker with monthly Flow aggregator`

---

## Task 12: LlmStructurer orchestrator

- [ ] **Write failing test** : `app/src/test/kotlin/com/mamy/android/domain/capture/LlmStructurerTest.kt`

```kotlin
package com.mamy.android.domain.capture

import com.mamy.android.data.llm.LlmProvider
import com.mamy.android.data.llm.LlmProviderFactory
import com.mamy.android.data.llm.LlmRequest
import com.mamy.android.data.llm.LlmResponse
import com.mamy.android.data.llm.ParseFailedException
import com.mamy.android.data.llm.cost.LlmCostTracker
import com.mamy.android.data.llm.model.StructuredNote
import com.mamy.android.data.settings.Settings
import com.mamy.android.data.settings.SettingsRepository
import com.mamy.android.util.Lang
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LlmStructurerTest {

    private val provider = mockk<LlmProvider>()
    private val factory = mockk<LlmProviderFactory> {
        every { byId("claude") } returns provider
    }
    private val settings = mockk<SettingsRepository> {
        every { stream() } returns flowOf(Settings(llmProvider = "claude", uiLanguage = "fr"))
    }
    private val tracker = mockk<LlmCostTracker>(relaxed = true)
    private val builder = PromptBuilder()

    private val structurer = LlmStructurer(
        factory = factory,
        settings = settings,
        tracker = tracker,
        promptBuilder = builder,
    )

    @Test
    fun `happy path returns structured note and records cost`() = runTest {
        coEvery { provider.id } returns "claude"
        coEvery { provider.structure(any()) } returns Result.success(
            LlmResponse(
                note = StructuredNote(),
                rawText = "{}",
                tokensIn = 100,
                tokensOut = 30,
            )
        )

        val outcome = structurer.structure("Marie va mieux", Lang.FR)

        assertTrue(outcome is StructureOutcome.Success)
        assertEquals("{}", (outcome as StructureOutcome.Success).rawText)
        coVerify { tracker.record("claude", 100, 30) }
    }

    @Test
    fun `parse failure path returns RawFallback (still records cost)`() = runTest {
        coEvery { provider.id } returns "claude"
        coEvery { provider.structure(any()) } returns Result.failure(ParseFailedException("not json"))

        val outcome = structurer.structure("foo", Lang.FR)

        assertTrue(outcome is StructureOutcome.RawFallback)
        assertEquals("not json", (outcome as StructureOutcome.RawFallback).rawText)
        // No cost recorded because we don't have token counts on parse failure
        coVerify(exactly = 0) { tracker.record(any(), any(), any()) }
    }

    @Test
    fun `network failure returns Failure`() = runTest {
        coEvery { provider.structure(any()) } returns Result.failure(IllegalStateException("Claude API HTTP 500"))

        val outcome = structurer.structure("x", Lang.EN)

        assertTrue(outcome is StructureOutcome.Failure)
        assertEquals("Claude API HTTP 500", (outcome as StructureOutcome.Failure).message)
    }

    @Test
    fun `selects EN prompt when UI lang is en`() = runTest {
        every { settings.stream() } returns flowOf(Settings(llmProvider = "claude", uiLanguage = "en"))
        coEvery { provider.structure(any()) } answers {
            val req = firstArg<LlmRequest>()
            assertTrue(req.systemPrompt.contains("team manager"))
            Result.success(LlmResponse(StructuredNote(), "{}", 1, 1))
        }

        structurer.structure("hello", Lang.EN)
    }
}
```

- [ ] **Run test** → **expect FAIL**.

- [ ] **Implement** : `app/src/main/kotlin/com/mamy/android/domain/capture/LlmStructurer.kt`

```kotlin
package com.mamy.android.domain.capture

import com.mamy.android.data.llm.LlmProviderFactory
import com.mamy.android.data.llm.LlmRequest
import com.mamy.android.data.llm.ParseFailedException
import com.mamy.android.data.llm.cost.LlmCostTracker
import com.mamy.android.data.llm.model.StructuredNote
import com.mamy.android.data.settings.SettingsRepository
import com.mamy.android.util.Lang
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

sealed interface StructureOutcome {
    data class Success(
        val note: StructuredNote,
        val rawText: String,
        val providerId: String,
        val tokensIn: Int,
        val tokensOut: Int,
    ) : StructureOutcome

    /** Provider replied but JSON parse failed. Persist raw text with `non_structured=true`. */
    data class RawFallback(val rawText: String, val providerId: String) : StructureOutcome

    /** Network/auth/quota error. Capture should not be saved. */
    data class Failure(val message: String) : StructureOutcome
}

@Singleton
class LlmStructurer @Inject constructor(
    private val factory: LlmProviderFactory,
    private val settings: SettingsRepository,
    private val tracker: LlmCostTracker,
    private val promptBuilder: PromptBuilder,
) {

    suspend fun structure(transcript: String, language: Lang): StructureOutcome {
        val current = settings.stream().first()
        val provider = factory.byId(current.llmProvider)
        val req = LlmRequest(
            transcript = transcript,
            language = language,
            systemPrompt = promptBuilder.systemPrompt(language),
        )

        val result = provider.structure(req)

        return result.fold(
            onSuccess = { resp ->
                tracker.record(provider.id, resp.tokensIn, resp.tokensOut)
                StructureOutcome.Success(
                    note = resp.note,
                    rawText = resp.rawText,
                    providerId = provider.id,
                    tokensIn = resp.tokensIn,
                    tokensOut = resp.tokensOut,
                )
            },
            onFailure = { ex ->
                if (ex is ParseFailedException) {
                    StructureOutcome.RawFallback(rawText = ex.rawText, providerId = provider.id)
                } else {
                    StructureOutcome.Failure(message = ex.message ?: "Unknown error")
                }
            },
        )
    }
}
```

- [ ] **Run test** → **expect PASS**.

- [ ] **Commit** : `feat: add LlmStructurer with success/raw-fallback/failure outcomes`

---

## Task 13: NoteWriter — persists outcome to DAOs

- [ ] **Write failing test** : `app/src/test/kotlin/com/mamy/android/domain/capture/NoteWriterTest.kt`

```kotlin
package com.mamy.android.domain.capture

import com.mamy.android.data.db.dao.ActionDao
import com.mamy.android.data.db.dao.FlagDao
import com.mamy.android.data.db.dao.NoteDao
import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.dao.PromiseDao
import com.mamy.android.data.db.entity.ActionEntity
import com.mamy.android.data.db.entity.FlagEntity
import com.mamy.android.data.db.entity.NoteEntity
import com.mamy.android.data.db.entity.PersonEntity
import com.mamy.android.data.db.entity.PromiseEntity
import com.mamy.android.data.llm.cost.LlmCostCalculator
import com.mamy.android.data.llm.model.EmotionalState
import com.mamy.android.data.llm.model.FlagType
import com.mamy.android.data.llm.model.MeetingMeta
import com.mamy.android.data.llm.model.Severity
import com.mamy.android.data.llm.model.StructuredAction
import com.mamy.android.data.llm.model.StructuredFlag
import com.mamy.android.data.llm.model.StructuredNote
import com.mamy.android.data.llm.model.StructuredPerson
import com.mamy.android.data.llm.model.StructuredPromise
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NoteWriterTest {

    private val now = Instant.parse("2026-05-02T11:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val personDao = mockk<PersonDao>(relaxed = true)
    private val noteDao = mockk<NoteDao>(relaxed = true)
    private val actionDao = mockk<ActionDao>(relaxed = true)
    private val promiseDao = mockk<PromiseDao>(relaxed = true)
    private val flagDao = mockk<FlagDao>(relaxed = true)
    private val calculator = LlmCostCalculator()

    private val writer = NoteWriter(
        personDao = personDao,
        noteDao = noteDao,
        actionDao = actionDao,
        promiseDao = promiseDao,
        flagDao = flagDao,
        calculator = calculator,
        clock = clock,
    )

    @Test
    fun `Success outcome creates Person Note Action Promise Flag rows`() = runTest {
        val marieId = UUID.randomUUID()
        coEvery { personDao.findByName("Marie") } returns null andThen PersonEntity(
            id = marieId, name = "Marie", createdAt = now, interactionCount = 1, unmatched = true
        )
        coEvery { personDao.insert(any()) } answers {
            val captured = firstArg<PersonEntity>()
            captured.id
        }

        val note = StructuredNote(
            persons = listOf(
                StructuredPerson(name = "Marie", emotionalState = EmotionalState.STRESSED, contextAdded = "projet X")
            ),
            actions = listOf(
                StructuredAction(description = "parler à David", assignee = "self", linkedPerson = "Marie")
            ),
            promises = listOf(
                StructuredPromise(from = "self", to = "Marie", what = "30 min CV review")
            ),
            flags = listOf(
                StructuredFlag(person = "Marie", type = FlagType.RISK, source = "direct", severity = Severity.LOW, note = "watch")
            ),
            meetingMeta = MeetingMeta(personMain = "Marie"),
        )

        val outcome = StructureOutcome.Success(
            note = note,
            rawText = "{}",
            providerId = "claude",
            tokensIn = 1000,
            tokensOut = 500,
        )

        val noteId = writer.write(outcome, transcript = "raw text", durationSec = 45)

        coVerify { personDao.insert(any()) }                          // Marie creation
        val noteSlot = slot<NoteEntity>()
        coVerify { noteDao.insert(capture(noteSlot)) }
        assertEquals("raw text", noteSlot.captured.rawText)
        assertEquals("claude", noteSlot.captured.llmProvider)
        assertEquals(false, noteSlot.captured.nonStructured)
        // 1000 in + 500 out @ claude = 3500 microcents = 0 cents stored (truncated)
        assertEquals(0, noteSlot.captured.llmCostCents)

        val actionSlot = slot<ActionEntity>()
        coVerify { actionDao.insert(capture(actionSlot)) }
        assertEquals("parler à David", actionSlot.captured.description)
        assertEquals("self", actionSlot.captured.assignee)
        assertEquals("open", actionSlot.captured.status)

        coVerify { promiseDao.insert(any()) }
        coVerify { flagDao.insert(any()) }

        assertTrue(noteId.toString().isNotEmpty())
    }

    @Test
    fun `RawFallback outcome creates Note with non_structured=true and no children`() = runTest {
        val outcome = StructureOutcome.RawFallback(rawText = "garbage from llm", providerId = "claude")

        val noteId = writer.write(outcome, transcript = "uh", durationSec = 10)

        val noteSlot = slot<NoteEntity>()
        coVerify { noteDao.insert(capture(noteSlot)) }
        assertEquals(true, noteSlot.captured.nonStructured)
        assertEquals("uh", noteSlot.captured.rawText)
        coVerify(exactly = 0) { actionDao.insert(any()) }
        coVerify(exactly = 0) { promiseDao.insert(any()) }
        coVerify(exactly = 0) { flagDao.insert(any()) }
    }

    @Test
    fun `Failure outcome writes nothing and returns null`() = runTest {
        val outcome = StructureOutcome.Failure(message = "no network")

        val noteId = writer.write(outcome, transcript = "x", durationSec = 5)

        org.junit.jupiter.api.Assertions.assertNull(noteId)
        coVerify(exactly = 0) { noteDao.insert(any()) }
    }
}
```

- [ ] **Run test** → **expect FAIL**.

- [ ] **Implement** : `app/src/main/kotlin/com/mamy/android/domain/capture/NoteWriter.kt`

```kotlin
package com.mamy.android.domain.capture

import com.mamy.android.data.db.dao.ActionDao
import com.mamy.android.data.db.dao.FlagDao
import com.mamy.android.data.db.dao.NoteDao
import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.dao.PromiseDao
import com.mamy.android.data.db.entity.ActionEntity
import com.mamy.android.data.db.entity.FlagEntity
import com.mamy.android.data.db.entity.NoteEntity
import com.mamy.android.data.db.entity.PersonEntity
import com.mamy.android.data.db.entity.PromiseEntity
import com.mamy.android.data.llm.cost.LlmCostCalculator
import com.mamy.android.data.llm.model.StructuredNote
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists a [StructureOutcome] to the local DB. Returns the inserted Note id, or null on Failure.
 *
 * For Success : creates/updates persons by name, inserts Note + child rows.
 * For RawFallback : inserts Note with `nonStructured=true`, no children.
 * For Failure : writes nothing.
 */
@Singleton
class NoteWriter @Inject constructor(
    private val personDao: PersonDao,
    private val noteDao: NoteDao,
    private val actionDao: ActionDao,
    private val promiseDao: PromiseDao,
    private val flagDao: FlagDao,
    private val calculator: LlmCostCalculator,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    suspend fun write(outcome: StructureOutcome, transcript: String, durationSec: Int): UUID? {
        return when (outcome) {
            is StructureOutcome.Success -> writeSuccess(outcome, transcript, durationSec)
            is StructureOutcome.RawFallback -> writeRaw(outcome, transcript, durationSec)
            is StructureOutcome.Failure -> null
        }
    }

    private suspend fun writeSuccess(o: StructureOutcome.Success, transcript: String, durationSec: Int): UUID {
        val now = clock.instant()
        val note = o.note

        // 1. Resolve / create persons by name
        val personIds = mutableMapOf<String, UUID>()
        for (p in note.persons) {
            personIds[p.name] = upsertPerson(p.name, p.roleHint, now)
        }
        // Also resolve named assignees / linkedPerson / promise from-to / flag person
        val extraNames = buildList {
            note.actions.forEach {
                if (it.assignee != "self") add(it.assignee)
                it.linkedPerson?.let(::add)
            }
            note.promises.forEach {
                if (it.from != "self") add(it.from)
                if (it.to != "self") add(it.to)
            }
            note.flags.forEach { add(it.person) }
            note.meetingMeta.personMain?.let(::add)
        }.distinct()
        for (name in extraNames) {
            if (!personIds.containsKey(name)) personIds[name] = upsertPerson(name, null, now)
        }

        val mainPersonId = note.meetingMeta.personMain?.let { personIds[it] }

        // 2. Insert Note
        val noteId = UUID.randomUUID()
        val costMicro = calculator.microCents(o.providerId, o.tokensIn, o.tokensOut)
        noteDao.insert(NoteEntity(
            id = noteId,
            personId = mainPersonId,
            meetingId = null,
            rawText = transcript,
            structuredJson = o.rawText,
            nonStructured = false,
            createdAt = now,
            audioDurationSec = durationSec,
            llmProvider = o.providerId,
            llmCostCents = (costMicro / 10_000L).toInt(),
        ))

        // 3. Insert Actions
        for (a in note.actions) {
            actionDao.insert(ActionEntity(
                id = UUID.randomUUID(),
                description = a.description,
                assignee = a.assignee,
                linkedPersonId = a.linkedPerson?.let(personIds::get),
                deadline = parseInstant(a.deadline),
                status = "open",
                fromNoteId = noteId,
                createdAt = now,
                doneAt = null,
            ))
        }

        // 4. Insert Promises
        for (p in note.promises) {
            promiseDao.insert(PromiseEntity(
                id = UUID.randomUUID(),
                fromId = if (p.from == "self") "self" else (personIds[p.from]?.toString() ?: p.from),
                toId = if (p.to == "self") "self" else (personIds[p.to]?.toString() ?: p.to),
                what = p.what,
                due = parseInstant(p.due),
                status = "active",
                fromNoteId = noteId,
                createdAt = now,
                resolvedAt = null,
            ))
        }

        // 5. Insert Flags
        for (f in note.flags) {
            val pid = personIds[f.person] ?: upsertPerson(f.person, null, now).also { personIds[f.person] = it }
            flagDao.insert(FlagEntity(
                id = UUID.randomUUID(),
                personId = pid,
                type = f.type.name.lowercase(),
                source = f.source,
                severity = f.severity.name.lowercase(),
                note = f.note,
                resolved = false,
                fromNoteId = noteId,
                createdAt = now,
            ))
        }

        return noteId
    }

    private suspend fun writeRaw(o: StructureOutcome.RawFallback, transcript: String, durationSec: Int): UUID {
        val noteId = UUID.randomUUID()
        noteDao.insert(NoteEntity(
            id = noteId,
            personId = null,
            meetingId = null,
            rawText = transcript,
            structuredJson = o.rawText,
            nonStructured = true,
            createdAt = clock.instant(),
            audioDurationSec = durationSec,
            llmProvider = o.providerId,
            llmCostCents = null,
        ))
        return noteId
    }

    private suspend fun upsertPerson(name: String, roleHint: String?, now: Instant): UUID {
        val existing = personDao.findByName(name)
        if (existing != null) return existing.id
        val id = UUID.randomUUID()
        personDao.insert(PersonEntity(
            id = id,
            name = name,
            email = null,
            roleHint = roleHint,
            calendarAttendeeId = null,
            createdAt = now,
            lastInteractionAt = now,
            interactionCount = 1,
            emotionalTrend = null,
            unmatched = true,
            archived = false,
        ))
        return id
    }

    private fun parseInstant(iso: String?): Instant? =
        iso?.let { runCatching { Instant.parse(it) }.getOrNull() }
}
```

> **Note for implementer:** P1 must ship `PersonDao.findByName(name: String): PersonEntity?` and `PersonDao.insert(p: PersonEntity): Long` (or unit-returning, signature must match calls above). Same for the four other DAOs (`insert(...)` per entity). If P1 ships different signatures, adapt this writer accordingly — these calls are intentionally minimal so adaptation is one line per DAO.

- [ ] **Run test** → **expect PASS**.

- [ ] **Commit** : `feat: add NoteWriter persisting StructureOutcome to DAOs`

---

## Task 14: TtsConfirmer — Android TextToSpeech wrapper

- [ ] **Write failing test** (Robolectric, JVM-side): `app/src/test/kotlin/com/mamy/android/data/tts/TtsConfirmerTest.kt`

```kotlin
package com.mamy.android.data.tts

import com.mamy.android.data.llm.model.StructuredNote
import com.mamy.android.data.llm.model.StructuredFlag
import com.mamy.android.data.llm.model.FlagType
import com.mamy.android.data.llm.model.StructuredAction
import com.mamy.android.util.Lang
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TtsConfirmerTextTest {

    private val builder = TtsConfirmer.MessageBuilder()

    @Test
    fun `FR singular forms`() {
        val note = StructuredNote(
            actions = listOf(StructuredAction("x", "self")),
            flags = listOf(StructuredFlag(person = "Marie", type = FlagType.RISK, source = "direct")),
        )
        assertEquals("Noté. 1 action, 1 personne flaggée.", builder.confirmation(note, Lang.FR))
    }

    @Test
    fun `FR plural forms`() {
        val note = StructuredNote(
            actions = listOf(StructuredAction("x", "self"), StructuredAction("y", "self")),
            flags = listOf(
                StructuredFlag("a", FlagType.RISK, "direct"),
                StructuredFlag("b", FlagType.RISK, "direct"),
                StructuredFlag("c", FlagType.RISK, "direct"),
            ),
        )
        assertEquals("Noté. 2 actions, 3 personnes flaggées.", builder.confirmation(note, Lang.FR))
    }

    @Test
    fun `EN singular forms`() {
        val note = StructuredNote(
            actions = listOf(StructuredAction("x", "self")),
            flags = listOf(StructuredFlag("M", FlagType.RISK, "direct")),
        )
        assertEquals("Noted. 1 action, 1 person flagged.", builder.confirmation(note, Lang.EN))
    }

    @Test
    fun `EN plural forms`() {
        val note = StructuredNote(
            actions = listOf(StructuredAction("x", "self"), StructuredAction("y", "self")),
            flags = listOf(
                StructuredFlag("a", FlagType.RISK, "direct"),
                StructuredFlag("b", FlagType.RISK, "direct"),
            ),
        )
        assertEquals("Noted. 2 actions, 2 people flagged.", builder.confirmation(note, Lang.EN))
    }

    @Test
    fun `zero items`() {
        val note = StructuredNote()
        assertEquals("Noté. 0 action, 0 personne flaggée.", builder.confirmation(note, Lang.FR))
        assertEquals("Noted. 0 actions, 0 people flagged.", builder.confirmation(note, Lang.EN))
    }
}
```

- [ ] **Run test** → **expect FAIL**.

- [ ] **Implement** : `app/src/main/kotlin/com/mamy/android/data/tts/TtsConfirmer.kt`

```kotlin
package com.mamy.android.data.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import com.mamy.android.data.llm.model.StructuredNote
import com.mamy.android.util.Lang
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Singleton
class TtsConfirmer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val builder: MessageBuilder = MessageBuilder(),
) {

    private var tts: TextToSpeech? = null

    suspend fun confirm(note: StructuredNote, language: Lang) {
        val message = builder.confirmation(note, language)
        ensureInitialized(language)
        tts?.language = if (language == Lang.FR) Locale.FRENCH else Locale.ENGLISH
        tts?.speak(message, TextToSpeech.QUEUE_ADD, null, "mamy-confirm")
    }

    private suspend fun ensureInitialized(language: Lang) {
        if (tts != null) return
        tts = suspendCancellableCoroutine { cont ->
            val engine = TextToSpeech(context) { status ->
                if (cont.isActive) {
                    cont.resume(if (status == TextToSpeech.SUCCESS) /* it */ tts!! else null!!)
                }
            }
            tts = engine
        }
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
    }

    /** Pure text builder — kept separate so it's unit-testable without a real TTS engine. */
    class MessageBuilder {
        fun confirmation(note: StructuredNote, language: Lang): String {
            val actions = note.actions.size
            val flags = note.flags.size
            return when (language) {
                Lang.FR -> {
                    val pers = if (flags <= 1) "personne flaggée" else "personnes flaggées"
                    val act = if (actions <= 1) "action" else "actions"
                    "Noté. $actions $act, $flags $pers."
                }
                Lang.EN -> {
                    val pers = if (flags == 1) "person flagged" else "people flagged"
                    val act = if (actions == 1) "action" else "actions"
                    "Noted. $actions $act, $flags $pers."
                }
            }
        }
    }
}
```

- [ ] **Run test** → **expect PASS**.

- [ ] **Commit** : `feat: add TtsConfirmer with FR/EN message builder + Android TextToSpeech wrapper`

---

## Task 15: CapturePipeline — wire IntentRouter → Structurer → NoteWriter → TTS

- [ ] **Write failing test** : `app/src/test/kotlin/com/mamy/android/domain/capture/CapturePipelineTest.kt`

```kotlin
package com.mamy.android.domain.capture

import com.mamy.android.data.llm.model.StructuredNote
import com.mamy.android.data.tts.TtsConfirmer
import com.mamy.android.domain.intent.Intent
import com.mamy.android.domain.intent.IntentRouter
import com.mamy.android.util.Lang
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class CapturePipelineTest {

    private val router = mockk<IntentRouter>()
    private val structurer = mockk<LlmStructurer>()
    private val writer = mockk<NoteWriter>()
    private val tts = mockk<TtsConfirmer>(relaxed = true)
    private val pipeline = CapturePipeline(router, structurer, writer, tts)

    @Test
    fun `CAPTURE intent runs structurer then writer then tts`() = runTest {
        val transcript = "Marie va mieux"
        val noteId = UUID.randomUUID()
        val outcome = StructureOutcome.Success(StructuredNote(), "{}", "claude", 100, 50)

        coEvery { router.route(transcript) } returns Intent.CAPTURE
        coEvery { structurer.structure(transcript, Lang.FR) } returns outcome
        coEvery { writer.write(outcome, transcript, 60) } returns noteId

        pipeline.handle(transcript = transcript, language = Lang.FR, durationSec = 60)

        coVerifyOrder {
            router.route(transcript)
            structurer.structure(transcript, Lang.FR)
            writer.write(outcome, transcript, 60)
            tts.confirm(any(), Lang.FR)
        }
    }

    @Test
    fun `non-CAPTURE intent skips structurer`() = runTest {
        coEvery { router.route("MamY ma journée") } returns Intent.DAILY_BRIEF

        pipeline.handle("MamY ma journée", Lang.FR, 5)

        coVerify(exactly = 0) { structurer.structure(any(), any()) }
        coVerify(exactly = 0) { writer.write(any(), any(), any()) }
    }

    @Test
    fun `Failure outcome triggers no TTS confirmation`() = runTest {
        val transcript = "x"
        val outcome = StructureOutcome.Failure("network down")

        coEvery { router.route(transcript) } returns Intent.CAPTURE
        coEvery { structurer.structure(transcript, Lang.EN) } returns outcome
        coEvery { writer.write(outcome, transcript, 30) } returns null

        pipeline.handle(transcript, Lang.EN, 30)

        coVerify(exactly = 0) { tts.confirm(any(), any()) }
    }
}
```

- [ ] **Run test** → **expect FAIL**.

- [ ] **Implement** : `app/src/main/kotlin/com/mamy/android/domain/capture/CapturePipeline.kt`

```kotlin
package com.mamy.android.domain.capture

import com.mamy.android.data.tts.TtsConfirmer
import com.mamy.android.domain.intent.Intent
import com.mamy.android.domain.intent.IntentRouter
import com.mamy.android.util.Lang
import javax.inject.Inject
import javax.inject.Singleton

/**
 * End-to-end orchestration of one captured transcript:
 * route → (if CAPTURE) structure → write → TTS confirm.
 *
 * Other intents (DAILY_BRIEF, NEXT_BRIEF, ...) are out of scope for P3 and
 * delegated to handlers that arrive in P5/P6.
 */
@Singleton
class CapturePipeline @Inject constructor(
    private val router: IntentRouter,
    private val structurer: LlmStructurer,
    private val writer: NoteWriter,
    private val tts: TtsConfirmer,
) {
    suspend fun handle(transcript: String, language: Lang, durationSec: Int) {
        val intent = router.route(transcript)
        if (intent != Intent.CAPTURE) return

        val outcome = structurer.structure(transcript, language)
        val noteId = writer.write(outcome, transcript, durationSec)

        if (outcome is StructureOutcome.Success || outcome is StructureOutcome.RawFallback) {
            val note = (outcome as? StructureOutcome.Success)?.note ?: com.mamy.android.data.llm.model.StructuredNote()
            tts.confirm(note, language)
        }
    }
}
```

- [ ] **Run test** → **expect PASS**.

- [ ] **Commit** : `feat: add CapturePipeline orchestrating intent->structure->write->tts`

---

## Task 16: End-to-end smoke test (LlmStructurer + NoteWriter + in-memory DB)

- [ ] **Write failing instrumented test** : `app/src/androidTest/kotlin/com/mamy/android/domain/capture/CaptureE2ETest.kt`

```kotlin
package com.mamy.android.domain.capture

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mamy.android.data.db.MamYDatabase
import com.mamy.android.data.llm.LlmProvider
import com.mamy.android.data.llm.LlmProviderFactory
import com.mamy.android.data.llm.LlmRequest
import com.mamy.android.data.llm.LlmResponse
import com.mamy.android.data.llm.cost.LlmCostCalculator
import com.mamy.android.data.llm.cost.LlmCostTracker
import com.mamy.android.data.llm.model.EmotionalState
import com.mamy.android.data.llm.model.FlagType
import com.mamy.android.data.llm.model.MeetingMeta
import com.mamy.android.data.llm.model.Severity
import com.mamy.android.data.llm.model.StructuredAction
import com.mamy.android.data.llm.model.StructuredFlag
import com.mamy.android.data.llm.model.StructuredNote
import com.mamy.android.data.llm.model.StructuredPerson
import com.mamy.android.data.settings.Settings
import com.mamy.android.data.settings.SettingsRepository
import com.mamy.android.util.Lang
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureE2ETest {

    private lateinit var db: MamYDatabase

    @Before
    fun setup() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, MamYDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun teardown() { db.close() }

    @Test
    fun fullFlow_capturedTranscript_yieldsRowsInDb() = runTest {
        val sample = StructuredNote(
            persons = listOf(
                StructuredPerson(name = "Marie", roleHint = "team lead", emotionalState = EmotionalState.STRESSED, contextAdded = "projet X")
            ),
            actions = listOf(
                StructuredAction(description = "parler à David", assignee = "self", linkedPerson = "Marie")
            ),
            flags = listOf(
                StructuredFlag(person = "Pierre", type = FlagType.DEMOTIVATION, source = "indirect:Marie", severity = Severity.MEDIUM, note = "traîne sur mockup")
            ),
            meetingMeta = MeetingMeta(personMain = "Marie"),
        )

        val provider = mockk<LlmProvider>()
        coEvery { provider.id } returns "claude"
        coEvery { provider.structure(any()) } returns Result.success(
            LlmResponse(note = sample, rawText = "raw", tokensIn = 200, tokensOut = 100)
        )
        val factory = mockk<LlmProviderFactory> { every { byId("claude") } returns provider }
        val settings = mockk<SettingsRepository> {
            every { stream() } returns flowOf(Settings(llmProvider = "claude", uiLanguage = "fr"))
        }
        val tracker = LlmCostTracker(db.llmCostDao(), LlmCostCalculator())
        val structurer = LlmStructurer(factory, settings, tracker, PromptBuilder())
        val writer = NoteWriter(
            personDao = db.personDao(),
            noteDao = db.noteDao(),
            actionDao = db.actionDao(),
            promiseDao = db.promiseDao(),
            flagDao = db.flagDao(),
            calculator = LlmCostCalculator(),
        )

        val outcome = structurer.structure("Marie va mieux, parler à David, Pierre traîne", Lang.FR)
        assertTrue(outcome is StructureOutcome.Success)
        val noteId = writer.write(outcome, transcript = "raw text", durationSec = 50)
        assertTrue(noteId != null)

        val people = db.personDao().getAll()
        assertEquals(2, people.size)                 // Marie + Pierre
        val notes = db.noteDao().getAll()
        assertEquals(1, notes.size)
        val actions = db.actionDao().getAll()
        assertEquals(1, actions.size)
        val flags = db.flagDao().getAll()
        assertEquals(1, flags.size)
    }
}
```

> **Implementer note**: this test assumes `PersonDao.getAll()`, `NoteDao.getAll()`, `ActionDao.getAll()`, `FlagDao.getAll()` exist (P1 likely already has `Flow<List<...>>` query methods — adapt to whatever P1 ships).

- [ ] **Run test** → **expect FAIL initially** (until task 13 complete).

- [ ] **Run test** after all wiring : `./gradlew :app:connectedDebugAndroidTest --tests "com.mamy.android.domain.capture.CaptureE2ETest"` → **expect PASS**.

- [ ] **Commit** : `test: add E2E smoke test for capture pipeline`

---

## Task 17: Settings UI hook — provider dropdown + key field + Test button

- [ ] **Write failing test** : `app/src/test/kotlin/com/mamy/android/ui/settings/LlmSettingsViewModelTest.kt`

```kotlin
package com.mamy.android.ui.settings

import app.cash.turbine.test
import com.mamy.android.data.llm.LlmProvider
import com.mamy.android.data.llm.LlmProviderFactory
import com.mamy.android.data.secrets.SecretsVault
import com.mamy.android.data.settings.Settings
import com.mamy.android.data.settings.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LlmSettingsViewModelTest {

    private val claude = mockk<LlmProvider>().also {
        every { it.id } returns "claude"; every { it.displayName } returns "Anthropic Claude"
    }
    private val factory = mockk<LlmProviderFactory> {
        every { byId("claude") } returns claude
        every { all() } returns listOf(claude)
    }
    private val vault = mockk<SecretsVault>(relaxed = true)
    private val settingsFlow = MutableStateFlow(Settings(llmProvider = "claude", uiLanguage = "fr"))
    private val settings = mockk<SettingsRepository>(relaxed = true) {
        every { stream() } returns settingsFlow
    }

    private val vm = LlmSettingsViewModel(factory, vault, settings)

    @Test
    fun `available providers exposed`() = runTest {
        assertEquals(listOf("claude"), vm.availableProviders().map { it.id })
    }

    @Test
    fun `saveKey persists via vault`() = runTest {
        vm.saveKey("claude", "sk-ant-test-1234")
        coVerify { vault.setKey("claude", "sk-ant-test-1234") }
    }

    @Test
    fun `testKey returns success flow update`() = runTest {
        coEvery { claude.testKey() } returns Result.success(Unit)

        vm.testKey("claude")

        vm.lastTestResult.test {
            val emitted = awaitItem()
            assertTrue(emitted is KeyTestResult.Success)
            assertEquals("claude", (emitted as KeyTestResult.Success).providerId)
        }
    }

    @Test
    fun `testKey failure surfaces error`() = runTest {
        coEvery { claude.testKey() } returns Result.failure(IllegalStateException("HTTP 401"))

        vm.testKey("claude")

        vm.lastTestResult.test {
            val emitted = awaitItem()
            assertTrue(emitted is KeyTestResult.Failure)
            assertEquals("HTTP 401", (emitted as KeyTestResult.Failure).message)
        }
    }
}
```

- [ ] **Run test** → **expect FAIL**.

- [ ] **Implement** : `app/src/main/kotlin/com/mamy/android/ui/settings/LlmSettingsViewModel.kt`

```kotlin
package com.mamy.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamy.android.data.llm.LlmProvider
import com.mamy.android.data.llm.LlmProviderFactory
import com.mamy.android.data.secrets.SecretsVault
import com.mamy.android.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface KeyTestResult {
    object Idle : KeyTestResult
    data class Pending(val providerId: String) : KeyTestResult
    data class Success(val providerId: String) : KeyTestResult
    data class Failure(val providerId: String, val message: String) : KeyTestResult
}

@HiltViewModel
class LlmSettingsViewModel @Inject constructor(
    private val factory: LlmProviderFactory,
    private val vault: SecretsVault,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _result = MutableStateFlow<KeyTestResult>(KeyTestResult.Idle)
    val lastTestResult: StateFlow<KeyTestResult> = _result.asStateFlow()

    fun availableProviders(): List<LlmProvider> = factory.all()

    fun saveKey(providerId: String, key: String) {
        viewModelScope.launch { vault.setKey(providerId, key) }
    }

    fun testKey(providerId: String) {
        viewModelScope.launch {
            _result.value = KeyTestResult.Pending(providerId)
            val provider = factory.byId(providerId)
            _result.value = provider.testKey().fold(
                onSuccess = { KeyTestResult.Success(providerId) },
                onFailure = { KeyTestResult.Failure(providerId, it.message ?: "Unknown error") },
            )
        }
    }

    fun selectProvider(providerId: String) {
        viewModelScope.launch { settings.setLlmProvider(providerId) }
    }
}
```

> **Implementer note**: P1's `SettingsRepository` must expose `suspend fun setLlmProvider(id: String)`. If the interface differs, adapt this one line.

- [ ] **Run test** → **expect PASS**.

- [ ] **Compose screen** : `app/src/main/kotlin/com/mamy/android/ui/settings/LlmSettingsScreen.kt`

```kotlin
package com.mamy.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmSettingsScreen(vm: LlmSettingsViewModel = hiltViewModel()) {
    val providers = remember { vm.availableProviders() }
    var selectedProvider by remember { mutableStateOf(providers.first().id) }
    var key by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val testResult by vm.lastTestResult.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            TextField(
                value = providers.first { it.id == selectedProvider }.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Provider") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            androidx.compose.material3.ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                providers.forEach { p ->
                    DropdownMenuItem(text = { Text(p.displayName) }, onClick = {
                        selectedProvider = p.id; expanded = false; vm.selectProvider(p.id)
                    })
                }
            }
        }

        OutlinedTextField(
            value = key, onValueChange = { key = it },
            label = { Text("API key") },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(onClick = { vm.saveKey(selectedProvider, key) }) { Text("Save key") }
        Button(onClick = { vm.testKey(selectedProvider) }) { Text("Test key") }

        Text(when (val r = testResult) {
            KeyTestResult.Idle -> ""
            is KeyTestResult.Pending -> "Testing ${r.providerId}..."
            is KeyTestResult.Success -> "Key OK for ${r.providerId}"
            is KeyTestResult.Failure -> "Key test failed: ${r.message}"
        })
    }
}
```

- [ ] **Commit** : `feat: add LlmSettingsScreen + ViewModel with provider dropdown / key save / test button`

---

## Task 18: Hilt DI module for LLM layer

- [ ] **Write failing test** (Hilt setup verification) : `app/src/androidTest/kotlin/com/mamy/android/di/LlmModuleInstrumentedTest.kt`

```kotlin
package com.mamy.android.di

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mamy.android.data.llm.LlmProviderFactory
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LlmModuleInstrumentedTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var factory: LlmProviderFactory

    @Test
    fun providerFactory_isInjectable() {
        hiltRule.inject()
        assertNotNull(factory)
        assertNotNull(factory.byId("claude"))
        assertNotNull(factory.byId("openai"))
        assertNotNull(factory.byId("gemini"))
    }
}
```

- [ ] **Run test** → **expect FAIL**.

- [ ] **Implement** : `app/src/main/kotlin/com/mamy/android/di/LlmModule.kt`

```kotlin
package com.mamy.android.di

import com.mamy.android.data.db.MamYDatabase
import com.mamy.android.data.llm.cost.LlmCostDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object LlmModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides
    fun provideLlmCostDao(db: MamYDatabase): LlmCostDao = db.llmCostDao()
}
```

> P1's `DatabaseModule` already provides `MamYDatabase`. Hilt resolves `ClaudeProvider`/`OpenAIProvider`/`GeminiProvider`/`LlmProviderFactory`/`LlmStructurer`/`NoteWriter`/`TtsConfirmer` via their `@Inject` constructors automatically.

- [ ] **Run test** → **expect PASS**.

- [ ] **Commit** : `feat: add LlmModule wiring OkHttp + LlmCostDao`

---

## Task 19: Service wiring — invoke CapturePipeline from MamYListenerService

- [ ] **Edit** `app/src/main/kotlin/com/mamy/android/service/MamYListenerService.kt`. The P2-shipped service exposes a callback or Flow when capture completes; wire `CapturePipeline.handle(...)` there.

  Insert :

```kotlin
@AndroidEntryPoint
class MamYListenerService : LifecycleService() {

    @Inject lateinit var pipeline: CapturePipeline
    @Inject lateinit var settingsRepo: SettingsRepository
    // ...P2-injected dependencies remain

    override fun onCreate() {
        super.onCreate()
        // P2 already starts wakeword/audio pipelines; here we observe its output.
        lifecycleScope.launch {
            captureSession.transcripts().collect { result ->
                val lang = Lang.fromTag(settingsRepo.stream().first().uiLanguage)
                pipeline.handle(
                    transcript = result.text,
                    language = lang,
                    durationSec = result.durationSec,
                )
            }
        }
    }
}
```

> **Implementer note**: `captureSession.transcripts(): Flow<TranscriptResult>` is the contract assumed from P2. Replace `captureSession` with whatever P2 named the field. The wiring is one block — adapt names as needed.

- [ ] **Run service smoke test** : start the app, trigger a fake transcript via debug `adb shell am broadcast` (P2 must expose this) and verify rows are written.

- [ ] **Commit** : `feat: wire CapturePipeline into MamYListenerService observing transcripts`

---

## Task 20: Cost-tracker UI hook (settings)

- [ ] **Write failing test** : `app/src/test/kotlin/com/mamy/android/ui/settings/CostViewModelTest.kt`

```kotlin
package com.mamy.android.ui.settings

import app.cash.turbine.test
import com.mamy.android.data.llm.cost.LlmCostCalculator
import com.mamy.android.data.llm.cost.LlmCostTracker
import com.mamy.android.data.llm.cost.MonthlyCost
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CostViewModelTest {

    @Test
    fun `formats monthly costs into display rows`() = runTest {
        val tracker = mockk<LlmCostTracker> {
            every { monthlyCosts(any()) } returns flowOf(
                listOf(
                    MonthlyCost(provider = "claude", tokensIn = 1000, tokensOut = 500, microCents = 3500),
                    MonthlyCost(provider = "openai", tokensIn = 2000, tokensOut = 800, microCents = 780),
                )
            )
        }
        val vm = CostViewModel(tracker, LlmCostCalculator())

        vm.rows.test {
            val items = awaitItem()
            assertEquals(2, items.size)
            assertEquals("Anthropic Claude", items[0].displayName)
            assertEquals("$0.0035", items[0].costDisplay)
            assertEquals("$0.0008", items[1].costDisplay)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Run test** → **expect FAIL**.

- [ ] **Implement** : `app/src/main/kotlin/com/mamy/android/ui/settings/CostViewModel.kt`

```kotlin
package com.mamy.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamy.android.data.llm.LlmProviderId
import com.mamy.android.data.llm.cost.LlmCostCalculator
import com.mamy.android.data.llm.cost.LlmCostTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CostRow(
    val providerId: String,
    val displayName: String,
    val tokensIn: Int,
    val tokensOut: Int,
    val costDisplay: String,
)

@HiltViewModel
class CostViewModel @Inject constructor(
    tracker: LlmCostTracker,
    calculator: LlmCostCalculator,
) : ViewModel() {

    val rows: StateFlow<List<CostRow>> = tracker.monthlyCosts()
        .map { list ->
            list.map { mc ->
                CostRow(
                    providerId = mc.provider,
                    displayName = displayNameFor(mc.provider),
                    tokensIn = mc.tokensIn,
                    tokensOut = mc.tokensOut,
                    costDisplay = calculator.formatUsd(mc.microCents),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun displayNameFor(id: String) = when (id) {
        LlmProviderId.CLAUDE -> "Anthropic Claude"
        LlmProviderId.OPENAI -> "OpenAI GPT-4o"
        LlmProviderId.GEMINI -> "Google Gemini"
        else -> id
    }
}
```

- [ ] **Run test** → **expect PASS**.

- [ ] **Compose snippet** in `LlmSettingsScreen.kt` (append at bottom) :

```kotlin
@Composable
fun MonthlyCostsSection(vm: CostViewModel = hiltViewModel()) {
    val rows by vm.rows.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Coût ce mois", style = MaterialTheme.typography.titleMedium)
        rows.forEach { r ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(r.displayName)
                Text(r.costDisplay)
            }
        }
    }
}
```

- [ ] **Commit** : `feat: add CostViewModel + MonthlyCostsSection in settings`

---

## Final verification

- [ ] **Run all unit tests** : `./gradlew :app:testDebugUnitTest`
- [ ] **Run all instrumented tests** : `./gradlew :app:connectedDebugAndroidTest`
- [ ] **Run lint** : `./gradlew :app:lintDebug`
- [ ] **Manual smoke test** :
  1. Build & install debug APK on Pixel emulator (API 35).
  2. Set Claude API key via Settings → Test key → expect "Key OK for claude".
  3. Trigger a fake transcript broadcast (P2 debug command).
  4. Verify TTS confirmation plays "Noté. N actions, M personnes flaggées." in FR.
  5. Open SQLite via `adb shell run-as com.mamy.android` → `sqlite3 databases/mamy.db` → check `Person`, `Note`, `Action`, `Flag`, `llm_cost` rows.

- [ ] **Final commit** : `chore(p3): close P3 — LLM Structurer pipeline ready end-to-end`

---

## Dependencies for next phases

- **P4 (Memory & queries)** consumes `Person`/`Note`/`Action`/`Promise`/`Flag` rows that P3 writes — schema is stable.
- **P5 (Calendar)** will add `Meeting` rows and call `NoteWriter` with a non-null `meetingId` (small extension).
- **P6 (Briefing)** will call providers via the same `LlmProviderFactory` and reuse `LlmCostTracker.record(...)`.
- **P7 (UI)** will build the full `ReportsListScreen` etc. on top of DAOs P3 already populates.
- **P8 (Settings + onboarding)** finalizes `LlmSettingsScreen` + `MonthlyCostsSection` styling.
