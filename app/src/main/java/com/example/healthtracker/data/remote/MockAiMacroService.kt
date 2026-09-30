package com.example.healthtracker.data.remote

import kotlinx.coroutines.delay
import java.io.IOException
import javax.inject.Inject

/**
 * Stand-in for the real LLM backend. Include `#fail` or `#malformed` in the
 * description to exercise the error paths from the UI.
 */
class MockAiMacroService @Inject constructor() : AiMacroService {

    override suspend fun analyzeMeal(description: String): String {
        delay(LATENCY_MS)
        return when {
            FAIL_TAG in description -> throw IOException("Simulated network failure")
            MALFORMED_TAG in description -> "Sure! This meal has about 540 calories."
            else -> """{ "calories": 540, "protein": 32, "carbs": 58, "fat": 18, "notes": "Dummy values from the mock service." }"""
        }
    }

    companion object {
        const val LATENCY_MS = 2_000L
        const val FAIL_TAG = "#fail"
        const val MALFORMED_TAG = "#malformed"
    }
}
