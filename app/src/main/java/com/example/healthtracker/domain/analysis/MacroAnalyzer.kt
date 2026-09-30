package com.example.healthtracker.domain.analysis

import com.example.healthtracker.domain.model.Macros

/** Turns a free-text recipe / meal description into macros. */
interface MacroAnalyzer {
    suspend fun analyze(description: String): MacroAnalysisResult
}

sealed interface MacroAnalysisResult {
    /** [notes]: the model's stated assumptions (portion size, uncertainties), shown to the user. */
    data class Success(val macros: Macros, val notes: String? = null) : MacroAnalysisResult

    sealed interface Failure : MacroAnalysisResult {
        val cause: Throwable
    }

    /** The request never produced a response body (offline, timeout, HTTP error). */
    data class NetworkError(override val cause: Throwable) : Failure

    /** A response arrived but did not contain usable macro JSON (or the model declined). */
    data class MalformedResponse(override val cause: Throwable) : Failure

    /** No API key, or the key was rejected: retrying won't help until it's fixed. */
    data class NotConfigured(override val cause: Throwable) : Failure

    /** Rate limit / free-tier quota reached: try again later. */
    data class RateLimited(override val cause: Throwable) : Failure

    /** The provider is overloaded or erroring (it answered, so it isn't the user's connection). */
    data class ServiceUnavailable(override val cause: Throwable) : Failure
}
