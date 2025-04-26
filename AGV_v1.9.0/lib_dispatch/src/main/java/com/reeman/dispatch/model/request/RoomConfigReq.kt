package com.reeman.dispatch.model.request


data class RoomConfigReq(
    var dynamicPlanningPath: Boolean,
    var queuingTimeoutDuration: Int,
    var avoidingDepth: Int,
    var positionRange: Float,
){
    override fun toString(): String {
        return "RoomConfigDTO(dynamicPlanningPath=$dynamicPlanningPath, queuingTimeoutDuration=$queuingTimeoutDuration, avoidingDepth=$avoidingDepth, positionRange=$positionRange)"
    }
}
