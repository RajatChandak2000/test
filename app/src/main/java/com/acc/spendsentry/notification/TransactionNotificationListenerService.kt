package com.acc.spendsentry.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.acc.spendsentry.data.TransactionRepository
import com.acc.spendsentry.reminder.ReminderScheduler

class TransactionNotificationListenerService : NotificationListenerService() {
    private val repository by lazy { TransactionRepository(applicationContext) }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        repository.ingestNotification(
            sourcePackage = sbn.packageName,
            title = title,
            text = text,
            postedAtMillis = sbn.postTime,
        )?.let { draft ->
            ReminderScheduler.scheduleEndOfDayReminder(applicationContext, draft.id.hashCode())
        }
    }
}
