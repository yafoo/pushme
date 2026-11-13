package com.android.pushme.plugin

import android.content.Context
import android.util.Log
import com.android.pushme.TAG
import com.android.pushme.model.AppDataBase
import com.android.pushme.model.EnvManager
import com.android.pushme.data.Message
import com.android.pushme.model.PluginDao
import com.android.pushme.model.PmfManager
import com.quickjs.JSContext
import com.quickjs.JSObject
import com.quickjs.QuickJS
import com.quickjs.plugin.ConsolePlugin
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors


class Plugin(private val context: Context) {
    private val tag = "${TAG}[PLUGIN]"
    private val pluginDao: PluginDao = AppDataBase.instance.getPluginDao()

    /**
     * QuickJS 的专用单线程。
     *
     * 消息处理链有三处会进到插件里：主线程（定时任务 / 通知转发的广播）、
     * Paho 回调线程（MQTT onMessage）、API 服务线程池（HTTP 推送）。
     * 而 QuickJS 的 runtime 是单线程的，多线程同时用会直接崩或者报
     * "Context disposed error"。所有碰 JS 的操作都必须排进这条线程串行执行。
     *
     * 更关键的是 destroy()：它原本由 MQTT 心跳回调（Paho 线程）直接调用，
     * 会在 calcMessage 正执行插件的中途把 runtime 关掉。排进这条线程后，
     * destroy 自然排在正在跑的那次 calcMessage 之后，不会再中途拔电源。
     */
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "pushme-plugin").apply { isDaemon = true }
    }

    @Volatile
    var pluginCount = 0
    // 插件数据
    @Volatile
    private var pluginJsCode = ""

    private var quickJS: QuickJS? = null
    // QuickJS插件
    private var consolePlugin: ConsolePlugin? = null
    // 环境变量
    private var envObjExt: EnvObjExt? = null
    // 内置方法
    private var pmfObjExt: PmfObjExt? = null

    init {
        initPluginJsCode()
    }

    fun initPluginJsCode() {
        pluginCount = pluginDao.getCount(1)
        if (pluginCount > 0) {
            Log.d(tag, "initPluginJsCode")
            val plugins = pluginDao.getList(1).map { it.content }
            pluginJsCode = PluginCode.getJSCode(plugins)
            // Log.d(tag, "PluginJsCode：\n$pluginJsCode")
        } else {
            this.destroy()
        }
    }

    fun initQuickJS() {
        if (quickJS == null) {
            Log.d(tag, "QuickJS init")
            quickJS = QuickJS.createRuntimeWithEventQueue()
            // consolePlugin = ConsolePlugin()
            envObjExt = EnvObjExt()
            pmfObjExt = PmfObjExt(context)
        }
    }

    fun refreshPluginEnv() {
        EnvManager.refreshEnvDataList()
    }

    fun refreshPluginPmf() {
        PmfManager.refreshPmfState()
    }

    fun destroy() {
        // 只排队，不直接在调用线程（MQTT 心跳线程）上关 runtime，
        // 否则正在跑的 calcMessage 会拿到一个被释放的 context。
        executor.execute { doDestroy() }
    }

    private fun doDestroy() {
        quickJS?.let {
            Log.d(tag, "QuickJS destroy")
            quickJS!!.close()
            quickJS = null
            consolePlugin = null
            envObjExt = null
            pmfObjExt = null
        }
    }

    fun calcMessage(message: Message): Message {
        if (pluginCount == 0) {
            return message
        }

        // 投到插件线程执行，并等结果（保持原有的同步语义：插件改完的 msg 才往下走）
        return try {
            executor.submit(Callable { doCalcMessage(message) }).get()
        } catch (e: ExecutionException) {
            // 还原插件内部抛出的原始异常（含 java.lang.Error），交给上层处理
            throw e.cause ?: e
        }
    }

    private fun doCalcMessage(message: Message): Message {
        initQuickJS()

        val jsContext: JSContext = quickJS!!.createContext()

        try {
            // msg
            val msg = JSObject(jsContext)
                .set("title", message.title)
                .set("content", message.content)
                .set("date", message.date)
                .set("type", message.type)
            jsContext.set("msg", msg)

            // console
            consolePlugin?.let {
                jsContext.addPlugin(it)
            }

            // ENV
            val env = JSObject(jsContext)
            env.addObjExt(envObjExt)
            jsContext.set("ENV", env)

            // PMF
            val pmf = JSObject(jsContext)
            pmf.addObjExt(pmfObjExt)
            jsContext.set("PMF", pmf)

            val msgNew: JSObject = jsContext.executeObjectScript(pluginJsCode, null)
            val title = msgNew.getString("title")
            val content = msgNew.getString("content")
            val date = msgNew.getString("date")
            val type = msgNew.getString("type")

            return Message(0, title, content, date, type)
        } finally {
            // 插件里抛异常（比如 JS 里的 ReferenceError）也必须把 context 关掉，
            // 否则每失败一次就漏一个 context，最后连 runtime 一起卡死。
            jsContext.close()
        }
    }
}