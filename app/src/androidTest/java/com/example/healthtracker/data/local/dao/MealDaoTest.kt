package com.example.healthtracker.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.example.healthtracker.data.local.AppDatabase
import com.example.healthtracker.data.local.entity.MealEntity
import com.example.healthtracker.data.local.model.MacroTotalsRow
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MealDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: MealDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        dao = db.mealDao()
    }

    @After
    fun tearDown() = db.close()

    private fun meal(at: Long, kcal: Int = 500) =
        MealEntity(description = "meal@$at", calories = kcal, proteinGrams = 30, carbsGrams = 50, fatGrams = 20, eatenAt = at)

    @Test
    fun insertAndGetById() = runTest {
        val id = dao.insert(meal(at = 1_000))
        assertThat(dao.getById(id)).isEqualTo(meal(at = 1_000).copy(id = id))
    }

    @Test
    fun observeAll_isNewestFirst() = runTest {
        dao.insert(meal(at = 1_000))
        dao.insert(meal(at = 3_000))
        dao.insert(meal(at = 2_000))

        dao.observeAll().test {
            assertThat(awaitItem().map { it.eatenAt }).containsExactly(3_000L, 2_000L, 1_000L).inOrder()
        }
    }

    @Test
    fun observeBetween_isHalfOpen() = runTest {
        dao.insert(meal(at = 999))
        dao.insert(meal(at = 1_000))
        dao.insert(meal(at = 1_999))
        dao.insert(meal(at = 2_000))

        dao.observeBetween(1_000, 2_000).test {
            assertThat(awaitItem().map { it.eatenAt }).containsExactly(1_999L, 1_000L).inOrder()
        }
    }

    @Test
    fun observeTotals_emptyRangeIsZero_thenUpdatesOnInsert() = runTest {
        dao.observeTotalsBetween(0, 10_000).test {
            assertThat(awaitItem()).isEqualTo(MacroTotalsRow(0, 0, 0, 0))

            dao.insert(meal(at = 1_000, kcal = 400))
            assertThat(awaitItem()).isEqualTo(MacroTotalsRow(400, 30, 50, 20))

            dao.insert(meal(at = 2_000, kcal = 600))
            assertThat(awaitItem()).isEqualTo(MacroTotalsRow(1_000, 60, 100, 40))
        }
    }
}
