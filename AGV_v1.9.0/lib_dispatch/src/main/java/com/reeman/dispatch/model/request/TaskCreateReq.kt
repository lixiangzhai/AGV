package com.reeman.dispatch.model.request

import com.reeman.dispatch.constants.TaskType


data class TaskCreateReq(
    val hostname: String,
    val targetMap: String? = null,
    val targetPoint: String? = null,
    val taskType:TaskType
){
    override fun toString(): String {
        return "TaskCreateReq(hostname='$hostname', targetMap=$targetMap, targetPoint=$targetPoint, taskType=$taskType)"
    }
}