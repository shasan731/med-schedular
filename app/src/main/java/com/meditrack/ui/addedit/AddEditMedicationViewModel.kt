package com.meditrack.ui.addedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.meditrack.AppGraph
import com.meditrack.data.local.entity.MedicationEntity
import com.meditrack.data.local.entity.MedicationScheduleEntity
import com.meditrack.domain.InventoryCalculator
import com.meditrack.domain.ScheduleCalculator
import com.meditrack.domain.model.FoodRelation
import com.meditrack.domain.model.IntervalUnit
import com.meditrack.domain.model.ScheduleType
import androidx.annotation.StringRes
import com.meditrack.R
import com.meditrack.domain.model.TreatmentType
import com.meditrack.ui.stockText
import com.meditrack.utils.ValidationUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class AddEditMedicationViewModel(
    private val medicationId: Long?
) : ViewModel() {
    private val repository = AppGraph.medicationRepository
    private val settings = AppGraph.settingsRepository
    private val scheduler = AppGraph.reminderScheduler
    // Tracks the persisted row id. Starts as the edit target (if any) and is updated after the
    // first successful insert so a follow-up save becomes an update instead of a duplicate insert.
    private var currentMedicationId: Long? = medicationId
    private val _state = MutableStateFlow(MedicationFormState.default(settings.load().defaultLowStockThresholdDays))
    val state: StateFlow<MedicationFormState> = _state.asStateFlow()

    init {
        if (medicationId != null && medicationId > 0L) {
            viewModelScope.launch {
                val existing = repository.observeMedication(medicationId).filterNotNull().first()
                _state.value = MedicationFormState.fromExisting(existing.medication, existing.schedules)
            }
        }
    }

    fun update(transform: (MedicationFormState) -> MedicationFormState) {
        _state.value = transform(_state.value).normalized().copy(errorMessage = null, warningMessage = null)
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val parsed = parseForm(_state.value)
            if (parsed.errors.isNotEmpty()) {
                _state.value = _state.value.copy(errorMessage = parsed.errors.joinToString("\n"))
                return@launch
            }

            val result = repository.saveMedication(parsed.medication!!, parsed.schedules)
            // Remember the saved id so re-saving (e.g. after adjusting stock in response to the
            // purchase warning) updates the same record rather than inserting a duplicate.
            currentMedicationId = result.medicationId
            scheduler.rescheduleMedicationReminders()
            if (result.insufficientStockForCourse) {
                _state.value = _state.value.copy(
                    warningMessage = AppGraph.appContext.getString(
                        com.meditrack.R.string.save_purchase_warning,
                        (result.totalRequiredStock ?: 0.0).stockText(),
                        parsed.medication.doseUnit
                    )
                )
            } else {
                onSaved()
            }
        }
    }

    private fun str(@StringRes id: Int, vararg args: Any): String =
        AppGraph.appContext.getString(id, *args)

    private fun parseForm(form: MedicationFormState): ParsedMedication {
        val normalized = form.normalized()
        val errors = mutableListOf<String>()
        val startDate = parseDate(normalized.startDate, errors)
        val currentStock = normalized.currentStock.toDoubleOrNull()
        val lowStockThreshold = normalized.lowStockThresholdDays.toDoubleOrNull()
        val scheduleParse = parseSchedules(normalized)
        val schedules = scheduleParse.schedules
        val firstDoseAmount = schedules.mapNotNull { it.doseAmount }.firstOrNull { it > 0.0 }
            ?: normalized.doseAmount.toDoubleOrNull()
            ?: 0.0
        val endDate = resolveEndDate(normalized, startDate, errors)

        if (currentStock == null) errors += str(R.string.val_stock_number)
        if (lowStockThreshold == null) errors += str(R.string.val_lowstock_number)
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            errors += str(R.string.val_end_after_start)
        }
        errors += scheduleParse.errors

        if (errors.isNotEmpty() || startDate == null) {
            return ParsedMedication(errors = errors.distinct())
        }

        val medication = MedicationEntity(
            id = currentMedicationId ?: 0L,
            name = normalized.name.trim(),
            dosageInstruction = normalized.dosageInstruction.ifBlank {
                normalized.generatedInstruction()
            }.trim(),
            doseAmount = firstDoseAmount,
            doseUnit = normalized.doseUnit.ifBlank { "tablet" }.trim(),
            treatmentType = normalized.treatmentType,
            startDate = startDate,
            endDate = endDate,
            currentStock = currentStock ?: 0.0,
            totalRequiredStock = null,
            foodRelation = normalized.foodRelation,
            lowStockThresholdDays = lowStockThreshold ?: 1.0
        )

        errors += ValidationUtils.validateMedication(medication, schedules).map { str(it) }
        return ParsedMedication(
            medication = medication,
            schedules = schedules,
            errors = errors.distinct()
        )
    }

    private fun resolveEndDate(
        form: MedicationFormState,
        startDate: LocalDate?,
        errors: MutableList<String>
    ): LocalDate? {
        if (form.treatmentType == TreatmentType.CONTINUOUS) {
            return if (form.endDate.isBlank()) null else parseDate(form.endDate, errors)
        }
        if (startDate == null) return null
        val duration = form.courseDurationValue.toIntOrNull()
        if (duration == null || duration <= 0) {
            errors += str(R.string.val_course_length_required)
            return null
        }
        return calculateEndDate(startDate, duration, form.courseDurationUnit)
    }

    private fun parseSchedules(form: MedicationFormState): ParsedSchedules {
        return if (form.useAdvancedSchedule) {
            parseAdvancedSchedules(form)
        } else {
            parsePrescriptionPattern(form)
        }
    }

    private fun parsePrescriptionPattern(form: MedicationFormState): ParsedSchedules {
        val entries = listOf(
            PrescriptionSlot(R.string.group_morning, "08:00", form.morningDose),
            PrescriptionSlot(R.string.group_afternoon, "14:00", form.afternoonDose),
            PrescriptionSlot(R.string.group_night, "22:00", form.nightDose)
        )
        val errors = mutableListOf<String>()
        val schedules = entries.mapNotNull { slot ->
            val dose = slot.value.toDoubleOrNull()
            if (dose == null || dose < 0.0) {
                errors += str(R.string.val_slot_nonneg, str(slot.labelRes))
                null
            } else if (dose == 0.0) {
                null
            } else {
                MedicationScheduleEntity(
                    medicationId = currentMedicationId ?: 0L,
                    scheduleType = ScheduleType.SPECIFIC_TIMES,
                    timeOfDay = slot.time,
                    doseAmount = dose
                )
            }
        }
        if (schedules.isEmpty()) {
            errors += str(R.string.val_enter_one_dose)
        }
        return ParsedSchedules(schedules = schedules, errors = errors)
    }

    private fun parseAdvancedSchedules(form: MedicationFormState): ParsedSchedules {
        val doseAmount = form.doseAmount.toDoubleOrNull()
        val errors = mutableListOf<String>()
        if (doseAmount == null || doseAmount <= 0.0) {
            errors += str(R.string.val_dose_positive)
        }

        return when (form.scheduleType) {
            ScheduleType.SPECIFIC_TIMES -> {
                val tokens = form.reminderTimes.split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                val normalized = tokens.mapNotNull { ScheduleCalculator.normalizeTimeInput(it) }
                val invalid = tokens.filter { ScheduleCalculator.normalizeTimeInput(it) == null }
                ParsedSchedules(
                    schedules = normalized.distinct().map { time ->
                        MedicationScheduleEntity(
                            medicationId = currentMedicationId ?: 0L,
                            scheduleType = ScheduleType.SPECIFIC_TIMES,
                            timeOfDay = time,
                            doseAmount = doseAmount
                        )
                    },
                    errors = errors + invalid.map {
                        str(R.string.val_reminder_time_invalid, it)
                    }
                )
            }
            ScheduleType.HOURLY_INTERVAL,
            ScheduleType.DAILY_INTERVAL,
            ScheduleType.WEEKLY_INTERVAL,
            ScheduleType.MONTHLY_INTERVAL -> {
                val interval = form.intervalValue.toIntOrNull()
                val firstTimeInput = form.reminderTimes.split(",").firstOrNull()?.trim().orEmpty()
                val normalizedTime = firstTimeInput.takeIf { it.isNotBlank() }
                    ?.let { ScheduleCalculator.normalizeTimeInput(it) }
                if (firstTimeInput.isNotBlank() && normalizedTime == null) {
                    errors += str(R.string.val_reminder_time_invalid, firstTimeInput)
                }

                val daysOfWeek = form.daysOfWeek.trim()
                if (form.scheduleType == ScheduleType.WEEKLY_INTERVAL) {
                    val parsedDays = InventoryCalculator.parseDaysOfWeek(daysOfWeek)
                    val tokens = daysOfWeek.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    if (tokens.isEmpty() || parsedDays.isEmpty() || parsedDays.size != tokens.size) {
                        errors += str(R.string.val_weekly_days_invalid)
                    }
                }

                val dayOfMonth = form.dayOfMonth.toIntOrNull()
                if (form.scheduleType == ScheduleType.MONTHLY_INTERVAL && (dayOfMonth == null || dayOfMonth !in 1..31)) {
                    errors += str(R.string.val_monthly_day_invalid)
                }

                ParsedSchedules(
                    schedules = listOf(
                        MedicationScheduleEntity(
                            medicationId = currentMedicationId ?: 0L,
                            scheduleType = form.scheduleType,
                            timeOfDay = normalizedTime,
                            doseAmount = doseAmount,
                            intervalValue = interval,
                            intervalUnit = when (form.scheduleType) {
                                ScheduleType.HOURLY_INTERVAL -> IntervalUnit.HOURS
                                ScheduleType.DAILY_INTERVAL -> IntervalUnit.DAYS
                                ScheduleType.WEEKLY_INTERVAL -> IntervalUnit.WEEKS
                                ScheduleType.MONTHLY_INTERVAL -> IntervalUnit.MONTHS
                                ScheduleType.SPECIFIC_TIMES -> null
                            },
                            daysOfWeek = daysOfWeek.ifBlank { null },
                            dayOfMonth = dayOfMonth
                        )
                    ),
                    errors = errors
                )
            }
        }
    }

    private fun parseDate(value: String, errors: MutableList<String>): LocalDate? {
        return runCatching { LocalDate.parse(value.trim()) }.getOrElse {
            errors += str(R.string.val_date_invalid)
            null
        }
    }

    companion object {
        fun factory(medicationId: Long?): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AddEditMedicationViewModel(medicationId) as T
                }
            }
        }
    }
}

