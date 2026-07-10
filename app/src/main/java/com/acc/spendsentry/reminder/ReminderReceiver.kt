package com.acc.spendsentry.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.acc.spendsentry.MainActivity
import com.acc.spendsentry.R

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        ensureChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Finish your spend note")
            .setContentText("A transaction was detected. Add merchant and purpose before you forget.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(MainActivity.openAppPendingIntent(context))
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Spend reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Reminds you to log detected card transactions"
            },
        )
    }

    companion object {
        const val CHANNEL_ID = "spend_sentry_reminders"
        const val NOTIFICATION_ID = 4101
    }
}
