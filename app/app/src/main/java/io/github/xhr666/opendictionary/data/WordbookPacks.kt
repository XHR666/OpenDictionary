package io.github.xhr666.opendictionary.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class PackInfo(
    val id: String, val name: String, val description: String, val tag: String,
    val count: Int, val size: Long, val sha256: String, val file: String,
)

/** 从 GitHub Releases 拉取考试词汇包清单并下载（文件校验 SHA-256 后才导入）。 */
object WordbookPacks {
    private const val BASE = "https://github.com/XHR666/OpenDictionary-apk/releases/download/wordbooks-v1/"
    private const val MANIFEST = BASE + "manifest.json"

    private fun readAll(url: String, onProgress: ((Long, Long) -> Unit)? = null): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.setRequestProperty("Accept", "application/json")
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            val out = java.io.ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf = ByteArray(32 * 1024)
                var read: Int
                var got = 0L
                while (input.read(buf).also { read = it } > 0) {
                    out.write(buf, 0, read); got += read
                    if (total > 0) onProgress?.invoke(got, total)
                }
            }
            return out.toByteArray()
        } finally { conn.disconnect() }
    }

    suspend fun fetchManifest(): List<PackInfo> = withContext(Dispatchers.IO) {
        val obj = JSONObject(String(readAll(MANIFEST), Charsets.UTF_8))
        val arr = obj.getJSONArray("packs")
        buildList {
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                add(PackInfo(p.optString("id"), p.optString("name"), p.optString("description"), p.optString("tag"),
                    p.optInt("count"), p.optLong("size"), p.optString("sha256"), p.optString("file")))
            }
        }
    }

    suspend fun downloadPack(info: PackInfo, onProgress: ((Float) -> Unit)? = null): List<String> = withContext(Dispatchers.IO) {
        val raw = readAll(BASE + info.file) { got, total -> onProgress?.invoke((got.toFloat() / total).coerceIn(0f, 1f)) }
        verifyAndParsePack(raw, info.sha256, info.size, info.count)
    }

    /** 校验并解析词表；分离为纯函数便于单元测试。 */
    internal fun verifyAndParsePack(raw: ByteArray, sha256: String, expectedSize: Long, expectedCount: Int = 0): List<String> {
        if (!sha256.matches(Regex("[0-9a-fA-F]{64}"))) error("清单缺少有效 SHA-256")
        if (raw.size.toLong() != expectedSize) error("文件大小与清单不符")
        val digest = MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it) }
        if (!digest.equals(sha256, ignoreCase = true)) error("校验失败，请重试")
        val obj = JSONObject(String(raw, Charsets.UTF_8))
        val arr = obj.getJSONArray("words")
        val words = buildList { for (i in 0 until arr.length()) arr.optString(i).trim().takeIf { it.isNotBlank() }?.let { add(it) } }
        if (expectedCount > 0 && words.size != expectedCount) error("词数与清单不符")
        return words
    }
}
