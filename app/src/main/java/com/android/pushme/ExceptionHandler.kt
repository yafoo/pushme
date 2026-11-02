package com.android.pushme

import android.content.Context
import android.util.Log
import com.android.pushme.utils.PushMeApi.sendPushMeLog
import java.io.File
import java.io.FileWriter

object ExceptionHandler {
    private var isInit = false
    private var from = ""

    fun init(context: Context, from: String = "") {
        synchronized(this) {
            if(isInit) {
                return
            }

            isInit = true
            this.from = from

            Log.d("PushMe[Exception]", "init $from")
            // 获取系统默认的异常处理器（用于崩溃后恢复或弹出 ANR 对话框）
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

            // 设置自定义异常处理器
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                // 记录崩溃日志
                val log = Utils.getCrashLog(from, throwable, context)
                // saveLogToFile(log, context)
                sendPushMeLog(log) {
                    // 可选：调用系统默认处理器（如需要弹出系统崩溃对话框）
                    defaultHandler?.uncaughtException(thread, throwable)

                    // 或者直接退出应用（避免 ANR 对话框）
                    // Process.killProcess(Process.myPid())
                    // System.exit(1)
                }
            }
        }
    }

    // 保存日志到文件
    private fun saveLogToFile(log: String, context: Context) {
        Log.d("PushMe[Exception]", "save crash_log.txt")
        try {
            // 保存到应用私有目录（无需权限）
            val logFile = File(context.getExternalFilesDir(null), "crash_log.txt")
            FileWriter(logFile, true).use { writer ->
                writer.append(log)
                writer.append("\n\n") // 添加换行分隔
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}