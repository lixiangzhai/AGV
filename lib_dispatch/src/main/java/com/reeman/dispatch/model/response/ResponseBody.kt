package com.reeman.dispatch.model.response


data class ResponseBody<T>(val code: Int, val message: String, val data: T? = null) {
    override fun toString(): String {
        return "ResponseVO(code=$code, message='$message', data=$data)"
    }
}
