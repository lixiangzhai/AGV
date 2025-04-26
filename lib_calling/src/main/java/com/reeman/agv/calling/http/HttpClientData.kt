package com.reeman.agv.calling.http

data class HttpClientData(
    val baseUrl: String = "https://navi.rmbot.cn",
    val connectTimeout: Long = 30L,
    val readTimeout: Long = 30L,
    val maxRetries: Int = 0,
    val retryDelaySeconds: Int = 0,
    val headers: Map<String, String> = emptyMap(),
    val tag: String
){
    override fun toString(): String {
        return "HttpClientData(baseUrl='$baseUrl', connectTimeout=$connectTimeout, readTimeout=$readTimeout, maxRetries=$maxRetries, retryDelaySeconds=$retryDelaySeconds, headers=$headers, tag='$tag')"
    }
}
