package io.github.xhr666.opendictionary.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.xhr666.opendictionary.MainActivity
import io.github.xhr666.opendictionary.R

/** 后台媒体播放：MediaSession + MediaStyle 通知 + 前台服务，支持锁屏/通知播放控制。 */
class PlaybackService : Service() {
    private lateinit var session: MediaSession
    private val handler = Handler(Looper.getMainLooper())
    private var ticker: Runnable? = null
    private var started = false
    private var idleTicks = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
        session = MediaSession(this, "OpenDictionaryPlayback").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { if (!MediaPlayback.playing) MediaPlayback.togglePause(); update() }
                override fun onPause() { if (MediaPlayback.playing) MediaPlayback.togglePause(); update() }
                override fun onStop() { MediaPlayback.stop(); update() }
                override fun onSeekTo(pos: Long) { MediaPlayback.seekTo(pos); update() }
            })
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> { MediaPlayback.togglePause(); update() }
            ACTION_STOP -> { MediaPlayback.stop(); update(); stopSelf() }
            else -> update()
        }
        if (!started) { startForeground(NOTIFICATION_ID, buildNotification()); started = true }
        if (ticker == null) {
            ticker = object : Runnable {
                override fun run() {
                    if (MediaPlayback.title.isBlank()) {
                        if (!MediaPlayback.playing) { stopSelf() } else handler.postDelayed(this, 1000)
                    } else {
                        if (!MediaPlayback.playing) {
                            idleTicks++
                            if (idleTicks >= 10) { MediaPlayback.stop(); stopSelf(); return }
                        } else idleTicks = 0
                        update(); handler.postDelayed(this, 500)
                    }
                }
            }
            handler.post(ticker!!)
        }
        return START_NOT_STICKY
    }

    private fun update() {
        if (!started) return
        val n = buildNotification()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, n)
        val playState = if (MediaPlayback.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        session.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP).setState(playState, MediaPlayback.positionMs, 1f).build())
        session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, MediaPlayback.title).build())
    }

    private fun buildNotification(): Notification {
        val content = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val toggle = PendingIntent.getService(this, 1, Intent(this, PlaybackService::class.java).setAction(ACTION_TOGGLE), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 2, Intent(this, PlaybackService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val compact = if (MediaPlayback.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(MediaPlayback.title.ifBlank { "OpenDictionary" })
            .setContentText(MediaPlayback.clip?.lineText ?: "本地媒体播放")
            .setContentIntent(content)
            .setOngoing(MediaPlayback.playing)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, compact), if (MediaPlayback.playing) "暂停" else "播放", toggle).build())
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), "停止", stop).build())
            .setStyle(Notification.MediaStyle().setShowActionsInCompactView(0, 1).setMediaSession(session.sessionToken))
        runCatching { session.setSessionActivity(content) }
        return builder.build()
    }

    override fun onDestroy() {
        ticker?.let { handler.removeCallbacks(it) }
        session.release()
        if (started) stopForeground(STOP_FOREGROUND_REMOVE)
        started = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "od_playback"
        private const val NOTIFICATION_ID = 1002
        private const val ACTION_TOGGLE = "toggle"
        private const val ACTION_STOP = "stop"

        fun start(context: Context) {
            createChannel(context)
            runCatching { ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java)) }
        }

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "媒体播放", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "本地音乐与歌词播放控制"
                    setShowBadge(false)
                })
            }
        }
    }
}
