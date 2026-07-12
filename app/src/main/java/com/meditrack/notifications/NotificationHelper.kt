package com.meditrack.notifications

import android.Manifest
import android.annotation.SuppressLint
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
    private const val CHANNEL_DOSE_REMINDERS_NO_VIBRATION = "dose_reminders_no_vibration"
    private const val CHANNEL_DOSE_ALARMS_NO_VIBRATION = "dose_alarms_no_vibration"
    // Keeps vaccination notification ids from colliding with dose notification ids.
    private const val VACCINATION_NOTIF_OFFSET = 1_000_000L
    const val ACTION_MARK_TAKEN = "com.meditrack.action.MARK_TAKEN"
    const val ACTION_SKIP = "com.meditrack.action.SKIP"
    const val EXTRA_DOSE_EVENT_ID = "dose_event_id"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                buildChannel(context, CHANNEL_DOSE_REMINDERS, false, true),
                buildChannel(context, CHANNEL_DOSE_ALARMS, true, true),
                buildChannel(context, CHANNEL_DOSE_REMINDERS_NO_VIBRATION, false, false),
                buildChannel(context, CHANNEL_DOSE_ALARMS_NO_VIBRATION, true, false)
            )
        )
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

        val channelId = channelId(useAlarmSound, vibrationEnabled)
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
        postNotification(context, payload.doseEvent.id.toInt(), builder.build())
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

        val channelId = channelId(useAlarmSound, vibrationEnabled)
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
        postNotification(context, notificationId, builder.build())
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

    private fun buildChannel(
        context: Context,
        id: String,
        useAlarmSound: Boolean,
        vibrationEnabled: Boolean
    ): NotificationChannel {
        val nameRes = when {
            useAlarmSound && vibrationEnabled -> R.string.notif_channel_alarm_name
            useAlarmSound -> R.string.notif_channel_alarm_no_vibration_name
            vibrationEnabled -> R.string.notif_channel_name
            else -> R.string.notif_channel_no_vibration_name
        }
        val descriptionRes = if (useAlarmSound) {
            R.string.notif_channel_alarm_desc
        } else {
            R.string.notif_channel_desc
        }
        val sound = if (useAlarmSound) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
        val usage = if (useAlarmSound) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION
        return NotificationChannel(
            id,
            context.getString(nameRes),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(descriptionRes)
            enableVibration(vibrationEnabled)
            setSound(
                sound,
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(usage)
                    .build()
            )
        }
    }

    private fun channelId(useAlarmSound: Boolean, vibrationEnabled: Boolean): String {
        return when {
            useAlarmSound && vibrationEnabled -> CHANNEL_DOSE_ALARMS
            useAlarmSound -> CHANNEL_DOSE_ALARMS_NO_VIBRATION
            vibrationEnabled -> CHANNEL_DOSE_REMINDERS
            else -> CHANNEL_DOSE_REMINDERS_NO_VIBRATION
        }
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(context: Context, id: Int, notification: android.app.Notification) {
        if (!canPostNotifications(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the explicit check and the system call.
        }
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
