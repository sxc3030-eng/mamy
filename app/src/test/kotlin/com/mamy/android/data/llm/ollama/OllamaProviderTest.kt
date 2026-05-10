package com.mamy.android.data.llm.ollama

import com.mamy.android.data.llm.LlmRequest
import com.mamy.android.data.llm.StructuredNoteParser
import com.mamy.android.util.Lang
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OllamaProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: OllamaProvider

    @BeforeEach
    fun setup() {
        server = MockWebServer().also { it.start() }
        provider = OllamaProvider(
            client = OkHttpClient(),
            parser = StructuredNoteParser(),
            baseUrl = server.url("/").toString().trimEnd('/'),
        )
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `structure sends Ollama generate body with format json`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """
            {"model":"mistral:7b-instruct-q4_0","response":"{\"persons\":[],\"actions\":[],\"promises\":[],\"flags\":[],\"meeting_meta\":{\"person_main\":null,\"date_inferred\":null}}","prompt_eval_count":120,"eval_count":35,"done":true}
            """.trimIndent()
        ))

        val req = LlmRequest(transcript = "Marie va mieux", language = Lang.FR, systemPrompt = "extract")
        val resp = provider.structure(req).getOrThrow()

        assertEquals(120, resp.tokensIn)
        assertEquals(35, resp.tokensOut)
        assertEquals(0, resp.note.persons.size)

        val recorded = server.takeRequest()
        assertEquals("/api/generate", recorded.path)
        assertTrue(recorded.getHeader("content-type")?.startsWith("application/json") == true)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"model\":\"mistral:7b-instruct-q4_0\""))
        assertTrue(body.contains("\"format\":\"json\""))
        assertTrue(body.contains("\"stream\":false"))
        assertTrue(body.contains("Marie va mieux"))
        assertTrue(body.contains("\"system\":\"extract\""))
    }

    @Test
    fun `structure returns failure on HTTP 500`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"oops"}"""))
        val req = LlmRequest(transcript = "x", language = Lang.EN, systemPrompt = "s")
        val result = provider.structure(req)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("500") == true)
    }

    @Test
    fun `structure returns failure when response field missing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"done":true}"""))
        val req = LlmRequest(transcript = "x", language = Lang.EN, systemPrompt = "s")
        val result = provider.structure(req)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("missing response") == true)
    }

    @Test
    fun `testKey hits api tags and reports success on 200`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"models":[]}"""))

        val result = provider.testKey()
        assertTrue(result.isSuccess)
        val recorded = server.takeRequest()
        assertEquals("/api/tags", recorded.path)
        assertEquals("GET", recorded.method)
    }

    @Test
    fun `testKey returns failure on non-200`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val result = provider.testKey()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("503") == true)
    }

    @Test
    fun `complete returns text with zero cost`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"model":"mistral:7b-instruct-q4_0","response":"Bonjour Marie","prompt_eval_count":50,"eval_count":15,"done":true}"""
        ))
        val result = provider.complete(systemPrompt = "be brief", userPrompt = "say hi", maxTokens = 64)
        assertEquals("Bonjour Marie", result.text)
        assertEquals(0, result.costCents)
        assertEquals("ollama", result.providerName)

        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"options\""))
        assertTrue(body.contains("\"num_predict\":64"))
        // Free-form completion should NOT force format=json
        assertTrue(!body.contains("\"format\":\"json\""))
    }

    @Test
    fun `provider id and displayName match contract`() {
        assertEquals("ollama", provider.id)
        assertEquals("Ollama (local backend)", provider.displayName)
    }
}
