package com.android.pushme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.android.pushme.components.AppUpdateDialog
import com.android.pushme.components.MeAppNavHost
import com.android.pushme.data.Message
import com.android.pushme.model.AppDataStore
import com.android.pushme.model.AppViewModel
import com.android.pushme.model.AppViewModelFactory
import com.android.pushme.model.HomeMessageViewModel
import com.android.pushme.model.PluginViewModel
import com.android.pushme.model.UserMessageViewModel
import com.android.pushme.notify.NotifyHelper
import com.android.pushme.task.TaskStore
import com.android.pushme.ui.theme.PushMeTheme
import com.android.pushme.utils.CacheUtil
import com.android.pushme.utils.LocaleUtil
import com.android.pushme.utils.Permission.checkAlarmPermission
import com.android.pushme.utils.Permission.goAlarmPermission
import com.android.pushme.utils.PushMeApi.sendPushMeLog
import com.android.pushme.utils.Settings
import kotlin.random.Random


const val MESSAGE_ID = "MessageID"
const val MESSAGE_TYPE = "MessageType"
const val MESSAGE_CONTENT = "MessageContent"

const val TAG = "PUSHME"
const val KEY_MAIN_ACTION = "MainAction"
const val KEY_NEW_MESSAGE_ID = "NewMessageID"
const val KEY_NEW_MESSAGE_TYPE = "NewMessageType"
const val KEY_SERVICE_STATUS = "ServiceStatus"
// 广播 action 必须带上本应用真实的包名前缀，不要用 com.example（Android Studio 模板命名空间）。
// 这些接收器是动态注册的，其它应用只要知道 action 字符串就能发广播进来，
// 用模板前缀会大幅提高与别的应用撞名的概率。
const val ACTION_MAIN_RECEIVER = "com.android.pushme.MAIN_RECEIVER"
const val ACTION_SERVICE_RECEIVER = "com.android.pushme.SERVICE_RECEIVER"

fun randomColor():Color {
    return Color(
        red = Random.nextFloat(),
        green = Random.nextFloat(),
        blue = Random.nextFloat()
    )
}
fun Modifier.colorful(): Modifier {
    return background(randomColor())
}

val LocalAppViewModel = staticCompositionLocalOf<AppViewModel> {
    error("No AppViewModel provided")
}
val LocalNavController = staticCompositionLocalOf<NavHostController> {
    error("No NavController provided")
}

val LocalHomeMessageViewModel = staticCompositionLocalOf<HomeMessageViewModel> {
    error("No HomeMessageViewModel provided")
}

val LocalUserMessageViewModel = staticCompositionLocalOf<UserMessageViewModel> {
    error("No UserMessageViewModel provided")
}

val LocalScreen = staticCompositionLocalOf<String> {
    error("No Screen provided")
}

class MainActivity: ComponentActivity() {
    private val tag = "$TAG[MAIN ACTIVITY]"
    private var intent: Intent? = null

    lateinit var appViewModel: AppViewModel

    var navController: NavHostController? = null
    lateinit var homeMessageViewModel: HomeMessageViewModel
    lateinit var userMessageViewModel: UserMessageViewModel
    var messageReceiver: MainReceiver? = null

    lateinit var pluginViewModel: PluginViewModel

