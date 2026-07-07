package com.meditrack.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.meditrack.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AppGraph.initialize(context)
                val today = LocalDate.now()
                AppGraph.medicationRepository.refreshDoseEventsForRange(today, today.plusDays(7))
                AppGraph.reminderScheduler.rescheduleMedicationReminders()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
