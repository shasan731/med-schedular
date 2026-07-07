package com.meditrack.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.meditrack.MainActivity
import com.meditrack.R
import com.meditrack.data.repository.DueDosePayload

object NotificationHelper {
    const val CHANNEL_DOSE_REMINDERS = "dose_reminders"
    const val ACTION_MARK_TAKEN = "com.meditrack.action.MARK_TAKEN"
    const val ACTION_SKIP = "com.meditrack.action.SKIP"
    const val EXTRA_DOSE_EVENT_ID = "dose_event_id"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_DOSE_REMINDERS,
                "Dose reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Medication dose reminder notifications"
                enableVibration(true)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    fun showDoseReminder(
        context: Context,
        payload: DueDosePayload,
        vibrationEnabled: Boolean
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

        val builder = NotificationCompat.Builder(context, CHANNEL_DOSE_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Medication due: ${payload.medication.name}")
            .setContentText(payload.medication.dosageInstruction)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.medication.dosageInstruction))
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "Mark Taken", takenIntent)
            .addAction(0, "Skip", skipIntent)

        if (!vibrationEnabled) {
            builder.setVibrate(null)
        }

        NotificationManagerCompat.from(context)
            .notify(payload.doseEvent.id.toInt(), builder.build())
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
