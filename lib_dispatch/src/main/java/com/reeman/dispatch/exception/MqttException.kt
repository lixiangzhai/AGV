package com.reeman.dispatch.exception

class MqttException(msg:String):Exception(msg) {

    companion object{
        const val CONNECTION_FAILURE = "CONNECTION_FAILURE"
        const val SUBSCRIBE_FAILURE = "SUBSCRIBE_FAILURE"
    }
}