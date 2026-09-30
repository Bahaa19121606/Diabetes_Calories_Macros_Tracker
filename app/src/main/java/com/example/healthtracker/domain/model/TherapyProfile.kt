package com.example.healthtracker.domain.model

/**
 * The user's current dosing parameters. All glucose values are mg/dL; the UI converts.
 * Tresiba dose and total daily dose are not stored here: they're derived from the insulin log.
 */
data class TherapyProfile(
    /** Grams of carbohydrate covered by one unit of NovoRapid (the "12" in 1:12). */
    val carbRatio: Double = DEFAULT_CARB_RATIO,
    /** mg/dL drop per unit of NovoRapid. */
    val correctionFactor: Double = DEFAULT_CORRECTION_FACTOR,
    val targetMgDl: Int = DEFAULT_TARGET_MG_DL,
) {
    companion object {
        const val DEFAULT_CARB_RATIO = 12.0
        const val DEFAULT_CORRECTION_FACTOR = 50.0
        const val DEFAULT_TARGET_MG_DL = 140
        val CARB_RATIO_RANGE = 3.0..50.0
        val CORRECTION_FACTOR_RANGE = 5.0..200.0
        val TARGET_RANGE = 90..180
    }
}
