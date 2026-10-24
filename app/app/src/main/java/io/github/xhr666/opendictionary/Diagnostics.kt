package io.github.xhr666.opendictionary

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 无 adb 环境下的应用内诊断：记录未捕获异常与关键事件，可导出文本给开发者。 */
object Diagnostics {
    private const val DIR = "diagnostics"
    private var appContext: Context? = null
    @Volatile private var installed = false

    fun install(context: Context) {
        appContext = context.applicationContext
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    fun writeCrash(thread: Thread, throwable: Throwable) {
        val ctx = appContext ?: return
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val f = File(dir(ctx), "crash-$ts.txt")
        runCatching {
            f.writeText(buildString {
                appendLine("OpenDictionary crash report")
                appendLine("time: ${Date()}")
                appendLine("thread: ${thread.name}")
                appendLine(throwable.stackTraceToString())
            })
            prune(ctx)
        }
    }

    fun log(tag: String, message: String) {
        val ctx = appContext ?: return
        runCatching {
            val f = File(dir(ctx), "events.log")
            if (f.length() > 256 * 1024) f.delete()
            f.appendText("${SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())} [$tag] $message\n")
        }
    }

    fun exports(context: Context): List<File> {
        val list = (dir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()).toMutableList()
        return list
    }

    fun read(file: File): String = runCatching { file.readText() }.getOrDefault("")

    fun clear(context: Context) = dir(context).listFiles()?.forEach { it.delete() }

    private fun prune(context: Context) {
        val files = dir(context).listFiles()?.filter { it.name.startsWith("crash-") }?.sortedBy { it.lastModified() } ?: return
        files.dropLast(20).forEach { it.delete() }
    }
}
