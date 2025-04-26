package com.reeman.agv.calling.model

import kotlinx.serialization.Serializable

@Serializable
data class ResponseBody<T>(val code: Int, val msg: String, val data: T? = null) {
    override fun toString(): String {
        return "ResponseVO(code=$code, message='$msg', data=$data)"
    }
}