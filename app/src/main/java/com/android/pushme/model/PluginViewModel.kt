package com.android.pushme.model

import android.annotation.SuppressLint
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.pushme.data.MessagePlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PluginViewModel: ViewModel() {
    private val pluginDao: PluginDao = AppDataBase.instance.getPluginDao()

    @SuppressLint("MutableCollectionMutableState")
    private var _pluginList = mutableStateOf(mutableListOf<MessagePlugin>())
    val pluginList: State<MutableList<MessagePlugin>> = _pluginList

    fun getList(state: Int? = null, callback: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val listMutable: MutableList<MessagePlugin> = mutableListOf()

            val list = if(state != null) pluginDao.getList(state) else pluginDao.getList()
            listMutable.addAll(list)
            withContext(Dispatchers.Main) {
                _pluginList.value = listMutable
            }

            callback()
        }
    }

    fun addPlugin(plugin: MessagePlugin) {
        if(plugin.id == 0 || plugin.id == null) {
            plugin.id = null
            plugin.sort = pluginDao.getCount() + 1
            pluginDao.add(plugin)
        } else {
            pluginDao.update(plugin)
        }

        getList() // 刷新列表
    }

    fun switchState(plugin: MessagePlugin) {
        plugin.state = if(plugin.state == 1) 0 else 1
        addPlugin(plugin)
    }

    fun getPlugin(id: Int): MessagePlugin? {
        return pluginDao.getByID(id)
    }

    fun getPluginCount(): Int {
        return pluginDao.getCount()
    }

    fun getPluginCount(state: Int = 1): Int {
        return pluginDao.getCount(state)
    }

    fun delPlugin(plugin: MessagePlugin) {
        pluginDao.deleteByID(plugin.id ?: 0)
        pluginDao.sortAfterBySort(plugin.sort)
        getList()
    }

    fun swapSort(plugin: MessagePlugin, plugin2: MessagePlugin) {
        val sort = plugin.sort
        plugin.sort = plugin2.sort
        plugin2.sort = sort
        pluginDao.update(plugin)
        pluginDao.update(plugin2)
        getList()
    }

    fun clearPlugin() {
        pluginDao.deleteAll()

        getList() // 刷新列表
    }

    init {
        getList()
    }
}