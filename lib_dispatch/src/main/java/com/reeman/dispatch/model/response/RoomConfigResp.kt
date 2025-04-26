package com.reeman.dispatch.model.response

data class RoomConfigResp(
    var room:String,
    val dynamicPlanningPath: Boolean,
    val queuingTimeoutDuration: Int,
    val avoidingDepth: Int,
    val positionRange: Float,
){
    override fun toString(): String {
        return "RoomConfigVO(room='$room', dynamicPlanningPath=$dynamicPlanningPath, queuingTimeoutDuration=$queuingTimeoutDuration, avoidingDepth=$avoidingDepth, positionRange=$positionRange)"
    }
}
