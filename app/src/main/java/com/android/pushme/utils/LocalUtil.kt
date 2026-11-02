package com.android.pushme.utils

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.LocaleList
import java.util.Locale


object LocaleUtil {
    // 保存系统原始语言，用于"系统"选项
    private var systemLocale: Locale = Locale.getDefault()
    
    fun initSystemLocale(context: Context) {
        systemLocale = context.resources.configuration.locales[0]
    }

    fun getLocale(): Locale {
        val localeCode = LangUtil.localeCode
        
        // 如果选择"系统"，使用系统原始语言
        if (localeCode == null) {
            val systemLang = systemLocale.language.take(2)
            return when (systemLang) {
                "zh" -> Locale("zh", "CN")
                else -> Locale.ENGLISH
            }
        }

        return when (localeCode) {
            "zh" -> Locale("zh", "CN")
            else -> Locale.ENGLISH
        }
    }

    fun onAttach(context: Context): Context {
        return createContextWithLocale(context)
    }

    fun activityRecreate(activity: Activity?) {
        // 重建 Activity 使语言生效
        activity?.recreate()
    }

    // 在 Activity 创建时应用语言
    fun createContextWithLocale(context: Context): Context {
        val locale = getLocale()
        Locale.setDefault(locale)

        return context.createConfigurationContext(
            context.resources.configuration.apply {
                setLocale(locale)
                // 对于 Android 13+，还需要设置用户选择的语言（可选）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    setLocales(LocaleList(locale))
                }
            }
        )
    }
}