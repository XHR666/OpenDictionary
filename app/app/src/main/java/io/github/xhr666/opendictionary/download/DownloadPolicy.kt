package io.github.xhr666.opendictionary.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** 下载网络策略：仅 Wi-Fi / 断点续传，读取设置页偏好。 */
object DownloadPolicy {
    private fun prefs(context: Context) = context.getSharedPreferences("od_prefs", Context.MODE_PRIVATE)

    fun onlyWifi(context: Context): Boolean = prefs(context).getBoolean("dl_wifi_only", false)
    fun resumeEnabled(context: Context): Boolean = prefs(context).getBoolean("dl_resume", true)

    fun canDownloadNow(context: Context): Boolean {
        if (!onlyWifi(context)) return true
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun checkOrThrow(context: Context) {
        if (!canDownloadNow(context)) error("当前设置为仅 Wi-Fi 下载，请连接 Wi-Fi 后重试")
    }
}
