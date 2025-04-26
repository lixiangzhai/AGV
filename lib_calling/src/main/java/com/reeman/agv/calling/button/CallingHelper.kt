package com.reeman.agv.calling.button

import android.os.Build
import android.util.Log
import com.reeman.agv.calling.BuildConfig
import com.reeman.agv.calling.CallingInfo
import com.reeman.agv.calling.TakeElevatorStep
import com.reeman.agv.calling.callback.ElevatorCallback
import com.reeman.agv.calling.event.CallingButtonEvent
import com.reeman.agv.calling.event.QRCodeButtonEvent
import com.reeman.agv.calling.event.UnboundButtonEvent
import com.reeman.agv.calling.exception.NoFreeElevatorException
import com.reeman.agv.calling.http.HttpClient
import com.reeman.agv.calling.model.ElevatorState
import com.reeman.agv.calling.model.MqttInfo
import com.reeman.agv.calling.model.QRCodeModeTaskModel
import com.reeman.agv.calling.model.TaskCancelBody
import com.reeman.agv.calling.model.TaskCreateBody
import com.reeman.agv.calling.utils.CallingStateManager
import com.reeman.commons.event.CallingModelDisconnectedEvent
import com.reeman.commons.event.CallingModelReconnectSuccessEvent
import com.reeman.commons.event.CloseDoorFailedEvent
import com.reeman.commons.event.DoorClosedEvent
import com.reeman.commons.event.DoorNumSettingResultEvent
import com.reeman.commons.event.DoorOpenedEvent
import com.reeman.commons.event.OpenDoorFailedEvent
import com.reeman.commons.event.SetDoorNumFailedEvent
import com.reeman.commons.eventbus.EventBus
import com.reeman.commons.provider.SerialPortProvider
import com.reeman.commons.state.RobotInfo
import com.reeman.commons.utils.ByteUtil
import com.reeman.commons.utils.UsbEventCallback
import com.reeman.commons.utils.UsbFileObserver
import com.reeman.serialport.controller.SerialPortParser
import com.reeman.serialport.util.Parser
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.experimental.xor

object CallingHelper {
    private var parser: SerialPortParser? = null
    private var start = false
    private val mPattern = Pattern.compile("AA55|AA56|AA89")
    const val INIT = 0
    const val CLOSED = 1
    const val OPENED = 2
    const val WAITING_OPEN_RESULT = 3
    const val WAITING_CLOSE_RESULT = 4
    var doorState = INIT
    private var opposite = false
    private var isDoorControlTest = false
    private var currentOpeningDoor = ""
    private var currentClosingDoor = ""
    private var openDoorDisposable: Disposable? = null
    private var closeDoorDisposable: Disposable? = null
    private var usbFileObserver: UsbFileObserver? = null
    private var checkDeviceDisposable: Disposable? = null

    private val ELEVATOR_TAG = BuildConfig.ELEVATOR_DIR
    private val elevatorControlCommandHeader = byteArrayOf(0x00, 0x02, 0x05, 0xAA.toByte(), 0x88.toByte())
    private var gotoStartFloorJob: Job? = null
    private var gotoTargetFloorJob: Job? = null
    private var taskCompleteJob: Job? = null
    private var abortTaskJob: Job? = null

    private var sn: ByteArray? = null
    private var hostname: String = ""
    private var startFloor: Int = 0
    private var targetFloor: Int = 0
    private var takeElevatorStep: TakeElevatorStep = TakeElevatorStep.INIT
    private var callback: ElevatorCallback? = null
    private var isDisconnectDuringLeaveElevator = false
    private var isOnlyUseLoRa = false

    fun getTakeElevatorStep() = takeElevatorStep


    fun isStart() = start

    fun init(
        hostname: String,
        startFloor: Int,
        targetFloor: Int,
        callback: ElevatorCallback
    ) {
        this.hostname = hostname
        this.startFloor = startFloor
        this.targetFloor = targetFloor
        this.callback = callback
        this.takeElevatorStep = TakeElevatorStep.INIT
    }


