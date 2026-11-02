package com.android.pushme.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.android.pushme.model.AppDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.reflect.KProperty

open class AutoSaveState<T>(
    private val key: String,
    private val defaultValue: T,
    private val debounceTime: Long = 0,
    coroutineScope: CoroutineScope? = null,
    private val serializer: ((T) -> String)? = null,
    private val deserializer: ((String) -> T)? = null
) {
    private var currentValue by mutableStateOf(defaultValue)

    private var scope: CoroutineScope? = coroutineScope
    private var job: Job? = null

    init {
        currentValue = loadFromStorage()
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return currentValue
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        if(value == currentValue) {
            return
        }

        currentValue = value

        if(scope == null) {
            saveToStorage(value)
        } else {
            // 取消之前的保存任务
            job?.cancel()
            job = scope!!.launch {
                delay(debounceTime)
                saveToStorage(value)
            }
        }
    }

    private fun loadFromStorage(): T {
        return if(deserializer == null) {
            try { AppDataStore.gutData(key, defaultValue) } catch(_: Exception) { defaultValue }
        } else {
            val serialized = try { AppDataStore.gutData(key, "") } catch(_: Exception) { "" }
            if(serialized.isBlank()) defaultValue else try { deserializer(serialized) } catch(_: Exception) { defaultValue }
        }
    }

    private fun saveToStorage(value: T) {
        val serialized = serializer?.invoke(value) ?: value
        AppDataStore.putData(key, serialized)
    }

    fun lateSetCoroutineScope(coroutineScope: CoroutineScope) {
        scope = coroutineScope
    }
}

data class AutoSaveDp(
    private val key: String,
    private val defaultValue: Dp = 0.dp,
    private val debounceTime: Long = 0,
    private val coroutineScope: CoroutineScope? = null
): AutoSaveState<Dp>(
    key, defaultValue, debounceTime, coroutineScope,
    serializer = {
        it.value.toString()
    },
    deserializer = {
        it.toFloat().dp
    }
)

data class AutoSaveDpSize(
    private val key: String,
    private val defaultValue: DpSize = DpSize(0.dp, 0.dp),
    private val debounceTime: Long = 0,
    private val coroutineScope: CoroutineScope? = null
): AutoSaveState<DpSize>(
    key, defaultValue, debounceTime, coroutineScope,
    serializer = {
        "${it.width.value},${it.height.value}"
    },
    deserializer = {
        val parts = it.split(",")
        DpSize(parts[0].toFloat().dp, parts[1].toFloat().dp)
    }
)

data class AutoSaveOffset(
    private val key: String,
    private val defaultValue: Offset = Offset.Zero,
    private val debounceTime: Long = 0,
    private val coroutineScope: CoroutineScope? = null
): AutoSaveState<Offset>(
    key, defaultValue, debounceTime, coroutineScope,
    serializer = {
        "${it.x},${it.y}"
    },
    deserializer = {
        val parts = it.split(",")
        Offset(parts[0].toFloat(), parts[1].toFloat())
    }
)