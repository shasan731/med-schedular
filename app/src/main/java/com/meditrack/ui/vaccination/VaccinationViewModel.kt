package com.meditrack.ui.vaccination

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.AppGraph
import com.meditrack.data.local.entity.VaccinationEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VaccinationViewModel : ViewModel() {
    private val repository = AppGraph.vaccinationRepository
    private val scheduler = AppGraph.reminderScheduler

    val uiState = repository.observeVaccinations()
        .map { VaccinationUiState(items = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = VaccinationUiState()
        )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            repository.markOverdueMissed()
            scheduler.rescheduleMedicationReminders()
        }
    }

    fun markDone(id: Long) {
        viewModelScope.launch {
            repository.markDone(id)
            scheduler.rescheduleMedicationReminders()
        }
    }

    fun markUpcoming(id: Long) {
        viewModelScope.launch {
            repository.markUpcoming(id)
            scheduler.rescheduleMedicationReminders()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repository.deleteVaccination(id)
            scheduler.rescheduleMedicationReminders()
        }
    }
}

data class VaccinationUiState(
    val items: List<VaccinationEntity> = emptyList()
)
