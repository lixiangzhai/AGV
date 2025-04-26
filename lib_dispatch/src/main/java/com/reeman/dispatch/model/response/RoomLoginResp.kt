package com.reeman.dispatch.model.response

data class RoomLoginResp(
    val token:String
){
    override fun toString(): String {
        return "RoomLoginRespBody(token='$token')"
    }
}
