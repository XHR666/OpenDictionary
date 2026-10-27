package io.github.xhr666.opendictionary.llm

import android.content.Context
import android.net.Uri
import io.github.xhr666.opendictionary.download.DownloadService
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class ModelSpec(
    val id: String,
    val name: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val description: String,
    val fallbackUrls: List<String>,
    val parts: Int = 1,
) {
    val ghBase = "https://github.com/XHR666/OpenDictionary-apk/releases/download/models-v1/"
}

object ModelCatalog {
    val all = listOf(
        ModelSpec(
            "qwen2.5-1.5b", "Qwen2.5-1.5B-Instruct · Q4_K_M", "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            1_117_320_736L, "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
            "质量优先 · 推荐",
            listOf("https://modelscope.cn/models/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/master/qwen2.5-1.5b-instruct-q4_k_m.gguf"),
            23
        ),
        ModelSpec(
            "qwen3-0.6b", "Qwen3-0.6B · Q8_0", "qwen3-0.6b-q8_0.gguf",
            639_446_688L, "9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031",
            "极速模式 · 低内存",
            listOf("https://modelscope.cn/models/Qwen/Qwen3-0.6B-GGUF/resolve/master/Qwen3-0.6B-Q8_0.gguf"),
            13
        ),
        ModelSpec(
            "qwen2.5-0.5b", "Qwen2.5-0.5B-Instruct · Q4_K_M", "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            491_400_032L, "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
            "最低配置",
            listOf("https://modelscope.cn/models/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/master/qwen2.5-0.5b-instruct-q4_k_m.gguf"),
            10
        )
    )
    fun byId(id: String): ModelSpec = all.firstOrNull { it.id == id } ?: all[1]
}

object ModelStore {
    fun dir(context: Context): File = File(context.filesDir, "models").apply { mkdirs() }
    fun file(context: Context, spec: ModelSpec): File = File(dir(context), spec.fileName)
    fun installed(context: Context, spec: ModelSpec): Boolean = file(context, spec).let { it.exists() && it.length() > spec.sizeBytes * 9 / 10 }
    fun installedAny(context: Context): ModelSpec? = ModelCatalog.all.firstOrNull { installed(context, it) }

