package com.meditrack.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.AppGraph
import com.meditrack.data.local.entity.DoseEventWithMedication
import com.meditrack.domain.InventoryCalculator
import com.meditrack.ui.daysRemainingText
import com.meditrack.ui.displayDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel : ViewModel() {
    private val repository = AppGraph.medicationRepository
    private val scheduler = AppGraph.reminderScheduler
    // The displayed day is reactive so returning to the app after midnight rolls the timeline
    // to the new day instead of staying stuck on the day the view model was created.
    private val selectedDate = MutableStateFlow(LocalDate.now())

    val uiState = selectedDate.flatMapLatest { today ->
        combine(
            repository.observeDoseEventsForDate(today),
            repository.observeActiveMedications()
        ) { doses, medications ->
            val summaries = medications.associate { item ->
                item.medication.id to InventoryCalculator.buildSummary(item.medication, item.schedules)
            }
            val alerts = medications.mapNotNull { item ->
                val medication = item.medication
                // Only surface refill/out-of-stock alerts for medications that are actually active
                // today. A finished Fixed Course or a not-yet-started medication has nothing to refill.
                if (!medication.isWithinTreatmentWindow(today)) return@mapNotNull null
                val summary = summaries.getValue(medication.id)
                when {
                    summary.outOfStock -> StockAlert(
                        message = "${medication.name} is out of stock.",
                        severity = AlertSeverity.CRITICAL
                    )
                    summary.lowStock -> StockAlert(
                        message = "Refill soon: ${medication.name} has ${summary.daysRemaining.daysRemainingText()} remaining.",
                        severity = AlertSeverity.WARNING
                    )
                    else -> null
                }
            }
            // Single source of truth for the per-dose stock badges so they match the alert cards
            // and the Inventory screen instead of using a separate per-dose heuristic.
            val stockStatus = summaries.mapValues { (_, summary) ->
                when {
                    summary.outOfStock -> StockStatus.OUT
                    summary.lowStock -> StockStatus.LOW
                    else -> StockStatus.OK
                }
            }
            DashboardUiState(
                todayLabel = today.displayDate(),
                stockAlerts = alerts,
                doses = doses,
                stockStatus = stockStatus
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(todayLabel = LocalDate.now().displayDate())
    )

    init {
        refreshToday()
    }

    fun refreshToday() {
        viewModelScope.launch {
            val today = LocalDate.now()
            selectedDate.value = today
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

private fun com.meditrack.data.local.entity.MedicationEntity.isWithinTreatmentWindow(
    date: java.time.LocalDate
): Boolean {
    if (date.isBefore(startDate)) return false
    val end = endDate ?: return true
    return !date.isAfter(end)
}

data class DashboardUiState(
    val todayLabel: String,
    val stockAlerts: List<StockAlert> = emptyList(),
    val doses: List<DoseEventWithMedication> = emptyList(),
    val stockStatus: Map<Long, StockStatus> = emptyMap()
)

data class StockAlert(
    val message: String,
    val severity: AlertSeverity
)

enum class StockStatus {
    OK,
    LOW,
    OUT
}

enum class AlertSeverity {
    WARNING,
    CRITICAL
}
