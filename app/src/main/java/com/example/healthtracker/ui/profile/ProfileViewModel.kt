package com.example.healthtracker.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthtracker.domain.calibration.DoseMath
import com.example.healthtracker.domain.model.GlucoseUnit
import com.example.healthtracker.domain.model.InsulinType
import com.example.healthtracker.domain.model.TherapyProfile
import com.example.healthtracker.domain.repository.InsulinRepository
import com.example.healthtracker.domain.repository.TherapyProfileRepository
import com.example.healthtracker.domain.settings.SettingsRepository
import com.example.healthtracker.ui.common.InputError
import com.example.healthtracker.ui.common.ParsedNumber
import com.example.healthtracker.ui.common.convertGlucoseInput
import com.example.healthtracker.ui.common.glucoseInputText
import com.example.healthtracker.ui.common.parseDecimal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import kotlin.math.roundToInt

/** Text fields; glucose-based values are in the display unit. */
data class ProfileForm(
    val carbRatio: String,
    val correctionFactor: String,
    val target: String,
    val carbRatioError: InputError? = null,
    val correctionFactorError: InputError? = null,
    val targetError: InputError? = null,
)

data class ProfileUiState(
    val form: ProfileForm = ProfileForm("", "", ""),
    val isEditing: Boolean = false,
    val glucoseUnit: GlucoseUnit = SettingsRepository.DEFAULT_GLUCOSE_UNIT,
    val tresibaDose: Double? = null,
    val dailyStats: DoseMath.DailyDoseStats? = null,
    val saved: Boolean = false,
) {
    /** 500 rule, from the logged total daily dose. */
    val rule500Ratio: Double? get() = dailyStats?.let { DoseMath.rule500CarbRatio(it.totalDailyDose) }

    /** 1800 rule, mg/dL per unit. */
    val rule1800FactorMgDl: Double? get() = dailyStats?.let { DoseMath.rule1800CorrectionFactor(it.totalDailyDose) }

    /** The 500/1800 rules assume basal ≈ 50% of the total; much more skews them. */
    val basalHeavy: Boolean get() = (dailyStats?.basalShare ?: 0.0) > 0.55
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: TherapyProfileRepository,
    settingsRepository: SettingsRepository,
    insulinRepository: InsulinRepository,
    private val clock: Clock,
) : ViewModel() {

    /** Non-null once the user starts typing; otherwise the form mirrors the saved profile. */
    private val editing = MutableStateFlow<ProfileForm?>(null)
    private val saved = MutableStateFlow(false)

    private val doseInfo = insulinRepository
        .observeBetween(clock.instant().minus(Duration.ofDays(30)), clock.instant().plus(Duration.ofDays(1)))
        .map { doses ->
            val tresiba = doses.filter { it.type == InsulinType.TRESIBA }.maxByOrNull { it.administeredAt }?.units
            tresiba to DoseMath.dailyDoseStats(doses, clock.instant(), clock.zone)
        }

    val uiState: StateFlow<ProfileUiState> = combine(
        profileRepository.profile,
        settingsRepository.glucoseUnit,
        editing,
        saved,
        doseInfo,
    ) { profile, unit, edit, isSaved, (tresiba, stats) ->
        ProfileUiState(
            form = edit ?: profile.toForm(unit),
            isEditing = edit != null,
            glucoseUnit = unit,
            tresibaDose = tresiba,
            dailyStats = stats,
            saved = isSaved,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    init {
        // Keeps half-typed glucose values meaningful when the unit is switched mid-edit.
        viewModelScope.launch {
            var previous: GlucoseUnit? = null
            settingsRepository.glucoseUnit.collect { unit ->
                val from = previous
                previous = unit
                if (from != null && from != unit) {
                    editing.update { form ->
                        form?.copy(
                            correctionFactor = convertGlucoseInput(form.correctionFactor, from, unit),
                            target = convertGlucoseInput(form.target, from, unit),
                        )
                    }
                }
            }
        }
    }

    fun onCarbRatioChange(text: String) = edit { it.copy(carbRatio = text, carbRatioError = null) }

    fun onCorrectionFactorChange(text: String) = edit { it.copy(correctionFactor = text, correctionFactorError = null) }

    fun onTargetChange(text: String) = edit { it.copy(target = text, targetError = null) }

    fun useRule1800Factor() {
        val factor = uiState.value.rule1800FactorMgDl ?: return
        onCorrectionFactorChange(glucoseInputText(factor.roundToInt(), uiState.value.glucoseUnit))
    }

    fun discardChanges() {
        editing.value = null
    }

    fun save() {
        val form = editing.value ?: return
        val unit = uiState.value.glucoseUnit
        val ratio = parseDecimal(form.carbRatio, TherapyProfile.CARB_RATIO_RANGE)
        val factor = parseDecimal(form.correctionFactor, 0.0..Double.MAX_VALUE)
        val target = parseDecimal(form.target, 0.0..Double.MAX_VALUE)

        val factorMgDl = (factor as? ParsedNumber.Valid)?.let { unit.toMgDl(it.value).toDouble() }
        val targetMgDl = (target as? ParsedNumber.Valid)?.let { unit.toMgDl(it.value) }
        val errors = form.copy(
            carbRatioError = (ratio as? ParsedNumber.Invalid)?.error,
            correctionFactorError = (factor as? ParsedNumber.Invalid)?.error
                ?: InputError.OUT_OF_RANGE.takeIf { factorMgDl != null && factorMgDl !in TherapyProfile.CORRECTION_FACTOR_RANGE },
            targetError = (target as? ParsedNumber.Invalid)?.error
                ?: InputError.OUT_OF_RANGE.takeIf { targetMgDl != null && targetMgDl !in TherapyProfile.TARGET_RANGE },
        )
        if (errors.carbRatioError != null || errors.correctionFactorError != null || errors.targetError != null) {
            editing.value = errors
            return
        }
        val profile = TherapyProfile(
            carbRatio = (ratio as ParsedNumber.Valid).value,
            correctionFactor = factorMgDl!!,
            targetMgDl = targetMgDl!!,
        )
        viewModelScope.launch {
            try {
                profileRepository.update(profile)
                editing.value = null
                saved.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saved.value = false
            }
        }
    }

    fun savedShown() {
        saved.value = false
    }

    private inline fun edit(transform: (ProfileForm) -> ProfileForm) {
        saved.value = false
        editing.update { current -> transform(current ?: uiState.value.form) }
    }

    private fun TherapyProfile.toForm(unit: GlucoseUnit) = ProfileForm(
        carbRatio = formatRatio(carbRatio),
        correctionFactor = glucoseInputText(correctionFactor.roundToInt(), unit),
        target = glucoseInputText(targetMgDl, unit),
    )
}

/** "12" or "7.5". */
fun formatRatio(ratio: Double): String =
    if (ratio % 1.0 == 0.0) ratio.toInt().toString() else ratio.toString()
