package com.meditrack.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.AppGraph
import com.meditrack.data.local.entity.MedicationEntity
import com.meditrack.domain.InventoryCalculator
import com.meditrack.domain.MedicationSummary
import com.meditrack.domain.ScheduleCalculator
import com.meditrack.domain.model.DoseStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InventoryViewModel : ViewModel() {
    private val repository = AppGraph.medicationRepository
    private val scheduler = AppGraph.reminderScheduler

    val uiState = combine(
        repository.observeActiveMedications(),
        repository.observeAllDoseEvents()
    ) { medications, doseEvents ->
        val takenCounts = doseEvents
            .filter { it.status == DoseStatus.TAKEN }
            .groupingBy { it.medicationId }
            .eachCount()

        InventoryUiState(
            items = medications.map { item ->
                val summary = InventoryCalculator.buildSummary(
                    medication = item.medication,
                    schedules = item.schedules,
                    takenDoseCount = takenCounts[item.medication.id]
                )
                InventoryItemUi(
                    medication = item.medication,
                    scheduleSummary = ScheduleCalculator.scheduleSummary(item.schedules),
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
