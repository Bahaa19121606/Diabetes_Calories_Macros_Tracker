package com.example.healthtracker.domain.calibration

import com.example.healthtracker.domain.calibration.CalibrationFixtures.ZONE
import com.example.healthtracker.domain.calibration.CalibrationFixtures.novorapid
import com.example.healthtracker.domain.calibration.CalibrationFixtures.tresiba
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant

class DoseMathTest {

    @Test
    fun rulesOf500And1800_matchTheProtocolArithmetic() {
        assertThat(DoseMath.rule500CarbRatio(72.0)).isWithin(0.01).of(6.94)
        assertThat(DoseMath.rule1800CorrectionFactor(72.0)).isWithin(0.001).of(25.0)
    }

    @Test
    fun ratioSteps_areAbout10PercentAndAtLeastOneGram() {
        assertThat(DoseMath.strongerRatio(12.0)).isEqualTo(11.0)
        assertThat(DoseMath.strongerRatio(10.0)).isEqualTo(9.0)
        assertThat(DoseMath.strongerRatio(7.0)).isEqualTo(6.0)
        assertThat(DoseMath.weakerRatio(12.0)).isEqualTo(13.0)
        assertThat(DoseMath.weakerRatio(7.0)).isEqualTo(8.0)
        assertThat(DoseMath.strongerRatio(3.0)).isEqualTo(3.0) // floor
    }

    @Test
    fun roundToWholeUnit_roundsToTheNearestUnit_halvesUp() {
        assertThat(DoseMath.roundToWholeUnit(4.49)).isEqualTo(4.0)
        assertThat(DoseMath.roundToWholeUnit(4.5)).isEqualTo(5.0)
        assertThat(DoseMath.roundToWholeUnit(4.99)).isEqualTo(5.0)
        assertThat(DoseMath.roundToWholeUnit(5.0)).isEqualTo(5.0)
        assertThat(DoseMath.roundToWholeUnit(0.4)).isEqualTo(0.0)
    }

    @Test
    fun dailyDoseStats_averagesOverDaysWithDoses() {
        val now = Instant.parse("2026-09-29T20:00:00Z")
        val doses = (1..3).flatMap { day ->
            val d = now.minus(Duration.ofDays(day.toLong()))
            listOf(tresiba(42.0, d), novorapid(10.0, d), novorapid(20.0, d.plusSeconds(3600)))
        }
        val stats = DoseMath.dailyDoseStats(doses, now, ZONE)!!
        assertThat(stats.days).isEqualTo(3)
        assertThat(stats.totalDailyDose).isEqualTo(72.0)
        assertThat(stats.basalShare).isWithin(0.001).of(42.0 / 72.0)

        assertThat(DoseMath.dailyDoseStats(doses.take(3), now, ZONE)).isNull() // one day only
    }

    @Test
    fun insulinOnBoard_decaysLinearlyOverFourHours() {
        val now = Instant.parse("2026-09-29T12:00:00Z")
        val doses = listOf(
            novorapid(4.0, now.minus(Duration.ofHours(2))),
            novorapid(8.0, now.minus(Duration.ofHours(5))),
            tresiba(42.0, now.minus(Duration.ofHours(1))),
        )
        assertThat(InsulinOnBoard.remainingUnits(doses, now)).isWithin(1e-9).of(2.0)
    }
}