enum class CourseDurationUnit(val label: String) {
    DAYS("Days"),
    WEEKS("Weeks"),
    MONTHS("Months")
}

data class MedicationFormState(
    val name: String,
    val dosageInstruction: String,
    val doseAmount: String,
    val doseUnit: String,
    val currentStock: String,
    val treatmentType: TreatmentType,
    val foodRelation: FoodRelation,
    val startDate: String,
    val endDate: String,
    val courseDurationValue: String,
    val courseDurationUnit: CourseDurationUnit,
    val useAdvancedSchedule: Boolean,
    val morningDose: String,
    val afternoonDose: String,
    val nightDose: String,
    val scheduleType: ScheduleType,
    val reminderTimes: String,
    val intervalValue: String,
    val daysOfWeek: String,
    val dayOfMonth: String,
    val lowStockThresholdDays: String,
    val errorMessage: String? = null,
    val warningMessage: String? = null
) {
    fun normalized(): MedicationFormState {
        return if (treatmentType == TreatmentType.FIXED_COURSE && endDate != autoEndDate().orEmpty()) {
            copy(endDate = autoEndDate().orEmpty())
        } else {
            this
        }
    }

    fun prescriptionPattern(): String = "${morningDose.ifBlank { "0" }}+${afternoonDose.ifBlank { "0" }}+${nightDose.ifBlank { "0" }}"

    fun autoEndDate(): String? {
        if (treatmentType != TreatmentType.FIXED_COURSE) return endDate.ifBlank { null }
        val start = runCatching { LocalDate.parse(startDate.trim()) }.getOrNull() ?: return null
        val duration = courseDurationValue.toIntOrNull()?.takeIf { it > 0 } ?: return null
        return calculateEndDate(start, duration, courseDurationUnit).toString()
    }

    fun estimatedSimpleRequiredStock(): Double? {
        if (useAdvancedSchedule || treatmentType != TreatmentType.FIXED_COURSE) return null
        val durationDays = durationDays() ?: return null
        val daily = listOf(morningDose, afternoonDose, nightDose)
            .sumOf { it.toDoubleOrNull()?.takeIf { dose -> dose > 0.0 } ?: 0.0 }
        return daily * durationDays
    }

    fun generatedInstruction(): String {
        if (useAdvancedSchedule) return "Take $doseAmount $doseUnit as scheduled."
        val parts = listOf(
            "morning" to morningDose,
            "afternoon" to afternoonDose,
            "night" to nightDose
        ).mapNotNull { (label, value) ->
            val dose = value.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return@mapNotNull null
            "${dose.cleanNumber()} $doseUnit $label"
        }
        return if (parts.isEmpty()) {
            "Take as directed."
        } else {
            "Take ${parts.joinToString(", ")}."
        }
    }

    private fun durationDays(): Int? {
        val duration = courseDurationValue.toIntOrNull()?.takeIf { it > 0 } ?: return null
        return when (courseDurationUnit) {
            CourseDurationUnit.DAYS -> duration
            CourseDurationUnit.WEEKS -> duration * 7
            CourseDurationUnit.MONTHS -> {
                val start = runCatching { LocalDate.parse(startDate.trim()) }.getOrNull() ?: return null
                ChronoUnit.DAYS.between(start, calculateEndDate(start, duration, courseDurationUnit)).toInt() + 1
            }
        }
    }

    companion object {
        fun default(defaultThreshold: Double): MedicationFormState {
            val today = LocalDate.now().toString()
            return MedicationFormState(
                name = "",
                dosageInstruction = "",
                doseAmount = "1",
                doseUnit = "tablet",
                currentStock = "0",
                treatmentType = TreatmentType.CONTINUOUS,
                foodRelation = FoodRelation.NONE,
                startDate = today,
                endDate = "",
                courseDurationValue = "7",
                courseDurationUnit = CourseDurationUnit.DAYS,
                useAdvancedSchedule = false,
                morningDose = "1",
                afternoonDose = "0",
                nightDose = "1",
                scheduleType = ScheduleType.SPECIFIC_TIMES,
                reminderTimes = "08:00, 14:00, 22:00",
                intervalValue = "1",
                daysOfWeek = "1,2,3,4,5,6,7",
                dayOfMonth = "1",
                lowStockThresholdDays = defaultThreshold.toString()
            )
        }

        fun fromExisting(
            medication: MedicationEntity,
            schedules: List<MedicationScheduleEntity>
        ): MedicationFormState {
            val first = schedules.firstOrNull()
            val scheduleType = first?.scheduleType ?: ScheduleType.SPECIFIC_TIMES
            val simpleDoses = schedules.toSimpleDoseMap(medication)
            val useAdvanced = scheduleType != ScheduleType.SPECIFIC_TIMES || simpleDoses == null
            val durationDays = medication.endDate?.let {
                ChronoUnit.DAYS.between(medication.startDate, it).toInt() + 1
            }?.coerceAtLeast(1) ?: 7
            val times = if (scheduleType == ScheduleType.SPECIFIC_TIMES) {
                schedules.mapNotNull { it.timeOfDay }.joinToString(", ")
            } else {
                first?.timeOfDay ?: "09:00"
            }
            return MedicationFormState(
                name = medication.name,
                dosageInstruction = medication.dosageInstruction,
                doseAmount = medication.doseAmount.cleanNumber(),
                doseUnit = medication.doseUnit,
                currentStock = medication.currentStock.cleanNumber(),
                treatmentType = medication.treatmentType,
                foodRelation = medication.foodRelation,
                startDate = medication.startDate.toString(),
                endDate = medication.endDate?.toString().orEmpty(),
                courseDurationValue = durationDays.toString(),
                courseDurationUnit = CourseDurationUnit.DAYS,
                useAdvancedSchedule = useAdvanced,
                morningDose = simpleDoses?.get("08:00")?.cleanNumber() ?: "1",
                afternoonDose = simpleDoses?.get("14:00")?.cleanNumber() ?: "0",
                nightDose = simpleDoses?.get("22:00")?.cleanNumber() ?: "1",
                scheduleType = scheduleType,
                reminderTimes = times,
                intervalValue = first?.intervalValue?.toString() ?: "1",
                daysOfWeek = first?.daysOfWeek ?: "1,2,3,4,5,6,7",
                dayOfMonth = first?.dayOfMonth?.toString() ?: "1",
                lowStockThresholdDays = medication.lowStockThresholdDays.cleanNumber()
            )
        }
    }
}

