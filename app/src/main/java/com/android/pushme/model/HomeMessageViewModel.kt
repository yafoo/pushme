package com.android.pushme.model

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import androidx.sqlite.db.SimpleSQLiteQuery
import com.android.pushme.TAG
import com.android.pushme.cancelAllNotifications
import com.android.pushme.cancelNotification
import com.android.pushme.data.Message
import com.android.pushme.utils.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeMessageViewModel: SearchViewModel() {
    override val tag = "$TAG[HomeMessageViewModel]"



    // 获取一条消息
    fun getMessage(id: Int): Message? {
        return dashboardList.find {it.id == id} ?: messageDao.getByID(id)
    }

    // 获取上一条消息
    fun getPrevMessage(msg: Message, type: String = "prev"): Message? {
        val notifyType = Message.notifyTypes.joinToString(prefix = "'", separator = "', '", postfix = "'")
        val query = buildString {
            append("SELECT * from message WHERE type in ($notifyType)")
            if(type == "prev") append(" AND id > ?") else append(" AND id < ?")
            append(" AND user = ?")
            if(type == "next") append(" ORDER BY id DESC")
            append(" LIMIT 1")
        }
        val params = mutableListOf<Any>().apply {
            add(msg.id)
            add(msg.user)
        }.toTypedArray()
        val sql = SimpleSQLiteQuery(query, params)
        return messageDao.getByQuery(sql)
    }

    // 获取下一条消息
    fun getNextMessage(msg: Message, ): Message? {
        return getPrevMessage(msg, "next")
    }

    // 清空消息
    fun clearMessage() {
        messageDao.clearMessage()

        updateMessageCount() // 更新总数
        updateUserUnreadList() // 更新未读数
        refreshList() // 刷新列表
    }

    // 更新总消息数（设置里显示消息总数）
    val messageCount = mutableIntStateOf(-1)
    fun updateMessageCount() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = messageDao.getMessageCount()
            withContext(Dispatchers.Main) {
                messageCount.intValue = count
            }
        }
    }

    // 更新未读消息数
    val userUnreadList = mutableStateMapOf<String, Int>()
    fun updateUserUnreadList() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = messageDao.getUserUnreadCount()
            withContext(Dispatchers.Main) {
                userUnreadList.clear()
                userUnreadList.putAll(list.associate { it.field to it.fieldCount })
            }
        }
    }

    // 其他
    fun cancelAllNotifications(context: Context) {
        try {
            context.cancelAllNotifications(1)
        } catch (_: Exception) { }
    }
    fun cancelNotification(context: Context, id: Int) {
        try {
            context.cancelNotification(id)
        } catch (_: Exception) { }
    }

    // dashboard
    private val dashboardShow = mutableStateOf(false)

    fun setDashboardShow() {
        dashboardShow.value = true
        updateDashboardList()
    }
    fun setDashboardHide() {
        dashboardShow.value = false
    }


    @SuppressLint("MutableCollectionMutableState")
    val dashboardList = mutableStateListOf<Message>()
    val dashboardCount: State<Int>
        get() {
            // 显示时更新一次
            getListDashboard()
            return mutableIntStateOf(dashboardList.size)
        }
    val dashboardSortBy = mutableStateOf(Settings.dashboardSortBy)
    val dashboardSort = mutableStateOf(Settings.dashboardSort)

    private fun getListDashboard() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = messageDao.getListDashboard()
            withContext(Dispatchers.Main) {
                dashboardList.clear()
                dashboardList.addAll(sortDashboardList(list))
            }
        }
    }
    fun updateDashboardList() {
        // 界面显示时才更新数据
        if(dashboardShow.value) {
            getListDashboard()
        }
    }
    // 强制刷新数据小屏列表（不受 dashboardShow 限制，供悬浮窗等外部触发）
    fun forceRefreshDashboard() {
        getListDashboard()
    }

    private fun sortDashboardList(list: MutableList<Message>): MutableList<Message> {
        val tempList: MutableList<Message> = mutableListOf()
        tempList.addAll(list)
        when {
            dashboardSortBy.value == "date" && dashboardSort.value == "desc" -> tempList.sortByDescending { it.date }
            dashboardSortBy.value == "date" && dashboardSort.value == "asc" -> tempList.sortBy { it.date }
            dashboardSortBy.value == "id" && dashboardSort.value == "desc" -> tempList.sortByDescending { it.id }
            dashboardSortBy.value == "id" && dashboardSort.value == "asc" -> tempList.sortBy { it.id }
        }
        return tempList
    }

    fun setDashboardSortBy(sortBy: String = "date") {
        if(dashboardSortBy.value == sortBy) {
            return
        }

        dashboardSortBy.value = sortBy
        Settings.dashboardSortBy = sortBy
        val sortedList = sortDashboardList(dashboardList)
        dashboardList.clear()
        dashboardList.addAll(sortedList)
    }

    fun setDashboardSort(sort: String = "desc") {
        if(dashboardSort.value == sort) {
            return
        }

        dashboardSort.value = sort
        Settings.dashboardSort = sort
        val sortedList = sortDashboardList(dashboardList)
        dashboardList.clear()
        dashboardList.addAll(sortedList)
    }

    fun clearDashboard() {
        messageDao.clearDashboard()
        getListDashboard() // 刷新列表
    }

    fun updateMessageFloating(id: Int) {
        val message = messageDao.getByID(id)
        if(message != null) {
            messageDao.updateUnFloatingAll()
            if(!message.star) {
                messageDao.updateStar(id, true)
            }
        }
        getListDashboard()
    }

    // 初始化数据
    init {
        updateUserUnreadList()
        refreshList()
        updateMessageCount()
    }
}