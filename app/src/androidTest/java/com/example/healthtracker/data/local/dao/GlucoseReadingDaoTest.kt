package com.example.healthtracker.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.example.healthtracker.data.local.AppDatabase
import com.example.healthtracker.data.local.entity.GlucoseReadingEntity
import com.example.healthtracker.domain.model.GlucoseContext
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlucoseReadingDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: GlucoseReadingDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        dao = db.glucoseReadingDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun observeLatest_emitsNullThenNewest() = runTest {
        dao.observeLatest().test {
            assertThat(awaitItem()).isNull()

            dao.insert(GlucoseReadingEntity(mgDl = 110, context = GlucoseContext.PRE_MEAL, measuredAt = 1_000))
            assertThat(awaitItem()?.mgDl).isEqualTo(110)

            dao.insert(GlucoseReadingEntity(mgDl = 165, context = GlucoseContext.POST_MEAL, measuredAt = 8_200_000))
            assertThat(awaitItem()?.mgDl).isEqualTo(165)
        }
    }

    @Test
    fun getLatestWithContext_filtersByContext() = runTest {
        dao.insert(GlucoseReadingEntity(mgDl = 100, context = GlucoseContext.PRE_MEAL, measuredAt = 1_000))
        dao.insert(GlucoseReadingEntity(mgDl = 120, context = GlucoseContext.PRE_MEAL, measuredAt = 2_000))
        dao.insert(GlucoseReadingEntity(mgDl = 180, context = GlucoseContext.POST_MEAL, measuredAt = 3_000))

        assertThat(dao.getLatestWithContext(GlucoseContext.PRE_MEAL)?.mgDl).isEqualTo(120)
    }
}
