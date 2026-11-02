package com.android.pushme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC
import com.android.pushme.model.AppDataBase

const val KEY_NOTIFICATION_ACTION = "NotificationAction"
const val KEY_NOTIFICATION_ID = "NotificationId"

const val KEY_NOTIFICATION_ACTION_READ = "Read"
const val KEY_NOTIFICATION_ACTION_DELETE = "Delete"
const val KEY_NOTIFICATION_ACTION_DELETE_CONFIRM = "DeleteConfirm"
const val KEY_NOTIFICATION_ACTION_CANCEL = "Cancel"

/**
 * 通知动作广播接收器
 * 处理通知上的操作按钮点击事件（已读、删除）
 */
class ReceiverNotificationAction: BroadcastReceiver() {
    private val tag = "$TAG[ReceiverNotificationAction]"

    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.run {
            val id = getIntExtra(KEY_NOTIFICATION_ID, 0)
            val action = getStringExtra(KEY_NOTIFICATION_ACTION)
            
            if (id <= 0 || action == null) {
                Log.w(tag, "Invalid notification action: id=$id, action=$action")
                return@run
            }

            Log.d(tag, "Notification action received: id=$id, action=$action")

            when(action) {
                KEY_NOTIFICATION_ACTION_READ -> handleReadAction(context, id)
                KEY_NOTIFICATION_ACTION_DELETE -> showDeleteConfirmation(context, id)
                KEY_NOTIFICATION_ACTION_DELETE_CONFIRM -> handleDeleteAction(context, id)
                KEY_NOTIFICATION_ACTION_CANCEL -> handleCancelAction(context, id)
                else -> Log.w(tag, "Unknown action: $action")
            }
        }
    }

    /**
     * 处理已读操作
     */
    private fun handleReadAction(context: Context?, messageId: Int) {
        context?.let { ctx ->
            // 取消通知
            ctx.cancelNotification(messageId)

            // 只有非确认通知才标记为已读
            updateMessageReadStatus(ctx, messageId, true)

            // 发送广播通知UI更新
            ctx.sendMainBroadcast("refresh_message_ui")

            Log.d(tag, "Message marked as read: id=$messageId")
        }
    }

    /**
     * 显示删除确认通知
     */
    private fun showDeleteConfirmation(context: Context?, messageId: Int) {
        context?.let { ctx ->
            val messageTitle = getMessageTitle(ctx, messageId) ?: "此消息"
            
            // 取消原通知
            ctx.cancelNotification(messageId)
            
            // 创建确认通知
            val confirmIntent = Intent(ctx, MainActivity::class.java).apply {
                putExtra(MESSAGE_ID, messageId)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            
            val notification = ctx.buildNotification(
                id = messageId,
                title = "确认删除",
                message = "确定要删除 \"$messageTitle\" 吗？",
                visibility = VISIBILITY_PUBLIC,
                activityIntent = confirmIntent,
                channel = MAIN_CHANNEL,
                group = "delete"
            )
            
            // 添加确认删除按钮
            notification.addActionButton(ctx, "确认删除", KEY_NOTIFICATION_ACTION_DELETE_CONFIRM)
            
            // 添加取消按钮（点击后取消确认通知）
            notification.addActionButton(ctx, "取消", KEY_NOTIFICATION_ACTION_CANCEL)

            // 静音通知
            notification.builder?.setSilent(true)
            notification.show()
            
            Log.d(tag, "Delete confirmation shown for message: id=$messageId")
        }
    }

    /**
     * 处理删除操作
     */
    private fun handleDeleteAction(context: Context?, messageId: Int) {
        context?.let { ctx ->
            // 取消确认通知（如果存在）
            ctx.cancelNotification(messageId)
            
            // 从数据库删除消息
            deleteMessage(ctx, messageId)
            
            // 发送广播通知UI更新
            ctx.sendMainBroadcast("refresh_message_ui")
            
            // 显示删除成功提示
            ctx.showToast("消息已删除")
            
            Log.d(tag, "Message deleted: id=$messageId")
        }
    }

    /**
     * 处理取消操作
     */
    private fun handleCancelAction(context: Context?, messageId: Int) {
        context?.let { ctx ->
            ctx.cancelNotification(messageId)
            Log.d(tag, "Message cancel: id=$messageId")
        }
    }

    /**
     * 获取消息标题
     */
    private fun getMessageTitle(context: Context, messageId: Int): String? {
        try {
            val messageDao = AppDataBase.instance.getMessageDao()
            return messageDao.getByID(messageId)?.title
        } catch (e: Exception) {
            Log.e(tag, "Failed to get message title: ${e.message}", e)
            return null
        }
    }

    /**
     * 更新消息已读状态
     */
    private fun updateMessageReadStatus(context: Context, messageId: Int, read: Boolean) {
        try {
            val messageDao = AppDataBase.instance.getMessageDao()
            messageDao.updateRead(messageId, read)
        } catch (e: Exception) {
            Log.e(tag, "Failed to update message read status: ${e.message}", e)
        }
    }

    /**
     * 删除消息
     */
    private fun deleteMessage(context: Context, messageId: Int) {
        try {
            val messageDao = AppDataBase.instance.getMessageDao()
            messageDao.deleteByID(messageId)
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete message: ${e.message}", e)
        }
    }
}
