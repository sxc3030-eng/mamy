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
