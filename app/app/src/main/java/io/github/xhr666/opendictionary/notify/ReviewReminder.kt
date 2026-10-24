package io.github.xhr666.opendictionary.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import io.github.xhr666.opendictionary.MainActivity
import io.github.xhr666.opendictionary.R
import io.github.xhr666.opendictionary.data.UserDb
import java.util.Calendar

object ReviewReminder {
    private const val CHANNEL = "od_reminder"
    private const val REQUEST = 3001
    private const val CHANNEL_NAME = "复习提醒"

    fun schedule(context: Context, enabled: Boolean) {
        createChannel(context)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context)
        if (!enabled) { am.cancel(pi); return }
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        runCatching {
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
        }
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST, Intent(context, ReviewReminderReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "每日复习提醒"
            })
        }
    }

    fun notifyNow(context: Context) {
        createChannel(context)
        val count = runCatching { UserDb.get(context).wordbookWords().size }.getOrDefault(0)
        val content = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("今天的词汇复习")
            .setContentText(if (count > 0) "生词本里有 $count 个词，花几分钟复习一下吧" else "打开 OpenDictionary，继续今天的学习")
            .setContentIntent(content)
            .setAutoCancel(true)
            .build()
        runCatching { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(3002, n) }
    }
}

class ReviewReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (context.getSharedPreferences("od_prefs", Context.MODE_PRIVATE).getBoolean("review_reminder", false)) {
            ReviewReminder.notifyNow(context)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (context.getSharedPreferences("od_prefs", Context.MODE_PRIVATE).getBoolean("review_reminder", false)) {
            ReviewReminder.schedule(context, true)
        }
    }
}
