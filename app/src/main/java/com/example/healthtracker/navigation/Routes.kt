package com.example.healthtracker.navigation

object Routes {
    const val TODAY = "today"
    const val REVIEWS = "reviews"
    const val INSIGHTS = "insights"
    const val LOG_MEAL = "log/meal"
    const val LOG_GLUCOSE = "log/glucose"
    const val LOG_INSULIN = "log/insulin"
    const val LOG_SLEEP = "log/sleep"
    const val LOG_WORKOUT = "log/workout"
    const val PROFILE = "profile"
    const val CARB_TEST = "carb-test"
    const val BOLUS = "bolus"
    const val ARG_MEAL_ID = "mealId"

    /** Dose calculator; optionally prefilled from a saved meal. */
    const val BOLUS_WITH_ARGS = "$BOLUS?$ARG_MEAL_ID={$ARG_MEAL_ID}"

    fun bolus(mealId: Long? = null) = if (mealId == null) BOLUS else "$BOLUS?$ARG_MEAL_ID=$mealId"

    /** The glucose log screen; the optional context is set by notification deep links. */
    const val LOG_GLUCOSE_WITH_ARGS = "$LOG_GLUCOSE?${DeepLinks.ARG_CONTEXT}={${DeepLinks.ARG_CONTEXT}}"
}
