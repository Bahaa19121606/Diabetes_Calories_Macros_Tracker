package com.example.healthtracker.data.timer

import android.content.Context
import androidx.core.content.edit
import com.example.healthtracker.domain.timer.PostMealTimer
import com.example.healthtracker.domain.timer.TimerTrigger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the pending timer. AlarmManager forgets alarms on reboot, so this is the
 * source of truth used to re-arm, and to tell whether a fired alarm was cancelled.
 */
interface PostMealTimerStore {
    val timer: StateFlow<PostMealTimer?>
    fun save(timer: PostMealTimer)
    fun clear()
}

@Singleton
class SharedPrefsPostMealTimerStore @Inject constructor(
    @ApplicationContext context: Context,
) : PostMealTimerStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _timer = MutableStateFlow(read())
    override val timer: StateFlow<PostMealTimer?> = _timer.asStateFlow()

    override fun save(timer: PostMealTimer) {
        prefs.edit {
            putLong(KEY_FIRE_AT, timer.fireAt.toEpochMilli())
            putString(KEY_TRIGGER, timer.trigger.name)
            putLong(KEY_SOURCE_ID, timer.sourceId)
        }
        _timer.value = timer
    }

    override fun clear() {
        prefs.edit { clear() }
        _timer.value = null
    }

    private fun read(): PostMealTimer? {
        if (!prefs.contains(KEY_FIRE_AT)) return null
        val trigger = prefs.getString(KEY_TRIGGER, null)
            ?.let { name -> TimerTrigger.entries.firstOrNull { it.name == name } }
            ?: return null
        return PostMealTimer(
            fireAt = Instant.ofEpochMilli(prefs.getLong(KEY_FIRE_AT, 0)),
            trigger = trigger,
            sourceId = prefs.getLong(KEY_SOURCE_ID, 0),
        )
    }

    private companion object {
        const val PREFS_NAME = "post_meal_timer"
        const val KEY_FIRE_AT = "fire_at"
        const val KEY_TRIGGER = "trigger"
        const val KEY_SOURCE_ID = "source_id"
    }
}
