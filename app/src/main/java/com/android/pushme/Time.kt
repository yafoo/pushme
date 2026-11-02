package com.android.pushme

import java.text.SimpleDateFormat
import java.util.*

fun getShortDate(input: String): String {
    return try {
        val formats = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd HH:mm"
        )

        var date: Date? = null
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.getDefault())
                date = sdf.parse(input)
                if (date != null) break
            } catch (e: Exception) {
                continue
            }
        }

        if (date == null) {
            return input
        }

        val calendar = Calendar.getInstance()
        val inputCalendar = Calendar.getInstance().apply { time = date }

        val today = Calendar.getInstance()
        val isToday = inputCalendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                inputCalendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)

        val isSameYear = inputCalendar.get(Calendar.YEAR) == today.get(Calendar.YEAR)

        when {
            isToday -> {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
            }
            isSameYear -> {
                SimpleDateFormat("MM月dd日", Locale.getDefault()).format(date)
            }
            else -> {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
            }
        }
    } catch (e: Exception) {
        input
    }
}

// 扩展函数版本，支持更多输入格式
fun String.showShortDate(): String {
    return getShortDate(this)
}