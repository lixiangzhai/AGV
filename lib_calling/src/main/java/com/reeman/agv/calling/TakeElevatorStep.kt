package com.reeman.agv.calling

enum class TakeElevatorStep {
    INIT,
    CALL_ELEVATOR_OUTSIDE,
    WAITING_OUTSIDE,
    ENTERING_ELEVATOR,
    CALL_ELEVATOR_INSIDE,
    WAITING_INSIDE,
    LEAVING_ELEVATOR,
    COMPLETE,
    ABORT,

}