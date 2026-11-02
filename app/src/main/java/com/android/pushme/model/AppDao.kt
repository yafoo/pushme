package com.android.pushme.model

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import com.android.pushme.App
import com.android.pushme.components.message.jsonChartData
import com.android.pushme.components.message.mergeChartData
import com.android.pushme.components.message.parseChartData
import com.android.pushme.data.FieldCount
import com.android.pushme.data.Message
import com.android.pushme.data.MessagePlugin
import com.android.pushme.utils.Settings
import com.google.gson.Gson
import kotlin.collections.toTypedArray

@Dao
interface BaseDao<T> {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(list: MutableList<T>)

    @Delete
    fun delete(element: T)

    @Delete
    fun deleteList(elements:MutableList<T>)

    @Delete
    fun deleteSome(vararg elements:T)

    @Update
    fun update(element: T)
}

@Dao
interface MessageDao: BaseDao<Message> {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(element: Message): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override fun insertAll(list: MutableList<Message>)


    @Query("select * from Message where id = :messageID")
    fun getByID(messageID: Int): Message?

    @Query("select * from Message where title like :title")
    fun getByTitle(title: String): Message?

    @Query("select * from Message where type in (:types) and star = 1")
    fun getFloating(types: List<String> = Message.dataTypes): Message?

    @RawQuery
    fun getByQuery(query: SimpleSQLiteQuery): Message?

    @Query("select * from Message where type in (:types) order by id desc limit :start,:pageSize")
    fun getList(pageSize: Int = 10, start: Int = 0, types: List<String> = Message.notifyTypes): MutableList<Message>

    @RawQuery
    fun getListByQuery(query: SimpleSQLiteQuery): MutableList<Message>


    @Query("select * from Message order by id desc")
    fun getAll(): MutableList<Message>

    @Query("select count(*) from Message where type in (:types)")
    fun getMessageCount(types: List<String> = Message.notifyTypes): Int

    @RawQuery
    fun getMessageCountByQuery(query: SimpleSQLiteQuery): Int

    @Query("select * from Message where type in (:types) order by id desc limit :limit")
    fun getListDashboard(limit: Int = 100, types: List<String> = Message.dataTypes): MutableList<Message>

    @Query("select * from Message where type in (:types) and user = :user order by id desc limit 1")
    fun getUserLatestMessage(user: String, types: List<String> = Message.notifyTypes): Message?

    @Query("select count(DISTINCT user) from Message where type in (:types) and user != :excludeUser")
    fun getUserCount(excludeUser: String = "", types: List<String> = Message.notifyTypes): Int

    @Query("select user as field,count(*) as fieldCount from Message where read = 0 and  type in (:types) GROUP BY user")
    fun getUserUnreadCount(types: List<String> = Message.notifyTypes): List<FieldCount>


    @Update
    override fun update(element: Message)

    @Query("update Message set read = :read where id = :messageID")
    fun updateRead(messageID: Int, read: Boolean)

    @Query("update Message set star = :star where id = :messageID")
    fun updateStar(messageID: Int, star: Boolean)

    @Query("update Message set star = 0 where type in (:types)")
    fun updateUnFloatingAll(types: List<String> = Message.dataTypes)

    @RawQuery
    fun updateMessageByQuery(query: SimpleSQLiteQuery): Int


    @Delete
    override fun delete(element: Message)

    @Query("delete from Message where id = :messageID")
    fun deleteByID(messageID: Int)

    @Delete
    override fun deleteList(elements:MutableList<Message>)

    @Query("delete from Message")
    fun deleteAll()

    @Query("delete from Message where type in (:types)")
    fun clearMessage(types: List<String> = Message.notifyTypes)

    @Query("delete from Message where type in (:types)")
    fun clearDashboard(types: List<String> = Message.dataTypes)


