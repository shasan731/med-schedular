package com.meditrack.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("meditrack_settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())

    fun observeSettings(): StateFlow<AppSettings> = state.asStateFlow()

    fun updateDefaultLowStockThreshold(days: Double) {
        prefs.edit().putFloat(KEY_LOW_STOCK_DAYS, days.toFloat()).apply()
        state.value = load()
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
        state.value = load()
    }

    fun updateVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
        state.value = load()
    }

    fun updateThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        state.value = load()
    }

    fun load(): AppSettings {
        return AppSettings(
            defaultLowStockThresholdDays = prefs.getFloat(KEY_LOW_STOCK_DAYS, 1f).toDouble(),
            notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true),
            vibrationEnabled = prefs.getBoolean(KEY_VIBRATION_ENABLED, true),
            themeMode = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
                ?.let { value -> runCatching { ThemeMode.valueOf(value) }.getOrNull() }
                ?: ThemeMode.SYSTEM
        )
    }

    private companion object {
        const val KEY_LOW_STOCK_DAYS = "low_stock_days"
        const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        const val KEY_THEME_MODE = "theme_mode"
    }
}

data class AppSettings(
    val defaultLowStockThresholdDays: Double,
    val notificationsEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val themeMode: ThemeMode
)

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}