    @Throws(Exception::class)
    fun start() {
        val path = SerialPortProvider.ofCallModule(Build.PRODUCT)
        val file = File(path)
        val files = file.listFiles()
        if (!file.exists() || files.isNullOrEmpty()) {
            throw FileNotFoundException()
        }
        val target =
            files.firstOrNull { it.name.startsWith("ttyUSB") } ?: throw FileNotFoundException()

        parser = SerialPortParser(
            File("/dev/${target.name}"),
            115200,
            object : SerialPortParser.OnDataResultListener {
                private val sb = StringBuilder()

                override fun onDataResult(bytes: ByteArray, len: Int) {

                    sb.append(ByteUtil.byteArr2HexString(bytes, len))
                    Log.w("receive", sb.toString())

                    while (sb.isNotEmpty()) {
                        if (sb.length < 4) return
                        val matcher = mPattern.matcher(sb)
                        if (matcher.find()) {
                            try {
                                val start = matcher.start()
                                val startIndex = start + 4
                                val header = sb.substring(start, startIndex)

                                if (startIndex + 2 >= sb.length) break

                                val dataSize = sb.substring(startIndex, startIndex + 2)
                                val intSize = dataSize.toInt(16)

                                val dataLastIndex = startIndex + intSize * 2 + 2

                                if (dataLastIndex + 2 > sb.length) break

                                val dataHexSum = sb.substring(startIndex, dataLastIndex)
                                val checkSum = sb.substring(dataLastIndex, dataLastIndex + 2)

                                if (checkSum == Parser.checkXor(dataHexSum)) {
                                    val data = sb.substring(startIndex + 2, dataLastIndex)
                                    Observable.just(Pair(header, data))
                                        .subscribeOn(Schedulers.io())
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribe({
                                            when (it.first) {
                                                "AA55" -> detailCallingEvent(it.second.substring(2))
                                                "AA56" -> detailDoorControlEvent(it.second)
                                                else -> detailElevatorEvent(it.second.take(2), it.second.substring(4))
                                            }
                                        }
                                        ) { throwable ->
                                            Timber.d(throwable, "detail data failed")
                                        }

                                    sb.delete(0, dataLastIndex + 2)
                                } else if (matcher.find()) {
                                    Timber.w("数据包校验不通过1 $sb")
                                    sb.delete(0, matcher.start())
                                } else {
                                    Timber.w("数据包校验不通过2 $sb")
                                    sb.clear()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Timber.w(e, "数据包校验不通过3 $sb")
                                sb.clear()
                            }
                        } else {
                            sb.clear()
                        }
                    }
                }
            })
        parser?.start()
        start = true
        usbFileObserver = UsbFileObserver("/dev/${target.name}", object : UsbEventCallback {
            override fun onUsbDeviceDetached(path: String?) {
                Timber.w("呼叫串口断开")
                EventBus.sendEvent(CallingModelDisconnectedEvent(0))
                stop()
                cancelOpenDoorRetry()
                cancelCloseDoorRetry()
                cancelGotoTargetFloorRetryJob()
                cancelTaskCompleteRetryJob()
                cancelAbortTaskRetryJob()
                checkDevice()
            }
        })
        usbFileObserver?.startWatching()
    }

    private fun checkDevice() {
        checkDeviceDisposable = Observable.interval(2, 1, TimeUnit.SECONDS)
            .take(10)
            .map { count ->
                Pair(
                    count,
                    File(SerialPortProvider.ofCallModule(Build.PRODUCT)).exists()
                )
            }
            .observeOn(Schedulers.io())
            .subscribe({
                Log.w("---", "呼叫模块重连检测 : $it")
                if (checkDeviceDisposable?.isDisposed == true) return@subscribe
                if (it.second) {
                    checkDeviceDisposable?.dispose()
                    Timber.w("等待门控串口重连成功")
                    start()
                    EventBus.sendEvent(CallingModelReconnectSuccessEvent())
                    if (takeElevatorStep == TakeElevatorStep.INIT) {
                        if (doorState == WAITING_OPEN_RESULT) {
                            if (isDoorControlTest) {
                                openDoor(currentOpeningDoor)
                            } else {
                                openDoor(currentOpeningDoor, opposite)
                            }
                        } else if (doorState == WAITING_CLOSE_RESULT) {
                            if (isDoorControlTest) {
                                closeDoor(currentClosingDoor)
                            } else {
                                closeDoor(currentClosingDoor, opposite)
                            }
                        }
                    } else {
                        if (takeElevatorStep == TakeElevatorStep.CALL_ELEVATOR_INSIDE) {
                            gotoTargetFloor()
                        } else if (takeElevatorStep == TakeElevatorStep.COMPLETE) {
                            taskComplete()
                        } else if (takeElevatorStep == TakeElevatorStep.ABORT) {
                            abortTaskWithRetryJob()
                        }
                    }
                } else if (it.first == 9L) {
                    EventBus.sendEvent(CallingModelDisconnectedEvent(1))
                    if (takeElevatorStep in setOf(TakeElevatorStep.INIT, TakeElevatorStep.LEAVING_ELEVATOR, TakeElevatorStep.COMPLETE, TakeElevatorStep.ABORT)) {
                        isDisconnectDuringLeaveElevator = true
                    } else {
                        callback?.onLoRaDisconnectDuringTaskExecuting()
                    }
                }
            },
                {
                    if (checkDeviceDisposable?.isDisposed == true) return@subscribe
                    Timber.w(it, "呼叫模块重连失败")
                    EventBus.sendEvent(CallingModelDisconnectedEvent(1))
                    if (takeElevatorStep !in setOf(TakeElevatorStep.INIT, TakeElevatorStep.LEAVING_ELEVATOR, TakeElevatorStep.COMPLETE, TakeElevatorStep.ABORT)) {
                        callback?.onLoRaDisconnectDuringTaskExecuting()
                    }
                }
            )
    }

    private fun detailElevatorEvent(cmd: String, data: String) {
        Timber.tag(ELEVATOR_TAG).w("cmd: $cmd, data: $data, step: $takeElevatorStep")
        when (cmd) {
            "01" -> {
                if (data.length > 10) {
                    val floor = data.take(2).toIntOrNull(16)
                    val gatewayId = ByteUtil.hexStringToByteArray(data.substring(2, 10))
                    val hostname = Parser.hexStringToString(data.substring(10))
                    Timber.tag(ELEVATOR_TAG).w("网关id: ${gatewayId.contentToString()} ,机器编号: $hostname ,已开始前往$floor 层")
                    if (gatewayId.contentEquals(sn) && hostname == this.hostname) {
                        if (isOnlyUseLoRa && takeElevatorStep == TakeElevatorStep.CALL_ELEVATOR_OUTSIDE) {
                            cancelGotoStartFloorRetryJob()
                            callback?.onInitiateTaskSuccess()
                            takeElevatorStep = TakeElevatorStep.WAITING_OUTSIDE
                        } else if (takeElevatorStep == TakeElevatorStep.CALL_ELEVATOR_INSIDE) {
                            cancelGotoTargetFloorRetryJob()
                            callback?.onGotoTargetFloor()
                            takeElevatorStep = TakeElevatorStep.WAITING_INSIDE
                        }
                    }
                }
            }

            "03" -> {
                if (data.length == 10) {
                    val gatewayId = ByteUtil.hexStringToByteArray(data.take(8))
                    if (gatewayId.contentEquals(sn) && takeElevatorStep in setOf(TakeElevatorStep.COMPLETE, TakeElevatorStep.ABORT)) {
                        val status = data.substring(8).toIntOrNull()
                        if (status == 0 && takeElevatorStep == TakeElevatorStep.COMPLETE) {
                            Timber.tag(ELEVATOR_TAG).w("已完成任务")
                        } else if (status == 2 && takeElevatorStep == TakeElevatorStep.ABORT) {
                            Timber.tag(ELEVATOR_TAG).w("已结束任务")
                        }
                        cancelAbortTaskRetryJob()
                        cancelTaskCompleteRetryJob()
                        callback?.onComplete()
                    }
                }

            }

            "04" -> {
                if (data.length == 4) {
                    val floor = data.take(2).toIntOrNull(16)
                    val status = data.substring(2).toInt()
                    Timber.tag(ELEVATOR_TAG).w("$floor 层,门状态: $status")
                }
            }

            "08" -> {
                if (data.length == 10) {
                    val gatewayId = ByteUtil.hexStringToByteArray(data.take(8))
                    if (gatewayId.contentEquals(sn)) {
                        val floor = data.substring(8).toIntOrNull()
                        Timber.tag(ELEVATOR_TAG).w("到达楼层: $floor")
                        if (takeElevatorStep == TakeElevatorStep.WAITING_OUTSIDE && floor == startFloor) {
                            callback?.onArrivedStartFloor()
                            takeElevatorStep = TakeElevatorStep.ENTERING_ELEVATOR
                        } else if (takeElevatorStep == TakeElevatorStep.WAITING_INSIDE && floor == targetFloor) {
                            callback?.onArrivedTargetFloor(targetFloor.toString())
                            takeElevatorStep = TakeElevatorStep.LEAVING_ELEVATOR
                        }
                    }
                }
            }

            "80" -> {
                if (data.length == 10) {
                    val gatewayId = ByteUtil.hexStringToByteArray(data.take(8))
                    if (gatewayId.contentEquals(sn)) {
                        val errorCode = data.substring(8,10).toIntOrNull(16) ?: -1
                        Timber.tag(ELEVATOR_TAG).w("乘梯异常,错误码: $errorCode")
                        if (errorCode == 1) {
                            if (isOnlyUseLoRa && takeElevatorStep == TakeElevatorStep.CALL_ELEVATOR_OUTSIDE) {
                                Timber.tag(ELEVATOR_TAG).w("发起乘梯时收到电梯中存在任务,排队等待")
                                callback?.onQueuing()
                            }
                            return
                        }

                        callback?.onErrorCodeFromGateway(errorCode)
                    }
                }
            }
        }
    }

    /**
     * 门控事件
     */
    fun detailDoorControlEvent(data: String) {
        if (data.length == 12) {
            val subDoorNum: String = data.substring(4, 12)
            var doorNum = ""
            for (i in 0..3) {
                doorNum += subDoorNum.substring(i * 2, i * 2 + 2).toInt(16)
            }
            if (data.startsWith("02", 2)) {
                if (opposite) {
                    Timber.w("控制盒收到关门指令: $doorNum")
                    if (doorNum == currentClosingDoor) {
                        cancelCloseDoorRetry()
                        EventBus.sendEvent(DoorClosedEvent(doorNum.toInt()))
                    }
                } else {
                    Timber.w("控制盒收到开门指令: $doorNum")
                    if (doorNum == currentOpeningDoor) {
                        cancelOpenDoorRetry()
                        EventBus.sendEvent(DoorOpenedEvent(doorNum.toInt()))
                    }
                }
            } else if (data.startsWith("03", 2)) {
                Timber.w("控制盒开门成功: $doorNum")

            } else if (data.startsWith("05", 2)) {
                if (opposite) {
                    Timber.w("控制盒收到开门指令: $doorNum")
                    if (doorNum == currentOpeningDoor) {
                        cancelOpenDoorRetry()
                        EventBus.sendEvent(DoorOpenedEvent(doorNum.toInt()))
                    }
                } else {
                    Timber.w("控制盒收到关门指令: $doorNum")
                    if (doorNum == currentClosingDoor) {
                        cancelCloseDoorRetry()
                        EventBus.sendEvent(DoorClosedEvent(doorNum.toInt()))
                    }
                }
            } else if (data.startsWith("06", 2)) {
                Timber.w("控制盒关门成功")

            } else if (data.startsWith("08", 2)) {
                Timber.w("设置标签编号成功: $doorNum")
                EventBus.sendEvent(DoorNumSettingResultEvent(doorNum.toInt()))
            }
        }
    }

    /**
     * 呼叫事件
     */
    fun detailCallingEvent(key: String) {
        if (RobotInfo.isElevatorMode) {
            if (CallingInfo.callingButtonMapWithElevator.containsKey(key)) {
                val pair = CallingInfo.callingButtonMapWithElevator[key]
                Timber.w("收到呼叫,key: $key, taskPoint: $pair")
                pair?.let { mPair ->
                    CallingStateManager.setCallingButtonEvent(
                        CallingButtonEvent(
                            key,
                            mPair.first,
                            mPair.second
                        )
                    )
                }
            } else {
                Timber.d("unbound key : $key")
                CallingStateManager.setUnboundButtonEvent(
                    UnboundButtonEvent(key)
                )
            }
        } else {
            if (CallingInfo.isQRCodeTaskUseCallingButton && CallingInfo.callingButtonWithQRCodeModelTaskMap.containsKey(
                    key
                )
            ) {
                val qrCodeModelTask =
                    CallingInfo.callingButtonWithQRCodeModelTaskMap[key]
                Timber.w("收到呼叫,key: $key, taskPoint: $qrCodeModelTask")
                qrCodeModelTask?.let { mQRCodeModelTask ->
                    CallingStateManager.setQRCodeButtonEvent(
                        QRCodeButtonEvent(
                            key,
                            mQRCodeModelTask.map {
                                Pair(
                                    QRCodeModeTaskModel(
                                        it.first.first,
                                        it.first.second
                                    ),
                                    QRCodeModeTaskModel(
                                        it.second.first,
                                        it.second.second
                                    )
                                )
                            })
                    )
                }
            } else if (CallingInfo.callingButtonMap.containsKey(key)) {
                val point = CallingInfo.callingButtonMap[key]
                Timber.w("收到呼叫,key: $key, taskPoint: $point")
                point?.let { mPoint ->
                    CallingStateManager.setCallingButtonEvent(
                        CallingButtonEvent(key, "", mPoint)
                    )
                }
            } else {
                Timber.d("unbound key : $key")
                CallingStateManager.setUnboundButtonEvent(
                    UnboundButtonEvent(key)
                )
            }
        }

    }

    fun stop() {
        parser?.stop()
        start = false
        usbFileObserver?.stopWatching()
        usbFileObserver = null
        checkDeviceDisposable?.apply {
            if (!this.isDisposed) {
                this.dispose()
            }
        }
    }


    fun openDoor(number: String, opposite: Boolean) {
        if (number.isBlank()) return
        isDoorControlTest = false
        this.opposite = opposite
        this.currentOpeningDoor = number
        this.doorState = WAITING_OPEN_RESULT
        val doorNum = ByteUtil.strToDoorNum(number)
        val bytes = byteArrayOf(
            0x00, 0x05, 0x05, 0xAA.toByte(), 0x56, 0x06, 0x01, if (opposite) 0x04 else 0x01,
            doorNum[0], doorNum[1], doorNum[2], doorNum[3], 0x00
        )

        openDoorDisposable = Observable.interval(1, TimeUnit.SECONDS)
            .take(10)
            .map {
                if (it == 9L) {
                    Timber.w("发送开门指令失败")
                    EventBus.sendEvent(OpenDoorFailedEvent())
                } else {
                    Timber.w("发送开门指令")
                    parser?.sendCommand(checksum(bytes))
                }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({}, {
                Timber.w(it, "发送开门指令失败")
                EventBus.sendEvent(OpenDoorFailedEvent())
            })

    }


    fun openDoor(number: String): Boolean {
        if (number.isBlank()) return false
        isDoorControlTest = true
        this.currentOpeningDoor = number
        this.doorState = WAITING_OPEN_RESULT
        val doorNum = ByteUtil.strToDoorNum(number)
        val bytes = byteArrayOf(
            0x00, 0x05, 0x05, 0xAA.toByte(), 0x56, 0x06, 0x01, 0x01,
            doorNum[0], doorNum[1], doorNum[2], doorNum[3], 0x00
        )
        return try {
            parser!!.sendCommand(checksum(bytes))
            true
        } catch (e: Exception) {
            Timber.w(e, "发送开门指令失败")
            doorState = INIT
            EventBus.sendEvent(OpenDoorFailedEvent())
            false
        }
    }

    fun closeDoor(number: String, opposite: Boolean) {
        if (number.isBlank()) return
        isDoorControlTest = false
        this.opposite = opposite
        this.currentClosingDoor = number
        this.doorState = WAITING_CLOSE_RESULT
        val doorNum = ByteUtil.strToDoorNum(number)
        val bytes = byteArrayOf(
            0x00, 0x05, 0x05, 0xAA.toByte(), 0x56, 0x06, 0x01, if (opposite) 0x01 else 0x04,
            doorNum[0], doorNum[1], doorNum[2], doorNum[3], 0x00
        )
        closeDoorDisposable = Observable.interval(1, TimeUnit.SECONDS)
            .take(10)
            .map {
                if (it == 9L) {
                    Timber.w("发送关门指令失败")
                    EventBus.sendEvent(CloseDoorFailedEvent())
                } else {
                    Timber.w("发送关门指令")
                    parser?.sendCommand(checksum(bytes))
                }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({}, {
                Timber.w(it, "发送关门指令失败")
                EventBus.sendEvent(CloseDoorFailedEvent())
            })
    }

    fun closeDoor(number: String): Boolean {
        if (number.isBlank()) return false
        isDoorControlTest = true
        this.currentClosingDoor = number
        this.doorState = WAITING_CLOSE_RESULT
        val doorNum = ByteUtil.strToDoorNum(number)
        val bytes = byteArrayOf(
            0x00, 0x05, 0x05, 0xAA.toByte(), 0x56, 0x06, 0x01, 0x04,
            doorNum[0], doorNum[1], doorNum[2], doorNum[3], 0x00
        )
        return try {
            parser!!.sendCommand(checksum(bytes))
            true
        } catch (e: Exception) {
            Timber.w(e, "发送关门指令失败")
            doorState = INIT
            EventBus.sendEvent(CloseDoorFailedEvent())
            false
        }
    }

    fun setDoorNum(number: String) {
        if (number.isBlank()) return
        val doorNum = ByteUtil.strToDoorNum(number)
        val bytes = byteArrayOf(
            0x00, 0x05, 0x05, 0xAA.toByte(), 0x56, 0x06, 0x01, 0x07,
            doorNum[0], doorNum[1], doorNum[2], doorNum[3], 0x00
        )
        try {
            parser!!.sendCommand(checksum(bytes))
        } catch (e: Exception) {
            Timber.w(e, "发送设置门编号指令失败")
            EventBus.sendEvent(SetDoorNumFailedEvent())
        }
    }

    private fun checksum(msg: ByteArray): ByteArray {
        var mSum: Byte = 0
        for (i in 5 until msg.size - 1) {
            mSum = (mSum.toInt() xor msg[i].toInt()).toByte()
        }
        msg[msg.size - 1] = mSum
        return msg
    }

    fun cancelOpenDoorRetry() {
        openDoorDisposable?.apply {
            if (!this.isDisposed) {
                this.dispose()
            }
        }
    }

    fun cancelCloseDoorRetry() {
        closeDoorDisposable?.apply {
            if (!this.isDisposed) {
                this.dispose()
            }
        }
    }

    fun callElevatorInside() {
        gotoTargetFloor()
    }

    fun callElevatorByLoRa(sn: String) {
        isOnlyUseLoRa = true
        this.sn = ByteUtil.hexStringToByteArray(sn)
        Timber.w("sn: ${this.sn.contentToString()}")
        gotoStartFloor()
    }


    fun callElevatorByNetwork() {
        isOnlyUseLoRa = false
        CoroutineScope(Dispatchers.IO).launch {
            try {
                getElevatorState(
                    hostname = hostname,
                    onSuccess = {
//                        online(
//                            sn = it,
//                            hostname = hostname,
//                            onSuccess = { sn, mqttInfo ->
                        if (it.isEmpty()) {
                            callback?.onInitiateTaskFailure(NoFreeElevatorException())
                            return@getElevatorState
                        }
                        takeElevatorStep = TakeElevatorStep.CALL_ELEVATOR_OUTSIDE
                        val firstFreeElevator = it.firstOrNull { it.taskRobot.isNullOrBlank() }
                        if (firstFreeElevator == null) {
                            val notFinishedTask = it.firstOrNull { it.taskRobot == hostname }
                            if (notFinishedTask != null) {
                                cancelTask(
                                    sn = notFinishedTask.sn,
                                    onResult = {
                                        callElevatorByNetwork()
                                    }
                                )
                            } else {
                                callback?.onQueuing()
                            }
                        } else {
                            startTask(
                                sn = firstFreeElevator.sn,
                                taskCreateBody = TaskCreateBody(
                                    hostname = hostname,
                                    from = startFloor,
                                    to = targetFloor,
                                ),
                                onSuccess = {
                                    callback?.onInitiateTaskSuccess()
                                    takeElevatorStep = TakeElevatorStep.WAITING_OUTSIDE
                                },
                                onFailure = {
                                    callback?.onInitiateTaskFailure(it)
                                }
                            )
                        }
//                            },
//                            onFailure = {
//                                callback?.onInitiateTaskFailure(it)
//                            }
//                        )
                    },
                    onFailure = {
                        callback?.onInitiateTaskFailure(it)
                    }
                )
            } catch (e: Exception) {
                callback?.onInitiateTaskFailure(e)
            }
        }
    }


    private fun getElevatorState(
        hostname: String, onSuccess: (elevatorStates: List<ElevatorState>) -> Unit,
        onFailure: (e: Exception) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            HttpClient.request(
                networkCall = {
                    HttpClient.getRetrofitClient().getElevatorState(hostname)
                },
                onSuccess = {
//                    val firstFreeElevator = it!!.firstOrNull { it.taskRobot.isNullOrBlank() || it.taskRobot == "null" }
                    withContext(Dispatchers.Main) {
                        onSuccess(it!!)
//                        if (firstFreeElevator == null) {
//                            onFailure(NoFreeElevatorException())
//                        } else {
//                            val newSN = if (firstFreeElevator.sn.length >= 8) {
//                                firstFreeElevator.sn
//                            } else {
//                                firstFreeElevator.sn.padStart(8, '0')
//                            }
//                            this@CallingHelper.sn = ByteUtil.hexStringToByteArray(newSN)
//                            Timber.w("sn: $sn")
//                            onSuccess(firstFreeElevator.sn)
//                        }
                    }
                },
                onFailure = {
                    withContext(Dispatchers.Main) {
                        onFailure(it)
                    }
                }
            )
        }
    }

    private fun online(
        sn: String, hostname: String, onSuccess: (sn: String, mqttInfo: MqttInfo) -> Unit,
        onFailure: (e: Exception) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            HttpClient.request(
                networkCall = {
                    HttpClient.getRetrofitClient().online(sn, hostname)
                },
                onSuccess = {
                    withContext(Dispatchers.Main) {
                        onSuccess(sn, it!!)
                    }
                },
                onFailure = {
                    withContext(Dispatchers.Main) {
                        onFailure(it)
                    }
                }
            )
        }
    }

    private fun startTask(
        sn: String, taskCreateBody: TaskCreateBody, onSuccess: () -> Unit,
        onFailure: (e: Exception) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            HttpClient.request(
                networkCall = {
                    HttpClient.getRetrofitClient().createTask(sn, taskCreateBody)
                },
                onSuccess = {
                    val newSN = if (sn.length >= 8) {
                        sn
                    } else {
                        sn.padStart(8, '0')
                    }
                    this@CallingHelper.sn = ByteUtil.hexStringToByteArray(newSN)
                    Timber.tag(ELEVATOR_TAG).w("sn: ${CallingHelper.sn.contentToString()}")
                    withContext(Dispatchers.Main) {
                        onSuccess()
                    }
                },
                onFailure = {
                    withContext(Dispatchers.Main) {
                        onFailure(it)
                    }
                }
            )
        }
    }

