package io.github.xhr666.opendictionary.update

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * 更新检测：只读 GitHub Releases 的 prerelease；要求资产含 update.json 与 .apk。
 * README/commit/push 不产生 Release，因此不会误报更新。
 */
class UpdateChecker(
    private val repo: String = "XHR666/OpenDictionary-apk",
    private val currentVersionCode: Int,
    private val currentMinSdk: Int,
) {
    private val api = "https://api.github.com/repos/$repo/releases?per_page=20"

    fun check(): Result<UpdateInfo?> = runCatching<UpdateInfo?> {
        val releasesText = httpGet(api, accept = "application/vnd.github+json")
        val releases = JSONArray(releasesText)
        val candidates = mutableListOf<Pair<UpdateManifest, String>>()
        for (i in 0 until releases.length()) {
            val rel = releases.getJSONObject(i)
            if (!rel.optBoolean("prerelease", false) || rel.optBoolean("draft", false)) continue
            val assets = rel.optJSONArray("assets") ?: continue
            var manifestUrl: String? = null
            var apkUrl: String? = null
            var apkName: String? = null
            for (j in 0 until assets.length()) {
                val a = assets.getJSONObject(j)
                val name = a.optString("name")
                val url = a.optString("browser_download_url")
                when {
                    name == "update.json" -> manifestUrl = url
                    name.endsWith(".apk", ignoreCase = true) -> { apkUrl = url; apkName = name }
                }
            }
            if (manifestUrl == null || apkUrl == null) continue
            val manifest = UpdateManifest.parse(httpGet(manifestUrl, accept = "application/json")) ?: continue
            if (!manifest.isNewerThan(currentVersionCode, currentMinSdk)) continue
            if (manifest.apkAsset != apkName) continue
            candidates += manifest to apkUrl
        }
        val found = candidates.maxByOrNull { it.first.versionCode }?.let { (manifest, apkUrl) ->
            UpdateInfo(manifest = manifest, apkUrl = apkUrl)
        }
        io.github.xhr666.opendictionary.Diagnostics.log("update", found?.let { "found ${it.manifest.versionName} (${it.manifest.versionCode})" } ?: "no newer prerelease")
        found
    }.onFailure { io.github.xhr666.opendictionary.Diagnostics.log("update", "check failed: ${it.message}") }

    private fun httpGet(url: String, accept: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", accept)
        conn.setRequestProperty("User-Agent", "OpenDictionary-Android")
        return try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

data class UpdateInfo(val manifest: UpdateManifest, val apkUrl: String)