    fun addMessage(message: Message, context: Context): Message {
        if(Message.isData(message.type)) {
            val msg = getByTitle(message.title)
            if(msg != null && msg.id > 0) {
                msg.date = message.date
                msg.type = message.type
                if(message.type != "chart") {
                    msg.content = message.content
                } else {
                    val newData = parseChartData(message.content)
                    val oldData = jsonChartData(msg.content, context)
                    val mergeData = mergeChartData(oldData, newData)
                    msg.content = Gson().toJson(mergeData)
                }
                update(msg)
                return msg
            } else if(message.type == "chart"){
                val newContent = parseChartData(message.content)
                message.content = Gson().toJson(newContent)
            }
        } else {
            val readInitValue = Settings.msgReadState
            message.read = readInitValue == "已读"
        }

        message.id = insert(message).toInt()
        return message
    }
}

@Dao
interface PluginDao: BaseDao<MessagePlugin> {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(element: MessagePlugin): Long

    @Query("select * from MessagePlugin where id = :pluginID")
    fun getByID(pluginID: Int): MessagePlugin?

    /** 按商店标识查已安装的插件；手写插件的 plugin_key 是空串，不会命中 */
    @Query("select * from MessagePlugin where plugin_key = :key limit 1")
    fun getByKey(key: String): MessagePlugin?

    @Query("select * from MessagePlugin order by sort asc, id asc")
    fun getList(): MutableList<MessagePlugin>

    @Query("select * from MessagePlugin where state = :state order by sort asc, id asc")
    fun getList(state: Int = 1): MutableList<MessagePlugin>

    @Query("select count(*) from MessagePlugin")
    fun getCount(): Int

    @Query("select count(*) from MessagePlugin where state = :state")
    fun getCount(state: Int = 1): Int


    @Update
    override fun update(element: MessagePlugin)


    @Delete
    override fun delete(element: MessagePlugin)

    @Query("delete from MessagePlugin where id = :pluginID")
    fun deleteByID(pluginID: Int)

    @Query("update MessagePlugin set sort=sort-1 where sort > :sort")
    fun sortAfterBySort(sort: Int)

    @Query("delete from MessagePlugin")
    fun deleteAll()

    fun add(plugin: MessagePlugin): Int {
        return insert(plugin).toInt()
    }
}

class AppViewModelFactory: ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return modelClass.getConstructor().newInstance()
    }
}

@Database(entities = [Message::class, MessagePlugin::class], version = 9, exportSchema = false)
abstract class AppDataBase : RoomDatabase() {
    abstract fun getMessageDao(): MessageDao

    abstract fun getPluginDao(): PluginDao

    companion object {
        val instance = Single.sin
    }

