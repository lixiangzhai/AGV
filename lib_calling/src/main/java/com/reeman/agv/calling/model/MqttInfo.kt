package com.reeman.agv.calling.model

import kotlinx.serialization.Serializable

@Serializable
data class MqttInfo(
    val clientId: String,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val topicSub: String,
    val topicPub: String,
)