private fun calculateEndDate(
    startDate: LocalDate,
    duration: Int,
    unit: CourseDurationUnit
): LocalDate {
    return when (unit) {
        CourseDurationUnit.DAYS -> startDate.plusDays(duration.toLong() - 1)
        CourseDurationUnit.WEEKS -> startDate.plusDays(duration.toLong() * 7L - 1)
        CourseDurationUnit.MONTHS -> startDate.plusMonths(duration.toLong()).minusDays(1)
    }
}

private fun List<MedicationScheduleEntity>.toSimpleDoseMap(
    medication: MedicationEntity
): Map<String, Double>? {
    if (any { it.scheduleType != ScheduleType.SPECIFIC_TIMES }) return null
    val allowedTimes = setOf("08:00", "14:00", "22:00")
    if (any { it.timeOfDay !in allowedTimes }) return null
    return associate { schedule ->
        schedule.timeOfDay.orEmpty() to (schedule.doseAmount ?: medication.doseAmount)
    }
}

private fun Double.cleanNumber(): String {
    return if (this % 1.0 == 0.0) toInt().toString() else toString()
}

private data class PrescriptionSlot(
    @StringRes val labelRes: Int,
    val time: String,
    val value: String
)

private data class ParsedMedication(
    val medication: MedicationEntity? = null,
    val schedules: List<MedicationScheduleEntity> = emptyList(),
    val errors: List<String> = emptyList()
)

private data class ParsedSchedules(
    val schedules: List<MedicationScheduleEntity>,
    val errors: List<String> = emptyList()
)
