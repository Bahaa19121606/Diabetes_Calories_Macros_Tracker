package com.example.healthtracker.domain.model

import java.time.LocalDate

/** A lab HbA1c result: the anchor the app's estimate starts from. */
data class LabHbA1c(val percent: Double, val measuredOn: LocalDate) {
    init {
        require(percent in MIN_PERCENT..MAX_PERCENT) { "HbA1c $percent% is outside the plausible range" }
    }

    /** IFCC units, used on many lab reports: (% − 2.15) × 10.929. */
    val mmolPerMol: Int get() = HbA1cUnits.toMmolPerMol(percent)

    companion object {
        const val MIN_PERCENT = 4.0
        const val MAX_PERCENT = 15.0
    }
}

object HbA1cUnits {
    fun toMmolPerMol(percent: Double): Int = kotlin.math.round((percent - 2.15) * 10.929).toInt()
}
