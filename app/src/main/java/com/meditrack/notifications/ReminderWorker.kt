package com.meditrack.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meditrack.AppGraph
import com.meditrack.domain.model.DoseStatus

class ReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        AppGraph.initialize(applicationContext)
        val doseEventId = inputData.getLong(KEY_DOSE_EVENT_ID, -1L)
        if (doseEventId <= 0) return Result.failure()

        val settings = AppGraph.settingsRepository.load()
        if (!settings.notificationsEnabled) return Result.success()

        val payload = AppGraph.medicationRepository.getDueDosePayload(doseEventId)
            ?: return Result.success()
        if (payload.doseEvent.status != DoseStatus.PENDING) return Result.success()

        NotificationHelper.showDoseReminder(
            context = applicationContext,
            payload = payload,
            vibrationEnabled = settings.vibrationEnabled
        )
        return Result.success()
    }

    companion object {
        const val KEY_DOSE_EVENT_ID = "dose_event_id"
    }
}
