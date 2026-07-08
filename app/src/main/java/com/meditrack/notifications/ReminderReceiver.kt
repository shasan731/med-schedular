package com.meditrack.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.meditrack.AppGraph
import com.meditrack.domain.model.DoseStatus
import com.meditrack.domain.model.VaccinationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fired by an exact alarm when a dose or vaccination is due. Shows the reminder only if reminders are
 * still enabled and the item is still pending, so stale or already-handled alarms simply do nothing.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> handleDose(context, intent.getLongExtra(EXTRA_DOSE_EVENT_ID, -1L))
            ACTION_FIRE_VACCINATION -> handleVaccination(context, intent.getLongExtra(EXTRA_VACCINATION_ID, -1L))
        }
    }

    private fun handleDose(context: Context, doseEventId: Long) {
        if (doseEventId <= 0L) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AppGraph.initialize(context)
                val settings = AppGraph.settingsRepository.load()
                if (!settings.notificationsEnabled) return@launch

                val payload = AppGraph.medicationRepository.getDueDosePayload(doseEventId)
                    ?: return@launch
                if (payload.doseEvent.status != DoseStatus.PENDING) return@launch

                NotificationHelper.showDoseReminder(
                    context = context,
                    payload = payload,
                    vibrationEnabled = settings.vibrationEnabled,
                    useAlarmSound = settings.alarmSoundEnabled
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleVaccination(context: Context, vaccinationId: Long) {
        if (vaccinationId <= 0L) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AppGraph.initialize(context)
                val settings = AppGraph.settingsRepository.load()
                if (!settings.notificationsEnabled) return@launch

                val vaccination = AppGraph.vaccinationRepository.getVaccination(vaccinationId)
                    ?: return@launch
                if (vaccination.status != VaccinationStatus.UPCOMING) return@launch

                NotificationHelper.showVaccinationReminder(
                    context = context,
                    vaccination = vaccination,
                    vibrationEnabled = settings.vibrationEnabled,
                    useAlarmSound = settings.alarmSoundEnabled
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.meditrack.action.DOSE_ALARM"
        const val ACTION_FIRE_VACCINATION = "com.meditrack.action.VACCINATION_ALARM"
        const val EXTRA_DOSE_EVENT_ID = "dose_event_id"
        const val EXTRA_VACCINATION_ID = "vaccination_id"
    }
}
