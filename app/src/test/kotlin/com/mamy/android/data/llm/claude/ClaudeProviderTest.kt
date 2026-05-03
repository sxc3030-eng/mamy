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
        assertTrue(recorded.getHeader("content-type")?.startsWith("application/json") == true)
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
