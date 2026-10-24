package io.github.xhr666.opendictionary.download

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import io.github.xhr666.opendictionary.update.UpdateInfo
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** 更新包下载：断点续传 + 通知栏进度/速度 + sha256 校验 + 安装。 */
object ApkDownloader {
    fun download(context: Context, info: UpdateInfo, onDone: (Boolean, String) -> Unit) {
        Thread {
            DownloadService.createChannel(context)
            DownloadService.resetControls()
            DownloadService.start(context, "OpenDictionary ${info.manifest.versionName}")
            val target = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), info.manifest.apkAsset)
            val part = File(target.path + ".part")
            if (!DownloadPolicy.resumeEnabled(context)) { part.delete(); target.delete() }
            var lastBytes = 0L
            var lastTime = System.currentTimeMillis()
            try {
                DownloadPolicy.checkOrThrow(context)
                var attempt = 0
                while (true) {
                    try {
                        if (DownloadService.isCancelled()) error("已取消下载")
                        var have = if (part.exists()) part.length() else 0L
                        if (have >= info.manifest.size && info.manifest.size > 0) { part.delete(); have = 0 }
                        val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
                        conn.connectTimeout = 15000
                        conn.readTimeout = 60000
                        conn.setRequestProperty("User-Agent", "OpenDictionary-Android")
                        if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
                        val code = conn.responseCode
                        if (code !in 200..299) error("HTTP $code")
                        if (code == 200 && have > 0) { part.delete(); have = 0 }
                        conn.inputStream.use { input ->
                            FileOutputStream(part, true).use { out ->
                                val buf = ByteArray(1 shl 16)
                                var downloaded = have
                                while (true) {
                                    val n = input.read(buf)
                                    if (n <= 0) break
                                    out.write(buf, 0, n); downloaded += n
                                    while (DownloadService.isPaused()) Thread.sleep(200)
                                    if (DownloadService.isCancelled()) error("已取消下载")
                                    val now = System.currentTimeMillis()
                                    if (now - lastTime >= 500) {
                                        val speed = ((downloaded - lastBytes) * 1000.0 / (now - lastTime)).toLong()
                                        DownloadService.progress(context, "OpenDictionary ${info.manifest.versionName}", downloaded, info.manifest.size, speed)
                                        lastBytes = downloaded; lastTime = now
                                    }
                                }
                            }
                        }
                        conn.disconnect()
                        break
                    } catch (e: Exception) {
                        e.message?.let { if (it.contains("416")) part.delete() }
                        if (DownloadService.isCancelled() || attempt >= 4) throw e
                        attempt++
                        DownloadService.progress(context, "网络中断，正在重试 $attempt/4", part.length(), info.manifest.size, 0)
                        Thread.sleep(1500L * attempt)
                    }
                }
                if (info.manifest.size > 0 && part.length() != info.manifest.size) error("大小不符")
                if (info.manifest.sha256.isNotBlank() && sha256(part) != info.manifest.sha256) error("sha256 校验失败")
                if (target.exists()) target.delete()
                if (!part.renameTo(target)) { part.copyTo(target, overwrite = true); part.delete() }
                validateApk(context, target)
                DownloadService.finish(context, "更新已下载", true)
                install(context, target)
                onDone(true, target.absolutePath)
            } catch (e: Exception) {
                io.github.xhr666.opendictionary.Diagnostics.log("download", "apk failed: ${e.message}")
                DownloadService.finish(context, "下载失败：${e.message}", false)
                onDone(false, e.message ?: "下载失败")
            }
        }.start()
    }

    private fun validateApk(context: Context, file: File) {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.absolutePath, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            ?: error("无法解析下载的 APK")
        if (archive.packageName != context.packageName) error("APK 包名不匹配")
        val installed = pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
        val oldSig = installed.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        val newSig = archive.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        if (oldSig == null || newSig == null || !oldSig.contentEquals(newSig)) error("APK 签名与应用不一致，已拒绝安装")
        val code = if (android.os.Build.VERSION.SDK_INT >= 28) archive.longVersionCode.toInt() else @Suppress("DEPRECATION") archive.versionCode
        if (code <= io.github.xhr666.opendictionary.BuildConfig.VERSION_CODE) error("下载的 APK 版本不高于当前版本")
    }

    private fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) { val n = input.read(buf); if (n <= 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
