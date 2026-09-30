package com.example.healthtracker.ui.sleep

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class SleepDialMathTest {

    @Test
    fun midnightIsAtTheTop_andTheDialRunsClockwise() {
        assertThat(SleepDialMath.angleFor(0)).isEqualTo(-90f)
        assertThat(SleepDialMath.angleFor(6 * 60)).isEqualTo(0f) // 3 o'clock
        assertThat(SleepDialMath.angleFor(18 * 60)).isEqualTo(180f)
    }

    @Test
    fun touchPoints_mapToSnappedTimes() {
        assertThat(SleepDialMath.minutesAt(0f, -100f)).isEqualTo(0) // top
        assertThat(SleepDialMath.minutesAt(100f, 0f)).isEqualTo(6 * 60) // right
        assertThat(SleepDialMath.minutesAt(0f, 100f)).isEqualTo(12 * 60) // bottom
        assertThat(SleepDialMath.minutesAt(-100f, 0f)).isEqualTo(18 * 60) // left
        // Left of the top is late evening (16.7° before midnight ≈ 22:53), snapped to 5 minutes.
        assertThat(SleepDialMath.minutesAt(-30f, -100f)).isEqualTo(22 * 60 + 55)
        // A hair before midnight snaps to midnight rather than 24:00.
        assertThat(SleepDialMath.minutesAt(-1f, -100f)).isEqualTo(0)
    }

    @Test
    fun duration_crossesMidnight() {
        assertThat(SleepDialMath.durationMinutes(23 * 60, 7 * 60)).isEqualTo(8 * 60)
        assertThat(SleepDialMath.durationMinutes(1 * 60, 7 * 60 + 30)).isEqualTo(6 * 60 + 30)
        assertThat(SleepDialMath.durationMinutes(7 * 60, 7 * 60)).isEqualTo(0)
    }

    @Test
    fun resolve_placesBedtimeOnThePreviousEvening() {
        val (bed, wake) = SleepDialMath.resolve(23 * 60 + 30, 7 * 60, LocalDate.of(2026, 9, 29), ZoneOffset.UTC)!!
        assertThat(bed).isEqualTo(Instant.parse("2026-09-28T23:30:00Z"))
        assertThat(wake).isEqualTo(Instant.parse("2026-09-29T07:00:00Z"))
        assertThat(SleepDialMath.resolve(420, 420, LocalDate.of(2026, 9, 29), ZoneOffset.UTC)).isNull()
    }
}
