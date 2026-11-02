package com.android.pushme.model

import android.database.Cursor
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase


fun isWindowFunctionsSupported(): Boolean {
    // 检查 SQLite 版本是否支持窗口函数 (3.25.0+)
    var cursor: Cursor? = null
    try {
        val db: SupportSQLiteDatabase = AppDataBase.instance.openHelper.readableDatabase
        cursor = db.query("SELECT sqlite_version()", emptyArray())
        if (cursor.moveToFirst()) {
            val version = cursor.getString(0)
            Log.d("SQLITE_VERSION", version)
            val parts: Array<String> =
                version.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val major = parts[0].toInt()
            val minor = parts[1].toInt()
            return (major > 3) || (major == 3 && minor >= 25)
        }
    } catch (e: Exception) {
        return false
    } finally {
        cursor?.close()
    }
    return false
}