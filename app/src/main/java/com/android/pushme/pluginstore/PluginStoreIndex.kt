package com.android.pushme.pluginstore

/**
 * 插件商店索引（`index.json`）的根对象。
 *
 * 仓库侧由 CI 生成，App 只读不写。
 * ⚠️ 这两个类**必须进 ProGuard keep 名单**（`proguard-rules.pro`）：
 * Gson 是按字段名反射出 key 的，release 里字段被 R8 改成 a/b/c 之后，
 * 整份索引会解析成一堆空值 —— 和当初 `NotifyEvent` 是同一个坑。
 */
data class StoreIndex(
    /** 索引格式版本。App 只认 <= PluginStore.SCHEMA_SUPPORTED，防止未来格式把老版本 App 搞崩 */
    val schema: Int = 1,
    /** 索引生成时间（ISO 8601），仅用于展示 */
    val updatedAt: String = "",
    val plugins: List<StorePlugin> = emptyList()
)

/**
 * 商店里的一个插件。
 *
 * 与本地 [com.android.pushme.data.MessagePlugin] 的关系：
 * - `key`  → `pluginKey`（稳定身份，判断「装没装过 / 能不能更新」全靠它）
 * - `versionCode` → `versionCode`（**用 int 比较**，别拿 "1.10.0" 和 "1.9.0" 比字符串）
 * - `sha256` → 安装时校验 + 之后判断「本地是否被改过」
 */
data class StorePlugin(
    /** 稳定标识，约定 `pushme.xxx`，全局唯一 */
    val key: String = "",
    val name: String = "",
    val description: String = "",
    val author: String = "",
    /** 可读版本号，如 1.0.2 */
    val version: String = "",
    /** 版本号整数形式，CI 按 major*10000 + minor*100 + patch 生成 */
    val versionCode: Int = 0,
    /** 要求的最低 App 版本（versionName 形式）。为空表示不限制 */
    val minAppVersion: String = "",
    val tags: List<String> = emptyList(),
    /** 依赖的内置方法，如 `PMF.toast`、`PMF.fetch`。安装前会提示用户 */
    val dependencies: List<String> = emptyList(),
    /** 需要用户填写环境变量的 key */
    val env: List<String> = emptyList(),
    /** 相对仓库根目录的路径，如 `plugins/plugin_time.js`。**绝不写绝对 URL**，否则换源就废了 */
    val file: String = "",
    /** 插件源码的 sha256（小写十六进制） */
    val sha256: String = "",
    /** 文件字节数，仅用于展示 */
    val size: Long = 0
) {
    /** 依赖里的 PMF 键名（`PMF.fetch` → `fetch`），用于查 [com.android.pushme.model.PmfManager] 的开关状态 */
    fun dependencyKeys(): List<String> = dependencies.mapNotNull { dep ->
        val key = dep.removePrefix("PMF.").trim()
        key.ifEmpty { null }
    }
}
