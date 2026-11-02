package com.android.pushme

import android.app.Application
import com.android.pushme.utils.LocaleUtil

class App: Application() {
    companion object {
        lateinit var instance: App

        // 悬浮窗数据变更回调，用于通知数据小屏刷新
        var onFloatingDataChanged: (() -> Unit)? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 在应用启动时保存系统原始语言（只执行一次）
        LocaleUtil.initSystemLocale(this)
    }
}