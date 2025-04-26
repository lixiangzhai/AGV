package com.reeman.dispatch.mqtt

import android.os.Build
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.exceptions.Mqtt3DisconnectException
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscription
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5DisconnectException
import com.reeman.dispatch.BuildConfig
import com.reeman.dispatch.exception.InitializeException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit

class MqttClient(
    private val host: String,
    private val port: Int,
    private val clientId: String,
    private val username: String,
    private val password: String,
    private val topicSub: String,
    private val retryDelaySeconds: Long = 2,
    private val maxRetries: Int = 30,
) {
    private val TAG = BuildConfig.DISPATCH_DIR

    private var mqttClient: Mqtt3AsyncClient? = null

    private var isReconnecting = false
    private var reconnectCount = 0

    fun isConnected() = mqttClient?.state?.isConnected == true

    fun connect(
        onConnectResult: (success: Boolean, throwable: Throwable?) -> Unit,
        onDisconnected: (retry: Boolean, reconnectCount: Int, throwable: Throwable) -> Unit,
        onReconnectSuccess: () -> Unit,
    ) {
        unsubscribe { _, _ -> }
        mqttClient = MqttClient.builder()
            .useMqttVersion3()
            .identifier(clientId)
            .serverHost(host)
            .serverPort(port)
            .simpleAuth()
            .username(username)
            .password(password.encodeToByteArray())
            .applySimpleAuth()
            .addConnectedListener {
                if (reconnectCount == 0) {
                    Timber.tag(TAG).w("连接成功")
                    return@addConnectedListener
                }
                Timber.tag(TAG).w("重连成功")
                reconnectCount = 0
                CoroutineScope(Dispatchers.Main).launch {
                    onReconnectSuccess()
                }

            }
            .addDisconnectedListener {
                Timber.tag(TAG).w(it.cause, "mqtt连接断开")
                if (it.cause is Mqtt3DisconnectException || it.cause is Mqtt5DisconnectException) return@addDisconnectedListener
                Timber.tag(TAG).w("$clientId 断开连接,重连次数: $reconnectCount, 最大重连次数: $maxRetries")
                val retry = reconnectCount <= maxRetries
                if (retry) {
                    Timber.tag(TAG).w("$retryDelaySeconds s后开始重连")
                    it.reconnector.reconnect(true)
                        .delay(retryDelaySeconds, TimeUnit.SECONDS)
                }
                isReconnecting = retry
                CoroutineScope(Dispatchers.Main).launch {
                    onDisconnected(retry, reconnectCount, it.cause)
                }
            }
            .buildAsync()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mqttClient!!
                .connectWith()
                .keepAlive(5)
                .cleanSession(false)
                .send()
                .whenComplete { _, throwable: Throwable? ->
                    Timber.tag(TAG).d("$clientId 连接结果: ${isConnected()}")
                    CoroutineScope(Dispatchers.Main).launch {
                        onConnectResult(isConnected(), throwable)
                    }
                }
        }
    }

    fun publish(
        topic: String, payload: String,
        onPublishResult: (success: Boolean, throwable: Throwable?) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (mqttClient == null) {
                onPublishResult(false, NullPointerException("MqttClient is null!"))
                return
            }
            mqttClient!!.publishWith()
                .topic(topic)
                .qos(MqttQos.AT_LEAST_ONCE)
                .payload(payload.toByteArray())
                .send()
                .whenComplete { _, throwable ->
                    if (throwable != null) {
                        onPublishResult(false, throwable)
                    } else {
                        onPublishResult(true, null)
                    }
                }
        }
    }

    fun subscribe(
        onSubscribeResult: (success: Boolean, throwable: Throwable?) -> Unit,
        onTopicReceived: (topic: String, payload: String) -> Unit,
    ) {
        if (mqttClient == null) {
            onSubscribeResult(false, InitializeException("mqtt client not initialized"))
            return
        }

        val subscription = Mqtt3Subscription.builder()
            .topicFilter(topicSub)
            .qos(MqttQos.AT_LEAST_ONCE)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mqttClient!!.subscribeWith()
                .addSubscription(subscription)
                .callback {
                    CoroutineScope(Dispatchers.Main).launch {
                        onTopicReceived(it.topic.toString(), String(it.payloadAsBytes))
                    }
                }
                .send()
                .whenComplete { _, throwable: Throwable? ->
                    Timber.tag(TAG).d("$clientId 订阅结果: ${throwable == null}")
                    CoroutineScope(Dispatchers.Main).launch {
                        onSubscribeResult(throwable == null, throwable)
                    }
                }
        }
    }

    private fun unsubscribe(
        onUnsubscribeResult: ((success: Boolean, throwable: Throwable?) -> Unit)? = null
    ) {
        if (mqttClient == null) {
            onUnsubscribeResult?.invoke(true,null)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mqttClient!!.unsubscribeWith()
                .addTopicFilter(topicSub)
                .send()
                .whenComplete { _, throwable: Throwable? ->
                    Timber.tag(TAG).d("$clientId 取消订阅结果: ${throwable == null}")
                    onUnsubscribeResult?.invoke(throwable == null, throwable)
                }
        }

    }

    fun disconnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (isConnected()) {
                unsubscribe()
                mqttClient?.disconnect()
                mqttClient = null
                Timber.tag(TAG).d("$clientId 主动断开连接")
            }
        }
    }

}