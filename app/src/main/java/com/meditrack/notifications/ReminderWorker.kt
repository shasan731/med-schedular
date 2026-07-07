package com.meditrack.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meditrack.AppGraph

/**
 * Periodic safety net. Exact alarms are the primary delivery mechanism, but the OS clears them on
 * reboot and can drop them over long idle periods. This worker runs about once a day to refresh the
 * dose-event horizon and re-arm the alarms, so reminders keep firing even if the app is never opened.
 */
class ReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        AppGraph.initialize(applicationContext)
        AppGraph.reminderScheduler.rescheduleMedicationReminders()
        return Result.success()
    }
}
