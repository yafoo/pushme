package com.android.pushme

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.io.PrintWriter
import java.io.StringWriter
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.net.toUri
import java.util.regex.Pattern

import android.os.Process
import com.android.pushme.utils.Settings

const val SERVICE_NAME = "com.android.pushme.MQTTService"

object Utils {
    fun isProcess64Bit(): Boolean {
        return Process.is64Bit()
    }

    fun isServiceRunning(context: Context, className: String): Boolean {
        if(className.isEmpty()){
            return false
        }

        var isRunning = false
        val activityManager: ActivityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val serviceList: List<ActivityManager.RunningServiceInfo> = activityManager.getRunningServices(10)
        if(serviceList.isEmpty()) {
            return false
        }

        serviceList.forEach { item ->
            if(item.service.className == className){
                isRunning = true
                return@forEach
            }
        }
        return isRunning
    }

    /**
     * 服务是否以前台服务方式运行。
     *
     * 查的是系统里真实的前台标记，而不是 Settings 里的开关，
     * 这样即使 startForeground 失败退化成后台服务，也能如实反映。
     */
    @Suppress("DEPRECATION")
    fun isServiceForeground(context: Context, className: String): Boolean {
        if(className.isEmpty()){
            return false
        }

        return try {
            val activityManager: ActivityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.getRunningServices(Int.MAX_VALUE)
                .any { it.service.className == className && it.foreground }
        } catch (e: Exception) {
            false
        }
    }

    fun isNetworkOnline(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.activeNetwork ?: return false
        val actNw =
            connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
        return when {
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    /**
     * 获取当前网络类型
     * @return "WIFI" - WiFi网络
     *         "MOBILE" - 移动网络
     *         "ETHERNET" - 以太网
     *         "UNKNOWN" - 未知或无网络
     */
    fun getNetworkType(context: Context): String {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return "UNKNOWN"
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return "UNKNOWN"
        
        return when {
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "MOBILE"
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "UNKNOWN"
        }
    }

    /**
     * 获取本机所有 IP 地址
     * @return IP 地址列表，IPv6 地址不带中括号
     */
    fun getLocalIPs(): List<String> {
        val ips = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return ips
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (addr.isLoopbackAddress) continue
                    val hostAddr = addr.hostAddress ?: continue
                    // 去掉 IPv6 地址中的 %scope 部分
                    val cleanAddr = if (addr is Inet6Address) {
                        hostAddr.substringBefore("%")
                    } else {
                        hostAddr
                    }
                    ips.add(cleanAddr)
                }
            }
        } catch (e: Exception) {
            // 忽略异常
        }
        return ips
    }

    fun setAppFromRecents(context: Context, isHide: Boolean): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val tasks = am.appTasks
        if(tasks != null && tasks.isNotEmpty()) {
            tasks[0].setExcludeFromRecents(isHide)
            return true
        }
        return false
    }

    fun getCurrentTime(pattern: String = "yyyy-MM-dd HH:mm:ss"): String {
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
    }

    /** 把指定时间戳格式化成字符串（通知监听转发时用通知自身的产生时间） */
    fun formatTime(timeMillis: Long, pattern: String = "yyyy-MM-dd HH:mm:ss"): String {
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timeMillis))
    }

    fun getCurrentDate(): String {
        return getCurrentTime("yyyy-MM-dd")
    }

    fun getCurrentHour(): Int {
        return getCurrentTime("HH").toInt()
    }

    fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    fun openUrl(url: String, mainActivity: MainActivity) {
        var newUrl: String? = null
        val targetPattern = Pattern.compile("[?&]target=_?(self|blank)", Pattern.CASE_INSENSITIVE)
        val urlTargetMatcher = targetPattern.matcher(url)

        val target = if(urlTargetMatcher.find()) {
            newUrl = url.replace(urlTargetMatcher.group(0)?:"", "")
            urlTargetMatcher.group(1)?.lowercase(Locale.getDefault())?:""
        } else if(Settings.linkClickMethod == "系统") {
            "blank"
        } else {
            "self"
        }

        openUrl(newUrl?:url, mainActivity, target)
    }
    fun openUrl(url: String, mainActivity: MainActivity, target: String = "blank") {
        if(target == "blank") {
            try {
                val uri = url.toUri()
                val intent = Intent(Intent.ACTION_VIEW, uri)
                mainActivity.startActivity(intent)
            } catch (e: Exception) {
                mainActivity.showToast("打开失败：${e.message}, $url")
            }
        } else {
            val urlData = URLEncoder.encode(url, "UTF-8")
            mainActivity.navController?.navigate("web/$urlData") {
                launchSingleTop = true
            }
        }
    }

    fun isAndroid12(): Boolean {
        return Build.VERSION.SDK_INT == Build.VERSION_CODES.S || Build.VERSION.SDK_INT == Build.VERSION_CODES.S_V2
    }

    fun isAppInForeground(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningProcesses = activityManager.runningAppProcesses ?: return false
        for (processInfo in runningProcesses) {
            if (processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                processInfo.processName == context.packageName) {
                return true
            }
        }
        return false
    }

    fun isSystemMuted(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        return when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> true  // 完全静音
            AudioManager.RINGER_MODE_VIBRATE -> true // 振动模式（通常也算“静音”
            AudioManager.RINGER_MODE_NORMAL -> false // 正常模式
            else -> false
        }
    }

    // 生成崩溃日志内容（包含堆栈跟踪和设备信息）
    fun getCrashLog(from: String, throwable: Throwable, context: Context): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        return """
========== Crash Log ==========
From: $from
Time: ${getCurrentTime()}
Brand: ${Build.BRAND}
Model: ${Build.MODEL}
Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
App Version: ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}
Exception: ${throwable.javaClass.simpleName}
Message: ${throwable.message}
Stack Trace:
$sw
===============================
""".trimIndent()
    }
}