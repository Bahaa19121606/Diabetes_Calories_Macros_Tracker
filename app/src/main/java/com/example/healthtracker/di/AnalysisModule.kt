package com.example.healthtracker.di

import com.example.healthtracker.BuildConfig
import com.example.healthtracker.data.remote.AiMacroAnalyzer
import com.example.healthtracker.data.remote.AiMacroService
import com.example.healthtracker.data.remote.ClaudeMacroService
import com.example.healthtracker.data.remote.GeminiMacroService
import com.example.healthtracker.domain.analysis.MacroAnalyzer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalysisModule {
    @Binds abstract fun bindMacroAnalyzer(impl: AiMacroAnalyzer): MacroAnalyzer

    companion object {
        /**
         * Chosen by `ai.provider` in local.properties: "gemini" (default; free tier) or "claude".
         * MockAiMacroService (fixed dummy macros) remains available for tests and offline UI work.
         */
        @Provides
        @Singleton
        fun provideAiMacroService(claude: Provider<ClaudeMacroService>): AiMacroService =
            when (BuildConfig.AI_PROVIDER.lowercase()) {
                "claude" -> claude.get()
                else -> GeminiMacroService(apiKey = BuildConfig.GEMINI_API_KEY, model = BuildConfig.GEMINI_MODEL)
            }
    }
}
