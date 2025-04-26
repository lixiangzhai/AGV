package com.reeman.dispatch.model.response

import com.reeman.points.model.dispatch.DispatchMapInfo

data class RobotOnlineResp(
    val room:String,
    val mqttInfo: MqttInfo,
    val maps: List<DispatchMapInfo>,
)

