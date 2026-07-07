package com.meditrack

import android.app.Application
import com.meditrack.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MediTrackApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AppGraph.initialize(this)
        NotificationHelper.createChannels(this)
        appScope.launch {
            AppGraph.medicationRepository.refreshDoseEventsForRange(
                java.time.LocalDate.now(),
                java.time.LocalDate.now().plusDays(7)
            )
            AppGraph.reminderScheduler.rescheduleMedicationReminders()
        }
    }
}
