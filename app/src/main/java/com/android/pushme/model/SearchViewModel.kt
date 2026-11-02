package com.android.pushme.model

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.sqlite.db.SimpleSQLiteQuery
import com.android.pushme.TAG
import com.android.pushme.data.Message
import com.android.pushme.data.Search
import com.android.pushme.data.SearchBox
import com.android.pushme.data.SearchResult
import com.android.pushme.utils.PushMeApi.sendPushMeLog
import com.android.pushme.utils.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class SearchViewModel: ViewModel() {
    open val tag = "$TAG[SearchViewModel]"
    var isSqliteSupported = false
    val messageDao: MessageDao = AppDataBase.instance.getMessageDao()

    // 分组设置
    val msgUserTop = mutableStateOf(Settings.msgUserTop)
    val msgUserEmpty = mutableStateOf(Settings.msgUserEmpty)

    // 搜索条件
    val search = mutableStateOf(Search())
    fun updateSearch(newSearch: Search) {
        search.value = newSearch
    }
    fun updateUser(user: String = "") {
        updateSearch(search.value.copy(user = user))
    }
    fun updateKeyword(keyword: String = "") {
        updateSearch(search.value.copy(keyword = keyword))
    }

    // 搜索盒子
    val searchBox = mutableStateOf(SearchBox())
    fun updateBox(newSearchBox: SearchBox) {
        searchBox.value = newSearchBox
    }
    fun updateBoxShow(show: Boolean = false) {
        updateBox(searchBox.value.copy(show = show))
    }

    // 搜索结果
    val searchResult = mutableStateOf(SearchResult())
    fun updateResult(newSearchResult: SearchResult) {
        searchResult.value = newSearchResult
    }
    fun updatePage(page: Int = 1) {
        updateResult(searchResult.value.copy(page = page))
    }

    // 搜索盒子自动隐藏
    fun searchBoxAutoHide() {
        if(searchBox.value.show && search.value.theme == "全部" && search.value.field == "标题" && search.value.state == "全部") {
            updateBox(searchBox.value.copy(filterShow = false, actionShow = false))
        }
        if(!searchBox.value.filterShow && searchBox.value.show && search.value.keyword == "") {
            updateBoxShow(false)
        }
    }
    // 显示搜索盒子
    fun setSearchBoxShow() {
        updateBoxShow(true)
        updateSearchMessageCount()
    }
    // 隐藏搜索盒子
    fun setSearchBoxHide() {
        updateBoxShow(false)
        updateKeyword()
        setSearchFilter()
    }
    // 切换高级搜索
    fun setSearchFilter(value: Boolean = false) {
        updateBox(searchBox.value.copy(filterShow = value))
        if(!value) {
            setSearchFilterTheme()
            setSearchFilterField()
            setSearchFilterState()
            setSearchFilterAction()
        }
    }
    fun setSearchFilterTheme(value: String = "全部") {
        updateSearch(search.value.copy(theme = value))
    }
    fun setSearchFilterField(value: String = "标题") {
        updateSearch(search.value.copy(field = value))
    }
    fun setSearchFilterState(value: String = "全部") {
        updateSearch(search.value.copy(state = value))
    }
    fun setSearchFilterAction(value: Boolean = false) {
        updateBox(searchBox.value.copy(actionShow = value))
    }

    fun clearData() {
        search.value = Search()
        searchBox.value = SearchBox()
        searchResult.value = SearchResult()
    }

    // 收到新消息
    fun receiveMessage(id: Int) {
        if(searchResult.value.isAtTop) {
            refreshList()
        } else {
            prependList(id)
        }
    }

    // 刷新列表
    fun refreshList(pageSize: Int = 10, callback: () -> Unit = {}) {
        updatePage(1)
        getList(pageSize) {
            callback()
            if(searchBox.value.show) {
                updateSearchMessageCount()
            }
        }
    }

    // 加载更多
    fun getListMore() {
        val resultValue = searchResult.value
        if(resultValue.isLoading || !resultValue.hasMore) {
            return
        }

        updateResult(searchResult.value.copy(isLoading = true, page = resultValue.page + 1))
        getList {
            updateResult(searchResult.value.copy(isLoading = false))
        }
    }

    fun delMessage(id: Int, user: String, type: String) {
        messageDao.deleteByID(id)

        if(Message.isData(type)) {
            return
        }

        val resultValue = searchResult.value
        if(resultValue.list.isNotEmpty()) {
            if(resultValue.list.size == 10) {
                refreshList()
            } else {
                var replaceMessage: Message? = null
                if(user != "" && search.value.user == "") {
                    replaceMessage = messageDao.getUserLatestMessage(user)
                }
                viewModelScope.launch {
                    withContext(Dispatchers.Main) {
                        if(replaceMessage != null) {
                            resultValue.list.replaceAll { it ->
                                if(it.id == id) Message.copyWithContentToText(replaceMessage) else it
                            }
                        } else {
                            resultValue.list.removeAll {
                                it.id == id
                            }
                        }
                    }

                    if(searchBox.value.show) {
                        updateSearchMessageCount()
                    }
                }
            }
        }
    }

    fun updateMessageRead(id: Int, read: Boolean? = null) {
        val message = messageDao.getByID(id)
        if(message != null) {
            messageDao.updateRead(id, read ?: !message.read)
            updateListItem(id)
        }
    }

    // 批量标记已读（对话模式用）
    fun batchMarkAsRead(ids: List<Int>, onComplete: () -> Unit = {}) {
        if(ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            var hasUnread = false
            ids.forEach { id ->
                val msg = messageDao.getByID(id)
                if(msg != null && !msg.read) {
                    messageDao.updateRead(id, true)
                    hasUnread = true
                }
            }
            if(hasUnread) {
                withContext(Dispatchers.Main) {
                    // 更新列表中的已读状态
                    ids.forEach { id ->
                        val index = searchResult.value.list.indexOfFirst { it.id == id }
                        if(index >= 0) {
                            searchResult.value.list[index] = searchResult.value.list[index].copy(read = true)
                        }
                    }
                    // 数据库写入完成后再执行回调
                    onComplete()
                }
            }
        }
    }

    fun updateMessageStar(id: Int) {
        val message = messageDao.getByID(id)
        if(message != null) {
            messageDao.updateStar(id, !message.star)
            updateListItem(id)
        }
    }

    fun updateMessageAll(action: String) {
        viewModelScope.launch(Dispatchers.IO) {
            messageDao.updateMessageByQuery(buildQuery(buildType = action))

            refreshList()
        }
    }

    private fun getList(pageSize: Int = 10, callback: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val resultValue = searchResult.value
            val isChat = resultValue.chatMode
            val currentListCopy = withContext(Dispatchers.Main) {
                resultValue.list.toList()
            }
            val list = try {
                messageDao.getListByQuery(buildQuery(pageSize))
            } catch(e: Exception) {
                sendPushMeLog("sqlite查询出错：\n" + e.printStackTrace())
                return@launch
            }

            val tempList = mutableListOf<Message>()
            if(isChat) {
                // 对话模式：保持数据库原始顺序（DESC，新消息在前）
                // reverseLayout=true 会自动反转显示，让新消息在底部
                if(resultValue.page > 1) {
                    // 加载更早的消息，添加到列表末尾
                    tempList.addAll(currentListCopy)
                    tempList.addAll(list)
                } else {
                    // 首次加载
                    tempList.addAll(list)
                }
            } else {
                if(resultValue.page > 1) {
                    tempList.addAll(currentListCopy)
                }
                for(item in list) {
                    tempList += Message.copyWithContentToText(item)
                }
            }

            withContext(Dispatchers.Main) {
                resultValue.list.clear()
                resultValue.list.addAll(tempList.distinctBy { it.id })

                updateResult(searchResult.value.copy(hasMore = list.size >= pageSize))
                if (resultValue.page == 1 && list.isNotEmpty()) {
                    updateResult(searchResult.value.copy(firstID = list[0].id))
                }
            }

            callback()
        }
    }

    // 开头插入数据
    private fun prependList(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val newMessage = messageDao.getByID(id)
            newMessage?.let {
                if(msgUserEmpty.value == "开启" && newMessage.user == "") {
                    newMessage.user = " "
                }
                // 如果在分组列表，新消息非本组，则不更新
                if(search.value.user != "" && search.value.user != newMessage.user) {
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    val tempList = mutableListOf<Message>()
                    // 对话模式保留原始内容用于富文本渲染
                    val newMsg = if(searchResult.value.chatMode) it else Message.copyWithContentToText(it)
                    tempList.add(newMsg)
                    if(search.value.user == "" && newMessage.user != "") {
                        searchResult.value.list.removeAll { msg ->
                            msg.user == newMessage.user
                        }
                    }
                    tempList.addAll(searchResult.value.list)
                    if (tempList.size > 100) { // 限制长度
                        tempList.removeAt(tempList.lastIndex)
                    }
                    searchResult.value.list.clear()
                    searchResult.value.list.addAll(tempList.distinctBy { msg -> msg.id })
                }

                updateResult(searchResult.value.copy(newerCount = searchResult.value.newerCount + 1))
            }
        }
    }

    // 生成查询语句
    private fun buildQuery(pageSize: Int = 10, buildType: String = "search"): SimpleSQLiteQuery {
        val searchValue = search.value
        val boxValue = searchBox.value

        val user = searchValue.user
        val keyword = if(searchValue.keyword !== "") "%${searchValue.keyword}%" else ""
        val themeWord = if(boxValue.filterShow && searchValue.theme != "全部") searchValue.getThemeValue() else ""
        val fieldName = when {
            !boxValue.filterShow -> "title"
            searchValue.field == "内容" -> "content"
            searchValue.field == "标题+内容" -> "(COALESCE(title, '') || ' ' || COALESCE(content, ''))"
            else -> "title"
        }
        val readValue = when(searchValue.state) {
            "已读" -> 1
            "未读" -> 0
            else -> -1
        }
        val starValue = when(searchValue.state) {
            "收藏" -> 1
            "未藏" -> 0
            else -> -1
        }

        val notifyType = Message.notifyTypes.joinToString(prefix = "'", separator = "', '", postfix = "'")

        val where = buildString {
            append(" WHERE type in ($notifyType)")
            if(user != "") append(" AND user = ?")
            if (keyword != "") append(" AND $fieldName LIKE ?")
            if (themeWord != "") append(" AND (title LIKE ? OR theme = ?)")
            if(readValue > -1) append(" AND read = $readValue")
            if(starValue > -1) append(" AND star = $starValue")
        }
        val params = mutableListOf<String>().apply {
            if(user != "") add(if(user == " ") "" else user)
            if (keyword !== "") add(keyword)
            if (themeWord !== "") {
                add("[${themeWord}]%")
                add(themeWord)
            }
        }.toTypedArray()

        val partBy = if (msgUserEmpty.value == "开启") "user" else "CASE WHEN user = '' THEN 'user_' || id ELSE user END"
        val groupTable = "SELECT *,ROW_NUMBER() OVER ( PARTITION BY $partBy ORDER BY id DESC) as row_num from message"

        val query = buildString {
            when(buildType) {
                "search" -> {
                    var userCount = 0
                    if(user == "") {
                        val excludeUser = if (msgUserEmpty.value == "开启") "-1000000" else ""
                        userCount = messageDao.getUserCount(excludeUser)
                    }
                    // 分组查询
                    if(isSqliteSupported && user == "" && (userCount > 0 || msgUserEmpty.value == "开启")) {
                        val fields = if(msgUserEmpty.value == "开启") "CASE WHEN user = '' THEN ' ' ELSE user END as user,id,title,content,date,type,read,star,face,theme" else "*"
                        append("Select $fields from ($groupTable $where) where row_num = 1")
                        if(msgUserTop.value == "开启") {
                            append(" ORDER BY CASE WHEN user = '' THEN 0 ELSE 1 END DESC, id DESC")
                        } else {
                            append(" ORDER BY id DESC")
                        }
                    } else {
                        append("Select * from message $where")
                        append(" ORDER BY id DESC")
                    }
                    append(" LIMIT ${(searchResult.value.page - 1) * pageSize}, $pageSize")
                }
                "count" -> append("Select count(*) from message $where")
                "read", "star" -> append("update message set $buildType = true $where")
                "unread" -> append("update message set read = false $where")
                "unstar" -> append("update message set star = false $where")
                "delete" -> append("delete from Message $where")
            }

        }

        return SimpleSQLiteQuery(query, params)
    }

    // 更新搜索结果数
    private fun updateSearchMessageCount() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = messageDao.getMessageCountByQuery(buildQuery(buildType = "count"))
            withContext(Dispatchers.Main) {
                updateResult(searchResult.value.copy(total = count))
            }
        }
    }

    // 更新消息列表项
    fun updateListItem(id: Int) {
        if(search.value.state != "全部") {
            return refreshList(searchResult.value.list.size)
        }
        viewModelScope.launch(Dispatchers.IO) {
            val updateMessage = messageDao.getByID(id)
            updateMessage?.let {
                if(msgUserEmpty.value == "开启" && updateMessage.user == "" && search.value.user == "") {
                    updateMessage.user = " "
                }
                withContext(Dispatchers.Main) {
                    // 对话模式保留原始内容用于富文本渲染
                    val tempMessage = if(searchResult.value.chatMode) it else Message.copyWithContentToText(it)
                    searchResult.value.list.replaceAll { msg ->
                        if(msg.id == id) tempMessage else msg
                    }
                }
            }
        }
    }

    init {
        isSqliteSupported = isWindowFunctionsSupported()
    }
}