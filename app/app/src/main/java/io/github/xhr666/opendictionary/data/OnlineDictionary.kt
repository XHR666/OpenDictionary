package io.github.xhr666.opendictionary.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 在线兜底查词：多开源接口并行查询，按优先级取第一个成功结果。
 * 当前源：Free Dictionary API、Datamuse、dictionaryapi.dev、Wiktionary REST、Wikipedia 摘要。
 * 数据许可：Wiktionary/Free Dictionary API = CC BY-SA 4.0；Wikipedia = CC BY-SA 4.0。
 */
object OnlineDictionary {
    data class Def(val pos: String, val text: String, val example: String)
    data class OnlineResult(
        val word: String,
        val phonetic: String,
        val source: String,
        val license: String,
        val defs: List<Def>,
        val url: String,
        val fetchedAt: Long,
        val synonyms: List<String> = emptyList(),
    ) {
        fun asText(): String = buildString {
            if (phonetic.isNotBlank()) append("/").append(phonetic).append("/\n")
            defs.forEach { d ->
                append(if (d.pos.isBlank()) "• " else "• ${d.pos} ").append(d.text)
                if (d.example.isNotBlank()) append("  例：").append(d.example)
                append("\n")
            }
        }.trim()
    }

    suspend fun lookup(word: String): OnlineResult = supervisorScope {
        val providers: List<Pair<String, suspend (String) -> OnlineResult>> = listOf(
            "FreeDictionaryAPI" to ::freeDictionary,
            "Datamuse" to ::datamuse,
            "dictionaryapi.dev" to ::dictionaryApiDev,
            "Wiktionary" to ::wiktionary,
            "Wikipedia" to ::wikipedia,
        )
        val tasks = providers.map { (name, fn) ->
            name to async(Dispatchers.IO) {
                withTimeout(12_000) { runCatching { fn(word) } }
            }
        }
        val outcomes = tasks.map { (name, deferred) -> name to deferred.await() }
        val hit = outcomes.firstOrNull { it.second.isSuccess }
            ?: throw IllegalStateException(
                "在线查询失败：" + outcomes.joinToString("；") { (n, r) -> "$n: ${r.exceptionOrNull()?.message}" }
            )
        android.util.Log.i("ODOnline", "hit ${hit.first} for $word")
        hit.second.getOrThrow()
    }

    private suspend fun freeDictionary(word: String): OnlineResult {
        val obj = JSONObject(get("https://freedictionaryapi.com/api/v1/entries/en/${URLEncoder.encode(word, "UTF-8")}"))
        val entries = obj.optJSONArray("entries") ?: error("无 entries")
        val defs = mutableListOf<Def>()
        var phonetic = ""
        var ei = 0
        while (ei < entries.length() && defs.size < 8) {
            val e = entries.getJSONObject(ei); ei++
            if (phonetic.isBlank()) {
                e.optJSONArray("pronunciations")?.let { ps ->
                    for (j in 0 until ps.length()) {
                        val p = ps.getJSONObject(j); if (p.optString("text").isNotBlank()) { phonetic = p.optString("text"); break }
                    }
                }
            }
            val pos = e.optString("partOfSpeech")
            e.optJSONArray("senses")?.let { senses ->
                var sj = 0
                while (sj < senses.length() && defs.size < 8) {
                    val s = senses.getJSONObject(sj); sj++
                    val text = s.optString("definition").trim()
                    if (text.isNotBlank()) {
                        val ex = s.optJSONArray("examples")?.optString(0).orEmpty()
                        defs += Def(pos, text, ex)
                    }
                }
            }
        }
        if (defs.isEmpty()) error("无释义")
        val syns = mutableListOf<String>()
        for (i in 0 until entries.length()) {
            entries.getJSONObject(i).optJSONArray("synonyms")?.let { ss -> for (j in 0 until ss.length()) { val x = ss.optString(j).trim(); if (x.isNotBlank()) syns += x } }
        }
        val src = obj.optJSONObject("source")
        return OnlineResult(
            word, phonetic.trim(), "Free Dictionary API（Wiktionary 数据）",
            src?.optJSONObject("license")?.optString("name") ?: "CC BY-SA 4.0",
            defs, src?.optString("url").orEmpty().ifBlank { "https://en.wiktionary.org/wiki/${URLEncoder.encode(word.replace(' ', '_'), "UTF-8")}" },
            System.currentTimeMillis(), syns.distinct().take(8)
        )
    }