    private fun navigateToMessage() {
        val currentIntent = intent
        val currentNavController = navController
        if(currentIntent != null && currentNavController != null) {
            val messageId = currentIntent.getIntExtra(MESSAGE_ID, 0)
            val messageType = currentIntent.getStringExtra(MESSAGE_TYPE)?:""
            if(messageId > 0) {
                Log.d(tag, "MESSAGE_ID: " + messageId.toString())
                if(messageType == "url") {
                    Utils.openUrl(currentIntent.getStringExtra(MESSAGE_CONTENT)?:"", this)
                    homeMessageViewModel.updateMessageRead(messageId, true)
                    homeMessageViewModel.updateUserUnreadList()
                } else {
                    currentNavController.navigate("message/$messageId") {
                        launchSingleTop = true
                    }
                }
                this.cancelNotification(messageId)
            }
            currentIntent.putExtra(MESSAGE_ID, 0)
            intent = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(tag, "onCreate")
        super.onCreate(savedInstanceState)

        // 自建服务增加状态字段后，兼容老数据
        val selfState = AppDataStore.gutData("self_state", "")
        if(selfState == "") {
            if(Settings.selfHost != "" && Settings.pushKey != "") {
                Settings.selfKey = Settings.pushKey
                Settings.selfState = "开启"
            }
        }

        // v5.2.0
        // 增加全局服务状态后，兼容老设置
        if(Settings.selfState == "开启") {
            Settings.selfState = "废弃"
            Settings.mqttState = "自建"
        }
        // 强制ssl，端口9010
        Settings.mqttPort = 9010

        // 记录APP崩溃日志
        if(Settings.appLogSend != "关闭") {
            ExceptionHandler.init(this, "MainActivity")
        }

        // 设置version
        val version = "v" + packageManager.getPackageInfo(packageName, 0).versionName
        Settings.appVersion = version
        Log.d(tag, "Settings.appVersion set: $version")

        // 设置host
//        val mqttHost = Settings.mqttHost
//        val mqttPort = Settings.mqttPort
//        getMqttHost(onUpdate =  {
//            if(it.host != mqttHost || it.port != mqttPort) {
//                this.sendServiceBroadcast("refresh_mqtt_host")
//            }
//        })

//        Settings.pushKey = ""
//        Settings.pushId = 0

        // 设置intent
        intent = getIntent()

        // viewModel
        appViewModel = ViewModelProvider(this, AppViewModelFactory())[AppViewModel::class.java]
        homeMessageViewModel = ViewModelProvider(this, AppViewModelFactory())[HomeMessageViewModel::class.java]
        // 注册悬浮窗数据变更回调，用于通知数据小屏和详情页刷新（不受导航影响）
        App.onFloatingDataChanged = { homeMessageViewModel.forceRefreshDashboard() }
        userMessageViewModel = ViewModelProvider(this, AppViewModelFactory())[UserMessageViewModel::class.java]
        pluginViewModel = ViewModelProvider(this, AppViewModelFactory())[PluginViewModel::class.java]

        // 动态注册广播接收器
        registerMessageReceiver()

        // 状态栏高度
        window.decorView.post {
            val statusBarHeight = getStatusBarHeight()
            if(statusBarHeight > 0 && statusBarHeight != CacheUtil.statusBarHeight) {
                CacheUtil.statusBarHeight = statusBarHeight
            }
        }

        setContent {
            navController = rememberNavController()
            PushMeTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Log.d(tag, "onSurface: ")
                    CompositionLocalProvider(
                        LocalAppViewModel provides appViewModel,
                        LocalHomeMessageViewModel provides homeMessageViewModel,
                        LocalUserMessageViewModel provides userMessageViewModel,
                        LocalNavController provides navController!!
                    ) {
                        MeAppNavHost()
                        AppUpdateDialog()
                    }
                }
            }
            navigateToMessage()
        }

