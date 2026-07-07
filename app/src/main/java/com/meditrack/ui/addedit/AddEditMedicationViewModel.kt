package com.meditrack.ui.addedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.meditrack.AppGraph
import com.meditrack.data.local.entity.MedicationEntity
import com.meditrack.data.local.entity.MedicationScheduleEntity
import com.meditrack.domain.ScheduleCalculator
import com.meditrack.domain.model.IntervalUnit
import com.meditrack.domain.model.ScheduleType
import com.meditrack.domain.model.TreatmentType
import com.meditrack.utils.ValidationUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class AddEditMedicationViewModel(
    private val medicationId: Long?
) : ViewModel() {
    private val repository = AppGraph.medicationRepository
    private val settings = AppGraph.settingsRepository
    private val scheduler = AppGraph.reminderScheduler
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
        _state.value = transform(_state.value).copy(errorMessage = null, warningMessage = null)
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val parsed = parseForm(_state.value)
            if (parsed.errors.isNotEmpty()) {
                _state.value = _state.value.copy(errorMessage = parsed.errors.joinToString("\n"))
                return@launch
            }

            val result = repository.saveMedication(parsed.medication!!, parsed.schedules)
            scheduler.rescheduleMedicationReminders()
            if (result.insufficientStockForCourse) {
                _state.value = _state.value.copy(
                    warningMessage = "Saved. Purchase warning: stock is below the total course requirement (${result.totalRequiredStock} needed)."
                )
            } else {
                onSaved()
            }
        }
    }

    private fun parseForm(form: MedicationFormState): ParsedMedication {
        val errors = mutableListOf<String>()
        val startDate = parseDate(form.startDate, "Start date", errors)
        val endDate = if (form.endDate.isBlank()) null else parseDate(form.endDate, "End date", errors)
        val doseAmount = form.doseAmount.toDoubleOrNull()
        val currentStock = form.currentStock.toDoubleOrNull()
        val lowStockThreshold = form.lowStockThresholdDays.toDoubleOrNull()

        if (doseAmount == null) errors += "Dose amount must be a number."
        if (currentStock == null) errors += "Current stock must be a number."
        if (lowStockThreshold == null) errors += "Low stock threshold must be a number."
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            errors += "End date must be on or after the start date."
        }
        if (errors.isNotEmpty() || startDate == null) {
            return ParsedMedication(errors = errors)
        }

        val medication = MedicationEntity(
            id = medicationId ?: 0L,
            name = form.name.trim(),
            dosageInstruction = form.dosageInstruction.trim(),
            doseAmount = doseAmount ?: 0.0,
            doseUnit = form.doseUnit.ifBlank { "unit" }.trim(),
            treatmentType = form.treatmentType,
            startDate = startDate,
            endDate = endDate,
            currentStock = currentStock ?: 0.0,
            totalRequiredStock = null,
            lowStockThresholdDays = lowStockThreshold ?: 1.0
        )
        val scheduleParse = parseSchedules(form)
        val schedules = scheduleParse.schedules
        errors += scheduleParse.errors
        errors += ValidationUtils.validateMedication(medication, schedules)
        return ParsedMedication(
            medication = medication,
            schedules = schedules,
            errors = errors.distinct()
        )
    }

    private fun parseSchedules(form: MedicationFormState): ParsedSchedules {
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
                            medicationId = medicationId ?: 0L,
                            scheduleType = ScheduleType.SPECIFIC_TIMES,
                            timeOfDay = time
                        )
                    },
                    errors = invalid.map { "Reminder time \"$it\" is not valid. Use a time like 08:00 or 8:00 AM." }
                )
            }
            ScheduleType.HOURLY_INTERVAL,
            ScheduleType.DAILY_INTERVAL,
            ScheduleType.WEEKLY_INTERVAL,
            ScheduleType.MONTHLY_INTERVAL -> {
                val interval = form.intervalValue.toIntOrNull()
                val errors = mutableListOf<String>()
                val firstTimeInput = form.reminderTimes.split(",").firstOrNull()?.trim().orEmpty()
                val normalizedTime = firstTimeInput.takeIf { it.isNotBlank() }
                    ?.let { ScheduleCalculator.normalizeTimeInput(it) }
                if (firstTimeInput.isNotBlank() && normalizedTime == null) {
                    errors += "Reminder time \"$firstTimeInput\" is not valid. Use a time like 09:00 or 9:00 AM."
                }

                val daysOfWeek = form.daysOfWeek.trim()
                if (form.scheduleType == ScheduleType.WEEKLY_INTERVAL) {
                    val parsedDays = com.meditrack.domain.InventoryCalculator.parseDaysOfWeek(daysOfWeek)
                    val tokens = daysOfWeek.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    if (tokens.isEmpty() || parsedDays.isEmpty() || parsedDays.size != tokens.size) {
                        errors += "Weekly schedule days must be 1-7 or names like Mon, Wed, Fri."
                    }
                }

                val dayOfMonth = form.dayOfMonth.toIntOrNull()
                if (form.scheduleType == ScheduleType.MONTHLY_INTERVAL && (dayOfMonth == null || dayOfMonth !in 1..31)) {
                    errors += "Monthly day must be between 1 and 31."
                }

                ParsedSchedules(
                    schedules = listOf(
                        MedicationScheduleEntity(
                            medicationId = medicationId ?: 0L,
                            scheduleType = form.scheduleType,
                            timeOfDay = normalizedTime,
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

    private fun parseDate(value: String, label: String, errors: MutableList<String>): LocalDate? {
        return runCatching { LocalDate.parse(value.trim()) }.getOrElse {
            errors += "$label must use YYYY-MM-DD."
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

data class MedicationFormState(
    val name: String,
    val dosageInstruction: String,
    val doseAmount: String,
    val doseUnit: String,
    val currentStock: String,
    val treatmentType: TreatmentType,
    val startDate: String,
    val endDate: String,
    val scheduleType: ScheduleType,
    val reminderTimes: String,
    val intervalValue: String,
    val daysOfWeek: String,
    val dayOfMonth: String,
    val lowStockThresholdDays: String,
    val errorMessage: String? = null,
    val warningMessage: String? = null
) {
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
                startDate = today,
                endDate = "",
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
            val times = if (scheduleType == ScheduleType.SPECIFIC_TIMES) {
                schedules.mapNotNull { it.timeOfDay }.joinToString(", ")
            } else {
                first?.timeOfDay ?: "09:00"
            }
            return MedicationFormState(
                name = medication.name,
                dosageInstruction = medication.dosageInstruction,
                doseAmount = medication.doseAmount.toString(),
                doseUnit = medication.doseUnit,
                currentStock = medication.currentStock.toString(),
                treatmentType = medication.treatmentType,
                startDate = medication.startDate.toString(),
                endDate = medication.endDate?.toString().orEmpty(),
                scheduleType = scheduleType,
                reminderTimes = times,
                intervalValue = first?.intervalValue?.toString() ?: "1",
                daysOfWeek = first?.daysOfWeek ?: "1,2,3,4,5,6,7",
                dayOfMonth = first?.dayOfMonth?.toString() ?: "1",
                lowStockThresholdDays = medication.lowStockThresholdDays.toString()
            )
        }
    }
}

private data class ParsedMedication(
    val medication: MedicationEntity? = null,
    val schedules: List<MedicationScheduleEntity> = emptyList(),
    val errors: List<String> = emptyList()
)

private data class ParsedSchedules(
    val schedules: List<MedicationScheduleEntity>,
    val errors: List<String> = emptyList()
)
