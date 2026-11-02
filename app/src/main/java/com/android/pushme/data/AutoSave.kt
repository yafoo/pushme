package com.android.pushme.data

import com.android.pushme.model.AppDataStore
import kotlin.reflect.KProperty

class AutoSave<T>(
    private val key: String,
    private val defaultValue: T,
    private val serializer: ((T) -> String)? = null,
    private val deserializer: ((String) -> T)? = null
) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return loadFromStorage()
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        saveToStorage(value)
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
}