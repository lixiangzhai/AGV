package com.reeman.agv.calling.model

import kotlinx.serialization.Serializable

@Serializable
data class TaskCancelBody(
    val hostname:String,
    val reason:String
)
