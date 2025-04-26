package com.reeman.dispatch.model.request


data class RoomLoginReq(
    val roomName: String,
    val roomPwd: String,
    val hostname: String,
    val robotType:Int,
){
    override fun toString(): String {
        return "RoomLoginRequestBody(roomName='$roomName', roomPwd='$roomPwd', hostname='$hostname', robotType=$robotType)"
    }
}