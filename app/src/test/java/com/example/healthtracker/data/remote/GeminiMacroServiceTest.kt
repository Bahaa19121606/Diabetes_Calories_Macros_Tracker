package com.example.healthtracker.data.remote

import com.example.healthtracker.domain.analysis.MacroAnalysisResult
import com.example.healthtracker.domain.model.Macros
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class GeminiMacroServiceTest {

    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    private fun analyzer(apiKey: String = "test-key") = AiMacroAnalyzer(
        GeminiMacroService(apiKey = apiKey, model = "gemini-test", baseUrl = server.url("/").toString()),
        MacroResponseParser(),
    )

    private fun reply(code: Int, body: String) = server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    private fun candidate(text: String, finishReason: String = "STOP") =
        """{"candidates":[{"content":{"role":"model","parts":[{"text":${Json.encodeToString(String.serializer(), text)}}]},"finishReason":"$finishReason"}]}"""

    @Test
    fun success_sendsSchemaAndKey_andParsesTheReply() = runTest {
        reply(200, candidate("""{"calories":480,"protein":30,"carbs":52,"fat":14,"notes":"One of four servings."}"""))

        val result = analyzer().analyze("Chicken curry, serves 4")

        assertThat(result).isEqualTo(MacroAnalysisResult.Success(Macros(480, 30, 52, 14), "One of four servings."))
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/v1beta/models/gemini-test:generateContent")
        assertThat(request.getHeader("x-goog-api-key")).isEqualTo("test-key")
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertThat(body["systemInstruction"].toString()).contains("type 1 diabetes")
        assertThat(body["contents"]!!.jsonArray[0].jsonObject["parts"].toString()).contains("Chicken curry, serves 4")
        val config = body["generationConfig"]!!.jsonObject
        assertThat(config["responseMimeType"]!!.jsonPrimitive.content).isEqualTo("application/json")
        val required = config["responseSchema"]!!.jsonObject["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertThat(required).containsExactly("calories", "protein", "carbs", "fat", "notes").inOrder()
    }

    @Test
    fun missingKey_isNotConfigured_withoutARequest() = runTest {
        assertThat(analyzer(apiKey = "").analyze("toast")).isInstanceOf(MacroAnalysisResult.NotConfigured::class.java)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun invalidKey_isNotConfigured() = runTest {
        reply(400, """{"error":{"code":400,"message":"API key not valid.","status":"INVALID_ARGUMENT","details":[{"reason":"API_KEY_INVALID"}]}}""")
        assertThat(analyzer().analyze("toast")).isInstanceOf(MacroAnalysisResult.NotConfigured::class.java)
    }

    @Test
    fun unknownModel_isNotConfigured() = runTest {
        reply(404, """{"error":{"code":404,"message":"models/gemini-test is not found","status":"NOT_FOUND"}}""")
        assertThat(analyzer().analyze("toast")).isInstanceOf(MacroAnalysisResult.NotConfigured::class.java)
    }

    @Test
    fun quotaExceeded_isRateLimited() = runTest {
        reply(429, """{"error":{"code":429,"message":"Quota exceeded","status":"RESOURCE_EXHAUSTED"}}""")
        assertThat(analyzer().analyze("toast")).isInstanceOf(MacroAnalysisResult.RateLimited::class.java)
    }

    @Test
    fun overloaded_isRetried_thenSucceeds() = runTest {
        reply(503, OVERLOADED)
        reply(500, """{"error":{"code":500,"message":"Internal error"}}""")
        reply(200, candidate("""{"calories":100,"protein":1,"carbs":20,"fat":0,"notes":""}"""))
        assertThat(analyzer().analyze("toast")).isInstanceOf(MacroAnalysisResult.Success::class.java)
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test
    fun stillOverloadedAfterRetries_isServiceUnavailable() = runTest {
        repeat(3) { reply(503, OVERLOADED) }
        assertThat(analyzer().analyze("toast")).isInstanceOf(MacroAnalysisResult.ServiceUnavailable::class.java)
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test
    fun otherHttpErrors_areNotRetried() = runTest {
        reply(400, """{"error":{"code":400,"message":"Invalid JSON payload"}}""")
        assertThat(analyzer().analyze("toast")).isInstanceOf(MacroAnalysisResult.NetworkError::class.java)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun blockedOrSafetyStopped_isMalformedResponse() = runTest {
        reply(200, """{"promptFeedback":{"blockReason":"SAFETY"}}""")
        assertThat(analyzer().analyze("toast")).isInstanceOf(MacroAnalysisResult.MalformedResponse::class.java)

        reply(200, candidate("", finishReason = "SAFETY"))
        assertThat(analyzer().analyze("toast")).isInstanceOf(MacroAnalysisResult.MalformedResponse::class.java)
    }

    @Test
    fun unusableReplies_areMalformedResponse() = runTest {
        reply(200, "<html>proxy error</html>")
        assertThat(analyzer().analyze("toast")).isInstanceOf(MacroAnalysisResult.MalformedResponse::class.java)

        reply(200, candidate("I think about 500 calories."))
        assertThat(analyzer().analyze("toast")).isInstanceOf(MacroAnalysisResult.MalformedResponse::class.java)
    }

    private companion object {
        const val OVERLOADED = """{"error":{"code":503,"message":"This model is currently experiencing high demand.","status":"UNAVAILABLE"}}"""
    }
}
