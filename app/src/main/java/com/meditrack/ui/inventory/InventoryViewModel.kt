package com.meditrack.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.AppGraph
import com.meditrack.data.local.entity.MedicationEntity
import com.meditrack.domain.InventoryCalculator
import com.meditrack.domain.MedicationSummary
import com.meditrack.domain.model.DoseStatus
import com.meditrack.ui.scheduleSummaryText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InventoryViewModel : ViewModel() {
    private val repository = AppGraph.medicationRepository
    private val scheduler = AppGraph.reminderScheduler

    val uiState = combine(
        repository.observeAllMedications(),
        repository.observeAllDoseEvents()
    ) { medications, doseEvents ->
        InventoryUiState(
            items = medications.map { item ->
                val takenEvents = doseEvents.filter { event ->
                    event.medicationId == item.medication.id &&
                        event.status == DoseStatus.TAKEN &&
                        !event.scheduledDateTime.toLocalDate().isBefore(item.medication.startDate) &&
                        (item.medication.endDate == null ||
                            !event.scheduledDateTime.toLocalDate().isAfter(item.medication.endDate))
                }
                val summary = InventoryCalculator.buildSummary(
                    medication = item.medication,
                    schedules = item.schedules,
                    takenDoseCount = takenEvents.size,
                    takenDoseAmount = takenEvents.sumOf { it.doseAmount }
                )
                InventoryItemUi(
                    medication = item.medication,
                    scheduleSummary = scheduleSummaryText(AppGraph.appContext, item.schedules),
                    summary = summary
                )
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InventoryUiState()
    )

    fun refill(id: Long, addedAmount: Double) {
        viewModelScope.launch {
            repository.refillMedication(id, addedAmount)
        }
    }

    fun disableMedication(id: Long) {
        viewModelScope.launch {
            repository.disableMedication(id)
            scheduler.rescheduleMedicationReminders()
        }
    }

    fun reactivateMedication(id: Long) {
        viewModelScope.launch {
            repository.reactivateMedication(id)
            scheduler.rescheduleMedicationReminders()
        }
    }

    fun deleteMedication(id: Long) {
        viewModelScope.launch {
            repository.deleteMedication(id)
            scheduler.rescheduleMedicationReminders()
        }
    }
}

data class InventoryUiState(
    val items: List<InventoryItemUi> = emptyList()
)

data class InventoryItemUi(
    val medication: MedicationEntity,
    val scheduleSummary: String,
    val summary: MedicationSummary
)
