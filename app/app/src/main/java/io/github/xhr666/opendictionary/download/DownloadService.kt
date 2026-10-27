package io.github.xhr666.opendictionary.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.xhr666.opendictionary.R

/** 前台下载服务：通知栏显示进度与速度，进程被回收时仍保留下载状态。 */
class DownloadService : Service() {
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra(EXTRA_ACTION) ?: ACTION_PROGRESS
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "下载中"
        val bytes = intent?.getLongExtra(EXTRA_BYTES, 0L) ?: 0L
        val total = intent?.getLongExtra(EXTRA_TOTAL, 0L) ?: 0L
        val speed = intent?.getLongExtra(EXTRA_SPEED, 0L) ?: 0L
        if (action == ACTION_PAUSE || action == ACTION_RESUME) {
            paused = action == ACTION_PAUSE
            if (started) { val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager; nm.notify(NOTIFICATION_ID, buildNotification(title, bytes, total, speed)) }
            return START_NOT_STICKY
        }
        if (action == ACTION_CANCEL) {
            cancelled = true; paused = false
            return START_NOT_STICKY
        }
        if (action == ACTION_FINISH) {
            val ok = intent?.getBooleanExtra(EXTRA_OK, true) ?: true
            val n = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(if (ok) "下载完成" else "下载失败")
                .setContentText(title).setAutoCancel(true).build()
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, n)
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
            return START_NOT_STICKY
        }
        val n = buildNotification(title, bytes, total, speed)
        startForeground(NOTIFICATION_ID, n)
        started = true
        return START_NOT_STICKY
    }

    private fun buildNotification(title: String, bytes: Long, total: Long, speed: Long): Notification {
        val pct = if (total > 0) ((bytes * 100) / total).toInt() else 0
        val speedText = formatSpeed(speed)
        val sizeText = "${formatBytes(bytes)} / ${formatBytes(total)} · $speedText"
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(sizeText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, pct, total <= 0)
        if (total > 0) b.setSubText("$pct%")
        if (paused) b.setContentText("$sizeText · 已暂停")
        val toggle = PendingIntent.getService(this, 3, Intent(this, DownloadService::class.java).setAction(if (paused) ACTION_RESUME else ACTION_PAUSE), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancel = PendingIntent.getService(this, 4, Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        b.addAction(0, if (paused) "继续" else "暂停", toggle)
        b.addAction(0, "取消", cancel)
        return b.build()
    }

    companion object {
        private const val CHANNEL = "od_downloads"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_ACTION = "action"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_BYTES = "bytes"
        private const val EXTRA_TOTAL = "total"
        private const val EXTRA_SPEED = "speed"
        private const val EXTRA_OK = "ok"
        private const val ACTION_PROGRESS = "progress"
        private const val ACTION_FINISH = "finish"
        private const val ACTION_PAUSE = "pause"
        private const val ACTION_RESUME = "resume"
        private const val ACTION_CANCEL = "cancel"

        @Volatile var paused = false
            private set
        @Volatile var cancelled = false
            private set

        fun resetControls() { paused = false; cancelled = false }
        fun isPaused(): Boolean = paused
        fun isCancelled(): Boolean = cancelled

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "下载", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "模型、词典资源与更新下载进度"
                    setShowBadge(false)
                })
            }
        }

        fun start(context: Context, title: String) = send(context, ACTION_PROGRESS, title, 0, 0, 0, true)

        fun progress(context: Context, title: String, bytes: Long, total: Long, speed: Long) =
            send(context, ACTION_PROGRESS, title, bytes, total, speed, true)

        fun finish(context: Context, title: String, ok: Boolean) =
            send(context, ACTION_FINISH, title, 0, 0, 0, ok)

        fun sendControl(context: Context, action: String) {
            runCatching { context.startService(Intent(context, DownloadService::class.java).setAction(action)) }
        }

        private fun send(context: Context, action: String, title: String, bytes: Long, total: Long, speed: Long, ok: Boolean) {
            runCatching {
                val i = Intent(context, DownloadService::class.java).apply {
                    putExtra(EXTRA_ACTION, action)
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_BYTES, bytes)
                    putExtra(EXTRA_TOTAL, total)
                    putExtra(EXTRA_SPEED, speed)
                    putExtra(EXTRA_OK, ok)
                }
                if (action == ACTION_FINISH) context.startService(i)
                else ContextCompat.startForegroundService(context, i)
            }
        }
    }
}

private fun formatBytes(v: Long): String = when {
    v >= 1024L * 1024 * 1024 -> "%.2f GB".format(v / 1024.0 / 1024 / 1024)
    v >= 1024L * 1024 -> "%.1f MB".format(v / 1024.0 / 1024)
    v >= 1024 -> "%.0f KB".format(v / 1024.0)
    else -> "$v B"
}

private fun formatSpeed(v: Long): String = if (v <= 0) "计算速度中…" else "${formatBytes(v)}/s"
