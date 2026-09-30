package com.example.healthtracker.data.remote

import com.example.healthtracker.domain.model.Macros
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class MacroResponseParserTest {

    private val parser = MacroResponseParser()

    @Test
    fun parsesPlainJson() {
        assertThat(parser.parse("""{"calories": 540, "protein": 32, "carbs": 58, "fat": 18}""").macros)
            .isEqualTo(Macros(540, 32, 58, 18))
    }

    @Test
    fun extractsJsonFromProseAndCodeFences() {
        val raw = "Here you go:\n```json\n{\"calories\": 400, \"protein\": 20, \"carbs\": 45, \"fat\": 12}\n```\nEnjoy!"
        assertThat(parser.parse(raw).macros).isEqualTo(Macros(400, 20, 45, 12))
    }

    @Test
    fun roundsFractionalValues_andIgnoresUnknownKeys() {
        val raw = """{"calories": 540.4, "protein": 31.5, "carbs": 58.49, "fat": 18, "fiber": 6}"""
        assertThat(parser.parse(raw).macros).isEqualTo(Macros(540, 32, 58, 18))
    }

    @Test
    fun rejectsMalformedResponses() {
        val bad = listOf(
            "",
            "Sure! This meal has about 540 calories.",
            """{"calories": 540, "protein": 32, "carbs": 58}""", // missing fat
            """{"calories": "lots", "protein": 32, "carbs": 58, "fat": 18}""",
            """{"calories": 540, "protein": 32, "carbs": -5, "fat": 18}""",
            """{"calories": 999999, "protein": 32, "carbs": 58, "fat": 18}""",
            """{"calories": 540, "protein": 32, """, // truncated
        )
        for (raw in bad) {
            assertThrows("should reject: $raw", MalformedMacroResponseException::class.java) { parser.parse(raw) }
        }
    }

    @Test
    fun readsNotes_whenPresent() {
        val raw = """{"calories": 480, "protein": 30, "carbs": 52, "fat": 14, "notes": "  One of four servings.  "}"""
        assertThat(parser.parse(raw).notes).isEqualTo("One of four servings.")
        assertThat(parser.parse("""{"calories": 1, "protein": 1, "carbs": 1, "fat": 1, "notes": ""}""").notes).isNull()
    }
}
