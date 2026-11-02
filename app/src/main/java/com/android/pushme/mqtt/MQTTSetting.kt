package com.android.pushme.mqtt

import com.android.pushme.utils.Settings

data class MQTTConnectParams(
    val protocol: String,
    val host: String,
    val port: Int,
    val pushKey: String,
    val serverUri: String,
    val clientId: String,
    val state: Boolean
)

object MQTTStatus {
    const val CONNECTING = "Connecting"
    const val CONNECTED = "Connected"
    const val DISCONNECTED = "Disconnected"
    const val DISCONNECTING = "Disconnecting"
    const val CLOSED = "Closed"
    const val CONNECT_FAILED = "ConnectFailed"
    const val DESTROY = "Destroy"
    const val OFFLINE = "Offline"
    const val CHANGE_SET = "changeSet"
    const val CLOSE = "close"
}

object MQTTSetting {
    fun getSetting(): MQTTConnectParams {
        var protocol = "ssl"
        var host = Settings.mqttHost
        var port = Settings.mqttPort
        var pushKey = Settings.pushKey

        if(Settings.mqttState == "自建") {
            pushKey = Settings.selfKey
            host = Settings.selfHost
            port = Settings.selfPort
            val isWebSocket = Settings.selfProtocol == "WebSocket"
            val hasCert = Settings.selfTls != "无证书"
            protocol = when {
                isWebSocket && hasCert -> "wss"
                isWebSocket -> "ws"
                hasCert -> "ssl"
                else -> "tcp"
            }
        }

        if(host.contains(":") && host.first() != '[') {
            host = "[$host]"
        }

        return MQTTConnectParams(protocol, host, port, pushKey, "$protocol://$host:$port", "me_$pushKey", Settings.mqttState != "关闭")
    }

    fun getProtocol(): String {
        var protocol = "ssl"
        if(Settings.mqttState == "自建") {
            val isWebSocket = Settings.selfProtocol == "WebSocket"
            val hasCert = Settings.selfTls != "无证书"
            protocol = when {
                isWebSocket && hasCert -> "wss"
                isWebSocket -> "ws"
                hasCert -> "ssl"
                else -> "tcp"
            }
        }
        return protocol
    }
}