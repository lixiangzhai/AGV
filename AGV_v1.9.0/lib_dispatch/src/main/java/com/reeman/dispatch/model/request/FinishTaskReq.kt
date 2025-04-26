package com.reeman.dispatch.model.request

data class FinishTaskReq(
    val hostname:String,
    val taskId:Long,
    val reason:Int = 1
){
    override fun toString(): String {
        return "FinishTaskReq(hostname='$hostname', taskId=$taskId, reason=$reason)"
    }
}
