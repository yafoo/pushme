package com.android.pushme.utils

import android.os.Build
import com.android.pushme.App
import com.android.pushme.utils.PushMeApi.PUSHMEURL
import com.android.pushme.utils.PushMeApi.postAsyncWithBak
import com.google.gson.Gson
import java.net.URLEncoder

data class PushKeyInfo(var push_id: Int = 0, var push_key: String = "")
data class TempKeyInfo(var temp_time: Int = 0, var temp_key: String = "")

object PushKey {
    // 更新PushKey
    fun updatePushKey(onUpdate: (error: String, value: String) -> Unit) {
        val pushId = Settings.pushId
        val pushKey = Settings.pushKey
        val version = Settings.appVersion
        val brand = URLEncoder.encode(Build.BRAND, "UTF-8")
        val android = Build.VERSION.RELEASE
        
        // 检查电池优化状态：1=未关闭优化(默认), 2=已关闭优化
        val battery = if (Permission.checkPermission(App.instance, PermissionName.BATTERY)) "2" else "1"

        postAsyncWithBak("$PUSHMEURL/client/push_key?version=$version&mobile=$brand&android=$android&battery=$battery", Gson().toJson(PushKeyInfo(pushId, pushKey))) { state, result ->
            run {
                if (state) {
                    try {
                        val pushKeyInfo = Gson().fromJson(result, PushKeyInfo::class.java)
                        Settings.pushId = pushKeyInfo.push_id
                        Settings.pushKey = pushKeyInfo.push_key
                        onUpdate("", pushKeyInfo.push_key)
                    } catch (e: Exception) {
                        onUpdate(e.toString(), "")
                    }
                } else {
                    onUpdate(result, "")
                }
            }
        }
    }

    // 更新TempKey
    fun updateTempKey(onUpdate: (error: String, value: String) -> Unit) {
        val pushId = Settings.pushId
        val pushKey = Settings.pushKey
        val version = Settings.appVersion
        val brand = URLEncoder.encode(Build.BRAND, "UTF-8")
        val android = Build.VERSION.RELEASE

        postAsyncWithBak("$PUSHMEURL/client/temp_key?version=$version&mobile=$brand&android=$android", Gson().toJson(PushKeyInfo(pushId, pushKey))) { state, result ->
            run {
                if (state) {
                    try {
                        val tempKeyInfo = Gson().fromJson(result, TempKeyInfo::class.java)
                        Settings.tempTime = tempKeyInfo.temp_time
                        Settings.tempKey = tempKeyInfo.temp_key
                        onUpdate("", tempKeyInfo.temp_key)
                    } catch (e: Exception) {
                        onUpdate(e.toString(), "")
                    }
                } else {
                    onUpdate(result, "")
                }
            }
        }
    }
}