    private fun cancelTask(sn: String, onResult: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            HttpClient.request(
                networkCall = {
                    HttpClient.getRetrofitClient().cancelTask(
                        sn = sn,
                        taskCancelBody = TaskCancelBody(
                            hostname = hostname,
                            reason = "cancel task"
                        )
                    )
                },
                onSuccess = {
                    Timber.tag(ELEVATOR_TAG).w("取消任务成功")
                    onResult()
                },
                onFailure = {
                    Timber.tag(ELEVATOR_TAG).w(it, "取消任务失败")
                    onResult()
                }
            )
        }
    }

    fun hexStringToByteArray(hex: String): ByteArray {
        return hex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    fun isElevatorModuleExists(): Boolean {
        val file = File(SerialPortProvider.ofCallModule(Build.PRODUCT))
        val files = file.listFiles()
        if (!file.exists() || files.isNullOrEmpty()) {
            return false
        }
        return files.any { it.name.startsWith("ttyUSB") }
    }

    fun gotoStartFloor() {
        Timber.tag(ELEVATOR_TAG).w("呼梯,起始楼层: $startFloor")
        takeElevatorStep = TakeElevatorStep.CALL_ELEVATOR_OUTSIDE
        val data = byteArrayOf(startFloor.toByte()) + sn!! + hostname.toByteArray()
        val command = byteArrayOf((data.size + 2).toByte(), 0x01, data.size.toByte()) + data
        val checksum = command.reduce { acc, byte -> acc xor byte }
        val finalCommand = elevatorControlCommandHeader + command + checksum
        Timber.tag(ELEVATOR_TAG).w("final Command: ${finalCommand.map { Parser.byte2Hex(it) }.toString().replace(",", "")}")
        gotoStartFloorJob = CoroutineScope(Dispatchers.IO).launch {
            (0 until 5).asFlow()
                .onEach {
                    if (isActive) {
                        Timber.tag(ELEVATOR_TAG).w("前往起始楼层,发送次数: $it")
                        if (it == 4) {
                            withContext(Dispatchers.Main) {
                                callback?.onSendStartTaskCommandTimeout()
                            }
                        } else {
                            parser?.sendCommand(finalCommand)
                            delay(3000)
                        }
                    }
                }
                .catch {
                    Timber.tag(ELEVATOR_TAG).w(it, "发送前往起始楼层指令失败")
                    withContext(Dispatchers.Main) {
                        callback?.onLoRaDisconnectDuringTaskExecuting()
                    }
                }
                .collect()
        }
    }

    private fun cancelGotoStartFloorRetryJob() {
        gotoStartFloorJob?.cancel()
        gotoStartFloorJob = null
    }

    fun gotoTargetFloor() {
        Timber.tag(ELEVATOR_TAG).w("进梯完成,前往目标楼层,目标楼层: $targetFloor")
        takeElevatorStep = TakeElevatorStep.CALL_ELEVATOR_INSIDE
        val data = byteArrayOf(targetFloor.toByte()) + sn!! + hostname.toByteArray()
        val command = byteArrayOf((data.size + 2).toByte(), 0x01, data.size.toByte()) + data
        val checksum = command.reduce { acc, byte -> acc xor byte }
        val finalCommand = elevatorControlCommandHeader + command + checksum
        Timber.tag(ELEVATOR_TAG).w("final Command: ${finalCommand.map { Parser.byte2Hex(it) }.toString().replace(",", "")}")
        gotoTargetFloorJob = CoroutineScope(Dispatchers.IO).launch {
            (0 until 5).asFlow()
                .onEach {
                    if (isActive) {
                        Timber.tag(ELEVATOR_TAG).w("前往目标楼层,发送次数: $it")
                        if (it == 4) {
                            withContext(Dispatchers.Main) {
                                callback?.onSendStartTaskCommandTimeout()
                            }
                        } else {
                            parser?.sendCommand(finalCommand)
                            delay(3000)
                        }
                    }
                }
                .catch {
                    Timber.tag(ELEVATOR_TAG).w(it, "发送前往目标楼层指令失败")
                    withContext(Dispatchers.Main) {
                        callback?.onLoRaDisconnectDuringTaskExecuting()
                    }
                }
                .collect()
        }
    }

    private fun cancelGotoTargetFloorRetryJob() {
        gotoTargetFloorJob?.cancel()
        gotoTargetFloorJob = null
    }

    /**
     * 正常结束任务
     */
    fun taskComplete() {
        if (isDisconnectDuringLeaveElevator) {
            isDisconnectDuringLeaveElevator = false
            callback?.onComplete()
            return
        }
        Timber.tag(ELEVATOR_TAG).w("出梯,任务完成")
        takeElevatorStep = TakeElevatorStep.COMPLETE
        val data = sn!! + 0x00
        val command = byteArrayOf((data.size + 2).toByte(), 0x03, data.size.toByte()) + data
        val checksum = command.reduce { acc, byte -> acc xor byte }
        val finalCommand = elevatorControlCommandHeader + command + checksum
        taskCompleteJob = CoroutineScope(Dispatchers.IO).launch {
            (0 until 5).asFlow()
                .onEach {
                    if (isActive) {
                        Timber.tag(ELEVATOR_TAG).w("出梯,任务完成,指令发送次数: $it")
                        if (it == 4) {
                            withContext(Dispatchers.Main) {
                                callback?.onComplete()
                            }
                        } else {
                            parser!!.sendCommand(finalCommand)
                            delay(2000)
                        }
                    }
                }
                .catch {
                    Timber.tag(ELEVATOR_TAG).w(it, "出梯指令失败")
                    withContext(Dispatchers.Main) {
                        callback?.onComplete()
                    }
                }
                .collect()
        }
    }

    private fun cancelTaskCompleteRetryJob() {
        taskCompleteJob?.cancel()
        taskCompleteJob = null
    }

    /**
     * 任务异常中止
     */
    fun abortTaskWithRetryJob() {
        if (isDisconnectDuringLeaveElevator) {
            isDisconnectDuringLeaveElevator = false
            callback?.onComplete()
            return
        }
        if (sn == null) {
            resetState()
            return
        }
        Timber.tag(ELEVATOR_TAG).w("中止任务")
        takeElevatorStep = TakeElevatorStep.ABORT
        val data = sn!! + 0x02
        val command = byteArrayOf((data.size + 2).toByte(), 0x03, data.size.toByte()) + data
        val checksum = command.reduce { acc, byte -> acc xor byte }
        val finalCommand = elevatorControlCommandHeader + command + checksum
        abortTaskJob = CoroutineScope(Dispatchers.IO).launch {
            (0 until 5).asFlow()
                .onEach {
                    if (isActive) {
                        Timber.tag(ELEVATOR_TAG).w("中止任务,指令发送次数: $it")
                        if (it == 4) {
                            withContext(Dispatchers.Main) {
                                callback?.onComplete()
                            }
                        } else {
                            parser!!.sendCommand(finalCommand)
                            delay(2000)
                        }
                    }
                }
                .catch {
                    Timber.tag(ELEVATOR_TAG).w(it, "中止任务指令失败")
                    withContext(Dispatchers.Main) {
                        callback?.onComplete()
                    }
                }
                .collect()
        }
    }

    fun abortTask() {
        if (sn == null) {
            resetState()
            return
        }
        Timber.tag(ELEVATOR_TAG).w("中止任务")
        takeElevatorStep = TakeElevatorStep.ABORT
        val data = sn!! + 0x02
        val command = byteArrayOf((data.size + 2).toByte(), 0x03, data.size.toByte()) + data
        val checksum = command.reduce { acc, byte -> acc xor byte }
        val finalCommand = elevatorControlCommandHeader + command + checksum
        try {
            parser?.sendCommand(finalCommand)
        } catch (e: Exception) {
            Timber.tag(ELEVATOR_TAG).w(e, "发送中止任务指令失败")
        }
        resetState()
    }

    private fun cancelAbortTaskRetryJob() {
        abortTaskJob?.cancel()
        abortTaskJob = null
    }

    fun resetState() {
        takeElevatorStep = TakeElevatorStep.INIT
        hostname = ""
        startFloor = 0
        targetFloor = 0
        callback = null
        cancelGotoStartFloorRetryJob()
        cancelGotoTargetFloorRetryJob()
        cancelTaskCompleteRetryJob()
        cancelAbortTaskRetryJob()
        checkDeviceDisposable?.dispose()
        checkDeviceDisposable = null
        isOnlyUseLoRa = false
    }

}
