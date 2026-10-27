package io.github.xhr666.opendictionary.update

import org.json.JSONObject

/**
 * 更新契约（见仓库 UPDATE.md）：只认 GitHub Releases 的 prerelease 资产中的 update.json + .apk。
 * README/代码提交不会触发更新检测。
 */
data class UpdateManifest(
    val schema: Int,
    val packageName: String,
    val versionCode: Int,
    val versionName: String,
    val minSdk: Int,
    val apkAsset: String,
    val sha256: String,
    val size: Long,
    val publishedAt: String,
    val notes: String,
) {
    fun isNewerThan(currentVersionCode: Int, currentMinSdk: Int): Boolean =
        versionCode > currentVersionCode && minSdk <= currentMinSdk &&
            packageName.isNotBlank() && apkAsset.endsWith(".apk", ignoreCase = true) &&
            sha256.length == 64 && size > 0

    companion object {
        fun parse(text: String): UpdateManifest? = runCatching {
            val o = JSONObject(text)
            UpdateManifest(
                schema = o.optInt("schema", 0),
                packageName = o.optString("packageName"),
                versionCode = o.optInt("versionCode", 0),
                versionName = o.optString("versionName"),
                minSdk = o.optInt("minSdk", Int.MAX_VALUE),
                apkAsset = o.optString("apkAsset"),
                sha256 = o.optString("sha256"),
                size = o.optLong("size", 0),
                publishedAt = o.optString("publishedAt"),
                notes = o.optString("notes"),
            )
        }.getOrNull()
    }
}

fun compareVersionCode(a: Int, b: Int): Int = a.compareTo(b)
