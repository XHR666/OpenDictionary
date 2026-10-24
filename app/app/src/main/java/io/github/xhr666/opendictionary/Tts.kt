package io.github.xhr666.opendictionary

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.util.Locale

/** 系统 TTS 发音：初始化前排队、语言可用性检查、无语音包时静默降级。 */
object Tts {
    private var engine: TextToSpeech? = null
    private var ready = false
    private var rate = 0.95f
    private var pending: MutableList<Pair<String, Boolean>> = mutableListOf()
    var lastError by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    fun init(context: Context) {
        if (engine != null) return
        rate = context.getSharedPreferences("od_prefs", Context.MODE_PRIVATE).getFloat("tts_rate", rate).coerceIn(0.5f, 1.6f)
        engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                runCatching {
                    engine?.language = Locale.US
                    engine?.setSpeechRate(rate)
                }
                val q = pending.toList(); pending.clear()
                q.forEach { (text, british) -> speakNow(text, british) }
            } else {
                lastError = "系统语音引擎初始化失败，请在系统设置中安装/启用文字转语音服务"
            }
        }
    }

    fun stop() { runCatching { engine?.stop() }; pending.clear() }

    fun setRate(value: Float) {
        rate = value.coerceIn(0.5f, 1.6f)
        runCatching { engine?.setSpeechRate(rate) }
    }

    fun isReady(): Boolean = ready

    /** 用户可见的失败原因；无引擎/未就绪时也给出明确提示，避免按钮“点了没反应”。 */
    fun reportNotReady() {
        if (lastError == null && !ready) lastError = "语音引擎尚未就绪；如系统未安装 TTS 或英文语音包，请在系统设置中安装"
    }

    fun speak(text: String, british: Boolean = false) {
        if (text.isBlank()) return
        if (!ready) { pending.add(text to british); return }
        speakNow(text, british)
    }

    private fun speakNow(text: String, british: Boolean) {
        val t = engine ?: return
        runCatching {
            val locale = if (british) Locale.UK else Locale.US
            val avail = t.isLanguageAvailable(locale)
            if (avail == TextToSpeech.LANG_MISSING_DATA || avail == TextToSpeech.LANG_NOT_SUPPORTED) {
                lastError = "系统缺少英语语音包，请在系统设置中安装英文语音数据"
                return
            }
            t.language = locale
            t.setSpeechRate(rate)
            t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "od-${System.nanoTime()}")
        }.onFailure { lastError = it.message }
    }
}
