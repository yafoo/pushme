package com.android.pushme

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.app.ActivityManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.work.workDataOf
import com.android.pushme.api.APIService
import com.android.pushme.data.Message
import com.android.pushme.model.AppDataBase
import com.android.pushme.model.MessageDao
import com.android.pushme.mqtt.MQTTAlarmReconnect
import com.android.pushme.mqtt.MQTTClient
import com.android.pushme.mqtt.MQTTSetting
import com.android.pushme.mqtt.MQTTStatus
import com.android.pushme.task.TaskStore
import com.android.pushme.utils.CacheUtil
import com.android.pushme.utils.FaceManager
import com.android.pushme.utils.FloatingMessage
import com.android.pushme.utils.Settings


const val KEY_SERVICE_ACTION = "ServiceAction"
const val KEY_REPEAT_MESSAGE_WORKER = "repeat_message_worker"
const val KEY_REPEAT_MESSAGE_ACTION = "repeat_message_action"

// 通知监听服务转发过来的通知事件
const val KEY_SERVICE_ACTION_NOTIFY_EVENT = "notify_event"
const val KEY_NOTIFY_EVENT_DATA = "NotifyEventData"

// 定时任务触发时转过来的消息（由 TaskReceiver → TaskScheduler 发出）
const val KEY_SERVICE_ACTION_TASK_EVENT = "task_event"
const val KEY_TASK_EVENT_DATA = "TaskEventData"

// 前台服务模式下连接看门狗的自检间隔
const val WATCHDOG_INTERVAL = 60 * 1000L

// 前台服务通知被用户划掉时，通过 deleteIntent 回传给服务的 action
const val ACTION_FOREGROUND_NOTIFICATION_DISMISSED = "com.android.pushme.FOREGROUND_NOTIFICATION_DISMISSED"

data class MessageInfo(var title: String? = "", var content: String? = "", var date: String? = "", var type: String? = "", var push_key: String? = "")

class MQTTService: Service() {
    private val tag = "$TAG[MQTT SERVICE]"
    private val messageDao: MessageDao = AppDataBase.instance.getMessageDao()
    private var pushKey: String = Settings.pushKey
    private var myMQTTClient: ServiceMQTTClient? = null
    private var delaySubscribe = true
    private var startTime: String = ""
    private var statusCurrent: String = ""
    private var statusTime: String = ""
    private var connectTimes = 0
    private var currentNetworkType: String = "UNKNOWN" // 记录当前网络类型

    private val messageProcessor = MessageProcessor(this)
    private val repostManager = RepostManager()
    private val apiService = APIService(this).apply {
        onMessageReceived = { rawMessage ->
            handleReceivedMessage(rawMessage)
        }
    }

    private var floatingMessage: FloatingMessage? = null

    // 当前是否以前台服务方式运行
    private var isForeground = false

    // 前台服务模式下的连接看门狗
    private var watchdogHandler: Handler? = null
    private var watchdogRunnable: Runnable? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        Log.d(tag,"onCreate")

        // 必须放在最前面：用 startForegroundService 启动时，系统只给 5 秒窗口调用 startForeground，
        // 放到下面这些初始化之后有可能超时（ForegroundServiceDidNotStartInTimeException）
        applyForegroundMode()

        // 记录APP崩溃日志
        if(Settings.appLogSend != "关闭") {
            ExceptionHandler.init(this, "Service")
        }

        // 服务启动时间
        startTime = Utils.getCurrentTime("MM-dd HH:mm")

        // 初始化网络类型
        currentNetworkType = Utils.getNetworkType(this)
        Log.d(tag, "初始网络类型: $currentNetworkType")

        // 注册广播接收器
        registerBroadcastReceivers()

        // 消息转发配置
        repostManager.refreshRepostData()

        // MQTT连接
        connect()

        // API服务
        startApiService()

        // 重复通知消息
        repeatShowMessage()

        // 悬浮消息
        refreshFloatingMessage()

