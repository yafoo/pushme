package com.android.pushme.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf

object PmfManager {
    private val PMF_LIST = mapOf("md5" to false, "base64" to false, "rsa" to false, "aes" to false, "toast" to false, "fetch" to false)
    private val _pmfStateMap = mutableStateOf(initPmfStateList())
    val pmfStateMap: Map<String, Boolean> by _pmfStateMap

    private fun initPmfStateList(): Map<String, Boolean> {
        val serialized = AppDataStore.gutData("pmf_data", "")
        val savedMap = serialized.split(";")
            .filter { it.isNotEmpty() && "|" in it }
            .associate {
                val parts = it.split("|", limit = 2)
                parts[0] to (parts[1].toBooleanStrictOrNull() ?: false)
            }
        return PMF_LIST.mapValues { (key, defaultValue) ->
            savedMap[key] ?: defaultValue
        }
    }

    private fun savePmfStateList() {
        val serialized = pmfStateMap.toList().joinToString(";") { it.first + "|" + it.second }
        AppDataStore.putData("pmf_data", serialized)
    }

    // 添加更新函数以便修改状态
    fun updatePmfState(key: String, value: Boolean) {
        if (_pmfStateMap.value.containsKey(key)) {
            _pmfStateMap.value = _pmfStateMap.value.toMutableMap().apply {
                this[key] = value
            }
            savePmfStateList()
        }
    }

    // 重置所有状态为默认值
    fun resetPmfState() {
        _pmfStateMap.value = PMF_LIST
        savePmfStateList()
    }

    fun refreshPmfState() {
        _pmfStateMap.value = initPmfStateList()
    }
}