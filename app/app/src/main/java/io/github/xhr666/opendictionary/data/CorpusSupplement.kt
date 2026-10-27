package io.github.xhr666.opendictionary.data

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/** 从本地语料包补充的缺词词典（word_supplement.json），只在主词库未命中时使用。 */
object CorpusSupplement {
    private val cache = AtomicReference<Map<String, Pair<String, String>>?>(null)
    private val loading = AtomicReference(false)
    private val phraseCache = AtomicReference<Map<String, Triple<String, String, String>>?>(null)
    private val phraseLoading = AtomicReference(false)

    fun lookup(context: Context, raw: String): Pair<String, String>? {
        val key = raw.trim().lowercase()
        if (key.isBlank()) return null
        val map = cache.get() ?: run {
            if (!loading.compareAndSet(false, true)) return null
            val loaded = runCatching {
                val text = context.assets.open("corpus/word_supplement.json").bufferedReader().use { it.readText() }
                val words = JSONObject(text).optJSONObject("words") ?: JSONObject()
                buildMap {
                    val keys = words.keys()
                    while (keys.hasNext()) {
                        val w = keys.next()
                        val o = words.optJSONObject(w) ?: continue
                        val phonetic = o.optString("p")
                        val trans = o.optString("t").ifBlank { o.optString("d") }
                        if (trans.isNotBlank()) put(w.lowercase(), phonetic to trans)
                    }
                }
            }.getOrDefault(emptyMap())
            cache.set(loaded)
            loading.set(false)
            loaded
        }
        return map[key]
    }

    /** 短语/习语补充：返回 (英文释义, 例句, 来源标签)。 */
    fun lookupPhrase(context: Context, raw: String): Triple<String, String, String>? {
        val key = raw.trim().lowercase().replace(Regex("\\s+"), " ")
        if (key.isBlank()) return null
        val map = phraseCache.get() ?: run {
            if (!phraseLoading.compareAndSet(false, true)) return null
            val loaded = runCatching {
                val text = context.assets.open("corpus/phrase_supplement.json").bufferedReader().use { it.readText() }
                val phrases = JSONObject(text).optJSONObject("phrases") ?: JSONObject()
                buildMap {
                    val keys = phrases.keys()
                    while (keys.hasNext()) {
                        val p = keys.next()
                        val o = phrases.optJSONObject(p) ?: continue
                        val m = o.optString("m")
                        val e = o.optString("e")
                        val lvl = o.optString("level")
                        val src = o.optString("src")
                        if (m.isNotBlank() || e.isNotBlank()) put(p.lowercase(), Triple(m, e, if (lvl.isNotBlank()) "$src · $lvl" else src))
                    }
                }
            }.getOrDefault(emptyMap())
            phraseCache.set(loaded); phraseLoading.set(false); loaded
        }
        return map[key]
    }
}
