package com.reeman.dispatch.constants

/**
 * 任务动作
 */
enum class DispatchAction {

    /**
     * 任务创建
     */
    CREATE,

    /**
     * 正在导航前往节点
     */
    NAVIGATING_TO_NODE,

    /**
     * 正在前往避让点
     */
    AVOIDING,

    /**
     * 排队中
     */
    QUEUING,

    /**
     * 到达目标点
     */
    NAVIGATING_TO_FINAL,

    /**
     * 正在前往乘梯点
     */
    NAVIGATING_TO_TAKE_ELEVATOR,

    /**
     * 正在进入电梯
     */
    NAVIGATING_TO_ELEVATOR_INSIDE,

    /**
     * 正在离开电梯
     */
    NAVIGATING_TO_LEAVE_ELEVATOR,

}