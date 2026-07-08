package com.meditrack.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.meditrack.data.repository.MedicationRepository
import com.meditrack.data.repository.SettingsRepository
import com.meditrack.data.repository.VaccinationRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Schedules dose reminders as exact alarms so they fire on time even in Doze / battery-saver,
 * which plain WorkManager cannot guarantee. A daily WorkManager job re-arms the alarm horizon so
 * reminders keep working even if the app is not opened for a long time or the OS clears alarms.
 */
class ReminderScheduler(
    private val context: Context,
    private val medicationRepository: MedicationRepository,
    private val vaccinationRepository: VaccinationRepository,
    private val settingsRepository: SettingsRepository
) {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("meditrack_alarms", Context.MODE_PRIVATE)

    suspend fun rescheduleMedicationReminders() {
        val today = LocalDate.now()
        medicationRepository.refreshDoseEventsForRange(today, today.plusDays(HORIZON_DAYS))
        vaccinationRepository.markOverdueMissed()
        ensurePeriodicRefresh()

        // Cancel every alarm we armed last time so stale, taken, skipped, or deleted items do not
        // keep waking the device. The sets are persisted because AlarmManager has no "cancel all".
        prefs.getStringSet(KEY_ARMED_IDS, emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.forEach { cancelAlarm(firePendingIntent(it)) }
        prefs.getStringSet(KEY_ARMED_VACC_IDS, emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.forEach { cancelAlarm(vaccinationPendingIntent(it)) }

        if (!settingsRepository.load().notificationsEnabled) {
            prefs.edit()
                .putStringSet(KEY_ARMED_IDS, emptySet())
                .putStringSet(KEY_ARMED_VACC_IDS, emptySet())
                .apply()
            return
        }

        val now = LocalDateTime.now()

        val upcoming = medicationRepository.upcomingPendingDoseEvents(
            now = now.minusMinutes(1),
            through = now.plusDays(HORIZON_DAYS)
        )
        val armed = mutableSetOf<String>()
        upcoming.forEach { event ->
            armExactAlarm(firePendingIntent(event.id), event.scheduledDateTime)
            armed += event.id.toString()
        }

        // Vaccinations are dated one-off events and may be scheduled well beyond the dose horizon,
        // so arm an alarm for every upcoming one regardless of how far away it is.
        val vaccinations = vaccinationRepository.upcomingVaccinations(now)
        val armedVacc = mutableSetOf<String>()
        vaccinations.forEach { vaccination ->
            armExactAlarm(vaccinationPendingIntent(vaccination.id), vaccination.scheduledDateTime)
            armedVacc += vaccination.id.toString()
        }

        prefs.edit()
            .putStringSet(KEY_ARMED_IDS, armed)
            .putStringSet(KEY_ARMED_VACC_IDS, armedVacc)
            .apply()
    }

    private fun armExactAlarm(pendingIntent: PendingIntent, at: LocalDateTime) {
        val triggerAtMillis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // canScheduleExactAlarms() can be false if the user revoked the permission (API 31-32).
        // Fall back to an inexact-but-idle-allowed alarm so a reminder still fires, just not exactly.
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        try {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (security: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancelAlarm(pendingIntent: PendingIntent) {
        alarmManager.cancel(pendingIntent)
    }

    private fun firePendingIntent(doseEventId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
            putExtra(ReminderReceiver.EXTRA_DOSE_EVENT_ID, doseEventId)
        }
        return PendingIntent.getBroadcast(
            context,
            doseEventId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun vaccinationPendingIntent(vaccinationId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE_VACCINATION
            putExtra(ReminderReceiver.EXTRA_VACCINATION_ID, vaccinationId)
        }
        return PendingIntent.getBroadcast(
            context,
            vaccinationId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensurePeriodicRefresh() {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        const val HORIZON_DAYS = 7L
        private const val KEY_ARMED_IDS = "armed_dose_event_ids"
        private const val KEY_ARMED_VACC_IDS = "armed_vaccination_ids"
        private const val PERIODIC_WORK_NAME = "meditrack-reminder-refresh"
    }
}