    private suspend fun datamuse(word: String): OnlineResult {
        val arr = JSONArray(get("https://api.datamuse.com/words?sp=${URLEncoder.encode(word, "UTF-8")}&md=drp&max=1"))
        if (arr.length() == 0) error("无结果")
        val o = arr.getJSONObject(0)
        if (!o.optString("word").equals(word, true)) error("无精确匹配")
        val defs = mutableListOf<Def>()
        o.optJSONArray("defs")?.let { ds ->
            var di = 0
            while (di < ds.length() && defs.size < 8) {
                val raw = ds.optString(di).trim(); di++
                if (raw.isBlank()) continue
                val tab = raw.indexOf('\t')
                defs += if (tab > 0) Def(raw.substring(0, tab), raw.substring(tab + 1), "") else Def("", raw, "")
            }
        }
        if (defs.isEmpty()) error("无释义")
        return OnlineResult(
            word, o.optString("pron").trim(), "Datamuse（Wiktionary 释义）", "CC BY-SA 4.0",
            defs, "https://en.wiktionary.org/wiki/${URLEncoder.encode(word.replace(' ', '_'), "UTF-8")}", System.currentTimeMillis()
        )
    }

    private suspend fun dictionaryApiDev(word: String): OnlineResult {
        val body = get("https://api.dictionaryapi.dev/api/v2/entries/en/${URLEncoder.encode(word, "UTF-8")}").trim()
        val first = if (body.startsWith("[")) JSONArray(body).getJSONObject(0) else JSONObject(body)
        val phonetic = first.optString("phonetic").ifBlank {
            first.optJSONArray("phonetics")?.let { ps -> (0 until ps.length()).map { ps.getJSONObject(it).optString("text") }.firstOrNull { it.isNotBlank() } ?: "" } ?: ""
        }
        val defs = mutableListOf<Def>()
        first.optJSONArray("meanings")?.let { ms ->
            for (i in 0 until ms.length()) {
                val m = ms.getJSONObject(i); val pos = m.optString("partOfSpeech")
                m.optJSONArray("definitions")?.let { ds ->
                    for (j in 0 until minOf(ds.length(), 4)) {
                        val d = ds.getJSONObject(j); val text = d.optString("definition").trim()
                        if (text.isNotBlank()) defs += Def(pos, text, d.optString("example").trim())
                    }
                }
            }
        }
        if (defs.isEmpty()) error("无释义")
        val syns = mutableListOf<String>()
        first.optJSONArray("meanings")?.let { ms -> for (i in 0 until ms.length()) ms.getJSONObject(i).optJSONArray("synonyms")?.let { ss -> for (j in 0 until ss.length()) { val x = ss.optString(j).trim(); if (x.isNotBlank()) syns += x } } }
        return OnlineResult(word, phonetic, "dictionaryapi.dev（Wiktionary 数据）", "CC BY-SA 4.0",
            defs.take(8), "https://en.wiktionary.org/wiki/${URLEncoder.encode(word.replace(' ', '_'), "UTF-8")}", System.currentTimeMillis(), syns.distinct().take(8))
    }

    private suspend fun wiktionary(word: String): OnlineResult {
        val obj = JSONObject(get("https://en.wiktionary.org/api/rest_v1/page/definition/${URLEncoder.encode(word.replace(' ', '_'), "UTF-8")}"))
        val en = obj.optJSONArray("en") ?: error("无英文释义")
        val defs = mutableListOf<Def>()
        var gi = 0
        while (gi < en.length() && defs.size < 8) {
            val group = en.getJSONObject(gi); gi++; val pos = group.optString("partOfSpeech")
            group.optJSONArray("definitions")?.let { ds ->
                for (j in 0 until minOf(ds.length(), 5)) {
                    val d = ds.getJSONObject(j)
                    val text = d.optString("definition").replace(Regex("<[^>]+>"), "").replace("&quot;", "\"").trim()
                    if (text.isNotBlank()) defs += Def(pos, text, "")
                }
            }
        }
        if (defs.isEmpty()) error("无释义")
        return OnlineResult(word, "", "Wiktionary", "CC BY-SA 4.0", defs,
            "https://en.wiktionary.org/wiki/${URLEncoder.encode(word.replace(' ', '_'), "UTF-8")}", System.currentTimeMillis())
    }

    private suspend fun wikipedia(word: String): OnlineResult {
        val wiki = ExternalContentFetcher.fetch(word)
        return OnlineResult(word, "", wiki.source, "CC BY-SA 4.0",
            listOf(Def("百科", wiki.content, "")), wiki.url, wiki.fetchedAt)
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000; conn.readTimeout = 8_000
        conn.setRequestProperty("User-Agent", "OpenDictionary/0.6 (offline dictionary; github.com/XHR666/OpenDictionary-apk)")
        conn.setRequestProperty("Accept", "application/json")
        return try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally { conn.disconnect() }
    }
}
