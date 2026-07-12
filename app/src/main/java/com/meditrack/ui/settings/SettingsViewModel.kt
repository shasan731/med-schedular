package com.meditrack.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.AppGraph
import com.meditrack.R
import com.meditrack.data.repository.AppSettings
import com.meditrack.data.repository.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {
    private val settingsRepository = AppGraph.settingsRepository
    private val medicationRepository = AppGraph.medicationRepository
    private val vaccinationRepository = AppGraph.vaccinationRepository
    private val scheduler = AppGraph.reminderScheduler

    val settings: StateFlow<AppSettings> = settingsRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settingsRepository.load())

    val exportJson = MutableStateFlow<String?>(null)
    val message = MutableStateFlow<String?>(null)

    fun saveLowStockThreshold(value: String) {
        val parsed = value.toDoubleOrNull()
        if (parsed == null || parsed < 0.0) {
            message.value = AppGraph.appContext.getString(R.string.msg_low_stock_invalid)
            return
        }
        settingsRepository.updateDefaultLowStockThreshold(parsed)
        message.value = AppGraph.appContext.getString(R.string.msg_low_stock_saved)
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

    fun setAlarmSoundEnabled(enabled: Boolean) {
        settingsRepository.updateAlarmSoundEnabled(enabled)
    }

    fun setThemeMode(mode: ThemeMode) {
        settingsRepository.updateThemeMode(mode)
    }

    fun exportJson() {
        exportJson.value = null
        viewModelScope.launch {
            exportJson.value = medicationRepository.exportLocalDataAsJson(settingsRepository.load())
            message.value = AppGraph.appContext.getString(R.string.msg_export_generated)
        }
    }

    fun setMessage(value: String) {
        message.value = value
    }

    fun clearAllData() {
        viewModelScope.launch {
            medicationRepository.clearAllData()
            vaccinationRepository.clearAll()
            scheduler.rescheduleMedicationReminders()
            exportJson.value = null
            message.value = AppGraph.appContext.getString(R.string.msg_cleared)
        }
    }
}
