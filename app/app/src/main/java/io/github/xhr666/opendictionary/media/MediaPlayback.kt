package io.github.xhr666.opendictionary.media

import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.xhr666.opendictionary.data.MediaClip

/** 片段/整曲播放；位置状态供歌词高亮轮询。 */
object MediaPlayback {
    private var player: MediaPlayer? = null
    private var speed = 1f
    private var focusRequest: AudioFocusRequest? = null
    private var appContext: Context? = null

    private fun requestFocus(context: Context): Boolean {
        val am = context.getSystemService(AUDIO_SERVICE) as AudioManager
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) { player?.pause(); playing = false }
            }.build()
        focusRequest = req
        return am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus(context: Context) {
        val am = context.getSystemService(AUDIO_SERVICE) as AudioManager
        focusRequest?.let { am.abandonAudioFocusRequest(it) }
        focusRequest = null
    }
    var title by mutableStateOf("")
        private set
    var playing by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var startMs by mutableLongStateOf(0L)
        private set
    var endMs by mutableLongStateOf(0L)
        private set
    var clip by mutableStateOf<MediaClip?>(null)
        private set
    var mediaId by mutableLongStateOf(-1L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set

    fun playTrack(context: Context, id: Long, uri: String, title: String, duration: Long) {
        stop()
        requestFocus(context)
        this.mediaId = id
        this.title = title
        durationMs = duration
        startMs = 0; endMs = duration
        val p = MediaPlayer()
        player = p
        runCatching {
            p.setDataSource(context, Uri.parse(uri))
            p.prepare()
            p.setOnCompletionListener { playing = false }
            p.start()
            applySpeed(p)
            playing = true
            positionMs = 0
            PlaybackService.start(context)
        }.onFailure { playing = false }
    }

    fun togglePause() {
        val p = player ?: return
        runCatching { if (p.isPlaying) { p.pause(); playing = false } else { p.start(); playing = true } }
    }

    fun setSpeed(value: Float) {
        speed = value.coerceIn(0.5f, 1.5f)
        player?.let { applySpeed(it) }
    }

    fun currentSpeed(): Float = speed

    private fun applySpeed(p: MediaPlayer) {
        runCatching { p.playbackParams = android.media.PlaybackParams().setSpeed(speed) }
    }

    fun seekTo(ms: Long) {
        val p = player ?: return
        runCatching { p.seekTo(ms.toInt()); positionMs = ms }
    }

    fun play(context: Context, clip: MediaClip) {
        stop()
        requestFocus(context)
        this.clip = clip
        title = clip.title
        startMs = clip.startMs
        endMs = clip.endMs
        val uri = Uri.parse(clip.uri)
        appContext = context.applicationContext
        val p = MediaPlayer()
        player = p
        val remote = clip.uri.startsWith("http://") || clip.uri.startsWith("https://")
        runCatching {
            p.setDataSource(context, uri)
            if (remote) {
                p.setOnPreparedListener { mp ->
                    runCatching {
                        mp.seekTo(clip.startMs.toInt())
                        mp.setOnCompletionListener { playing = false }
                        mp.start()
                        applySpeed(mp)
                        playing = true
                        positionMs = clip.startMs
                        PlaybackService.start(context)
                    }.onFailure { playing = false }
                }
                p.prepareAsync()
            } else {
                p.prepare()
                p.seekTo(clip.startMs.toInt())
                p.setOnCompletionListener { playing = false }
                p.start()
                applySpeed(p)
                playing = true
                positionMs = clip.startMs
                PlaybackService.start(context)
            }
        }.onFailure { playing = false }
    }

    fun stop() {
        appContext?.let { abandonFocus(it) }
        runCatching { player?.stop(); player?.release() }
        player = null; playing = false; clip = null; title = ""; mediaId = -1L; durationMs = 0L
    }

    fun poll() {
        val p = player ?: return
        runCatching {
            positionMs = p.currentPosition.toLong()
            if (playing && endMs > startMs && positionMs >= endMs) { p.pause(); playing = false }
        }
    }
}
