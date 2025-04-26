package com.reeman.agv.calling.exception

class RequestFailureException(val code:Int, val msg:String):Exception(){
    override fun toString(): String {
        return "RequestFailureException(code=$code, msg='$msg')"
    }
}