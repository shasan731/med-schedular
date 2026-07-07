package com.meditrack.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.meditrack.data.repository.MedicationRepository
import com.meditrack.data.repository.SettingsRepository
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.math.max

class ReminderScheduler(
    private val context: Context,
    private val medicationRepository: MedicationRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend fun rescheduleMedicationReminders() {
        val workManager = WorkManager.getInstance(context)
        if (!settingsRepository.load().notificationsEnabled) {
            workManager.cancelAllWorkByTag(WORK_TAG)
            return
        }

        val today = LocalDate.now()
        medicationRepository.refreshDoseEventsForRange(today, today.plusDays(7))

        val now = LocalDateTime.now()
        val upcoming = medicationRepository.upcomingPendingDoseEvents(
            now = now.minusMinutes(1),
            through = now.plusDays(7)
        )

        upcoming.forEach { event ->
            val delayMillis = max(0L, Duration.between(now, event.scheduledDateTime).toMillis())
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInputData(
                    Data.Builder()
                        .putLong(ReminderWorker.KEY_DOSE_EVENT_ID, event.id)
                        .build()
                )
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .addTag(WORK_TAG)
                .build()

            workManager.enqueueUniqueWork(
                "dose-reminder-${event.id}",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    companion object {
        private const val WORK_TAG = "meditrack-dose-reminders"
    }
}
