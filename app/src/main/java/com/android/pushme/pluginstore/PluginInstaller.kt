package com.android.pushme.pluginstore

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.pushme.R
import com.android.pushme.TAG
import com.android.pushme.data.MessagePlugin
import com.android.pushme.model.AppDataBase
import com.android.pushme.sendServiceBroadcast
import java.security.MessageDigest

/**
 * 插件安装 / 更新。
 *
 * 只负责「把远端的一份 JS 安全地落到本地表里」，不做 UI 判断。
 * 依赖提示、是否覆盖本地改动这些决策留在商店页。
 */
object PluginInstaller {
    private val tag = "${TAG}[PLUGIN INSTALL]"

    /**
     * `MessagePlugin.source` 的取值：来自官方商店。
     *
     * ⚠️ 这个值和 key 前缀**没有关系** —— key 是 `pushme.xxx`。这里只是标记「这条记录是商店装的」，
     * 改了会让已装插件的 `source` 对不上，别动。
     */
    const val SOURCE_OFFICIAL = "official"

    /**
     * 插件内容必须是**裸函数表达式**（允许前面带 JSDoc 头注释）。
     *
     * 原因在 `PluginCode.getPluginCode`：它是 `plugins.push(\n${code}\n);` 的字符串拼接。
     * 内容末尾多一个分号就变成 `plugins.push(function(){...};)`，整段 JS 语法错误，
     * 而且要到 QuickJS 执行期才炸 —— 表现是**所有插件一起失效**，极难定位。
     */
    private val FUNC_PATTERN = Regex("^(async\\s+)?function\\s*\\(")

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 安装或更新一个商店插件。回调在主线程，失败时第二个参数是给用户看的原因。
     */
    fun install(context: Context, item: StorePlugin, onDone: (Boolean, String) -> Unit) {
        Thread {
            var success = false
            var message = ""
            try {
                val content = normalize(PluginStore.fetchFile(item.file))
                validate(context, item, content)

                val dao = AppDataBase.instance.getPluginDao()
                val existing = dao.getByKey(item.key)

                if (existing == null) {
                    dao.add(
                        MessagePlugin(
                            id = null,
                            title = item.name,
                            content = content,
                            // 追加到末尾，和手工新建插件的规则一致
                            sort = dao.getCount() + 1,
                            state = 1,
                            pluginKey = item.key,
                            versionCode = item.versionCode,
                            versionName = item.version,
                            source = SOURCE_OFFICIAL,
                            sha256 = item.sha256.trim().lowercase(),
                            author = item.author,
                            description = item.description
                        )
                    )
                } else {
                    // 更新：只覆盖内容与元数据，**保留 sort 和 state** ——
                    // 用户排好的顺序、以及特意关掉的插件，不能被一次更新悄悄重置。
                    dao.update(
                        existing.copy(
                            title = item.name,
                            content = content,
                            versionCode = item.versionCode,
                            versionName = item.version,
                            source = SOURCE_OFFICIAL,
                            sha256 = item.sha256.trim().lowercase(),
                            author = item.author,
                            description = item.description,
                            // 装了新版本就把「忽略此版本」清掉，否则下次又提示
                            ignoredVersion = 0
                        )
                    )
                }

                // 所有启用插件的源码会被拼成**一个** JS 程序一次性编译（PluginCode.getJSCode），
                // 不广播的话服务里跑的还是旧代码。
                context.sendServiceBroadcast("refresh_plugins")

                success = true
                Log.d(tag, "安装成功 ${item.key} ${item.version}")
            } catch (e: Exception) {
                message = e.message ?: e.toString()
                Log.e(tag, "安装失败 ${item.key}: $message", e)
            }
            mainHandler.post { onDone(success, message) }
        }.start()
    }

    /**
     * 本地内容是否被用户改过。
     *
     * 判据是「当前内容的 sha256」和「安装时记下的 sha256」不一致。
     * 用来在更新前提示「会覆盖你的改动」—— 编辑页本来就允许改商店插件。
     */
    fun isModified(plugin: MessagePlugin): Boolean {
        if (plugin.sha256.isBlank()) return false
        return !sha256(plugin.content).equals(plugin.sha256, ignoreCase = true)
    }

    fun sha256(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ==================== 内部 ====================

    /**
     * 归一化：统一行尾为 LF、去 BOM、去首尾空白、**去掉末尾多余的分号**。
     * 仓库里的插件可能写成 `function(){};`，直接拼进 `plugins.push(...)` 会语法错误。
     *
     * 🔴 **行尾必须统一**，而且必须和仓库侧 `gen-index.js` 的 `normalize()` 一字不差：
     * sha256 算的是字节，插件仓库在 Windows 上 checkout 出来是 CRLF、在 Linux/CI 上是 LF，
     * 不统一的话同一份源码算出两个 hash —— 表现是**商店里每个插件都报「文件校验失败」**。
     *
     * 商店详情页展示源码时也走这里，保证「看到的就是装进去的」。
     */
    fun normalize(raw: String): String {
        var text = raw.removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
        while (text.endsWith(";")) {
            text = text.dropLast(1).trimEnd()
        }
        return text
    }

    /**
     * 剥掉源码开头的注释与空白，返回第一行真正的代码。
     *
     * 🔴 **必须剥**：插件文件头是 `@name` / `@version` 那段 JSDoc（官方模板 `PLUGIN_DEMO_CODE` 也带），
     * 而 [FUNC_PATTERN] 的 `^` 锚定的是字符串开头。不剥的话，所有带头注释的插件都会被判成
     * 「不是函数表达式」—— 商店里每一个插件都装不进去。
     */
    private fun stripLeadingComments(code: String): String {
        var text = code.trimStart()
        while (true) {
            text = when {
                text.startsWith("/*") -> text.substringAfter("*/", "").trimStart()
                text.startsWith("//") -> text.substringAfter("\n", "").trimStart()
                else -> return text
            }
        }
    }

    private fun validate(context: Context, item: StorePlugin, content: String) {
        if (content.length < 20) {
            throw IllegalStateException(context.getString(R.string.plugin_store_err_empty))
        }
        if (item.sha256.isNotBlank() && !sha256(content).equals(item.sha256.trim(), ignoreCase = true)) {
            throw IllegalStateException(context.getString(R.string.plugin_store_err_hash))
        }
        if (!FUNC_PATTERN.containsMatchIn(stripLeadingComments(content))) {
            throw IllegalStateException(context.getString(R.string.plugin_store_err_not_function))
        }
        if (content.contains("module.exports") || content.contains("require(")) {
            throw IllegalStateException(context.getString(R.string.plugin_store_err_commonjs))
        }
    }
}
