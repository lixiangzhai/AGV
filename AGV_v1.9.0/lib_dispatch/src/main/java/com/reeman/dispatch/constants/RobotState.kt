package com.reeman.dispatch.constants

enum class RobotState(val value: Int) {
    FREE(0),//空闲
    TASK_EXECUTING(1),//任务执行中
    CHARGING(2);//正在充电

    companion object {
        @OptIn(ExperimentalStdlibApi::class)
        fun fromByte(value: Byte): RobotState {
            return entries.find { it.value.toByte() == value }?:FREE
        }
    }
}