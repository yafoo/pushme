package com.android.pushme.task

import com.android.pushme.utils.Settings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 定时任务的持久化。
 *
 * 任务列表整份以 JSON 存在 `Settings.taskList` 里 —— 任务数量是个位数，
 * 整存整取最简单，不需要为它单开一张 Room 表。
 *
 * ⚠️ **只有本对象可以读写任务列表**，UI 和调度器都走这里，
 * 避免两边各持一份内存副本互相覆盖（通知过滤的应用名单踩过这个坑）。
 */
object TaskStore {
    private val gson = Gson()
    private val listType = object : TypeToken<MutableList<TaskInfo>>() {}.type
    private val stringListType = object : TypeToken<MutableList<String>>() {}.type

    /** 待处理消息队列最多留这么多条，防止异常情况下无限堆积 */
    private const val PENDING_LIMIT = 20

    fun load(): MutableList<TaskInfo> {
        val raw = Settings.taskList
        if (raw.isBlank()) return mutableListOf()
        return try {
            gson.fromJson<MutableList<TaskInfo>>(raw, listType) ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun save(list: List<TaskInfo>) {
        Settings.taskList = gson.toJson(list)
    }

    fun find(id: Int): TaskInfo? = load().firstOrNull { it.id == id }

    /** 新增或更新一条任务，返回保存后的 id */
    fun upsert(task: TaskInfo): Int {
        val list = load()
        val index = list.indexOfFirst { it.id == task.id }
        if (index >= 0) {
            list[index] = task
        } else {
            task.id = nextId(list)
            list.add(task)
        }
        save(list)
        return task.id
    }

    fun delete(id: Int) {
        val list = load()
        list.removeAll { it.id == id }
        save(list)
    }

    fun nextId(list: List<TaskInfo>): Int {
        // id 从 1 开始：0 留给「新建任务」这个未保存状态
        return (list.maxOfOrNull { it.id } ?: 0) + 1
    }

    /**
     * 上移 / 下移一条任务（[direction] = `"up"` / `"down"`）。
     *
     * **没有单独的 order 字段**：任务列表整份存 JSON，数组顺序本身就是显示顺序，
     * 新增任务 append 到末尾也符合直觉。所以直接交换两个位置即可，
     * 不用像 `EnvData` 那样维护一份 order 再重排。
     *
     * ⚠️ 换位置**不需要重排闹钟**：每个任务一个闹钟、requestCode = task.id，
     * 闹钟和列表顺序无关。
     *
     * @return 交换后的新列表；任务不存在或已经到顶/到底时返回 null
     */
    fun move(taskId: Int, direction: String): MutableList<TaskInfo>? {
        val list = load()
        val index = list.indexOfFirst { it.id == taskId }
        if (index < 0) return null

        val target = if (direction == "up") index - 1 else index + 1
        if (target !in list.indices) return null

        val temp = list[index]
        list[index] = list[target]
        list[target] = temp

        save(list)
        return list
    }

    // ==================== 待处理消息队列 ====================
    //
    // 闹钟触发时 MQTTService 可能没在运行。此时消息不能直接丢掉，
    // 先入队，再把服务拉起来；服务 onCreate 末尾会 drain 一次队列。
    // 用 DataStore 而不是内存变量，是因为进程可能已经被杀干净了。

    @Synchronized
    fun pushPending(messageJson: String) {
        val list = loadPending().toMutableList()
        list.add(messageJson)
        while (list.size > PENDING_LIMIT) {
            list.removeAt(0)
        }
        Settings.pendingTaskQueue = gson.toJson(list)
    }

    @Synchronized
    fun drainPending(): List<String> {
        val list = loadPending()
        if (list.isNotEmpty()) {
            Settings.pendingTaskQueue = ""
        }
        return list
    }

    private fun loadPending(): List<String> {
        val raw = Settings.pendingTaskQueue
        if (raw.isBlank()) return emptyList()
        return try {
            gson.fromJson<List<String>>(raw, stringListType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
