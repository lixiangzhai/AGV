package com.reeman.agv.calling.callback

interface ElevatorCallback {

    fun onInitiateTaskSuccess()

    fun onArrivedStartFloor()

    fun onGotoTargetFloor()

    fun onQueuing()

    fun onArrivedTargetFloor(floor:String)

    fun onInitiateTaskFailure(throwable: Throwable)

    fun onCheckLoRaNotExist()

    fun onLoRaDisconnectDuringTaskExecuting()

    fun onSendStartTaskCommandTimeout()

//    fun onLoRaDisconnectDuringTaskFinished()

    fun onErrorCodeFromGateway(code:Int)

    fun onComplete()

}