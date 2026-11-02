package com.android.pushme.mqtt;

import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MQTTClientWithPingAndTimer extends MqttClient {
    public MQTTClientWithPingAndTimer(String serverURI, String clientId, MemoryPersistence persistence, MQTTPingSender pingSender) throws MqttException {
        super(serverURI, clientId, persistence);
        this.aClient = new MqttAsyncClient(serverURI, clientId, persistence, pingSender, null, new MQTTHighResolutionTimer());
    }
}