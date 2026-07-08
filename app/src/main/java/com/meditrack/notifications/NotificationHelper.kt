package com.meditrack.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.meditrack.MainActivity
import com.meditrack.R
import com.meditrack.data.local.entity.VaccinationEntity
import com.meditrack.data.repository.DueDosePayload
import com.meditrack.domain.model.FoodRelation
import com.meditrack.ui.labelRes


object NotificationHelper {
    const val CHANNEL_DOSE_REMINDERS = "dose_reminders"
    const val CHANNEL_DOSE_ALARMS = "dose_alarms"
    // Keeps vaccination notification ids from colliding with dose notification ids.
    private const val VACCINATION_NOTIF_OFFSET = 1_000_000L
    const val ACTION_MARK_TAKEN = "com.meditrack.action.MARK_TAKEN"
    const val ACTION_SKIP = "com.meditrack.action.SKIP"
    const val EXTRA_DOSE_EVENT_ID = "dose_event_id"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)

            // Standard reminder channel: default notification sound.
            val reminderChannel = NotificationChannel(
                CHANNEL_DOSE_REMINDERS,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_desc)
                enableVibration(true)
                // Explicitly play a sound (heads-up importance already implies one, but this makes
                // the reminder audible even if the default behaviour changes).
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
            }

            // Alarm channel: louder, alarm-stream ringtone for users who want an alarm-style alert.
            val alarmChannel = NotificationChannel(
                CHANNEL_DOSE_ALARMS,
                context.getString(R.string.notif_channel_alarm_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_alarm_desc)
                enableVibration(true)
                val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                setSound(
                    alarmSound,
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
            }

            manager.createNotificationChannel(reminderChannel)
            manager.createNotificationChannel(alarmChannel)
        }
    }

    fun showDoseReminder(
        context: Context,
        payload: DueDosePayload,
        vibrationEnabled: Boolean,
        useAlarmSound: Boolean = false
    ) {
        if (!canPostNotifications(context)) return

        val contentIntent = PendingIntent.getActivity(
            context,
            payload.doseEvent.id.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val takenIntent = actionPendingIntent(
            context = context,
            doseEventId = payload.doseEvent.id,
            action = ACTION_MARK_TAKEN,
            requestOffset = 10_000
        )
        val skipIntent = actionPendingIntent(
            context = context,
            doseEventId = payload.doseEvent.id,
            action = ACTION_SKIP,
            requestOffset = 20_000
        )

        val foodRelation = payload.medication.foodRelation
        val body = if (foodRelation != FoodRelation.NONE) {
            "${payload.medication.dosageInstruction}\n${context.getString(foodRelation.labelRes())}"
        } else {
            payload.medication.dosageInstruction
        }

        val channelId = if (useAlarmSound) CHANNEL_DOSE_ALARMS else CHANNEL_DOSE_REMINDERS
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_title, payload.medication.name))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.notif_action_taken), takenIntent)
            .addAction(0, context.getString(R.string.notif_action_skip), skipIntent)

        if (useAlarmSound) {
            builder.setCategory(NotificationCompat.CATEGORY_ALARM)
        }
        if (!vibrationEnabled) {
            builder.setVibrate(null)
        }

        NotificationManagerCompat.from(context)
            .notify(payload.doseEvent.id.toInt(), builder.build())
    }

    fun showVaccinationReminder(
        context: Context,
        vaccination: VaccinationEntity,
        vibrationEnabled: Boolean,
        useAlarmSound: Boolean = false
    ) {
        if (!canPostNotifications(context)) return

        val notificationId = (vaccination.id + VACCINATION_NOTIF_OFFSET).toInt()
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (useAlarmSound) CHANNEL_DOSE_ALARMS else CHANNEL_DOSE_REMINDERS
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_vaccination_title, vaccination.name))
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (vaccination.doseLabel.isNotBlank()) {
            builder.setContentText(vaccination.doseLabel)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(vaccination.doseLabel))
        }
        if (useAlarmSound) {
            builder.setCategory(NotificationCompat.CATEGORY_ALARM)
        }
        if (!vibrationEnabled) {
            builder.setVibrate(null)
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    fun cancelDoseNotification(context: Context, doseEventId: Long) {
        NotificationManagerCompat.from(context).cancel(doseEventId.toInt())
    }

    fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun actionPendingIntent(
        context: Context,
        doseEventId: Long,
        action: String,
        requestOffset: Int
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_DOSE_EVENT_ID, doseEventId)
        }
        return PendingIntent.getBroadcast(
            context,
            doseEventId.toInt() + requestOffset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
