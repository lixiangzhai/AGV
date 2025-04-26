package com.reeman.agv.calling.model

import kotlinx.serialization.Serializable

@Serializable
data class ElevatorState(
    var sn: String,
    var validFloors: IntArray,
    var maxOpenTime: Int,
    var highestFloor: Int,
    var lowestFloor: Int,
    var taskRobot: String?,
    var currentFloor: Int = 0,
    var frontDoorState: Int = -1,
    var backDoorState: Int = -1,
)