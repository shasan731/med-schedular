package com.meditrack.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.meditrack.AppGraph
import com.meditrack.domain.model.DoseStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fired by an exact alarm when a dose is due. Shows the reminder notification only if reminders are
 * still enabled and the dose is still pending, so stale or already-handled alarms simply do nothing.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val doseEventId = intent.getLongExtra(EXTRA_DOSE_EVENT_ID, -1L)
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
                    vibrationEnabled = settings.vibrationEnabled
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.meditrack.action.DOSE_ALARM"
        const val EXTRA_DOSE_EVENT_ID = "dose_event_id"
    }
}
