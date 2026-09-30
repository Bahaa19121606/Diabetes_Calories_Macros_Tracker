package com.example.healthtracker.data.remote

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.InternalServerException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.messages.BetaJsonOutputFormat
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import com.example.healthtracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.optionals.getOrNull

/**
 * Estimates macros with Claude. Structured outputs constrain the reply to the macro JSON
 * schema; [MacroResponseParser] still validates it (plausibility, negatives) downstream.
 */
@Singleton
class ClaudeMacroService @Inject constructor() : AiMacroService {

    private val apiKey: String = BuildConfig.ANTHROPIC_API_KEY

    private val client: AnthropicClient by lazy {
        AnthropicOkHttpClient.builder().apiKey(apiKey).build()
    }

    override suspend fun analyzeMeal(description: String): String {
        if (apiKey.isBlank()) {
            throw AiNotConfiguredException("No Anthropic API key: add anthropic.apiKey to local.properties and rebuild")
        }
        val params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(16_000L)
            .system(MacroPrompt.SYSTEM_PROMPT)
            .outputConfig(
                BetaOutputConfig.builder()
                    .format(BetaJsonOutputFormat.builder().schema(SCHEMA).build())
                    .build(),
            )
            .addUserMessage(description)
            .build()

        // The SDK client is blocking; keep it off the main thread.
        val message = withContext(Dispatchers.IO) {
            try {
                client.beta().messages().create(params)
            } catch (e: UnauthorizedException) {
                throw AiNotConfiguredException("The Anthropic API key was rejected", e)
            } catch (e: PermissionDeniedException) {
                throw AiNotConfiguredException("The Anthropic API key doesn't have access to $MODEL", e)
            } catch (e: RateLimitException) {
                throw AiRateLimitedException("Anthropic rate limit reached", e)
            } catch (e: InternalServerException) {
                throw AiUnavailableException("Anthropic is overloaded or erroring (HTTP ${e.statusCode()})", e)
            }
        }
        if (message.stopReason().getOrNull() == BetaStopReason.REFUSAL) throw AiRefusedException()
        return message.content().mapNotNull { it.text().getOrNull()?.text() }.joinToString("")
    }

    private companion object {
        const val MODEL = "claude-sonnet-5"

        val SCHEMA: BetaJsonOutputFormat.Schema = BetaJsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty(
                "properties",
                JsonValue.from(
                    MacroPrompt.INTEGER_FIELDS.mapValues { (_, description) -> mapOf("type" to "integer", "description" to description) } +
                        (MacroPrompt.NOTES_FIELD to mapOf("type" to "string", "description" to MacroPrompt.NOTES_DESCRIPTION)),
                ),
            )
            .putAdditionalProperty("required", JsonValue.from(MacroPrompt.REQUIRED_FIELDS))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build()
    }
}
