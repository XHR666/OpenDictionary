package io.github.xhr666.opendictionary

import android.app.Application

/** 所有进程入口（主界面、PROCESS_TEXT/SEND 快速查词）统一安装诊断日志。 */
class OdApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { Diagnostics.install(this) }
    }
}
