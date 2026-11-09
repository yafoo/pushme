package com.android.pushme.model

import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// 环境变量数据类
data class EnvData(
    val key: String = "",
    val value: String = "",
    var order: Int = 0,
    val enabled: Boolean = true
)

// 序列化转换
private fun EnvData.toPreferences(): String {
    val list = listOf(key, value, order.toString(), enabled.toString())
    return list.joinToString("|") {
        try {
            Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP).trim()
        } catch (_: Exception) {
            ""
        }
    }
}

private fun String.toEnvData(): EnvData {
    val parts = split("|").map {
        try {
            String(Base64.decode(it, Base64.DEFAULT))
        } catch (_: Exception) {
            ""
        }
    }
    return EnvData(
        key = parts.getOrElse(0) { "" },
        value = parts.getOrElse(1) { "" },
        order = parts.getOrElse(2) { "0" }.toIntOrNull() ?: 0,
        enabled = parts.getOrElse(3) { "true" }.toBoolean()
    )
}

object EnvManager {
    var envDataList by mutableStateOf(initEnvDataList())
        private set

    private fun initEnvDataList(): List<EnvData> {
        val serialized = AppDataStore.gutData("env_data", "")
        return if(serialized.isEmpty()) {
            emptyList()
        } else {
            serialized.split(";").map {
                it.toEnvData()
            }
        }
    }

    private fun saveEnvDataList() {
        // 落盘时按当前顺序重排 order，并把重排结果写回内存，
        // 避免删除/新增后内存里的 order 与磁盘不一致（order 重复会导致按 order 更新时一次改到多条）
        val normalized = envDataList.mapIndexed { index, data -> data.copy(order = index + 1) }
        if(normalized != envDataList) {
            envDataList = normalized
        }
        val serialized = normalized.joinToString(";") { it.toPreferences() }
        AppDataStore.putData("env_data", serialized)
    }

    fun refreshEnvDataList() {
        envDataList = initEnvDataList()
    }

    fun addEnvData(envData: EnvData): Boolean {
        if(envDataList.find { it.key == envData.key } != null) {
            return false
        }
        envData.order = envDataList.size + 1
        envDataList = envDataList + envData
        // 必须落盘：界面新增后会广播 refresh_plugins_env，服务端会 refreshEnvDataList()
        // 从 DataStore 重新读，不落盘的话刚加进去的变量会被直接冲掉（列表又变空）
        saveEnvDataList()
        return true
    }

    fun updateEnvData(envData: EnvData) {
        envDataList = envDataList.map {
            if(it.key == envData.key) envData else it
        }
        saveEnvDataList()
    }

    fun updateEnvDataByOrder(envData: EnvData): Boolean {
        if(envDataList.find { it.key == envData.key && it.order != envData.order } != null) {
            return false
        }
        envDataList = envDataList.map {
            if(it.order == envData.order) envData else it
        }
        saveEnvDataList()
        return true
    }

    fun delEnvData(envData: EnvData) {
        envDataList = envDataList.filter { it.key != envData.key }
        saveEnvDataList()
    }

    fun sortEnvData(i: Int, sort: String) {
        val current = envDataList[i]
        val target = envDataList[if(sort == "up") i - 1 else i + 1]
        val sortValue = current.order
        current.order = target.order
        target.order = sortValue
        envDataList = envDataList.map {
            when(it.key) {
                current.key -> current
                target.key -> target
                else -> it
            }
        }.sortedBy { it.order }
        saveEnvDataList()
    }
}