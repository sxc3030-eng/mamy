package com.mamy.android.data.llm.ollama

import com.mamy.android.data.llm.LlmProvider
import com.mamy.android.data.llm.LlmProviderId
import com.mamy.android.data.llm.LlmRequest
import com.mamy.android.data.llm.LlmResponse
import com.mamy.android.data.llm.LlmResult
import com.mamy.android.data.llm.StructuredNoteParser
import com.mamy.android.data.llm.claude.ParseFailedException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * LLM provider backed by a remote Ollama server (Mistral 7B Instruct by default).
 *
 * Unlike the BYOK providers (Claude/OpenAI/Gemini), this one does not require an
 * API key — auth is enforced by the network boundary (Cloudflare Tunnel in alpha).
 * `baseUrl` is supplied by [com.mamy.android.di.OllamaModule] from `BuildConfig`
 * with a `local.properties` override, and falls back to the bundled tunnel URL.
 */
@Singleton
class OllamaProvider(
    private val client: OkHttpClient,
    private val parser: StructuredNoteParser,
    private val baseUrl: String,
    private val model: String = DEFAULT_MODEL,
) : LlmProvider {

    @Inject constructor(
        client: OkHttpClient,
        parser: StructuredNoteParser,
        @com.mamy.android.di.OllamaBaseUrl baseUrl: String,
    ) : this(client, parser, baseUrl, DEFAULT_MODEL)

    override val id: String = LlmProviderId.OLLAMA
    override val displayName: String = "Ollama (local backend)"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonMedia = "application/json".toMediaType()

    override suspend fun structure(req: LlmRequest): Result<LlmResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildRequestBody(
                prompt = req.transcript,
                system = req.systemPrompt,
                forceJson = true,
            )

            client.newCall(
                Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/api/generate")
                    .header("content-type", "application/json")
                    .post(body.toString().toRequestBody(jsonMedia))
                    .build()
            ).execute().use { r ->
                if (!r.isSuccessful) {
                    throw IllegalStateException(
                        "Ollama HTTP ${r.code}: ${r.body?.string().orEmpty().take(200)}"
                    )
                }
                val payload = json.parseToJsonElement(r.body!!.string()).jsonObject
                val text = payload["response"]?.jsonPrimitive?.content
                    ?: throw IllegalStateException("Ollama: missing response field")
                val tokensIn = payload["prompt_eval_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val tokensOut = payload["eval_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0

                val parsed = parser.parse(text) ?: throw ParseFailedException(text)
                LlmResponse(note = parsed, rawText = text, tokensIn = tokensIn, tokensOut = tokensOut)
            }
        }
    }

    override suspend fun testKey(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(
                Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/api/tags")
                    .get()
                    .build()
            ).execute().use { r ->
                if (!r.isSuccessful) {
                    throw IllegalStateException("Ollama reachability test failed: HTTP ${r.code}")
                }
            }
        }
    }

    override suspend fun complete(systemPrompt: String, userPrompt: String, maxTokens: Int): LlmResult =
        withContext(Dispatchers.IO) {
            val body = buildRequestBody(
                prompt = userPrompt,
                system = systemPrompt,
                forceJson = false,
                maxTokens = maxTokens,
            )

            client.newCall(
                Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/api/generate")
                    .header("content-type", "application/json")
                    .post(body.toString().toRequestBody(jsonMedia))
                    .build()
            ).execute().use { r ->
                if (!r.isSuccessful) {
                    throw IllegalStateException(
                        "Ollama HTTP ${r.code}: ${r.body?.string().orEmpty().take(200)}"
                    )
                }
                val payload = json.parseToJsonElement(r.body!!.string()).jsonObject
                val text = payload["response"]?.jsonPrimitive?.content
                    ?: throw IllegalStateException("Ollama: missing response field")
                LlmResult(text = text, costCents = 0, providerName = LlmProviderId.OLLAMA)
            }
        }

    private fun buildRequestBody(
        prompt: String,
        system: String,
        forceJson: Boolean,
        maxTokens: Int? = null,
    ): JsonObject = buildJsonObject {
        put("model", model)
        put("prompt", prompt)
        put("system", system)
        put("stream", false)
        if (forceJson) put("format", "json")
        if (maxTokens != null) {
            put("options", buildJsonObject { put("num_predict", maxTokens) })
        }
    }

    companion object {
        const val DEFAULT_MODEL = "mistral:7b-instruct-q4_0"
    }
}
