package com.meditrack.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.meditrack.AppGraph
import com.meditrack.data.local.entity.DoseEventEntity
import com.meditrack.data.local.entity.MedicationEntity
import com.meditrack.domain.InventoryCalculator
import com.meditrack.domain.MedicationSummary
import com.meditrack.domain.ScheduleCalculator
import com.meditrack.domain.model.DoseStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MedicationDetailViewModel(
    private val medicationId: Long
) : ViewModel() {
    private val repository = AppGraph.medicationRepository

    fun refill(addedAmount: Double) {
        viewModelScope.launch {
            repository.refillMedication(medicationId, addedAmount)
        }
    }

    val uiState = combine(
        repository.observeMedication(medicationId),
        repository.observeDoseHistory(medicationId)
    ) { medicationWithSchedules, history ->
        if (medicationWithSchedules == null) {
            MedicationDetailUiState()
        } else {
            val takenCount = history.count { it.status == DoseStatus.TAKEN }
            MedicationDetailUiState(
                medication = medicationWithSchedules.medication,
                scheduleSummary = ScheduleCalculator.scheduleSummary(medicationWithSchedules.schedules),
                summary = InventoryCalculator.buildSummary(
                    medication = medicationWithSchedules.medication,
                    schedules = medicationWithSchedules.schedules,
                    takenDoseCount = takenCount
                ),
                history = history
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MedicationDetailUiState()
    )

    companion object {
        fun factory(medicationId: Long): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MedicationDetailViewModel(medicationId) as T
                }
            }
        }
    }
}

data class MedicationDetailUiState(
    val medication: MedicationEntity? = null,
    val scheduleSummary: String = "",
    val summary: MedicationSummary? = null,
    val history: List<DoseEventEntity> = emptyList()
)
