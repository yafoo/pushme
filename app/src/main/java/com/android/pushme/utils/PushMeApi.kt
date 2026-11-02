package com.android.pushme.utils

import android.os.Build
import com.android.pushme.App
import com.android.pushme.utils.HTTP.postAsync
import com.android.pushme.MessageInfo
import com.google.gson.Gson
import java.net.URLEncoder

data class MqttHostInfo(var port: Int = 0, var host: String = "")
data class VersionInfo(var version: String = "", var update: String = "")
data class MessageRepostInfo(var title: String? = "", var content: String? = "", var date: String? = "", var type: String? = "", var push_key: String? = "")
data class PushMeLogInfo(var push_id: Int = 0, var push_key: String = "", var log: String = "", var brand: String = "", var android: String = "")

object PushMeApi {
    const val PUSHMEURL = "https://push.i-i.me"

    fun getBakUrl(url: String): String {
        return url.replace(PUSHMEURL, "http://47.120.43.147:3010")
    }

    fun getAsyncWithBak(url: String, callback: (state: Boolean, result: String) -> Unit = {_,_ ->}) {
        HTTP.getAsyncWithBak(url, getBakUrl(url), callback)
    }

    fun postAsyncWithBak(url: String, json: String = "", callback: (state: Boolean, result: String) -> Unit = {_,_ ->}) {
        HTTP.postAsyncWithBak(url, getBakUrl(url), json, callback)
    }

    // 获取服务地址
    fun getMqttHost(onUpdate: (mqttHostInfo: MqttHostInfo) -> Unit) {
        getAsyncWithBak("$PUSHMEURL/client/get_host") { state, result ->
            run {
                if (state) {
                    try {
                        val mqttHostInfo = Gson().fromJson(result, MqttHostInfo::class.java)
                        Settings.mqttHost = mqttHostInfo.host
                        Settings.mqttPort = mqttHostInfo.port
                        onUpdate(mqttHostInfo)
                    } catch (_: Exception) { }
                }
            }
        }
    }

    // 获取新版本号
    fun getVersion(callback: (error: String, version: String, update: String) -> Unit) {
        val pushId = Settings.pushId
        val version = Settings.appVersion
        val brand = URLEncoder.encode(Build.BRAND, "UTF-8")
        val android = Build.VERSION.RELEASE
        
        // 检查电池优化状态：1=未关闭优化(默认), 2=已关闭优化
        val battery = if (Permission.checkPermission(App.instance, PermissionName.BATTERY)) "2" else "1"

        getAsyncWithBak("$PUSHMEURL/client/get_version?update=1&push_id=$pushId&version=$version&mobile=$brand&android=$android&battery=$battery") { state, result ->
            run {
                if (state) {
                    try {
                        val versionInfo = Gson().fromJson(result, VersionInfo::class.java)
                        callback("", versionInfo.version, versionInfo.update)
                    } catch (e: Exception) {
                        callback(e.toString(), "", "")
                    }
                } else {
                    callback(result, "", "")
                }
            }
        }
    }

    // 发送自建服务统计
    fun selfHost() {
        val pushId = Settings.pushId
        val pushKey = Settings.pushKey
        val self = if(Settings.mqttState == "自建") "self" else ""

        getAsyncWithBak("$PUSHMEURL/client/self_host?push_id=$pushId&push_key=$pushKey&self=$self")
    }

    // 发送插件统计
    fun setPlugin(count: Int) {
        val pushId = Settings.pushId
        val pushKey = Settings.pushKey

        getAsyncWithBak("$PUSHMEURL/client/set_plugin?push_id=$pushId&push_key=$pushKey&plugin=$count")
    }

    // 发送消息
    fun sendMessage(title: String?, content: String?, date: String?, type: String = "text", pushKey: String, onSend: (v: String) -> Unit) {
        var apiUrl = PUSHMEURL
        if(Settings.mqttState == "自建") {
            apiUrl = Settings.selfApi
        }
        val msg = Gson().toJson(MessageInfo(title, content, date, type, push_key = pushKey))

        postAsync(apiUrl, msg) { state, _ ->
            run {
                if (state) {
                    onSend("消息发送成功！")
                } else if(apiUrl == PUSHMEURL) {
                    postAsync(getBakUrl(apiUrl), msg) { state, _ ->
                        run {
                            if (state) {
                                onSend("消息发送成功！")
                            } else {
                                onSend("消息发送失败！")
                            }
                        }
                    }
                } else {
                    onSend("消息发送失败！")
                }
            }
        }
    }

    // 转发消息
    fun repostMessage(repostUrl: String, title: String?, content: String?, date: String?, type: String = "text", pushKey: String? = null) {
        postAsync(repostUrl, Gson().toJson(MessageRepostInfo(title, content, date, type, push_key = pushKey)))
    }

    // 发送崩溃日志
    fun sendPushMeLog(log: String, onSend: () -> Unit = {}) {
        if(Settings.appLogSend == "发送") {
            val pushId = Settings.pushId
            val pushKey = Settings.pushKey

            postAsyncWithBak("$PUSHMEURL/log/save", Gson().toJson(PushMeLogInfo(pushId, pushKey, log, Build.BRAND, Build.VERSION.RELEASE))) { _, _ -> onSend() }
        } else {
            onSend()
        }
    }
}