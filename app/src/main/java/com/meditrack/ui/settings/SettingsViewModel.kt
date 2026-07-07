package com.meditrack.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.AppGraph
import com.meditrack.data.repository.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {
    private val settingsRepository = AppGraph.settingsRepository
    private val medicationRepository = AppGraph.medicationRepository
    private val scheduler = AppGraph.reminderScheduler

    val settings: StateFlow<AppSettings> = settingsRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settingsRepository.load())

    val exportJson = MutableStateFlow<String?>(null)
    val message = MutableStateFlow<String?>(null)

    fun saveLowStockThreshold(value: String) {
        val parsed = value.toDoubleOrNull()
        if (parsed == null || parsed < 0.0) {
            message.value = "Low-stock threshold must be 0 or greater."
            return
        }
        settingsRepository.updateDefaultLowStockThreshold(parsed)
        message.value = "Default low-stock threshold saved."
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateNotificationsEnabled(enabled)
            scheduler.rescheduleMedicationReminders()
        }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        settingsRepository.updateVibrationEnabled(enabled)
    }

    fun exportJson() {
        viewModelScope.launch {
            exportJson.value = medicationRepository.exportLocalDataAsJson()
            message.value = "Local data export generated below."
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            medicationRepository.clearAllData()
            scheduler.rescheduleMedicationReminders()
            exportJson.value = null
            message.value = "All local medication data cleared."
        }
    }
}
