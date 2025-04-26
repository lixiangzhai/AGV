package com.reeman.dispatch.model.response

data class MqttInfo(
    val host: String,
    val port: Int,
    val clientId: String,
    val username: String,
    val password: String,
    val topicSub:String,
    val topicSubHeartbeat: String,
    val topicSubTask: String,
    val topicSubOffline: String,
    val topicSubMapUpdate:String,
    val topicSubConfigUpdate:String,
    val topicPubHeartbeat: String,
    val topicPubReconnected:String
){
    override fun toString(): String {
        return "MqttInfo(host='$host', port=$port, clientId='$clientId', username='$username', password='$password', topicSub='$topicSub', topicSubHeartbeat='$topicSubHeartbeat', topicSubTask='$topicSubTask', topicSubOffline='$topicSubOffline', topicSubMapUpdate='$topicSubMapUpdate', topicSubConfigUpdate='$topicSubConfigUpdate', topicPubHeartbeat='$topicPubHeartbeat', topicPubReconnected='$topicPubReconnected')"
    }
}