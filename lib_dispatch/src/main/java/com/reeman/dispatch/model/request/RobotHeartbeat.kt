package com.reeman.dispatch.model.request

import com.reeman.dispatch.constants.TaskProcess

data class RobotHeartbeat(
    /**
     * 是否使用梯控
     */
    val useElevator: Boolean = false,
    /**
     * 机器编号
     */
    val hostname: String,
    val room:String?=null,
    /**
     * 当前地图
     */
    val currentMap:String,
    val currentPower:Int,
    /**
     * 机型
     */
    val robotType: Int,

    val currentState:Int,
    /**
     * 顶升模块/叉臂位置 0:低位,1:高位;
     */
    val liftModelLocation:Int,
    /**
     * 急停开关状态 0:按下,1:弹起;
     */
    val emergencyStopButton: Int,
    /**
     * 当前坐标
     */
    val position: DoubleArray,

    val currentPoint:String?=null,
    val taskProcess: TaskProcess?=null

    )
