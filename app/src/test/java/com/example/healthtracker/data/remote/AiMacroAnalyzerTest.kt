package com.example.healthtracker.data.remote

import com.example.healthtracker.domain.analysis.MacroAnalysisResult
import com.example.healthtracker.domain.model.Macros
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiMacroAnalyzerTest {

    private val analyzer = AiMacroAnalyzer(MockAiMacroService(), MacroResponseParser())

    @Test
    fun mockService_returnsDummyMacrosAfterLatency() = runTest {
        val result = analyzer.analyze("chicken and rice")
        assertThat(result).isEqualTo(MacroAnalysisResult.Success(Macros(540, 32, 58, 18), "Dummy values from the mock service."))
        assertThat(currentTime).isEqualTo(MockAiMacroService.LATENCY_MS)
    }

    @Test
    fun serviceException_isNetworkError() = runTest {
        assertThat(analyzer.analyze("toast ${MockAiMacroService.FAIL_TAG}"))
            .isInstanceOf(MacroAnalysisResult.NetworkError::class.java)
    }

    @Test
    fun unparseableBody_isMalformedResponse() = runTest {
        assertThat(analyzer.analyze("toast ${MockAiMacroService.MALFORMED_TAG}"))
            .isInstanceOf(MacroAnalysisResult.MalformedResponse::class.java)
    }
}
