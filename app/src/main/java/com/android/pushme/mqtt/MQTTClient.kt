package com.android.pushme.mqtt

import android.util.Log
import android.widget.Toast
import android.content.Context
import com.android.pushme.TAG
import com.android.pushme.showToast
import com.android.pushme.utils.Settings
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.concurrent.Executors
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory


open class MQTTClient(private val serverUri: String, private val clientId: String, private val context: Context, val mqttAlarmReconnect: MQTTAlarmReconnect) {
    private val tag = "$TAG[MQTT CLIENT]"
    private var pingSender: MyPingSender = MyPingSender(context)
    private lateinit var mqttClient: MQTTClientWithPingAndTimer
    private var initConnectOptions = false

    companion object {
        // 复用单线程池，避免每次连接创建新线程
        private val connectExecutor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "MQTTConnect").apply { isDaemon = true }
        }

        // 处于 Disconnecting 状态时的重连重试参数
        private const val RECONNECT_MAX_RETRY = 5
        private const val RECONNECT_RETRY_DELAY = 500L
    }

    init {
        initConnect()
    }

    open fun initConnect() {
        try {
            onConnecting()
            mqttClient = MQTTClientWithPingAndTimer(serverUri, clientId, MemoryPersistence(), pingSender)
            Log.d(tag, "initConnect: $serverUri $clientId")
            connect()
        } catch (e: Exception) {
            Log.e(tag, "initConnect: ", e)
            context.showToast("连接失败，请检查host设置是否正确，错误码：$e", Toast.LENGTH_LONG)
            onConnectFailed()
        }
    }

    open fun isConnected(): Boolean {
        return try {
            pingSender.comms.isConnected
        } catch (e: Exception) {
            false
        }
    }

    open fun isConnecting(): Boolean {
        return try {
            pingSender.comms.isConnecting
        } catch (e: Exception) {
            false
        }
    }

    open fun isDisconnected(): Boolean {
        return try {
            pingSender.comms.isDisconnected
        } catch (e: Exception) {
            false
        }
    }

    open fun isDisconnecting(): Boolean {
        return try {
            pingSender.comms.isDisconnecting
        } catch (e: Exception) {
            false
        }
    }

    open fun isClosed(): Boolean {
        return try {
            pingSender.comms.isClosed
        } catch (e: Exception) {
            false
        }
    }

    open fun connectState(): String {
        return when {
            isConnected() -> "Connected"
            isConnecting() -> "Connecting"
            isDisconnected() -> "Disconnected"
            isDisconnecting() -> "Disconnecting"
            isClosed() -> "Closed"
            else -> "Unknown"
        }
    }

    open fun onConnecting() {
        Log.d(tag, "onConnecting")
    }

    open fun onConnected(reconnect: Boolean, serverURI: String) {
        //连接成功
        val msg = if(reconnect) "onReConnected: " else "onConnected: "
        Log.d(tag, msg)
    }

    open fun onDisconnected(cause: Throwable) {
        //断开连接
        Log.e(tag, "onDisconnected: ", cause)
    }

    open fun onConnectFailed() {
        //重试连接
        Log.e(tag, "onConnectFailed:")
    }

    @Throws(Exception::class)
    open fun onMessage(topic: String, message: String) {
        Log.d(tag, "onMessage: topic:$topic msg:$message")
    }

    open fun onDelivered(token: IMqttDeliveryToken) {
        //发送消息成功后的回调
        Log.d(tag, "onDelivered")
    }

    open fun onPinged() {
        //发送ping回调
        Log.d(tag, "onPinged")
    }

    private fun connect() {
        val mqttCallback: MqttCallbackExtended = object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String) {
                onConnected(reconnect, serverURI)
            }

            override fun connectionLost(cause: Throwable) {
                onDisconnected(cause)
            }

            @Throws(Exception::class)
            override fun messageArrived(topic: String, message: MqttMessage) {
                val msg = String(message.payload)
                onMessage(topic, msg)
            }

            override fun deliveryComplete(token: IMqttDeliveryToken) {
                onDelivered(token)
            }
        }
        mqttClient.setCallback(mqttCallback) //设置回调函数

        val mOptions = MqttConnectOptions()
        mOptions.isAutomaticReconnect = false //断开后，是否自动连接
        mOptions.isCleanSession = false //是否清空客户端的连接记录。若为true，则断开后，broker将自动清除该客户端连接信息
        mOptions.connectionTimeout = 10 //设置超时时间，单位为秒
        //mOptions.userName = "Admin" //设置用户名。跟Client ID不同。用户名可以看做权限等级
        //mOptions.setPassword("Admin") //设置登录密码
        //心跳时间，单位为秒。即多长时间确认一次Client端是否在线
        // 由设置项「MQTT心跳间隔」提供（界面按分钟显示，1~20 分钟，存的是秒 → 60~1200）。
        // 这里再 coerceIn 兜一道：MQTT 协议里 0 表示「不做心跳」，
        // 万一存储被写坏成 0/负数，会静默退化成长连接不做保活，极难排查。
        mOptions.keepAliveInterval = Settings.mqttHeartbeat.coerceIn(60, 1200)
        mOptions.maxInflight = 10 //允许同时发送几条消息（未收到broker确认信息）
        mOptions.mqttVersion = MqttConnectOptions.MQTT_VERSION_3_1_1 //选择MQTT版本

        connectExecutor.execute {
            initConnectOptions = true
            try {
                // 不拦截会无限重启
                try {
                    if(Settings.mqttState == "自建") {
                        val selfTls = Settings.selfTls
                        if(selfTls == "自签名证书") {
                            mOptions.socketFactory = createCustomTrustFactory()
                        } else if(selfTls == "公共证书") {
                            mOptions.socketFactory = createSSLContext().socketFactory
                        }
                    } else {
                        mOptions.socketFactory = createCustomTrustFactory()
                    }
                } catch (e: Exception) {
                    Log.e(tag, "TLS/SSL: ", e)
                }
                Log.d(tag, "connect: serverUri $serverUri clientId $clientId")
                mqttClient.connect(mOptions)
            } catch (e: Exception) {
                Log.e(tag, "connect: ", e)
                onConnectFailed()
            }
        }
    }

    /**
     * 重连。
     *
     * 注意两点：
     * 1. Paho 的 reconnect()/disconnect() 是阻塞调用，必须放到后台线程，
     *    否则最长会卡住主线程 connectionTimeout 秒（ANR 风险）。
     * 2. disconnect 是异步的，若当前正处于 Disconnecting，要等它断干净再重连，
     *    否则这里的判断会直接跳过，导致连接一直不上线。
     */
    open fun reConnect(retry: Int = 0) {
        if(!this::mqttClient.isInitialized) {
            initConnect()
            return
        }
        if(!initConnectOptions) {
            Log.w(tag, "reConnect stop, because the initConnectOptions is false")
            return
        }

        connectExecutor.execute {
            when {
                isDisconnected() -> {
                    onConnecting()
                    try {
                        mqttClient.reconnect()
                        Log.d(tag, "reConnect")
                    } catch (e: MqttException) {
                        Log.e(tag, "reConnect: ", e)
                    } catch (e: NullPointerException) {
                        Log.e(tag, "reConnect NullPointerException, fallback to initConnect", e)
                        initConnect()
                    }
                }

                isDisconnecting() && retry < RECONNECT_MAX_RETRY -> {
                    Log.d(tag, "reConnect: 正在断开，${RECONNECT_RETRY_DELAY}ms 后重试(${retry + 1})")
                    Thread.sleep(RECONNECT_RETRY_DELAY)
                    reConnect(retry + 1)
                }

                else -> {
                    Log.w(tag, "reConnect stop, because the connect state now is ${connectState()}")
                }
            }
        }
    }

    open fun disConnect() {
        if(!isConnected()) {
            Log.w(tag, "disConnect: MQTT非已连接状态")
            return
        }

        //断开连接
        try {
            mqttClient.disconnect()
            Log.d(tag, "disConnect")
        } catch (e: MqttException) {
            Log.e(tag, "disConnect: ", e)
        }
    }

    //订阅topic
    open fun subscribe(topic: String, qos: Int = 0) {
        if(!isConnected()) {
            Log.e(tag, "subscribe: MQTT已断开")
            return
        }

        try {
            mqttClient.subscribe(topic, qos)
            Log.d(tag, "subscribe: $topic" )
        } catch (e: MqttException) {
            Log.e(tag, "subscribe: ", e)
        }
    }

    //取消订阅topic
    open fun unsubscribe(topic: String) {
        if(!isConnected()) {
            Log.e(tag, "unsubscribe: MQTT已断开")
            return
        }

        try {
            mqttClient.unsubscribe(topic)
            Log.d(tag, "unsubscribe: $topic" )
        } catch (e: MqttException) {
            Log.e(tag, "unsubscribe: ", e)
        }
    }

    //发布消息
    open fun publish(topic: String, message: String) {
        if(!isConnected()) {
            Log.e(tag, "publish: MQTT尚未连接或已断开")
            return
        }

        try {
            val msg = MqttMessage()
            msg.payload =message.toByteArray()
            mqttClient.publish(topic, msg)
            Log.d(tag, "publish: $message")
        } catch (e: MqttException) {
            Log.e(tag, "publish: ", e)
        }
    }

    open fun stopPingSender() {
        Log.d(tag, "stopPingSender")
        pingSender.stop()
    }

    private fun createSSLContext(): SSLContext {
        try {
            // 使用系统默认的信任管理器
            val trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
            )
            trustManagerFactory.init(null as KeyStore?)

            val sslContext = SSLContext.getInstance("TLSv1.2")
            sslContext.init(null, trustManagerFactory.trustManagers, null)

            return sslContext
        } catch (e: Exception) {
            throw RuntimeException("Failed to create SSL context", e)
        }
    }

    private fun createCustomTrustFactory(): SSLSocketFactory {
        try {
            val certContent = if(Settings.mqttState == "自建") Settings.selfCert else Settings.mqttCert
            if(certContent == "") {
                throw Exception("自签名证书内容为空！")
            }
            // 1. 加载自签名证书
            val cf = CertificateFactory.getInstance("X.509")
            val certInputStream: InputStream = ByteArrayInputStream(certContent.toByteArray())
            val ca: Certificate = cf.generateCertificate(certInputStream)
            certInputStream.close()


            // 2. 创建KeyStore并加载证书
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            keyStore.load(null, null)
            keyStore.setCertificateEntry("broker", ca)

            // 3. 创建TrustManager
            val tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
            )
            tmf.init(keyStore)


            // 4. 创建SSLContext
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, tmf.trustManagers, null)

            return sslContext.socketFactory
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    inner class MyPingSender(context: Context): MQTTPingSender(context) {
        override fun onPinged() {
            this@MQTTClient.onPinged()
        }
    }
}