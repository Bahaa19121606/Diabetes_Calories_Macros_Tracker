package com.example.healthtracker.data.remote

/** Instructions and output fields shared by every AI backend, so providers can't drift apart. */
object MacroPrompt {

    val SYSTEM_PROMPT = """
        You estimate the nutrition of a meal for someone with type 1 diabetes. They use your carbohydrate figure to dose rapid-acting insulin, so accuracy on carbohydrates matters most.

        The user message is a meal description or a recipe. Estimate the totals for what the person will eat:
        - Use any quantities, weights, brands or cooking methods given.
        - If it's a recipe that states a number of servings and the message doesn't say how much they ate, give the values for one serving.
        - If quantities are missing, assume one typical adult portion.

        Report carbohydrates as total carbohydrate excluding dietary fibre, in grams (the figure on UK and EU nutrition labels). Report calories in kcal, protein and fat in grams. Use whole numbers.

        In notes, say in one or two short sentences what portion you assumed and anything that makes the carbohydrate estimate uncertain. If the text doesn't describe food, return zeros and say so in notes.
    """.trimIndent()

    /** Output field → description; all fields are required. Must match [MacroResponseParser]'s DTO. */
    val INTEGER_FIELDS: Map<String, String> = linkedMapOf(
        "calories" to "Energy in kcal",
        "protein" to "Protein in grams",
        "carbs" to "Carbohydrate excluding fibre, in grams",
        "fat" to "Fat in grams",
    )
    const val NOTES_FIELD = "notes"
    const val NOTES_DESCRIPTION = "Portion assumed and main uncertainties"
    val REQUIRED_FIELDS: List<String> = INTEGER_FIELDS.keys.toList() + NOTES_FIELD
}
