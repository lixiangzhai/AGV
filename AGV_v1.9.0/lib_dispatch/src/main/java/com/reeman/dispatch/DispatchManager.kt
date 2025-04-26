package com.reeman.dispatch

import com.google.gson.Gson
import com.reeman.commons.utils.SpManager
import com.reeman.dispatch.callback.DispatchCallback
import com.reeman.dispatch.constants.Constants
import com.reeman.dispatch.constants.DispatchAction
import com.reeman.dispatch.constants.RobotState
import com.reeman.dispatch.constants.TaskProcess
import com.reeman.dispatch.constants.TaskType
import com.reeman.dispatch.exception.RequestFailureException
import com.reeman.dispatch.model.request.FinishTaskReq
import com.reeman.dispatch.model.response.MqttInfo
import com.reeman.dispatch.model.request.RobotHeartbeat
import com.reeman.dispatch.model.request.RobotReconnected
import com.reeman.dispatch.model.response.RobotOffline
import com.reeman.dispatch.model.response.Task
import com.reeman.dispatch.model.request.TaskCreateReq
import com.reeman.dispatch.model.response.ResponseBody
import com.reeman.dispatch.mqtt.MqttClient
import com.reeman.dispatch.request.ApiClient
import com.reeman.dispatch.request.Urls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response
import timber.log.Timber

object DispatchManager {

    const val TAG = BuildConfig.DISPATCH_DIR
    private val gson = Gson()

    private var mqttInfo: MqttInfo? = null
    private var mqttClientManager: MqttClient? = null
    private var room: String? = null
    var host: String? = null
        set(value) {
            field = value
            ApiClient.host = value
        }
    var roomName: String? = null
        set(value) {
            field = value
            ApiClient.roomName = value
        }
    var token: String? = null
        set(value) {
            field = value
            ApiClient.token = token
            SpManager.getInstance().edit().putString(Constants.DISPATCH_TOKEN, token).apply()
        }
    var roomPwd: String? = null
        set(value) {
            field = value
            ApiClient.roomPwd = value
        }
    var hostname: String? = null
        set(value) {
            field = value
            ApiClient.hostname = value
        }
    var currentMap: String? = null
    var useElevator: Boolean = false
    var currentPower: Int = 50
    var robotType: Int = 1
    var currentState: RobotState = RobotState.FREE
    var liftModuleLocation: Int = 1
    var emergencyStopButton: Int = 0
    var currentPosition: DoubleArray? = null
    var positionRange: Float = 1.0F
    private var callback: DispatchCallback? = null
    private var job: Job? = null
    private val triggerFlow = MutableSharedFlow<TaskProcess?>(replay = 1)

    private var taskId: Long = 0
    var task: Task? = null
    var currentPoint: String? = null

    fun isMqttConnected() = mqttClientManager?.isConnected() == true

    fun isActive() = job?.isActive == true

    fun getApiService() = ApiClient.getApiService()

    fun registerCallback(callback: DispatchCallback) {
        this.callback = callback
    }

    fun unregisterCallback() {
        this.callback = null
    }

    fun release() {
        finishTask()
        stopHeartbeat()
        mqttClientManager?.disconnect()
        callback = null
    }

    inline fun <reified T> request(
        networkCall: () -> Response<T>,
        onSuccess: (T?) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        try {
            val response = networkCall()
            if (response.isSuccessful) {
                onSuccess(response.body())
            } else {
                response.errorBody()?.string()?.let {
                    val responseBody = Gson().fromJson<ResponseBody<String?>>(it, ResponseBody::class.java)
                    onFailure(
                        RequestFailureException(
                            responseBody.code, responseBody.data ?: responseBody.message
                        )
                    )
                    return
                }
                onFailure(RequestFailureException(response.code(), "parse error body failure"))
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "请求失败")
            onFailure(e)
        }
    }


    fun init(host: String, roomName: String, roomPwd: String) {
        this.host = host
        this.roomName = roomName
        this.roomPwd = roomPwd
        ApiClient.apply {
            this.host = host
            this.roomName = roomName
            this.roomPwd = roomPwd
        }
    }

    fun robotOnline(callback: DispatchCallback) {
        registerCallback(callback)
        CoroutineScope(Dispatchers.IO).launch {
            request(
                networkCall = {
                    getApiService().robotOnline(
                        Urls.getRobotOnlineUrl(host!!),
                        RobotHeartbeat(
                            hostname = hostname!!,
                            useElevator = useElevator,
                            currentMap = currentMap!!,
                            currentPower = currentPower,
                            currentState = currentState.value,
                            robotType = robotType,
                            liftModelLocation = liftModuleLocation,
                            emergencyStopButton = emergencyStopButton,
                            position = currentPosition!!
                        )
                    )
                },
                onSuccess = {
                    val robotOnlineResp = it!!.data!!
                    room = robotOnlineResp.room
                    withContext(Dispatchers.Main) {
                        callback.onOnlineSuccess(robotOnlineResp.maps, robotOnlineResp.mqttInfo)
                    }
                },
                onFailure = {
                    withContext(Dispatchers.Main) {
                        callback.onOnlineFailure(it)
                    }
                }
            )
        }
    }

    fun connectMqtt(mqttInfo: MqttInfo) {
        this.mqttInfo = mqttInfo
        mqttClientManager = MqttClient(
            host = mqttInfo.host,
            port = mqttInfo.port,
            clientId = mqttInfo.clientId,
            username = mqttInfo.username,
            password = mqttInfo.password,
            topicSub = mqttInfo.topicSub,
        )
        mqttClientManager?.connect(
            onConnectResult = { success, throwable ->
                if (success) {
                    callback?.onMqttConnectSuccess()
                    subscribeMqtt()
                } else {
                    callback?.onMqttConnectFailure(throwable)
                }
            },
            onDisconnected = { retry, reconnectCount, throwable ->
                callback?.onMqttDisconnect(retry, reconnectCount, throwable)
            },
            onReconnectSuccess = {
                callback?.onMqttReconnected()
            }
        )
    }

