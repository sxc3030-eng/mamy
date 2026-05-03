package com.mamy.android.data.llm.openai

import com.mamy.android.data.llm.LlmProvider
import com.mamy.android.data.llm.LlmProviderId
import com.mamy.android.data.llm.LlmRequest
import com.mamy.android.data.llm.LlmResponse
import com.mamy.android.data.llm.claude.ParseFailedException
import com.mamy.android.data.llm.StructuredNoteParser
import com.mamy.android.data.secrets.SecretsVault
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
