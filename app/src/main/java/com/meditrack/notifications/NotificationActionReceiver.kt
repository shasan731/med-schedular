package com.meditrack.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.meditrack.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val doseEventId = intent.getLongExtra(NotificationHelper.EXTRA_DOSE_EVENT_ID, -1L)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AppGraph.initialize(context)
                when (intent.action) {
                    NotificationHelper.ACTION_MARK_TAKEN -> {
                        AppGraph.medicationRepository.markDoseTaken(doseEventId)
                    }
                    NotificationHelper.ACTION_SKIP -> {
                        AppGraph.medicationRepository.markDoseSkipped(doseEventId)
                    }
                }
                NotificationHelper.cancelDoseNotification(context, doseEventId)
                AppGraph.reminderScheduler.rescheduleMedicationReminders()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
