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


class Plugin(private val context: Context) {
    private val tag = "${TAG}[PLUGIN]"
    private val pluginDao: PluginDao = AppDataBase.instance.getPluginDao()

    var pluginCount = 0
    // 插件数据
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

        initQuickJS()

        val jsContext: JSContext = quickJS!!.createContext()

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

        // 销毁
        jsContext.close()

        return Message(0, title, content, date, type)
    }
}