    private object Single {
        val sin: AppDataBase = Room.databaseBuilder(
            App.instance,
                AppDataBase::class.java,
                "App.db"
            )
            .fallbackToDestructiveMigration()
            .addMigrations(MIGRATION_1_2)
            .addMigrations(MIGRATION_2_3)
            .addMigrations(MIGRATION_3_4)
            .addMigrations(MIGRATION_4_5)
            .addMigrations(MIGRATION_5_6)
            .addMigrations(MIGRATION_6_7)
            .addMigrations(MIGRATION_7_8)
            .addMigrations(MIGRATION_8_9)
            .allowMainThreadQueries()
            .build()
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE 'Message' ADD COLUMN 'type' VARCHAR(20) DEFAULT ''")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Message RENAME TO 'Message_bak'")
        db.execSQL("CREATE TABLE `Message` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `title` VARCHAR NOT NULL DEFAULT '', `content` VARCHAR NOT NULL DEFAULT '', `date` VARCHAR NOT NULL DEFAULT '', `type` VARCHAR NOT NULL DEFAULT '')")
        db.execSQL("INSERT INTO Message(id,title,content,date,type) select id,title,content,date,type FROM Message_bak")
        db.execSQL("DROP TABLE Message_bak")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE `MessagePlugin` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `title` VARCHAR NOT NULL DEFAULT '', `content` VARCHAR NOT NULL DEFAULT '', `sort` INTEGER NOT NULL DEFAULT 0, `state` INTEGER NOT NULL DEFAULT 1)")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Message ADD COLUMN read INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE Message ADD COLUMN star INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Message ADD COLUMN user VARCHAR NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE Message ADD COLUMN face VARCHAR NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE Message ADD COLUMN theme VARCHAR NOT NULL DEFAULT ''")

        updateExistingDataInBatches(db)
    }

    private fun updateExistingDataInBatches(db: SupportSQLiteDatabase, batchSize: Int = 100) {
        var offset = 0
        var hasMoreData = true

        while (hasMoreData) {
            val cursor = db.query(
                "SELECT id, title FROM Message LIMIT ? OFFSET ?",
                arrayOf(batchSize.toString(), offset.toString())
            )

            val updates = mutableListOf<Pair<Long, Pair<String, String>>>()

            while (cursor.moveToNext()) {
                val idColumnIndex = cursor.getColumnIndex("id")
                val titleColumnIndex = cursor.getColumnIndex("title")
                // 检查列是否存在
                if (idColumnIndex > 0 && titleColumnIndex > 0) {
                    val id = cursor.getLong(idColumnIndex)
                    val title = cursor.getString(titleColumnIndex)
                    val info = Message.getTitleTheme(title)
                    updates.add(id to (info.title to info.theme))
                }
            }

            cursor.close()

            // 执行批量更新
            if (updates.isNotEmpty()) {
                db.beginTransaction()
                try {
                    updates.forEach { (id, data) ->
                        val params = mutableListOf<Any>().apply {
                            add(data.first)
                            add(data.second)
                            add(id)
                        }.toTypedArray()
                        db.execSQL(
                            "UPDATE Message SET title = ?, theme = ? WHERE id = ?",
                            params
                        )
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }

                offset += batchSize
            } else {
                hasMoreData = false
            }
        }
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 为type字段创建索引
        db.execSQL("CREATE INDEX IF NOT EXISTS index_Message_type ON Message(type)")
        // 为user字段创建索引
        db.execSQL("CREATE INDEX IF NOT EXISTS index_Message_user ON Message(user)")
        // id字段由于是主键，默认已有索引，无需额外创建
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Message RENAME TO 'Message_bak'")
        db.execSQL("""
            CREATE TABLE `Message` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL DEFAULT '',
                `content` TEXT NOT NULL DEFAULT '',
                `date` TEXT NOT NULL DEFAULT '',
                `type` TEXT NOT NULL DEFAULT '',
                `read` INTEGER NOT NULL DEFAULT 1,
                `star` INTEGER NOT NULL DEFAULT 0,
                `user` TEXT NOT NULL DEFAULT '',
                `face` TEXT NOT NULL DEFAULT '',
                `theme` TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent())
        db.execSQL("INSERT INTO Message(id,title,content,date,type,read,star,user,face,theme) select id,title,content,date,type,read,star,user,face,theme FROM Message_bak")
        db.execSQL("DROP TABLE Message_bak")
        // 重新创建索引
        db.execSQL("CREATE INDEX IF NOT EXISTS index_Message_type ON Message(type)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_Message_user ON Message(user)")
    }
}

/**
 * 插件商店：给 `MessagePlugin` 补上「稳定标识 + 版本 + 来源」等字段。
 *
 * ⚠️ 这个 migration **必须存在**：`AppDataBase` 用了 `fallbackToDestructiveMigration()`，
 * 只要 version 加了却没配 migration，升级时会直接清库 —— 用户所有消息和插件全没。
 *
 * 全部给 `NOT NULL DEFAULT`，老数据迁移上来就是「手写插件」的形态：
 * `plugin_key = ''`、`version_code = 0`、`source = ''`，商店页据此判断「未安装」。
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE MessagePlugin ADD COLUMN plugin_key TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE MessagePlugin ADD COLUMN version_code INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE MessagePlugin ADD COLUMN version_name TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE MessagePlugin ADD COLUMN source TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE MessagePlugin ADD COLUMN sha256 TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE MessagePlugin ADD COLUMN author TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE MessagePlugin ADD COLUMN description TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE MessagePlugin ADD COLUMN ignored_version INTEGER NOT NULL DEFAULT 0")
    }
}