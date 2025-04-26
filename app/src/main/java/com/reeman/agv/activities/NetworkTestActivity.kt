package com.reeman.agv.activities

import android.net.wifi.WifiManager
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.reeman.agv.R
import com.reeman.agv.base.BaseActivity
import com.reeman.agv.utils.DebounceClickListener
import com.reeman.agv.utils.ToastUtils
import com.reeman.agv.widgets.EasyDialog
import com.reeman.commons.state.RobotInfo
import com.reeman.commons.utils.TimeUtil
import com.reeman.dispatch.DispatchManager
import com.reeman.dispatch.constants.ResponseCode
import com.reeman.dispatch.exception.RequestFailureException
import com.reeman.dispatch.model.response.MqttTestInfo
import com.reeman.dispatch.mqtt.MqttClient
import com.reeman.dispatch.request.Urls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class NetworkTestActivity : BaseActivity(), DebounceClickListener {

    private lateinit var tvCurrentAndroidNetwork: TextView
    private lateinit var tvMacAddress: TextView
    private lateinit var tvNetworkSignalStrength: TextView
    private lateinit var tvSendCount: TextView
    private lateinit var tvReceiveCount: TextView
    private lateinit var tvLogs: TextView

    private var mqttClient: MqttClient? = null
    private var mqttTestInfo: MqttTestInfo? = null
    private var job: Job? = null

    private var isTesting = false

    override fun getLayoutRes() = R.layout.activity_network_test

    override fun initCustomView() {
        tvCurrentAndroidNetwork = findViewById(R.id.tv_current_android_network)
        tvMacAddress = findViewById(R.id.tv_mac_address)
        tvNetworkSignalStrength = findViewById(R.id.tv_network_signal_strength)
        tvSendCount = findViewById(R.id.tv_send_count)
        tvReceiveCount = findViewById(R.id.tv_receive_count)
        tvLogs = findViewById(R.id.tv_logs)
        tvLogs.movementMethod = ScrollingMovementMethod.getInstance()
        tvSendCount.text = getString(R.string.text_send_count, 0)
        tvReceiveCount.text = getString(R.string.text_receive_count, 0)
        findViewById<TextView>(R.id.tv_back).setDebounceClickListener(::onDebounceClick)
        findViewById<Button>(R.id.btn_start_test).setDebounceClickListener(::onDebounceClick)
        findViewById<Button>(R.id.btn_stop_test).setDebounceClickListener(::onDebounceClick)
        findViewById<TextView>(R.id.tv_clean_counts).setDebounceClickListener(::onDebounceClick)
        findViewById<TextView>(R.id.tv_clean_logs).setDebounceClickListener(::onDebounceClick)
    }

    override fun onResume() {
        super.onResume()
        getMqttTestInfo()
        job = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                val wifiInfo = wifiManager.connectionInfo
                if (wifiInfo == null || wifiInfo.ssid == WifiManager.UNKNOWN_SSID) {
                    tvCurrentAndroidNetwork.text = getString(R.string.text_get_wifi_info_failure)
                    tvMacAddress.text = ""
                    tvNetworkSignalStrength.text = ""
                } else {
                    tvCurrentAndroidNetwork.text = wifiInfo.ssid.replace("\"", "")
                    tvMacAddress.text = wifiInfo.macAddress
                    tvNetworkSignalStrength.text = "RSSI: ${wifiInfo.rssi}"
                }
                delay(1000)
            }
        }
    }

    private fun getMqttTestInfo() {
        EasyDialog.getLoadingInstance(this).loading(getString(R.string.text_is_getting_test_info))
        CoroutineScope(Dispatchers.IO).launch {
            DispatchManager.request(
                networkCall = {
                    DispatchManager.getApiService().getMqttTestInfo(url = Urls.getMqttTestInfo(RobotInfo.dispatchSetting.serverAddress, RobotInfo.ROSHostname))
                },
                onSuccess = {
                    withContext(Dispatchers.Main) {
                        startConnectMqtt(it!!.data!!)
                    }
                },
                onFailure = {
                    val errorTip = when (it) {
                        is IOException -> getString(R.string.text_get_mqtt_test_info_failure_cause_network)
                        is RequestFailureException -> {
                            if (it.code == ResponseCode.ROBOT_NOT_BOUND_TO_ROOM_ERROR) {
                                getString(R.string.text_get_mqtt_test_info_failure_cause)
                            } else {
                                getString(R.string.text_get_mqtt_test_info_failure_cause_undefined_exception, it.code, it.message)
                            }
                        }

                        else -> getString(R.string.text_get_mqtt_test_info_failure_cause_unknown_exception, it.message)
                    }
                    withContext(Dispatchers.Main) {
                        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
                        EasyDialog.getInstance(this@NetworkTestActivity)
                            .warn(errorTip)
                            { dialog, id ->
                                dialog.dismiss()
                                finish()
                            }
                    }
                }
            )
        }
    }

    private fun startConnectMqtt(mqttTestInfo: MqttTestInfo) {
        this.mqttTestInfo = mqttTestInfo
        if (EasyDialog.isShow() && EasyDialog.getInstance().isLoading) EasyDialog.getInstance().updateLoadingMessage(getString(R.string.text_start_connect_to_mqtt))
        mqttClient = MqttClient(
            host = mqttTestInfo.host,
            port = mqttTestInfo.port,
            clientId = mqttTestInfo.clientId,
            username = mqttTestInfo.username,
            password = mqttTestInfo.password,
            topicSub = mqttTestInfo.topicSub,
            maxRetries = 0
        )
        mqttClient?.connect(
            onConnectResult = { success, throwable ->
                if (success) {
                    startSubMqtt()
                } else {
                    if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
                    EasyDialog.getInstance(this)
                        .warn(getString(R.string.text_connect_to_mqtt_failure_please_check_network))
                        { dialog, id ->
                            dialog.dismiss()
                            finish()
                        }
                }
            },
            onDisconnected = { retry, reconnectCount, throwable ->
                if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
                EasyDialog.getInstance(this)
                    .warn(getString(R.string.text_mqtt_disconnect_please_check_network))
                    { dialog, id ->
                        dialog.dismiss()
                        finish()
                    }
            },
            onReconnectSuccess = {

            }
        )
    }

    private fun startSubMqtt() {
        mqttClient?.subscribe(
            onSubscribeResult = { success, throwable ->
                if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
                if (success) {
                    EasyDialog.getInstance(this)
                        .warn(getString(R.string.text_mqtt_subscribe_success_and_can_start_test))
                        { dialog, id ->
                            dialog.dismiss()
                        }
                } else {
                    EasyDialog.getInstance(this)
                        .warn(getString(R.string.text_subscribe_to_mqtt_failure))
                        { dialog, id ->
                            dialog.dismiss()
                            finish()
                        }
                }
            },
            onTopicReceived = { topic, payload ->
                runOnUiThread {
                    tvReceiveCount.apply {
                        val count = if (tag == null) {
                            1
                        } else {
                            (tag.toString().toIntOrNull() ?: 0) + 1
                        }
                        text = getString(R.string.text_receive_count, count)
                        tag = count
                    }
                    updateLogData("ACK: $payload")
                }
                CoroutineScope (Dispatchers.Main).launch {
                    delay(500)
                    sendToServer()
                }
            }
        )
    }

    private fun sendToServer() {
        if (!::tvSendCount.isInitialized || !isTesting) return
        val sendTag = tvSendCount.tag
        val count = if (sendTag == null) {
            1
        } else {
            (sendTag.toString().toIntOrNull() ?: 0) + 1
        }
        val payload = "PING COUNT: $count"
        updateLogData(payload)
        mqttTestInfo?.let {
            mqttClient?.publish(
                topic = it.topicPub,
                payload = payload,
                onPublishResult = { success, throwable ->
                    runOnUiThread {
                        if (success) {
                            tvSendCount.apply {
                                text = getString(R.string.text_send_count, count)
                                tag = count
                            }
                            updateLogData("PING SUCCESS")
                        } else {
                            updateLogData("PING FAILURE: ${throwable?.message}")
                        }
                    }
                }
            )
        }
    }


    private fun onDebounceClick(view: View) {
        when (view.id) {
            R.id.tv_back -> {
                isTesting = false
                job?.cancel()
                mqttClient?.disconnect()
                finish()
            }

            R.id.btn_start_test -> {
                if (isTesting) {
                    ToastUtils.showShortToast(getString(R.string.text_is_testing))
                    return
                }
                isTesting = true
                sendToServer()
            }

            R.id.btn_stop_test -> {
                isTesting = false
                ToastUtils.showShortToast(getString(R.string.text_already_stop_test))
            }

            R.id.tv_clean_counts -> {
                tvSendCount.apply {
                    text = getString(R.string.text_send_count, 0)
                    tag = 0
                }
                tvReceiveCount.apply {
                    text = getString(R.string.text_receive_count, 0)
                    tag = 0
                }
            }

            R.id.tv_clean_logs -> {
                tvLogs.apply {
                    text = ""
                    scrollTo(0, 0)
                }
            }
        }
    }

    private fun updateLogData(newData: String) {
        val data: String = tvLogs.text.toString()

        tvLogs.text = if (tvLogs.lineCount > 200) {
            String.format("%s %s", TimeUtil.formatMilliseconds(), newData)
        } else {
            String.format("%s\n%s %s", data, TimeUtil.formatMilliseconds(), newData)
        }
        val offset: Int = tvLogs.lineCount * tvLogs.lineHeight
        if (offset > tvLogs.height) {
            tvLogs.scrollTo(0, offset - tvLogs.height + 20)
        }
    }
}