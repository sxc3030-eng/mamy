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
