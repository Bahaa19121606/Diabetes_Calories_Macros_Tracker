package com.example.healthtracker.domain.calibration

/** ADA consensus thresholds (mg/dL). */
object GlucoseLimits {
    /** Level 1 hypoglycaemia. */
    const val LOW_MG_DL = 70
    /** Level 2 (clinically significant) hypoglycaemia. */
    const val VERY_LOW_MG_DL = 54
    const val HIGH_MG_DL = 180
}
