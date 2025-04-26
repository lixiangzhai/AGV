package com.reeman.agv.calling.model

import kotlinx.serialization.Serializable

@Serializable
data class TaskCreateBody(
    var hostname:String,
    var from:Int?,
    var to:Int,
)
