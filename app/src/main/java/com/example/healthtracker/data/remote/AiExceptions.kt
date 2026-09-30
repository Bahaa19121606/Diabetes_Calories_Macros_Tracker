package com.example.healthtracker.data.remote

/** No key configured, or the key was rejected. */
class AiNotConfiguredException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The provider declined the request (safety filter / refusal). */
class AiRefusedException(message: String = "The model declined to analyse this description") : Exception(message)

/** Rate limit or quota exhausted (HTTP 429), e.g. the Gemini free tier's per-minute or daily cap. */
class AiRateLimitedException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The provider is overloaded or erroring (HTTP 5xx) even after retries; temporary. */
class AiUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)