        if(Settings.recentTask == "隐藏") {
            Utils.setAppFromRecents(this, true)
        }
    }

    override fun onStart() {
        Log.d(tag, "onStart")

        // 安卓12检查闹钟权限
        if (Utils.isAndroid12()) {
            if (checkAlarmPermission(this)) {
                startMQTTService()
            } else {
                goAlarmPermission(this) {
                    if(it) startMQTTService()
                }
            }
        } else {
            startMQTTService()
        }

        // 权限提醒初始化
        appViewModel.initPermit(this)

        // 版本检测
        appViewModel.appUpdateCheck(this)

        super.onStart()
    }

    override fun onRestart() {
        Log.d(tag, "onRestart")
        super.onRestart()
    }

    override fun onNewIntent(intent: Intent) {
        Log.d(tag, "onNewIntent")
        super.onNewIntent(intent)

        if(intent != getIntent()) {
            this.intent = intent
        }
    }

    override fun onResume() {
        Log.d(tag, "onResume")
        super.onResume()

        // 跳转消息详情页
        navigateToMessage()
    }

    override fun onPause() {
        Log.d(tag, "onPause")
        super.onPause()
    }

    override fun onStop() {
        Log.d(tag, "onStop")
        super.onStop()
    }

    override fun onDestroy() {
        Log.d(tag, "onDestroy")
        unregisterMessageReceiver()
        App.onFloatingDataChanged = null
        super.onDestroy()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleUtil.onAttach(newBase))
    }

    fun startMQTTService() {
        val isRunning = Utils.isServiceRunning(this, SERVICE_NAME)
        Log.d(tag, "isRunning: $isRunning")
        // 同步一次真实的前台服务状态，保证首页 FGS 标签在启动时就是准的
        appViewModel.serviceForeground.value = Utils.isServiceForeground(this, SERVICE_NAME)
        // 同步通知监听的真实生效状态（开关开启 + 已授权），保证首页 NFY 标签准确。
        // 放在这里是因为 onStart 每次都会调本方法，从系统设置页授权完回来也能立刻刷新。
        appViewModel.serviceNotifyListen.value =
            Settings.notifyListenState == "开启" && NotifyHelper.isGranted(this)
        // 同步定时任务的真实生效状态（总开关开启 + 至少一个启用中的任务），保证首页 TSK 标签准确
        refreshTaskTag()
        // 启动service
        if(!isRunning) {
            val intent = Intent(this, MQTTService::class.java)
            try {
                // 前台服务模式必须用 startForegroundService，否则 5 秒内没 startForeground 会被系统杀掉
                if(Settings.serviceForeground == "开启" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } catch (e: Exception) {
                val log = Utils.getCrashLog("StartService", e, this)
                sendPushMeLog(log)
            }
        } else {
            this.sendServiceBroadcast("mqtt_reconnect")
            // 设置项可能在服务运行期间被改过，让服务自己切换前后台形态
            this.sendServiceBroadcast("refresh_foreground_mode")
        }
    }

    /**
     * 刷新首页 TSK 标签：定时任务总开关开启 **且** 至少有一个启用中的任务。
     *
     * 和 FGS / NFY 一样，标签反映「真实在跑」而不是「设置项」——
     * 开关开着但一条任务都没有（或全部停用）时，到点不会有任何东西触发，亮标签是误导。
     *
     * 调用时机：
     * - [startMQTTService]（onStart 每次都调）—— 冷启动、从其它应用/系统设置回来
     * - 「服务设置 - 定时任务」切换总开关
     * - 任务列表页刷新、启停、编辑页保存/删除
     * （这些操作都在应用内，不会触发 onStart，等不到自动回填）
     */
    fun refreshTaskTag() {
        appViewModel.serviceTask.value =
            Settings.taskState == "开启" && TaskStore.load().any { it.enabled }
    }

    fun registerMessageReceiver() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(ACTION_MAIN_RECEIVER)
        messageReceiver = MainReceiver()
        if(Build.VERSION.SDK_INT >= 33) {
            // 这条广播只有本应用的服务会发（新消息、服务状态、刷新 UI），用 NOT_EXPORTED 收口，
            // 避免别的应用伪造 new_message 之类把界面刷成异常状态。
            registerReceiver(messageReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(messageReceiver, intentFilter)
        }
        Log.d(tag, "registerMessageReceiver")
    }

    fun unregisterMessageReceiver() {
        messageReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: IllegalArgumentException) {
                Log.w(tag, "unregisterMessageReceiver: $e")
            }
        }
        messageReceiver = null
        Log.d(tag, "unregisterMessageReceiver")
    }

    private fun getStatusBarHeight(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.rootWindowInsets
                ?.getInsets(android.view.WindowInsets.Type.statusBars())?.top ?: 0
        } else {
            val resourceId = resources.getIdentifier(
                "status_bar_height", "dimen", "android"
            )
            if (resourceId > 0) {
                resources.getDimensionPixelSize(resourceId)
            } else {
                window.decorView.rootWindowInsets?.systemWindowInsetTop ?: 0
            }
        }
    }

    // 新消息广播接收器
    inner class MainReceiver(): BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.run {
                val action = getStringExtra(KEY_MAIN_ACTION)
                Log.d(tag, "MainReceiver: onReceive ACTION -> $action")
                when(action) {
                    "new_message" -> {
                        val messageId = getIntExtra(KEY_NEW_MESSAGE_ID, 0)
                        val messageType = getStringExtra(KEY_NEW_MESSAGE_TYPE)?:""
                        Log.d(tag, "MainReceiver NEW_MESSAGE_ID: $messageId NEW_MESSAGE_TYPE: $messageType")
                        if(Message.isData(messageType)) {
                            homeMessageViewModel.updateDashboardList()
                        } else if(messageId > 0) {
                            homeMessageViewModel.receiveMessage(messageId)
                            homeMessageViewModel.updateMessageCount()
                            homeMessageViewModel.updateUserUnreadList()
                            if(userMessageViewModel.search.value.user != "") {
                                userMessageViewModel.receiveMessage(messageId)
                            }
                        }
                    }
                    "refresh_message_ui" -> {
                        homeMessageViewModel.refreshList()
                        homeMessageViewModel.updateMessageCount()
                        homeMessageViewModel.updateUserUnreadList()
                        if(userMessageViewModel.search.value.user != "") {
                            userMessageViewModel.refreshList()
                        }
                    }
                    "service_status" -> {
                        val status = getStringExtra(KEY_SERVICE_STATUS)?:""
                        appViewModel.serviceStatus = status
                        // 直接问系统要真实的前台服务状态，避免标签只是反映设置项
                        appViewModel.serviceForeground.value = Utils.isServiceForeground(this@MainActivity, SERVICE_NAME)
                    }
                }
            }
        }
    }
}