        // 定时任务：闹钟触发时如果本服务没在运行，消息会先入队（见 TaskScheduler.dispatch），
        // 这里补处理一次。放在最后，保证前面各条链路（插件、通知、转发）都已就绪。
        drainPendingTasks()

        super.onCreate()
    }

    /**
     * 处理闹钟触发时因服务未运行而暂存的任务消息。
     *
     * ⚠️ 只在 onCreate 里调用：队列是一次性取空的（TaskStore.drainPending 会清空存储），
     * 多调几次不会重复处理。
     */
    private fun drainPendingTasks() {
        val pending = TaskStore.drainPending()
        if(pending.isEmpty()) return

        Log.d(tag, "补处理 ${pending.size} 条定时任务消息")
        pending.forEach { rawMessage ->
            try {
                handleReceivedMessage(rawMessage)
            } catch (e: Exception) {
                Log.e(tag, "补处理定时任务消息失败", e)
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(tag,"onBind")

        class MqttBinder: Binder() {
        }
        return MqttBinder()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tag,"onStartCommand: action=${intent?.action}")

        // 再补处理一次定时任务队列。
        // 为什么 onCreate 处理过了这里还要处理：TaskScheduler.dispatch 判断「服务是否在运行」
        // 用的是 getRunningServices，有极小概率误判成「没在跑」，于是走了「入队 + startService」分支。
        // 而服务其实活着时 startService 只会触发 onStartCommand、不会触发 onCreate ——
        // 队列就没人来取了。这里补一刀，drainPending 是幂等的（取空即清空存储），重复调用安全。
        drainPendingTasks()

        // Android 14 起允许用户划掉前台服务通知，收到 deleteIntent 后立刻把通知贴回来
        if(intent?.action == ACTION_FOREGROUND_NOTIFICATION_DISMISSED) {
            if(Settings.serviceForeground == "开启") {
                startForegroundMode(force = true)
            } else {
                applyForegroundMode()
            }
            return START_STICKY
        }

        // 按设置项决定以前台服务还是普通后台服务方式运行
        applyForegroundMode()

        // 被系统杀掉后自动重建，配合前台服务尽量保持连接不断
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(tag,"onDestroy")

        stopWatchdog()

        // 注销广播接收器
        unregisterBroadcastReceivers()

        // 销毁实际滞后，造成新连接被断开
        myMQTTClient?.disConnect()
        myMQTTClient?.destroy()
        myMQTTClient = null

        messageProcessor.destroyPlugin()

        // 没啥用
        showStatus(MQTTStatus.DESTROY)

        // TTS
        TTSManager.getInstance().release()

        // 悬浮窗
        floatingMessage?.close()

        // API服务
        apiService.stop()

        super.onDestroy()
    }



    private fun connect() {
        synchronized(this) {
            if(myMQTTClient != null) {
                reConnect()
                return
            }

            val params = MQTTSetting.getSetting()
            Log.d(tag, "MQTT connect $params")
            pushKey = params.pushKey

            if(!params.state) {
                Log.w(tag, "mqtt服务已关闭")
                showStatus(MQTTStatus.CLOSE)
            } else if(pushKey != "" && params.host != "") {
                myMQTTClient = ServiceMQTTClient(params.serverUri, params.clientId, this, MQTTAlarmReconnect(this))
            } else {
                Log.w(tag, "等待初始化push_key和host")
            }
        }
    }

    fun reConnect() {
        synchronized(this) {
            if(myMQTTClient == null) {
                Handler(Looper.getMainLooper()).postDelayed({
                    connect()
                }, 3000)
            } else {
                Handler(Looper.getMainLooper()).postDelayed({
                    myMQTTClient?.reConnect()
                    sendStatusBroadcast(statusCurrent)
                }, 1000)
            }
        }
    }

    fun stopConnect() {
        synchronized(this) {
            myMQTTClient?.disConnect()
        }
    }

    // ==================== 前台服务 / 普通后台服务 兼容 ====================

    /** 按设置项在「前台服务」和「普通后台服务」之间切换 */
    fun applyForegroundMode() {
        val wantForeground = Settings.serviceForeground == "开启"

        // 形态没变就直接返回。
        // MainActivity.onStart() 每次都会调 startMQTTService()，服务已在跑时会发 refresh_foreground_mode，
        // 如果这里不拦一道，每次 App 从后台切回前台都会重贴一次状态通知 —— 状态通知是 IMPORTANCE_HIGH，
        // 重贴就会响一声。连接状态没变就不该出声。
        if(wantForeground == isForeground) return

        if(wantForeground) {
            startForegroundMode()
        } else {
            stopForegroundMode()
        }

        // 切换后刷新一次状态通知，让「服务状态 / 前台服务」副标题即时生效。
        // 必须传 isPing = true 走静默分支：这是运行方式的切换，不是连接状态变化，不该有提示音。
        if(statusCurrent.isNotEmpty()) {
            showStatus(statusCurrent, isPing = true)
        }
    }

    /** 切换为前台服务：常驻通知 + connectedDevice 类型 */
    private fun startForegroundMode(force: Boolean = false) {
        // 通知被划掉时虽然服务仍是前台状态，但通知需要重新贴一次，所以用 force 绕过这里的判断
        if(isForeground && !force) return
        try {
            val notification = buildServiceForegroundNotification()
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    SERVICE_FOREGROUND_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(SERVICE_FOREGROUND_NOTIFICATION_ID, notification)
            }
            isForeground = true
            startWatchdog()
            Log.d(tag, "startForegroundMode: 已切换为前台服务")
            // 自检：确认系统真的把服务标记成了前台服务（而不是悄悄降级）
            logForegroundState()
        } catch (e: Exception) {
            // 例如系统拒绝从后台启动前台服务，退化成普通后台服务即可，不能崩
            isForeground = false
            Log.e(tag, "startForegroundMode: 失败，已退化为后台服务", e)
        }
    }

    /**
     * 查询系统里本服务的真实前台标记。
     *
     * 通知能不能被划掉不能作为判断依据（Android 14 起本来就允许划掉），
     * 这里直接问 ActivityManager 才准。
     */
    @Suppress("DEPRECATION")
    private fun logForegroundState() {
        try {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val record = am.getRunningServices(Int.MAX_VALUE)
                .find { it.service.className == MQTTService::class.java.name }
            Log.d(tag, "前台服务自检: isForeground=${record?.foreground} (true 表示前台服务已生效)")
        } catch (e: Exception) {
            Log.e(tag, "logForegroundState: ", e)
        }
    }

    /** 退出前台服务，退回普通后台服务 */
    private fun stopForegroundMode() {
        if(!isForeground) return
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.e(tag, "stopForegroundMode: ", e)
        }
        isForeground = false
        stopWatchdog()
        Log.d(tag, "stopForegroundMode: 已退回后台服务")
    }

    /**
     * 前台服务模式下的连接看门狗。
     *
     * 前台服务的进程不会被系统冻结，所以这里用 Handler 定时自检；
     * 一旦发现 MQTT 掉线就主动补一次重连，避免心跳闹钟被限流后长时间不上线。
     */
    private fun startWatchdog() {
        if(watchdogRunnable != null) return
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                try {
                    if(myMQTTClient != null && myMQTTClient?.isConnected() == false) {
                        Log.w(tag, "Watchdog: MQTT 未连接，主动重连")
                        reConnect()
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Watchdog: ", e)
                }
                handler.postDelayed(this, WATCHDOG_INTERVAL)
            }
        }
        watchdogHandler = handler
        watchdogRunnable = runnable
        handler.postDelayed(runnable, WATCHDOG_INTERVAL)
    }

    private fun stopWatchdog() {
        watchdogRunnable?.let { watchdogHandler?.removeCallbacks(it) }
        watchdogRunnable = null
        watchdogHandler = null
    }

    private fun refreshPushKey() {
        synchronized(this) {
            delaySubscribe = false
            myMQTTClient?.let {
                it.unsubscribe(pushKey)
                it.disConnect()
            }
            myMQTTClient = null
            reConnect()
        }
    }

    private fun refreshMqttHost() {
        synchronized(this) {
            myMQTTClient?.disConnect()
            myMQTTClient = null
            reConnect()
        }
    }

    private fun refreshMqttState() {
        refreshMqttHost()
    }

    private fun refreshSelfHost() {
        refreshMqttHost()
    }

    private fun refreshChannelSet() {
        myMQTTClient?.initChannelSet()
    }

    private fun refreshMessageSet() {
        //
    }

    private fun startApiService() {
        if (Settings.apiState == "开启") {
            apiService.start(Settings.apiHost, Settings.apiPort)
        } else {
            apiService.stop()
        }
    }

    private fun refreshApiService() {
        apiService.stop()
        startApiService()
    }

    inner class ServiceMQTTClient(serverUri: String, clientId: String, service: MQTTService, mqttAlarmReconnect: MQTTAlarmReconnect): MQTTClient(serverUri, clientId, service, mqttAlarmReconnect) {
        // 定时关闭QuickJS
        private var pingedTimes = 0

        override fun onConnecting() {
            super.onConnecting()

            showStatus(MQTTStatus.CONNECTING)
        }

        override fun onConnected(reconnect: Boolean, serverURI: String) {
            super.onConnected(reconnect, serverURI)

            connectTimes++
            showStatus(MQTTStatus.CONNECTED)
            mqttAlarmReconnect.stop()

            // 连接后，自动订阅
            if(!delaySubscribe) {
                delaySubscribe = true
                Handler(Looper.getMainLooper()).postDelayed({
                    subscribe(pushKey, 1)
                }, 1000)
            } else {
                Handler(Looper.getMainLooper()).postDelayed({
                    subscribe(pushKey, 1)
                }, 5000)
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onDisconnected(cause: Throwable) {
            super.onDisconnected(cause)

            showStatus(MQTTStatus.DISCONNECTED)
            // 掉线后尽快补连，失败再按指数退避（5s→10s→20s→40s→60s）
            mqttAlarmReconnect.start(5)
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onConnectFailed() {
            super.onConnectFailed()

            showStatus(MQTTStatus.CONNECT_FAILED)
            mqttAlarmReconnect.start(10)
        }

        private fun getStatus(): String {
            val status = when {
                isConnected() -> MQTTStatus.CONNECTED
                isDisconnected() -> MQTTStatus.DISCONNECTED
                isConnecting() -> MQTTStatus.CONNECTING
                isDisconnecting() -> MQTTStatus.DISCONNECTING
                else -> MQTTStatus.CLOSED
            }
            return status
        }

        override fun onPinged() {
            super.onPinged()
            showStatus(getStatus(), true)

            pingedTimes = messageProcessor.onPinged(pingedTimes)
        }

        fun initChannelSet() {
            showStatus(MQTTStatus.CHANGE_SET)
        }

        @Throws(Exception::class)
        override fun onMessage(topic: String, message: String) {
            super.onMessage(topic, message)

            showStatus(getStatus(), true)

            handleReceivedMessage(message)
        }

        fun destroy() {
            mqttAlarmReconnect.stop()
        }
    }

    /**
     * 处理收到的消息（MQTT、API、通知监听共用）
     * @param rawMessage 原始 JSON 消息字符串
     * @return 处理结果描述
     */
    private fun handleReceivedMessage(rawMessage: String): String {
        val result = messageProcessor.processMessage(rawMessage) ?: return "消息处理失败"
        val newMessage = result.newMessage
        val titleInfo = result.titleInfo

        if(Message.isNotify(newMessage.type)) {
            // 发送通知
            messageProcessor.showNotification(newMessage, titleInfo)
        } else {
            // 更新悬浮窗
            refreshFloatingMessage(newMessage)
        }

        // 发送广播
        sendMessageBroadcast(newMessage.id, newMessage.type)

        // 消息转发
        if(repostManager.needRepost(result.originalTitle)) {
            RepostManager.doRepost(
                Settings.repostUrl,
                result.originalTitle,
                newMessage.content,
                newMessage.date,
                newMessage.type
            )
        }

        return "success"
    }

    // ==================== 通知监听 ====================

    /**
     * 接收来自通知监听服务（NotifyListenerService）的通知消息。
     *
     * 监听服务在发广播之前已经做完了全部工作：
     * - 过滤：应用白/黑名单、关键词、常驻、空内容，以及「忽略自己」；
     * - 包装成和 MQTT / API 消息完全一致的结构
     *   （title 带 [#来源应用名] 分组标记、content 是事件 JSON、date 取通知时间、type=notify）。
     *
     * 所以这里既不需要解析也不需要包装，原样交给现成的消息处理链路即可。
     *
     * @param rawMessage 监听服务包装好的消息 JSON
     */
    private fun handleNotifyEvent(rawMessage: String) {
        if(rawMessage.isEmpty()) return

        Log.d(tag, "handleNotifyEvent: $rawMessage")

        handleReceivedMessage(rawMessage)
    }

    // ==================== 定时任务 ====================

    /**
     * 接收定时任务触发的消息（见 [com.android.pushme.task.TaskScheduler]）。
     *
     * 包装已经在 TaskScheduler.buildMessageJson 里做完了
     * （title 带 [#定时任务] 分组标记、content 是任务内容、type=task），
     * 所以这里和 handleNotifyEvent 一样，只打日志 + 原样转交，
     * 后面完全复用「插件 → 入库 → 通知 → 转发」的整条链路。
     *
     * 插件处理完如果 type 还是 task，MessageProcessor 会直接丢弃这条消息。
     *
     * @param rawMessage TaskScheduler 包装好的消息 JSON
     */
    private fun handleTaskEvent(rawMessage: String) {
        if(rawMessage.isEmpty()) return

        Log.d(tag, "handleTaskEvent: $rawMessage")

        handleReceivedMessage(rawMessage)
    }

    fun repeatShowMessage() {
        val messageID = CacheUtil.repeatMessageId
        if(messageID == 0) {
            return
        }
        if(getActiveNotification(messageID) == null) {
            Log.d(tag, "REPEAT 任务结束")
            CacheUtil.repeatMessageId = 0
            return
        }

        val title = CacheUtil.repeatMessageTitle
        val content = CacheUtil.repeatMessageContent
        val channel = CacheUtil.repeatMessageChannel
        val group = CacheUtil.repeatMessageGroup
        val type = CacheUtil.repeatMessageType
        val faceBitmap = if(group != "") FaceManager.loadFaceBitmap(this, group) else null
        showMessage(messageID, title, content, channel, type, group, Settings.messageAction == "显示", faceBitmap)

        if(CacheUtil.repeatMessageSpeak) {
            val text = when(Settings.speakFields) {
                "标题" -> title
                "内容" -> content
                else -> "$title\n$content"
            }
            speakMessage(text)
        }

        startWorker(this, workDataOf(
            "action" to KEY_REPEAT_MESSAGE_ACTION,
        ), Settings.repeatTime, KEY_REPEAT_MESSAGE_WORKER)
    }

    fun showStatus(status: String, isPing: Boolean = false) {
        val statusNotifySet = Settings.statusNotify

        if(status != MQTTStatus.CHANGE_SET) {
            statusCurrent = status
            sendStatusBroadcast(status)
        }

        if(status == MQTTStatus.CLOSE) {
            this.cancelNotification(1)
            return
        }

        if(statusNotifySet == "关闭") {
            if(status == MQTTStatus.CHANGE_SET) {
                this.cancelNotification(1)
            }
            return
        }

        if(statusNotifySet == "限断" && statusCurrent == MQTTStatus.CONNECTED) {
            Handler(Looper.getMainLooper()).postDelayed({
                if(statusCurrent == MQTTStatus.CONNECTED) this.cancelNotification(1)
            }, 500)
            return
        }

        val nowTime = Utils.getCurrentTime("HH:mm:ss")
        if(!isPing) {
            statusTime = nowTime
        }
        // 静音
        var silent = false
        if(isPing || statusNotifySet == "静音") {
            silent = true
        } else if(statusNotifySet == "限静") {
            val time = Utils.getCurrentTime("HHmm").toInt()
            val storeChannelStartTime = Settings.statusNotifyMuteStartTime
            val storeChannelEndTime = Settings.statusNotifyMuteEndTime
            if(storeChannelStartTime < storeChannelEndTime) {
                if(time in storeChannelStartTime until storeChannelEndTime) {
                    silent = true
                }
            } else {
                if(storeChannelStartTime == storeChannelEndTime || storeChannelStartTime <= time || time <= storeChannelEndTime) {
                    silent = true
                }
            }
        }

        val statusIcon = if(statusCurrent == MQTTStatus.CONNECTED) "\uD83D\uDFE2" else "\uD83D\uDD34"
        val title = "$statusIcon $statusCurrent $statusTime"
        val message = "\uD83D\uDD1B $startTime  \uD83C\uDD99 $nowTime  \uD83D\uDD22 $connectTimes"

        showStatus(title, message, silent, isForeground)
    }

    fun refreshFloatingMessage(newMsg: Message? = null) {
        val floatingMessageId = CacheUtil.floatingMessageId
        // 来新消息，更新悬浮窗数据
        if(newMsg != null && floatingMessageId == newMsg.id) {
            floatingMessage?.refreshMessage(newMsg)
            return
        }

        // 从数据获取是否显示悬浮窗消息
        val msg = messageDao.getFloating()
        if(msg == null) {
            floatingMessage?.close()
            floatingMessage = null
            return
        }

        if( floatingMessageId!= msg.id) {
            CacheUtil.floatingMessageId = msg.id
        }
        if(floatingMessage == null) {
            floatingMessage = FloatingMessage(this, msg)
        } else {
            floatingMessage?.refreshMessage(msg)
        }
    }

    fun resetFloatingMessage() {
        floatingMessage?.resetState()
    }


    private var serviceReceiver: ServiceReceiver? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: NetworkCallback? = null

    // 注册广播接收器
    @RequiresApi(Build.VERSION_CODES.O)
    private fun registerBroadcastReceivers() {
        if(connectivityManager == null) {
            connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback = NetworkCallback()
            connectivityManager?.registerNetworkCallback(NetworkRequest.Builder().build(),
                networkCallback!!
            )
        }

        if(serviceReceiver == null) {
            val intentFilter = IntentFilter()
            intentFilter.addAction(ACTION_SERVICE_RECEIVER)
            serviceReceiver = ServiceReceiver()
            if(Build.VERSION.SDK_INT >= 33) {
                // 这个接收器只处理本应用自己发的指令（MainActivity / 各种设置页 / MeWorker / 悬浮窗），
                // 用 NOT_EXPORTED 收口，避免别的应用知道 action 就能发 mqtt_reconnect、refresh_push_key 等指令进来。
                //
                // ⚠️ 配套约束：Android 14 起「隐式 intent 只能传送到导出的组件」，
                // 所以发往本接收器的广播**必须** setPackage(packageName)，
                // 否则静默丢弃、不报错。发送方统一在 Notification.kt 底部的
                // sendServiceBroadcast / sendNotifyEventBroadcast，已加 setPackage。
                // 新增发送方时别漏，否则这个接收器收不到任何东西。
                //
                // 与开机自启无关：自启走 ReceiverBootBroadcast（清单里静态注册，系统 BOOT_COMPLETED 触发，
                // 再用显式 Intent 拉起本服务），跟这条动态注册的广播没有任何关系。
                registerReceiver(serviceReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(serviceReceiver, intentFilter)
            }
            Log.d(tag, "registerServiceReceiver")
        }
    }

    // 注销广播接收器
    private fun unregisterBroadcastReceivers() {
        if(connectivityManager != null) {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
            networkCallback = null
            connectivityManager = null
        }

        if (serviceReceiver != null) {
            unregisterReceiver(serviceReceiver)
            serviceReceiver = null
            Log.d(tag, "unregisterServiceReceiver")
        }

        myMQTTClient?.stopPingSender()
    }

    // 服务广播接收器
    inner class ServiceReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.run {
                val action = getStringExtra(KEY_SERVICE_ACTION)
                Log.d(tag, "ServiceReceiver: onReceive ACTION -> $action")
                when(action) {
                    "refresh_push_key" -> refreshPushKey()
                    "refresh_mqtt_host" -> refreshMqttHost()
                    "refresh_mqtt_state" -> refreshMqttState()
                    "mqtt_reconnect" -> reConnect()
                    "refresh_plugins" -> messageProcessor.refreshPlugins()
                    "refresh_self_host" -> refreshSelfHost()
                    "refresh_repost_data" -> repostManager.refreshRepostData()
                    "refresh_channel_set" -> refreshChannelSet()
                    KEY_REPEAT_MESSAGE_ACTION -> repeatShowMessage()
                    "refresh_plugins_env" -> messageProcessor.refreshPluginEnv()
                    "refresh_plugins_pmf" -> messageProcessor.refreshPluginPmf()
                    "refresh_floating_message" -> refreshFloatingMessage()
                    "reset_floating_message" -> resetFloatingMessage()
                    "refresh_message_set" -> refreshMessageSet()
                    "refresh_api_service" -> refreshApiService()
                    "refresh_foreground_mode" -> applyForegroundMode()
                    KEY_SERVICE_ACTION_NOTIFY_EVENT -> handleNotifyEvent(getStringExtra(KEY_NOTIFY_EVENT_DATA) ?: "")
                    KEY_SERVICE_ACTION_TASK_EVENT -> handleTaskEvent(getStringExtra(KEY_TASK_EVENT_DATA) ?: "")
                }
            }
        }
    }


    /**
     * 网络监听回调
     */
    inner class NetworkCallback: ConnectivityManager.NetworkCallback() {
        private var isFirstCall = true

        // 网络可用时调用
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            Log.d(tag, "NetworkCallback: onAvailable")
            val networkCapabilities = connectivityManager?.getNetworkCapabilities(network)
            val newNetworkType = when {
                networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WIFI"
                networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "CELLULAR"
                else -> "UNKNOWN"
            }
            Log.d(tag, "NetworkCallback网络类型: $newNetworkType")

            val pm = getSystemService(POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "com.android.pushme:NetworkCallback")

            if (isFirstCall) {
                isFirstCall = false
            } else {
                try {
                    wl.acquire(60*1000L /*60秒*/)
                    if (currentNetworkType != "WIFI" && newNetworkType == "WIFI") {
                        Log.d(tag, "从移动网络切换到Wi-Fi，重建TCP连接")
                        // 先把旧连接断干净，再重连；reConnect 内部会等 disconnect 完成，避免被跳过
                        stopConnect()
                    }
                    reConnect()
                } finally {
                    if (wl.isHeld) {
                        wl.release();
                    }
                }
            }
            currentNetworkType = newNetworkType
        }

        // 网络丢失时标记为离线，等 onAvailable 回来再重连
        override fun onLost(network: Network) {
            super.onLost(network)
            Log.d(tag, "NetworkCallback: onLost")
            currentNetworkType = "UNKNOWN"
            showStatus(MQTTStatus.OFFLINE, true)
        }
    }

}
