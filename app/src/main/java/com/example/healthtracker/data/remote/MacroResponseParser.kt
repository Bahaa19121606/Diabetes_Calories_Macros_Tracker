package com.example.healthtracker.data.remote

import com.example.healthtracker.domain.model.Macros
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.math.roundToInt

/** Parsed macros plus the model's free-text assumptions, if it gave any. */
data class MacroEstimate(val macros: Macros, val notes: String?)

class MalformedMacroResponseException(message: String, cause: Throwable? = null) : Exception(message, cause)

class MacroResponseParser @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    /** @throws MalformedMacroResponseException if [raw] has no valid, plausible macro object. */
    fun parse(raw: String): MacroEstimate {
        // LLMs often wrap JSON in prose or ``` fences; take the outermost object.
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) throw MalformedMacroResponseException("No JSON object in response")

        val dto = try {
            json.decodeFromString(MacroDto.serializer(), raw.substring(start, end + 1))
        } catch (e: IllegalArgumentException) { // includes SerializationException
            throw MalformedMacroResponseException("Response JSON does not match the macro schema", e)
        }

        val values = listOf(dto.calories, dto.protein, dto.carbs, dto.fat)
        if (values.any { it < 0 }) throw MalformedMacroResponseException("Negative macro value: $dto")
        if (dto.calories > MAX_PLAUSIBLE_CALORIES) throw MalformedMacroResponseException("Implausible calories: $dto")

        val macros = Macros(
            calories = dto.calories.roundToInt(),
            proteinGrams = dto.protein.roundToInt(),
            carbsGrams = dto.carbs.roundToInt(),
            fatGrams = dto.fat.roundToInt(),
        )
        return MacroEstimate(macros, dto.notes?.trim()?.takeIf { it.isNotEmpty() })
    }

    /** Doubles so that "carbs": 58.5 is accepted and rounded rather than rejected. */
    @Serializable
    private data class MacroDto(
        val calories: Double,
        val protein: Double,
        val carbs: Double,
        val fat: Double,
        val notes: String? = null,
    )

    private companion object {
        const val MAX_PLAUSIBLE_CALORIES = 20_000.0
    }
}
