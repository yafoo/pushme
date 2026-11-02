package com.android.pushme

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class MeWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    private val tag = "$TAG[MeWorker]"

    override fun doWork(): Result {
        // 在这里执行你的任务逻辑
        Log.d(tag, "Worker 开始执行任务")

        val action = inputData.getString("action")?:""
        if(action == KEY_REPEAT_MESSAGE_ACTION) {
            applicationContext.sendServiceBroadcast(KEY_REPEAT_MESSAGE_ACTION)
        }

        return Result.success() // 返回执行结果
    }
}

fun startWorker(context: Context, inputData: Data, duration: Int = 60, workerID: String = "") {
    val workRequest = OneTimeWorkRequestBuilder<MeWorker>()
        .setInputData(inputData)
        .setInitialDelay(duration.toLong(), TimeUnit.SECONDS)  // 1分钟后再次执行
        .build()
    when(workerID) {
        "" -> WorkManager.getInstance(context).enqueue(workRequest)
        else -> WorkManager.getInstance(context).enqueueUniqueWork(
            workerID,      // 唯一名称
            ExistingWorkPolicy.REPLACE,      // 如果已存在，先取消，再启动新的
            workRequest
        )
    }

}