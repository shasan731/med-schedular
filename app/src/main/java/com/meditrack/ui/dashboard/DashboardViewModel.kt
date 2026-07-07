package com.meditrack.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.AppGraph
import com.meditrack.data.local.entity.DoseEventWithMedication
import com.meditrack.domain.InventoryCalculator
import com.meditrack.ui.daysRemainingText
import com.meditrack.ui.displayDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class DashboardViewModel : ViewModel() {
    private val repository = AppGraph.medicationRepository
    private val scheduler = AppGraph.reminderScheduler
    private val today = LocalDate.now()

    val uiState = combine(
        repository.observeDoseEventsForDate(today),
        repository.observeActiveMedications()
    ) { doses, medications ->
        val warnings = medications.mapNotNull { item ->
            val summary = InventoryCalculator.buildSummary(item.medication, item.schedules)
            when {
                summary.outOfStock -> "${item.medication.name} is out of stock."
                summary.lowStock -> "Refill soon: ${item.medication.name} has ${summary.daysRemaining.daysRemainingText()} remaining."
                else -> null
            }
        }
        DashboardUiState(
            todayLabel = today.displayDate(),
            lowStockWarnings = warnings,
            doses = doses
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(todayLabel = today.displayDate())
    )

    init {
        refreshToday()
    }

    fun refreshToday() {
        viewModelScope.launch {
            repository.refreshTodayDoseEvents(today)
            repository.markOverdueDosesMissed()
            scheduler.rescheduleMedicationReminders()
        }
    }

    fun markTaken(doseEventId: Long) {
        viewModelScope.launch {
            repository.markDoseTaken(doseEventId)
            scheduler.rescheduleMedicationReminders()
        }
    }

    fun skip(doseEventId: Long) {
        viewModelScope.launch {
            repository.markDoseSkipped(doseEventId)
            scheduler.rescheduleMedicationReminders()
        }
    }
}

data class DashboardUiState(
    val todayLabel: String,
    val lowStockWarnings: List<String> = emptyList(),
    val doses: List<DoseEventWithMedication> = emptyList()
)
