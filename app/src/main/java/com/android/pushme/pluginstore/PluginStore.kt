package com.android.pushme.pluginstore

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.pushme.TAG
import com.android.pushme.model.AppDataStore
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 插件商店的网络层：双源拉索引 / 拉插件源码 / 本地缓存。
 *
 * ## 双源策略
 * 主源 GitHub raw，备源 Gitee raw。每次请求按「上次成功的源优先，再试另一个」的顺序轮，
 * 任何一个成功就记住它，下次先走它。**索引和插件文件各自独立回退** ——
 * 完全可能出现「GitHub 拉得到索引、拉不到文件」的情况，不能把两者的源绑死。
 *
 * ## 为什么不用 `utils/HTTP.kt`
 * 那个 client 是 connect 2s / read 2s，给 MQTT 自己的服务端用的。
 * raw.githubusercontent 在国内 2 秒基本必失败，商店必须单独一套超时（8s / 15s）。
 */
object PluginStore {
    private val tag = "${TAG}[PLUGIN STORE]"

    /** App 支持的索引格式版本，超过就拒绝解析，避免未来格式把老版本 App 搞崩 */
    const val SCHEMA_SUPPORTED = 1

    private const val INDEX_FILE = "index.json"

    private const val KEY_CACHE = "plugin_store_index"
    private const val KEY_CACHE_TIME = "plugin_store_index_time"
    private const val KEY_LAST_BASE = "plugin_store_base"

    /**
     * 只信任官方这两个源，**一期不支持用户自定义源**。
     * 插件是任意 JS（能调 PMF.fetch、能读 ENV 里的全部密钥），
     * 允许填任意源等于把远程代码执行的入口交给任何人。
     */
    private val BASES = listOf(
        "https://raw.githubusercontent.com/yafoo/pushme-plugin/master/",
        "https://gitee.com/yafu/pushme-plugin/raw/master/"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // 部分 raw 服务对不认识的 UA 直接 403，伪装成普通浏览器最稳
    private const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    // ==================== 缓存 ====================

    /** 上一次成功拉到的索引。断网时商店页仍能列出插件（只是装不了） */
    fun loadCache(): StoreIndex? {
        val json = AppDataStore.gutData(KEY_CACHE, "")
        if (json.isBlank()) return null
        return try {
            gson.fromJson(json, StoreIndex::class.java)
        } catch (e: Exception) {
            Log.w(tag, "缓存索引解析失败: ${e.message}")
            null
        }
    }

    fun cacheTime(): Long = AppDataStore.gutData(KEY_CACHE_TIME, 0L)

    // ==================== 拉取 ====================

    /**
     * 拉索引。回调一定在主线程；失败时 [StoreIndex] 为 null，第二个参数是给用户看的原因。
     */
    fun fetchIndex(callback: (StoreIndex?, String) -> Unit) {
        Thread {
            var lastError = ""
            for (base in orderedBases()) {
                try {
                    val json = get(base + INDEX_FILE)
                    val index = gson.fromJson(json, StoreIndex::class.java)
                        ?: throw IllegalStateException("索引内容为空")

                    if (index.schema > SCHEMA_SUPPORTED) {
                        lastError = "索引格式版本过新（${index.schema}），请升级应用"
                        continue
                    }

                    AppDataStore.putData(KEY_CACHE, json)
                    AppDataStore.putData(KEY_CACHE_TIME, System.currentTimeMillis())
                    AppDataStore.putData(KEY_LAST_BASE, base)
                    Log.d(tag, "索引拉取成功 $base 共 ${index.plugins.size} 个插件")

                    mainHandler.post { callback(index, "") }
                    return@Thread
                } catch (e: Exception) {
                    lastError = e.message ?: e.toString()
                    Log.w(tag, "索引拉取失败 $base: $lastError")
                }
            }
            val reason = lastError.ifEmpty { "网络不可用" }
            mainHandler.post { callback(null, reason) }
        }.start()
    }

    /**
     * 同步拉插件源码。**调用方必须自己在子线程调**，失败直接抛异常由调用方转文案。
     */
    fun fetchFile(path: String): String {
        val safe = checkPath(path)
        var lastError = ""
        for (base in orderedBases()) {
            try {
                return get(base + safe)
            } catch (e: Exception) {
                lastError = e.message ?: e.toString()
                Log.w(tag, "插件下载失败 $base$safe: $lastError")
            }
        }
        throw IllegalStateException(lastError.ifEmpty { "网络不可用" })
    }

    // ==================== 内部 ====================

    private fun orderedBases(): List<String> {
        val last = AppDataStore.gutData(KEY_LAST_BASE, "")
        return if (last.isBlank()) BASES else listOf(last) + BASES.filter { it != last }
    }

    /**
     * 路径只允许「仓库内的相对路径」。
     * 挡住 `../`、绝对路径和外部 URL —— 索引是远端内容，不能无条件相信。
     */
    private fun checkPath(path: String): String {
        val p = path.trim()
        if (p.isEmpty()) throw IllegalStateException("插件文件路径为空")
        if (p.startsWith("http", ignoreCase = true) || p.startsWith("/") || p.contains("..")) {
            throw IllegalStateException("插件文件路径不合法")
        }
        return p
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            return response.body?.string() ?: throw IllegalStateException("响应内容为空")
        }
    }
}
