package com.android.pushme.mqtt

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi
import com.android.pushme.MQTTService
import com.android.pushme.TAG
import com.android.pushme.Utils
import java.text.SimpleDateFormat
import java.util.Date

class MQTTAlarmReconnect(private val service: MQTTService) {
    private val tag = "$TAG[MQTTAlarmReconnect]"
    private var alarmReceiver: BroadcastReceiver? = null
    private var pendingIntent: PendingIntent? = null

    @Volatile
    private var hasStarted = false

    private val ACTION = "[MQTTAlarmReconnect].ACTION."
    private val WAKELOCK = "com.android.pushme:MQTTAlarmReconnect"

    // 重连退避的上下限（秒）
    private val MIN_DELAY = 5
    private val MAX_DELAY = 300

    private var delayReconnect: Int = MIN_DELAY

    init {
        alarmReceiver = MQTTAlarmReconnectReceiver()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun start(delayTime: Int) {
        if(hasStarted) {
            stop()
        }

        // 首次重连延迟，之后每次失败按指数退避翻倍，上限 MAX_DELAY 秒。
        // 上限刻意压到 60 秒：原来是每次 +60s 最多到 600s，一次瞬时断网会变成最长 10 分钟空窗。
        delayReconnect = if(delayTime > 0) delayTime.coerceIn(MIN_DELAY, MAX_DELAY) else MIN_DELAY

        Log.d(tag, "Register MQTTAlarmReconnectReceiver to MqttService")
        if(Build.VERSION.SDK_INT >= 33) {
            service.registerReceiver(alarmReceiver, IntentFilter(ACTION), RECEIVER_EXPORTED)
        } else {
            service.registerReceiver(alarmReceiver, IntentFilter(ACTION))
        }

        var flag = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flag = PendingIntent.FLAG_UPDATE_CURRENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flag = PendingIntent.FLAG_IMMUTABLE
        }
        pendingIntent = PendingIntent.getBroadcast(service, 0, Intent(ACTION), flag)
        schedule(delayReconnect)
        hasStarted = true
    }

    fun stop() {
        if (hasStarted) {
            Log.d(tag, "Unregister MQTTAlarmReconnectReceiver to MqttService")
            if (pendingIntent != null) {
                // Cancel Alarm.
                val alarmManager = service.getSystemService(Service.ALARM_SERVICE) as AlarmManager
                alarmManager.cancel(pendingIntent!!)
            }
            hasStarted = false
            try {
                service.unregisterReceiver(alarmReceiver)
            } catch (e: IllegalArgumentException) {
                //Ignore unregister errors.
            }
        }
    }

    private fun schedule(delayTime: Int) {
        val nextAlarmInMilliseconds = System.currentTimeMillis() + delayTime * 1000
        Log.d(tag, "Schedule next MQTTAlarmReconnect at " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(nextAlarmInMilliseconds)))
        val alarmManager = service.getSystemService(Service.ALARM_SERVICE) as AlarmManager
        pendingIntent?.let {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAlarmInMilliseconds, it)
        }
    }

    inner class MQTTAlarmReconnectReceiver: BroadcastReceiver() {
        private val wakeLockTag: String = (WAKELOCK)

        @SuppressLint("Wakelock")
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(tag, "MQTTAlarmReconnectReceiver: onReceive")
            val pm = context.getSystemService(Service.POWER_SERVICE) as PowerManager
            val wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, wakeLockTag)
            try {
                wakelock.acquire(60*1000L /*60秒*/)
                delayReconnect = (delayReconnect * 2).coerceAtMost(MAX_DELAY)
                schedule(delayReconnect)
                if(Utils.isNetworkOnline(context)) {
                    service.reConnect()
                } else {
                    service.showStatus(MQTTStatus.OFFLINE, true)
                }
            } finally {
                if (wakelock.isHeld) {
                    wakelock.release();
                }
            }
        }
    }
}