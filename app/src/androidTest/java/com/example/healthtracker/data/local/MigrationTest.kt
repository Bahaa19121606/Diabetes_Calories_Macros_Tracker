package com.example.healthtracker.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Existing installs must keep their data across schema changes. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate1To2_keepsDosesAndAddsCalculatorColumns() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL("INSERT INTO insulin_doses (id, type, units, administered_at) VALUES (1, 'TRESIBA', 42.0, 1000)")
            db.execSQL("INSERT INTO glucose_readings (id, mg_dl, context, measured_at) VALUES (1, 110, 'FASTING', 2000)")
        }

        helper.runMigrationsAndValidate(DB, 2, true).use { db ->
            db.query("SELECT units, carbs_g, carb_ratio FROM insulin_doses WHERE id = 1").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getDouble(0)).isEqualTo(42.0)
                assertThat(c.isNull(1)).isTrue()
                assertThat(c.isNull(2)).isTrue()
            }
            db.query("SELECT COUNT(*) FROM glucose_readings").use { c ->
                c.moveToFirst()
                assertThat(c.getInt(0)).isEqualTo(1)
            }
            db.execSQL("INSERT INTO workouts (started_at, duration_min, intensity) VALUES (3000, 60, 'HEAVY_LIFTING')")
            db.execSQL(
                "INSERT INTO carb_ratio_tests (started_at, start_mg_dl, carbs_g, ratio_tested, dose_units, status) " +
                    "VALUES (4000, 120, 50, 12.0, 4.0, 'IN_PROGRESS')",
            )
        }
    }

    @Test
    fun migrate2To3_keepsCalculatorDosesAndAddsCorrectionFactor() {
        helper.createDatabase(DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO insulin_doses (id, type, units, administered_at, carbs_g, carb_ratio, correction_units) " +
                    "VALUES (1, 'NOVORAPID', 5.0, 1000, 60, 12.0, 0.0)",
            )
        }

        helper.runMigrationsAndValidate(DB, 3, true).use { db ->
            db.query("SELECT units, carbs_g, correction_factor FROM insulin_doses WHERE id = 1").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getDouble(0)).isEqualTo(5.0)
                assertThat(c.getInt(1)).isEqualTo(60)
                assertThat(c.isNull(2)).isTrue()
            }
        }
    }

    @Test
    fun migrate3To4_keepsDataAndAddsSleepSessions() {
        helper.createDatabase(DB, 3).use { db ->
            db.execSQL("INSERT INTO glucose_readings (id, mg_dl, context, measured_at) VALUES (1, 110, 'FASTING', 2000)")
        }

        helper.runMigrationsAndValidate(DB, 4, true).use { db ->
            db.query("SELECT COUNT(*) FROM glucose_readings").use { c ->
                c.moveToFirst()
                assertThat(c.getInt(0)).isEqualTo(1)
            }
            db.execSQL("INSERT INTO sleep_sessions (bedtime, wake_time) VALUES (1000, 28000000)")
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
