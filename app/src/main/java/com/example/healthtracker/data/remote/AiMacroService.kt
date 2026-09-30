package com.example.healthtracker.data.remote

/**
 * Sends a meal description to an LLM and returns its raw response body, which is expected to
 * contain `{ "calories": Int, "protein": Int, "carbs": Int, "fat": Int }`.
 *
 * Returning the raw text (rather than a decoded DTO) keeps malformed-output handling in one
 * place, [MacroResponseParser], regardless of which backend is plugged in.
 */
interface AiMacroService {
    suspend fun analyzeMeal(description: String): String
}
