package com.example.healthtracker.data.remote

import com.example.healthtracker.domain.analysis.MacroAnalysisResult
import com.example.healthtracker.domain.analysis.MacroAnalyzer
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class AiMacroAnalyzer @Inject constructor(
    private val service: AiMacroService,
    private val parser: MacroResponseParser,
) : MacroAnalyzer {

    override suspend fun analyze(description: String): MacroAnalysisResult {
        val raw = try {
            service.analyzeMeal(description)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiNotConfiguredException) {
            return MacroAnalysisResult.NotConfigured(e)
        } catch (e: AiRateLimitedException) {
            return MacroAnalysisResult.RateLimited(e)
        } catch (e: AiUnavailableException) {
            return MacroAnalysisResult.ServiceUnavailable(e)
        } catch (e: AiRefusedException) {
            return MacroAnalysisResult.MalformedResponse(e)
        } catch (e: MalformedMacroResponseException) {
            return MacroAnalysisResult.MalformedResponse(e)
        } catch (e: Exception) {
            return MacroAnalysisResult.NetworkError(e)
        }
        return try {
            val estimate = parser.parse(raw)
            MacroAnalysisResult.Success(estimate.macros, estimate.notes)
        } catch (e: MalformedMacroResponseException) {
            MacroAnalysisResult.MalformedResponse(e)
        }
    }
}
