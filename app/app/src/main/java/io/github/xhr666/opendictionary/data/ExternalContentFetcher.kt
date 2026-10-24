package io.github.xhr666.opendictionary.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 从 Wikipedia REST API 抓取词条摘要并落库；内容 CC BY-SA 4.0，用户主动触发、可刷新。 */
object ExternalContentFetcher {
    private const val USER_AGENT = "OpenDictionary/0.6 (offline dictionary; contact: github.com/XHR666/OpenDictionary-apk)"

    suspend fun fetch(word: String): UserDb.ExternalContent = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(word.replace(' ', '_'), "UTF-8")
        var lastError: Exception? = null
        for (lang in listOf("zh", "en")) {
            try {
                val conn = URL("https://$lang.wikipedia.org/api/rest_v1/page/summary/$encoded").openConnection() as HttpURLConnection
                conn.connectTimeout = 12000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", USER_AGENT)
                try {
                    if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val obj = JSONObject(text)
                    val extract = obj.optString("extract").trim()
                    if (extract.isBlank()) error("无摘要内容")
                    io.github.xhr666.opendictionary.Diagnostics.log("wiki", "fetched ${word} ($lang) len=${extract.length}")
                    val page = obj.optJSONObject("content_urls")?.optJSONObject("desktop")?.optString("page").orEmpty()
                    return@withContext UserDb.ExternalContent(
                        word = word,
                        source = if (lang == "zh") "Wikipedia 中文（CC BY-SA 4.0）" else "Wikipedia English (CC BY-SA 4.0)",
                        title = obj.optString("title", word),
                        content = extract.take(4000),
                        url = page,
                        fetchedAt = System.currentTimeMillis(),
                    )
                } finally { conn.disconnect() }
            } catch (e: Exception) { lastError = e }
        }
        io.github.xhr666.opendictionary.Diagnostics.log("wiki", "failed $word: ${lastError?.message}")
        throw lastError ?: IllegalStateException("抓取失败")
    }
}
