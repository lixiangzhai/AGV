package com.reeman.dispatch.model.request

data class RobotReconnected(
    var room: String,

    val hostname: String,

    val taskId: Long,

    val orderId: Long,
)