    private fun subscribeMqtt() {
        mqttClientManager?.subscribe(
            onSubscribeResult = { success, throwable ->
                if (success) {
                    callback?.onMqttSubscribeSuccess()
                    startHeartbeat()
                } else {
                    callback?.onMqttSubscribeFailure(throwable)
                }
            },
            onTopicReceived = { topic, payload ->
                Timber.tag(TAG).w("topic: $topic ,payload: $payload")
                when (topic) {
                    mqttInfo?.topicSubTask -> {
                        task = gson.fromJson(payload, Task::class.java)
                        callback?.onTaskReceived(task)
                    }

                    mqttInfo?.topicSubHeartbeat -> onHeartbeat()
                    mqttInfo?.topicSubOffline -> callback?.onRobotOffline(gson.fromJson(payload, RobotOffline::class.java).offlineRobots)
                    mqttInfo?.topicSubMapUpdate -> callback?.onMapUpdate()
                    mqttInfo?.topicSubConfigUpdate -> callback?.onConfigUpdate()
                }
            }
        )
    }

    private fun onHeartbeat() {

    }

    private fun startHeartbeat() {
        job?.cancel()
        val periodicFlow = flow {
            while (true) {
                emit(null)
                delay(500)
            }
        }
        var lastTriggerTime = System.currentTimeMillis()
        job = CoroutineScope(Dispatchers.IO).launch {
            merge(periodicFlow, triggerFlow)
                .onEach {
                    if (it != null) {
                        sendHeartbeat(it)
                        lastTriggerTime = System.currentTimeMillis()
                    } else {
                        val timeInterval = if (task == null) {
                            5000
                        } else if (task?.action in setOf(DispatchAction.NAVIGATING_TO_NODE, DispatchAction.NAVIGATING_TO_FINAL)) {
                            3000
                        } else {
                            2000
                        }
                        if (System.currentTimeMillis() - lastTriggerTime > timeInterval) {
                            sendHeartbeat()
                            lastTriggerTime = System.currentTimeMillis()
                        }
                    }
                }
                .launchIn(this)
        }
    }


    private fun sendHeartbeat(taskProcess: TaskProcess? = null) {
        try {
            if (mqttClientManager?.isConnected() == true) {
                val robotHeartbeat = RobotHeartbeat(
                    hostname = hostname!!,
                    room = room,
                    useElevator = useElevator,
                    currentMap = currentMap!!,
                    currentPower = currentPower,
                    currentState = currentState.value,
                    robotType = robotType,
                    liftModelLocation = liftModuleLocation,
                    emergencyStopButton = emergencyStopButton,
                    position = currentPosition!!,
                    currentPoint = currentPoint,
                    taskProcess = taskProcess
                )
                mqttClientManager?.publish(
                    topic = mqttInfo!!.topicPubHeartbeat,
                    payload = gson.toJson(robotHeartbeat),
                    onPublishResult = { _, throwable ->
                        throwable?.let { Timber.tag(TAG).w(it) }
                    }
                )
                Timber.tag(TAG).d("publish heartBeat: $robotHeartbeat")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e)
        }
    }

    fun reconnectedDuringTaskExecuting(taskId: Long, orderId: Long) {
        try {
            mqttClientManager?.publish(
                topic = mqttInfo!!.topicPubReconnected,
                payload = gson.toJson(RobotReconnected(room = room!!, hostname = hostname!!, taskId = taskId, orderId = orderId)),
                onPublishResult = { _, throwable ->
                    throwable?.let { Timber.tag(TAG).w(it) }
                }
            )
            Timber.tag(TAG).d("publish reconnected ack,task id: $taskId, order id: $orderId")
        } catch (e: Exception) {
            Timber.tag(TAG).w(e)
        }
    }

    fun triggerHeartbeat(taskProcess: TaskProcess?) {
        Timber.tag(TAG).w("taskProcess: $taskProcess")
        CoroutineScope(Dispatchers.IO).launch {
            triggerFlow.emit(taskProcess)
        }
    }

    private fun stopHeartbeat() {
        job?.cancel()
    }

    fun createTask(target: String? = null, taskType: TaskType) {
        CoroutineScope(Dispatchers.IO).launch {
            request(
                networkCall = {
                    getApiService().createTask(
                        Urls.getCreateTaskUrl(host!!),
                        TaskCreateReq(
                            hostname = hostname!!,
                            targetMap = currentMap,
                            targetPoint = target,
                            taskType = taskType
                        )
                    )
                },
                onSuccess = {
                    withContext(Dispatchers.Main) {
                        callback?.onCreateTaskSuccess()
                    }
                },
                onFailure = {
                    withContext(Dispatchers.Main) {
                        callback?.onCreateTaskFailure(it)
                    }
                }
            )
        }
    }

    fun finishTask(reason: Int = 1) {
        if (taskId == 0L) return
        CoroutineScope(Dispatchers.IO).launch {
            request(
                networkCall = {
                    getApiService().finishTask(
                        Urls.getFinishTaskUrl(host!!),
                        FinishTaskReq(
                            hostname = hostname!!,
                            taskId = taskId,
                            reason = reason
                        )
                    )
                },
                onSuccess = {
                    taskId = 0
                    task = null
                    withContext(Dispatchers.Main) {
                        callback?.onFinishTaskSuccess()
                    }
                },
                onFailure = {
                    taskId = 0
                    task = null
                    withContext(Dispatchers.Main) {
                        callback?.onFinishTaskFailure(it)
                    }
                }
            )
        }
    }


}