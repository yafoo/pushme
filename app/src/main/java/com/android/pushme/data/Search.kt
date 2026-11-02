package com.android.pushme.data

data class Search(
    val user: String = "",
    val keyword: String = "",
    val theme: String = "全部",
    val field: String = "标题",
    val state: String = "全部",
    private val themeMaps: Map<String, String> = mapOf("信息" to "i", "成功" to "s", "警告" to "w", "失败" to "f"),
) {
    fun getThemeValue(): String {
        return themeMaps.getOrDefault(theme, "")
    }
}