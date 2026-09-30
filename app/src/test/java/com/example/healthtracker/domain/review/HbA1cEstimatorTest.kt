package com.example.healthtracker.domain.review

import com.example.healthtracker.domain.calibration.CalibrationFixtures.ZONE
import com.example.healthtracker.domain.calibration.CalibrationFixtures.at
import com.example.healthtracker.domain.calibration.CalibrationFixtures.reading
import com.example.healthtracker.domain.model.GlucoseReading
import com.example.healthtracker.domain.model.LabHbA1c
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class HbA1cEstimatorTest {

    private val today = LocalDate.of(2026, 9, 30)

    /** Two readings a day, both [mgDl], for each day in [fromDaysAgo] down to [toDaysAgo]. */
    private fun daily(mgDl: Int, fromDaysAgo: Long, toDaysAgo: Long = 0): List<GlucoseReading> =
        (toDaysAgo..fromDaysAgo).flatMap { ago ->
            val date = today.minusDays(ago)
            listOf(reading(mgDl, at(date, "08:00")), reading(mgDl, at(date, "19:00")))
        }

    @Test
    fun convertsBetweenHbA1cAndAverageGlucose_withTheAdagFormula() {
        assertThat(HbA1cEstimator.averageGlucoseFor(7.0)).isWithin(0.01).of(154.2)
        assertThat(HbA1cEstimator.percentFor(154.2)).isWithin(0.001).of(7.0)
        assertThat(LabHbA1c(7.0, today).mmolPerMol).isEqualTo(53)
    }

    @Test
    fun withoutReadingsSinceTheLabTest_theEstimateIsTheLabResult() {
        val lab = LabHbA1c(7.4, today.minusDays(10))
        val estimate = HbA1cEstimator.estimate(lab, emptyList(), today, ZONE)

        assertThat(estimate.percent).isEqualTo(7.4)
        assertThat(estimate.change).isEqualTo(0.0)
        assertThat(estimate.loggedShare).isEqualTo(0.0)
        assertThat(estimate.confidence).isEqualTo(HbA1cEstimate.Confidence.LOW)
    }

    @Test
    fun onceTheLabTestIsOlderThan120Days_itComesEntirelyFromReadings() {
        val lab = LabHbA1c(7.0, today.minusDays(200))
        // 126 mg/dL is an HbA1c of about 6.0%.
        val estimate = HbA1cEstimator.estimate(lab, daily(126, fromDaysAgo = 119), today, ZONE)

        assertThat(estimate.percent).isEqualTo(6.0)
        assertThat(estimate.change).isEqualTo(-1.0)
        assertThat(estimate.loggedShare).isWithin(1e-9).of(1.0)
        assertThat(estimate.calibrated).isFalse()
        assertThat(estimate.confidence).isEqualTo(HbA1cEstimate.Confidence.HIGH)
    }

    @Test
    fun theLast30DaysCountForHalf() {
        // Lab 8.0% (182.9 mg/dL) 30 days ago, then a month at 126 mg/dL:
        // 0.5 × 126 + 0.5 × 182.9 = 154.45 mg/dL → 7.0%.
        val lab = LabHbA1c(8.0, today.minusDays(30))
        val estimate = HbA1cEstimator.estimate(lab, daily(126, fromDaysAgo = 29), today, ZONE)

        assertThat(estimate.percent).isEqualTo(7.0)
        assertThat(estimate.loggedShare).isWithin(1e-9).of(0.5)
        assertThat(estimate.recentReadings).isEqualTo(60)
        assertThat(estimate.confidence).isEqualTo(HbA1cEstimate.Confidence.MEDIUM)
    }

    @Test
    fun readingsOnOrBeforeTheLabDay_areCoveredByTheLabResult() {
        val lab = LabHbA1c(8.0, today.minusDays(30))
        // Only a few readings before the lab test: not enough to calibrate, and the lab result already covers them.
        val readings = daily(126, fromDaysAgo = 29) + daily(300, fromDaysAgo = 35, toDaysAgo = 30)
        val estimate = HbA1cEstimator.estimate(lab, readings, today, ZONE)

        assertThat(estimate.percent).isEqualTo(7.0)
        assertThat(estimate.calibrated).isFalse()
    }

    @Test
    fun readingsBeforeTheLabTest_calibrateForWhenYouUsuallyCheck() {
        // Before the test, readings averaged 160 while the lab said 182.9: checks run about 14% low.
        // Later readings of 126 are scaled to 144.0, giving 0.5 × 144.0 + 0.5 × 182.9 → 7.3%.
        val lab = LabHbA1c(8.0, today.minusDays(30))
        val readings = daily(126, fromDaysAgo = 29) + daily(160, fromDaysAgo = 119, toDaysAgo = 30)
        val estimate = HbA1cEstimator.estimate(lab, readings, today, ZONE)

        assertThat(estimate.calibrated).isTrue()
        assertThat(estimate.percent).isEqualTo(7.3)
    }

    @Test
    fun calibrationIsCapped() {
        // Pre-test readings far below the lab result would scale later readings by 2×; capped at 1.25×.
        val lab = LabHbA1c(8.0, today.minusDays(200))
        val readings = daily(126, fromDaysAgo = 119) + daily(90, fromDaysAgo = 260, toDaysAgo = 200)
        val estimate = HbA1cEstimator.estimate(lab, readings, today, ZONE)

        // 126 × 1.25 = 157.5 mg/dL → 7.1%.
        assertThat(estimate.percent).isEqualTo(7.1)
    }
}
