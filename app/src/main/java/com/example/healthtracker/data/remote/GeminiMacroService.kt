package com.example.healthtracker.data.remote

import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Estimates macros with Gemini's `generateContent` REST endpoint. A response schema constrains
 * the reply to the macro JSON; [MacroResponseParser] still validates it downstream.
 */
class GeminiMacroService(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: OkHttpClient = defaultClient(),
    /** Waits before each retry of a transient 5xx; the free tier is often briefly "overloaded". */
    private val retryDelaysMs: List<Long> = listOf(2_000, 5_000),
) : AiMacroService {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun analyzeMeal(description: String): String {
        if (apiKey.isBlank()) {
            throw AiNotConfiguredException("No Gemini API key: add gemini.apiKey to local.properties and rebuild")
        }
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(requestBody(description).toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        var (code, body) = client.newCall(request).await()
        for (wait in retryDelaysMs) {
            if (code !in TRANSIENT_CODES) break
            delay(wait)
            client.newCall(request).await().let { code = it.first; body = it.second }
        }
        when {
            code == 429 -> throw AiRateLimitedException("Gemini rate limit or free-tier quota reached: ${errorMessage(body)}")
            code == 401 || code == 403 || (code == 400 && "API_KEY_INVALID" in body) ->
                throw AiNotConfiguredException("Gemini rejected the API key: ${errorMessage(body)}")
            code == 404 -> throw AiNotConfiguredException("Gemini model \"$model\" not found: set gemini.model in local.properties")
            code in TRANSIENT_CODES -> throw AiUnavailableException("Gemini HTTP $code: ${errorMessage(body)}")
            code !in 200..299 -> throw IOException("Gemini HTTP $code: ${errorMessage(body)}")
        }
        return extractText(body)
    }

    private fun requestBody(description: String): JsonObject = buildJsonObject {
        putJsonObject("systemInstruction") {
            putJsonArray("parts") { add(buildJsonObject { put("text", MacroPrompt.SYSTEM_PROMPT) }) }
        }
        putJsonArray("contents") {
            add(
                buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { add(buildJsonObject { put("text", description) }) }
                },
            )
        }
        putJsonObject("generationConfig") {
            put("responseMimeType", "application/json")
            putJsonObject("responseSchema") {
                put("type", "OBJECT")
                putJsonObject("properties") {
                    MacroPrompt.INTEGER_FIELDS.forEach { (name, text) ->
                        putJsonObject(name) {
                            put("type", "INTEGER")
                            put("description", text)
                        }
                    }
                    putJsonObject(MacroPrompt.NOTES_FIELD) {
                        put("type", "STRING")
                        put("description", MacroPrompt.NOTES_DESCRIPTION)
                    }
                }
                put("required", buildJsonArray { MacroPrompt.REQUIRED_FIELDS.forEach { add(JsonPrimitive(it)) } })
                put("propertyOrdering", buildJsonArray { MacroPrompt.REQUIRED_FIELDS.forEach { add(JsonPrimitive(it)) } })
            }
        }
    }

    /** Joins the first candidate's text parts; blocked or safety-stopped replies become [AiRefusedException]. */
    private fun extractText(body: String): String {
        val root = try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: IllegalArgumentException) {
            throw MalformedMacroResponseException("Gemini response isn't JSON", e)
        }
        root["promptFeedback"]?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull?.let {
            throw AiRefusedException("Gemini blocked the request ($it)")
        }
        val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw MalformedMacroResponseException("Gemini returned no candidates")
        val finishReason = candidate["finishReason"]?.jsonPrimitive?.contentOrNull
        if (finishReason in REFUSAL_FINISH_REASONS) throw AiRefusedException("Gemini stopped the reply ($finishReason)")
        return candidate["content"]?.jsonObject?.get("parts")?.jsonArray
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            ?.joinToString("")
            .orEmpty()
    }

    private fun errorMessage(body: String): String = try {
        json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull ?: body.take(200)
    } catch (e: IllegalArgumentException) {
        body.take(200)
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com"
        const val DEFAULT_MODEL = "gemini-3.8-flash"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val TRANSIENT_CODES = setOf(500, 502, 503, 504)
        private val REFUSAL_FINISH_REASONS = setOf("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "RECITATION")

        private fun defaultClient() = OkHttpClient.Builder()
            .callTimeout(90, TimeUnit.SECONDS) // thinking models can take a while
            .build()
    }
}

/** Runs the call on OkHttp's dispatcher; cancelling the coroutine cancels the HTTP request. */
private suspend fun Call.await(): Pair<Int, String> = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.use { it.code to it.body?.string().orEmpty() }
                continuation.resume(result)
            }
        },
    )
}