    fun import(context: Context, uri: Uri, target: File, onProgress: ((Long) -> Unit)? = null): File {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { out -> copy(input, out, onProgress) }
        } ?: error("无法读取所选文件")
        return target
    }

    private fun copy(input: InputStream, out: FileOutputStream, onProgress: ((Long) -> Unit)?) {
        val buf = ByteArray(1 shl 16)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            out.write(buf, 0, n); total += n; onProgress?.invoke(total)
        }
        out.flush()
    }

    private const val PART_SIZE = 50_000_000L

    /** 断点续传下载；GitHub 上的模型按 50MB 分块托管，自动下载分块并合并校验。 */
    fun download(context: Context, spec: ModelSpec, onProgress: (downloaded: Long, total: Long) -> Unit): File {
        val target = file(context, spec)
        if (target.exists() && target.length() > 0) return target
        io.github.xhr666.opendictionary.download.DownloadPolicy.checkOrThrow(context)
        io.github.xhr666.opendictionary.download.DownloadService.resetControls()
        if (!io.github.xhr666.opendictionary.download.DownloadPolicy.resumeEnabled(context)) {
            File(target.path + ".part").delete()
            File(target.path + ".parts").deleteRecursively()
        }
        DownloadService.createChannel(context)
        DownloadService.start(context, "下载模型：${spec.name}")
        var lastBytes = 0L
        var lastTime = System.currentTimeMillis()
        val progress = { done: Long, total: Long ->
            onProgress(done, total)
            val now = System.currentTimeMillis()
            if (now - lastTime >= 500) {
                val speed = ((done - lastBytes) * 1000.0 / (now - lastTime)).toLong()
                DownloadService.progress(context, "下载模型：${spec.name}", done, total, speed)
                lastBytes = done; lastTime = now
            }
        }
        return try {
            downloadInner(context, spec, progress).also { DownloadService.finish(context, "模型已下载", true) }
        } catch (e: Exception) {
            DownloadService.finish(context, "模型下载失败", false)
            throw e
        }
    }

    private fun downloadInner(context: Context, spec: ModelSpec, onProgress: (downloaded: Long, total: Long) -> Unit): File {
        val target = file(context, spec)
        if (spec.parts > 1) {
            runCatching { return downloadParts(context, spec, onProgress) }
        }
        // 单文件回退（ModelScope 等镜像）
        val part = File(target.path + ".part")
        val urls = listOf(spec.ghBase + spec.fileName) + spec.fallbackUrls
        var lastError: Exception? = null
        for (url in urls) {
            try {
                downloadStreamWithRetry(url, part, spec.sizeBytes, spec.sha256, onProgress)
                if (target.exists()) target.delete()
                if (!part.renameTo(target)) { part.copyTo(target, overwrite = true); part.delete() }
                return target
            } catch (e: Exception) { lastError = e }
        }
        throw lastError ?: IllegalStateException("下载失败")
    }

    private fun downloadParts(context: Context, spec: ModelSpec, onProgress: (Long, Long) -> Unit): File {
        val target = file(context, spec)
        val partsDir = File(dir(context), spec.fileName + ".parts").apply { mkdirs() }
        var done = 0L
        for (i in 0 until spec.parts) {
            val expected = minOf(PART_SIZE, spec.sizeBytes - i * PART_SIZE)
            val partFile = File(partsDir, "%03d".format(i))
            if (!partFile.exists() || partFile.length() != expected) {
                downloadStreamWithRetry(spec.ghBase + spec.fileName + ".part%03d".format(i), partFile, expected, "", null)
            }
            done += expected
            onProgress(done, spec.sizeBytes)
        }
        val merged = File(target.path + ".merged")
        merged.delete()
        FileOutputStream(merged).use { out ->
            for (i in 0 until spec.parts) {
                File(partsDir, "%03d".format(i)).inputStream().use { it.copyTo(out) }
            }
            out.flush()
        }
        if (spec.sizeBytes > 0 && merged.length() != spec.sizeBytes) {
            merged.delete(); throw IllegalStateException("合并后大小不符")
        }
        if (spec.sha256.isNotBlank() && sha256(merged) != spec.sha256) {
            merged.delete(); partsDir.deleteRecursively(); throw IllegalStateException("sha256 校验失败")
        }
        if (target.exists()) target.delete()
        if (!merged.renameTo(target)) { merged.copyTo(target, overwrite = true); merged.delete() }
        partsDir.deleteRecursively()
        return target
    }

    private fun downloadStreamWithRetry(url: String, target: File, expectedSize: Long, sha: String, onProgress: ((Long, Long) -> Unit)?) {
        var last: Exception? = null
        for (attempt in 0 until 4) {
            try { downloadStream(url, target, expectedSize, sha, onProgress); return } catch (e: Exception) {
                last = e
                if (attempt < 3) Thread.sleep(1500L * (attempt + 1))
            }
        }
        throw last ?: IllegalStateException("下载失败")
    }

    private fun downloadStream(url: String, target: File, expectedSize: Long, sha: String, onProgress: ((Long, Long) -> Unit)?) {
        val part = File(target.path + ".download")
        var have = if (part.exists()) part.length() else 0L
        if (have > expectedSize) { part.delete(); have = 0 }
        if (have == expectedSize && expectedSize > 0) {
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) { part.copyTo(target, overwrite = true); part.delete() }
            onProgress?.invoke(expectedSize, expectedSize)
            return
        }
        val conn = URL(url).openConnection() as HttpURLConnection
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
                    while (io.github.xhr666.opendictionary.download.DownloadService.isPaused()) Thread.sleep(200)
                    if (io.github.xhr666.opendictionary.download.DownloadService.isCancelled()) error("已取消下载")
                    onProgress?.invoke(downloaded, expectedSize)
                }
                out.flush()
            }
        }
        conn.disconnect()
        if (expectedSize > 0 && part.length() != expectedSize) error("大小不符: ${part.length()} != $expectedSize")
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) { part.copyTo(target, overwrite = true); part.delete() }
        if (sha.isNotBlank() && sha256(target) != sha) { target.delete(); error("sha256 校验失败") }
    }

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) { val n = input.read(buf); if (n <= 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun delete(context: Context, spec: ModelSpec) {
        file(context, spec).delete()
        File(file(context, spec).path + ".part").delete()
    }
}
