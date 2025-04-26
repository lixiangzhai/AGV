package com.reeman.dispatch.request

object Urls {
    fun getLoginUrl(host:String) = "$host/room/login"
    fun getMapUploadUrl(host: String) = "$host/map/upload"
    fun getRobotOnlineUrl(host:String) = "$host/robot/online"
    fun getCreateTaskUrl(host: String) = "$host/task/create"
    fun getFinishTaskUrl(host:String) = "$host/task/finish"
    fun getOrUpdateRoomConfig(host:String) = "$host/room/config"
    fun getMapInfo(host: String) = "$host/map"
    fun getMqttTestInfo(host: String,hostname:String) = "$host/robot/$hostname/mqtt/info"
}