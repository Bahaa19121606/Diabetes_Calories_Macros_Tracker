package com.example.healthtracker.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.example.healthtracker.data.local.AppDatabase
import com.example.healthtracker.data.local.entity.InsulinDoseEntity
import com.example.healthtracker.data.local.entity.MealEntity
import com.example.healthtracker.domain.model.InsulinType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InsulinDoseDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: InsulinDoseDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        dao = db.insulinDoseDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun enumAndFractionalUnits_roundTrip() = runTest {
        val id = dao.insert(InsulinDoseEntity(type = InsulinType.NOVORAPID, units = 4.5, administeredAt = 1_000))
        val loaded = dao.getById(id)!!
        assertThat(loaded.type).isEqualTo(InsulinType.NOVORAPID)
        assertThat(loaded.units).isEqualTo(4.5)
    }

    @Test
    fun observeLatestOfType_ignoresOtherType() = runTest {
        dao.insert(InsulinDoseEntity(type = InsulinType.TRESIBA, units = 18.0, administeredAt = 1_000))
        dao.insert(InsulinDoseEntity(type = InsulinType.NOVORAPID, units = 5.0, administeredAt = 5_000))
        dao.insert(InsulinDoseEntity(type = InsulinType.TRESIBA, units = 20.0, administeredAt = 3_000))

        dao.observeLatestOfType(InsulinType.TRESIBA).test {
            assertThat(awaitItem()?.units).isEqualTo(20.0)
        }
    }

    @Test
    fun observeTotalUnitsBetween_sumsOnlyTypeAndRange() = runTest {
        dao.insert(InsulinDoseEntity(type = InsulinType.NOVORAPID, units = 4.5, administeredAt = 1_000))
        dao.insert(InsulinDoseEntity(type = InsulinType.NOVORAPID, units = 6.0, administeredAt = 2_000))
        dao.insert(InsulinDoseEntity(type = InsulinType.NOVORAPID, units = 3.0, administeredAt = 9_000)) // out of range
        dao.insert(InsulinDoseEntity(type = InsulinType.TRESIBA, units = 20.0, administeredAt = 1_500))

        dao.observeTotalUnitsBetween(InsulinType.NOVORAPID, 0, 5_000).test {
            assertThat(awaitItem()).isEqualTo(10.5)
        }
    }

    @Test
    fun deletingMeal_keepsDose_andNullsLink() = runTest {
        val meal = MealEntity(description = "pasta", calories = 700, proteinGrams = 25, carbsGrams = 90, fatGrams = 20, eatenAt = 1_000)
        val mealId = db.mealDao().insert(meal)
        val doseId = dao.insert(InsulinDoseEntity(type = InsulinType.NOVORAPID, units = 7.0, administeredAt = 1_000, mealId = mealId))

        db.mealDao().delete(meal.copy(id = mealId))

        val dose = dao.getById(doseId)
        assertThat(dose).isNotNull()
        assertThat(dose!!.mealId).isNull()
    }
}
