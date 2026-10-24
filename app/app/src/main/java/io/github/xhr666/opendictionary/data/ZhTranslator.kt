package io.github.xhr666.opendictionary.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 免费机器翻译兜底（Microsoft Edge / MyMemory / Google gtx）；结果标注“机器翻译”并允许失败。 */
object ZhTranslator {
    data class Zh(val text: String, val source: String)

    /** 有道在线释义：优先 AI 中文解释，其次词典 suggest；仅用户主动查询时调用，接口可能变化。 */
    suspend fun translateWord(word: String): Zh? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        runCatching {
            val obj = JSONObject(get("https://dict.youdao.com/jsonapi?q=${URLEncoder.encode(word, "UTF-8")}"))
            val ai = obj.optJSONObject("aiDefinition")?.optJSONArray("words")?.optJSONObject(0)?.optString("content").orEmpty()
            if (ai.isNotBlank()) return@runCatching Zh(stripHtml(ai).take(1200), "有道 AI 释义（在线）")
            val entries = obj.optJSONObject("data")?.optJSONArray("entries") ?: org.json.JSONArray()
            for (i in 0 until entries.length()) {
                val e = entries.getJSONObject(i)
                if (e.optString("entry").equals(word, true)) {
                    val explain = e.optString("explain").trim()
                    if (explain.isNotBlank()) return@runCatching Zh(explain, "有道词典在线")
                }
            }
            // 退回 suggest 接口
            val sug = JSONObject(get("https://dict.youdao.com/suggest?num=2&ver=3.0&doctype=json&cache=false&le=en&q=${URLEncoder.encode(word, "UTF-8")}"))
            val es = sug.optJSONObject("data")?.optJSONArray("entries") ?: error("无结果")
            for (i in 0 until es.length()) {
                val e = es.getJSONObject(i)
                if (e.optString("entry").equals(word, true)) {
                    val explain = e.optString("explain").trim()
                    if (explain.isNotBlank()) return@runCatching Zh(explain, "有道词典在线")
                }
            }
            error("无中文")
        }.getOrNull()
    }

    private fun stripHtml(s: String): String = s.replace(Regex("<[^>]+>"), "").replace("&nbsp;", " ").replace("&quot;", "\"").replace(Regex("\\s+"), " ").trim()

    suspend fun translate(text: String): Zh? {
        val t = text.trim().take(1200)
        if (t.isEmpty()) return null
        return supervisorScope {
            val providers = listOf<Pair<String, suspend (String) -> String>>(
                "Microsoft" to ::microsoft,
                "MyMemory" to ::myMemory,
                "Google" to ::google,
            )
            val tasks = providers.map { (name, fn) -> name to async(Dispatchers.IO) { withTimeout(10_000) { runCatching { fn(t) } } } }
            val outcomes = tasks.map { (name, d) -> name to d.await() }
            outcomes.firstOrNull { it.second.isSuccess && it.second.getOrNull()?.isNotBlank() == true }
                ?.let { Zh(it.second.getOrThrow().trim(), it.first) }
        }
    }

    private suspend fun microsoft(text: String): String {
        val tokenConn = URL("https://edge.microsoft.com/translate/auth").openConnection() as HttpURLConnection
        tokenConn.connectTimeout = 6_000; tokenConn.readTimeout = 6_000
        tokenConn.setRequestProperty("User-Agent", UA)
        val token = try {
            if (tokenConn.responseCode !in 200..299) error("auth HTTP ${tokenConn.responseCode}")
            tokenConn.inputStream.bufferedReader().use { it.readText() }.trim()
        } finally { tokenConn.disconnect() }
        if (token.isEmpty()) error("no token")
        val body = JSONArray().put(JSONObject().put("Text", text)).toString()
        val conn = URL("https://api-edge.cognitive.microsofttranslator.com/translate?from=en&to=zh-Hans&api-version=3.0").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"; conn.doOutput = true
        conn.connectTimeout = 8_000; conn.readTimeout = 8_000
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "application/json")
        return try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val txt = conn.inputStream.bufferedReader().use { it.readText() }
            JSONArray(txt).getJSONObject(0).getJSONArray("translations").getJSONObject(0).getString("text")
        } finally { conn.disconnect() }
    }

    private suspend fun myMemory(text: String): String {
        val obj = JSONObject(get("https://api.mymemory.translated.net/get?q=${URLEncoder.encode(text, "UTF-8")}&langpair=en|zh-CN"))
        val out = obj.optJSONObject("responseData")?.optString("translatedText").orEmpty().trim()
        if (out.contains("MYMEMORY WARNING", true) || out.contains("QUOTA", true)) error("quota")
        return out
    }

    private suspend fun google(text: String): String {
        val arr = JSONArray(get("https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=zh-CN&dt=t&q=${URLEncoder.encode(text, "UTF-8")}"))
        val segments = arr.optJSONArray(0) ?: error("empty")
        return buildString { for (i in 0 until segments.length()) segments.optJSONArray(i)?.optString(0)?.let { append(it) } }
    }

    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 6_000; conn.readTimeout = 8_000
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Accept", "application/json")
        return try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally { conn.disconnect() }
    }
}
