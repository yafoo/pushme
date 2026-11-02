/*******************************************************************************
 * Copyright (c) 2014 IBM Corp.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution. 
 *
 * The Eclipse Public License is available at 
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at 
 *   http://www.eclipse.org/org/documents/edl-v10.php.
 */
package com.android.pushme.mqtt;

import static android.content.Context.RECEIVER_EXPORTED;
import static com.android.pushme.MainActivityKt.TAG;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttPingSender;
import org.eclipse.paho.client.mqttv3.internal.ClientComms;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;


import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Default ping sender implementation on Android. It is based on AlarmManager.
 *
 * <p>This class implements the {@link MqttPingSender} pinger interface
 * allowing applications to send ping packet to server every keep alive interval.
 * </p>
 *
 * @see MqttPingSender
 */
public class MQTTPingSender implements MqttPingSender {
	// Identifier for Intents, log messages, etc..
	private static final String tag = TAG + "[AlarmPingSender]";

	// TODO: Add log.
	public ClientComms comms;
	private Context context;
	private BroadcastReceiver alarmReceiver;
	private PendingIntent pendingIntent;
	private volatile boolean hasStarted = false;

	private String PING_SENDER = "[AlarmPingSender].pingSender.";
	private String PING_WAKELOCK = "[AlarmPingSender].client.";

	public MQTTPingSender(Context context) {
		if (context == null) {
			throw new IllegalArgumentException(
					"Context cannot be null.");
		}
		this.context = context;
	}

	@Override
	public void init(ClientComms comms) {
		this.comms = comms;
		this.alarmReceiver = new AlarmReceiver();
	}

	@Override
	public void start() {
		if(hasStarted) {
			this.stop();
		}

		String action = PING_SENDER
				+ comms.getClient().getClientId();
		Log.d(tag, "Register PingAlarmReceiver to MqttService");
		if(Build.VERSION.SDK_INT >= 33) {
			context.registerReceiver(alarmReceiver, new IntentFilter(action), RECEIVER_EXPORTED);
		} else {
			context.registerReceiver(alarmReceiver, new IntentFilter(action));
		}

		int flag = 0;
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			flag = PendingIntent.FLAG_UPDATE_CURRENT;
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			flag = PendingIntent.FLAG_IMMUTABLE;
		}
		pendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(
				action), flag);

		schedule(comms.getKeepAlive());
		hasStarted = true;
	}

	@Override
	public void stop() {
		if(hasStarted){
			Log.d(tag, "Unregister PingAlarmReceiver to MqttService");
			if(pendingIntent != null){
				// Cancel Alarm.
				AlarmManager alarmManager = (AlarmManager) context.getSystemService(Service.ALARM_SERVICE);
				alarmManager.cancel(pendingIntent);
			}

			hasStarted = false;
			try{
				context.unregisterReceiver(alarmReceiver);
			}catch(IllegalArgumentException e){
				//Ignore unregister errors.			
			}
		}
	}

	public void onSuccess() {
	}

	public void onFailure() {
	}

	public void onPinged() {
	}

	@Override
	public void schedule(long delayInMilliseconds) {
		long nextAlarmInMilliseconds = System.currentTimeMillis() + delayInMilliseconds;
		Log.d(tag, "Schedule next alarm at " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(nextAlarmInMilliseconds)));
		AlarmManager alarmManager = (AlarmManager) context.getSystemService(Service.ALARM_SERVICE);

        if(Build.VERSION.SDK_INT >= 23) {
			alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAlarmInMilliseconds, pendingIntent);
//			alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(nextAlarmInMilliseconds, pendingIntent), pendingIntent);
		} else if (Build.VERSION.SDK_INT >= 19) {
			alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextAlarmInMilliseconds, pendingIntent);
		} else {
			alarmManager.set(AlarmManager.RTC_WAKEUP, nextAlarmInMilliseconds, pendingIntent);
		}
	}

	/*
	 * This class sends PingReq packet to MQTT broker
	 */
	public class AlarmReceiver extends BroadcastReceiver {
		private WakeLock wakelock;
		private final String wakeLockTag = PING_WAKELOCK
				+ MQTTPingSender.this.comms.getClient().getClientId();

		@Override
        @SuppressLint("Wakelock")
		public void onReceive(Context context, Intent intent) {
			// According to the docs, "Alarm Manager holds a CPU wake lock as
			// long as the alarm receiver's onReceive() method is executing.
			// This guarantees that the phone will not sleep until you have
			// finished handling the broadcast.", but this class still get
			// a wake lock to wait for ping finished.

			Log.d(tag, "PingAlarmReceiver: onReceiver " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(System.currentTimeMillis())));

			PowerManager pm = (PowerManager) context
					.getSystemService(Service.POWER_SERVICE);
			wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, wakeLockTag);
			wakelock.acquire(60*1000L /*60秒*/);

			try {
				// Assign new callback to token to execute code after PingResq
				// arrives. Get another wakelock even receiver already has one,
				// release it until ping response returns.
				IMqttToken token = comms.checkForActivity(new IMqttActionListener() {

					@Override
					public void onSuccess(IMqttToken asyncActionToken) {
						MQTTPingSender.this.onSuccess();
						MQTTPingSender.this.onPinged();
						if (wakelock.isHeld()) {
							wakelock.release();
						}
					}

					@Override
					public void onFailure(IMqttToken asyncActionToken,
										  Throwable exception) {
						MQTTPingSender.this.onFailure();
						MQTTPingSender.this.onPinged();
						if (wakelock.isHeld()) {
							wakelock.release();
						}
					}
				});


				if (token == null && wakelock.isHeld()) {
					wakelock.release();
				}
			} finally {
				if (wakelock.isHeld()) {
					wakelock.release();
				}
			}
		}
	}

}
