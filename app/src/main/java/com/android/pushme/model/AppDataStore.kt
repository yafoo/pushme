package com.android.pushme.model

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.android.pushme.App

object AppDataStore {

    // 创建DataStore
    private val App.appDataStore: DataStore<Preferences> by preferencesDataStore(
        name = "App"
    )

    // DataStore变量
    private val dataStore = App.instance.appDataStore

    fun <T> putData(key: String, value: T) {
        dataStore.putData(key, value)
    }

    fun <T> gutData(key: String, value: T): T {
        return dataStore.getData(key, value)
    }

    fun remove(key: String) {
        dataStore.remove(key)
    }

    fun clear() {
        dataStore.clear()
    }
}