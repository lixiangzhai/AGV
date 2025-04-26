package com.reeman.dispatch.model.response

import com.reeman.dispatch.constants.DispatchAction
import com.reeman.dispatch.constants.TaskProcess
import com.reeman.dispatch.constants.TaskType


data class Task(
    val taskId: Long,
    val finalTargetPoint: String,
    val taskType: TaskType,
    val createTime: Long,
    var orderId: Long,
    var globalPath: List<Pair<String, List<String>>>,
    var globalAvoidPath: List<String>,
    var path: List<String>,
    var action: DispatchAction,
    var taskProcess: TaskProcess,
    var updateTime: Long,
){
    constructor( finalTargetPoint: String):this(
        -1,
        finalTargetPoint,
        TaskType.TASK_CHARGE,
        0,
        0,
        emptyList(),
        emptyList(),
        emptyList(),
        DispatchAction.NAVIGATING_TO_FINAL,
        TaskProcess.COMPLETE,
        0
    )
}