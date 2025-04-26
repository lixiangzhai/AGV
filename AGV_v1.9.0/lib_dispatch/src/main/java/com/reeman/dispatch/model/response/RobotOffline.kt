package com.reeman.dispatch.model.response

data class RobotOffline(
    val roomName: String,
    val offlineRobots: List<String>
)