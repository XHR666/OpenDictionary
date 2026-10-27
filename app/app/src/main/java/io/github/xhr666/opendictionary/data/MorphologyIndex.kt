package io.github.xhr666.opendictionary.data

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

data class MorphPart(val form: String, val type: String, val meaningZh: String, val source: String)
data class MorphInfo(val segments: List<String>, val parts: List<MorphPart>)

/** 构词法索引：CityLex 分段 + XDF/手工词缀释义；只显示已收录的切分，不编造释义。 */
object MorphologyIndex {
    private val cache = AtomicReference<Map<String, MorphInfo>?>(null)
    private val loading = AtomicReference(false)

    fun lookup(context: Context, raw: String): MorphInfo? {
        val key = raw.trim().lowercase()
        if (key.isBlank()) return null
        val map = cache.get() ?: run {
            if (!loading.compareAndSet(false, true)) return null
            val loaded = runCatching {
                val text = context.assets.open("corpus/morphology_index.json").bufferedReader().use { it.readText() }
                val words = JSONObject(text).optJSONObject("words") ?: JSONObject()
                buildMap {
                    val keys = words.keys()
                    while (keys.hasNext()) {
                        val w = keys.next()
                        val o = words.optJSONObject(w) ?: continue
                        val segs = o.optJSONArray("segments")
                        val parts = o.optJSONArray("parts")
                        if (segs == null || parts == null || parts.length() < 2) continue
                        put(w, MorphInfo(
                            segments = buildList { for (i in 0 until segs.length()) add(segs.optString(i)) },
                            parts = buildList {
                                for (i in 0 until parts.length()) {
                                    val p = parts.optJSONObject(i)
                                    if (p != null) add(MorphPart(p.optString("form"), p.optString("type"), p.optString("meaningZh"), p.optString("source")))
                                }
                            }
                        ))
                    }
                }
            }.getOrDefault(emptyMap())
            cache.set(loaded); loading.set(false); loaded
        }
        return map[key]
    }
}
