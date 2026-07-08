package com.meditrack.ui.vaccination

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.meditrack.AppGraph
import com.meditrack.R
import com.meditrack.data.local.entity.VaccinationEntity
import com.meditrack.domain.ScheduleCalculator
import com.meditrack.domain.model.VaccinationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AddEditVaccinationViewModel(
    private val vaccinationId: Long?
) : ViewModel() {
    private val repository = AppGraph.vaccinationRepository
    private val scheduler = AppGraph.reminderScheduler
    private var currentId: Long? = vaccinationId

    private val _state = MutableStateFlow(VaccinationFormState.default())
    val state: StateFlow<VaccinationFormState> = _state.asStateFlow()

    init {
        if (vaccinationId != null && vaccinationId > 0L) {
            viewModelScope.launch {
                repository.getVaccination(vaccinationId)?.let {
                    _state.value = VaccinationFormState.fromExisting(it)
                }
            }
        }
    }

    fun update(transform: (VaccinationFormState) -> VaccinationFormState) {
        _state.value = transform(_state.value).copy(errorMessage = null)
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val form = _state.value
            val name = form.name.trim()
            val date = runCatching { LocalDate.parse(form.date.trim()) }.getOrNull()
            val timeInput = form.time.trim()
            val normalizedTime = if (timeInput.isBlank()) "09:00" else ScheduleCalculator.normalizeTimeInput(timeInput)

            val errors = mutableListOf<String>()
            if (name.isBlank()) errors += str(R.string.vacc_val_name)
            if (date == null) errors += str(R.string.val_date_invalid)
            if (normalizedTime == null) errors += str(R.string.val_reminder_time_invalid, timeInput)

            if (errors.isNotEmpty() || date == null || normalizedTime == null) {
                _state.value = form.copy(errorMessage = errors.joinToString("\n").ifBlank { str(R.string.val_date_invalid) })
                return@launch
            }

            val scheduledDateTime = LocalDateTime.of(date, LocalTime.parse(normalizedTime))
            val entity = VaccinationEntity(
                id = currentId ?: 0L,
                name = name,
                doseLabel = form.doseLabel.trim(),
                scheduledDateTime = scheduledDateTime,
                status = form.status,
                note = form.note.trim().ifBlank { null }
            )
            currentId = repository.saveVaccination(entity)
            scheduler.rescheduleMedicationReminders()
            onSaved()
        }
    }

    private fun str(@StringRes id: Int, vararg args: Any): String =
        AppGraph.appContext.getString(id, *args)

    companion object {
        fun factory(vaccinationId: Long?): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AddEditVaccinationViewModel(vaccinationId) as T
                }
            }
        }
    }
}

data class VaccinationFormState(
    val name: String,
    val doseLabel: String,
    val date: String,
    val time: String,
    val note: String,
    val status: VaccinationStatus = VaccinationStatus.UPCOMING,
    val errorMessage: String? = null
) {
    companion object {
        fun default(): VaccinationFormState = VaccinationFormState(
            name = "",
            doseLabel = "",
            date = LocalDate.now().toString(),
            time = "09:00",
            note = ""
        )

        fun fromExisting(vaccination: VaccinationEntity): VaccinationFormState = VaccinationFormState(
            name = vaccination.name,
            doseLabel = vaccination.doseLabel,
            date = vaccination.scheduledDateTime.toLocalDate().toString(),
            time = vaccination.scheduledDateTime.toLocalTime().toString(),
            note = vaccination.note.orEmpty(),
            status = vaccination.status
        )
    }
}
