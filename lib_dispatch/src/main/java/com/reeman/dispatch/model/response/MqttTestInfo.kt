package com.reeman.dispatch.model.response

data class MqttTestInfo(
    val host: String,
    val port: Int,
    val clientId: String,
    val username: String,
    val password: String,
    val topicSub:String,
    val topicPub:String,
)