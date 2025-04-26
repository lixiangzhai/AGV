package com.reeman.agv.calling.http

import com.reeman.agv.calling.BuildConfig
import com.reeman.agv.calling.http.api.ElevatorAPI
import com.reeman.agv.calling.exception.RequestFailureException
import com.reeman.agv.calling.model.ResponseBody
import kotlinx.serialization.json.Json
import retrofit2.Response
import timber.log.Timber

object HttpClient {

    inline fun <reified T> request(
        networkCall: () -> Response<ResponseBody<T>>,
        onSuccess: (T?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        try {
            val response = networkCall()
            if (response.isSuccessful) {
                onSuccess(response.body()?.data)
            } else {
                response.errorBody()?.string()?.let {
                    try {
                        val responseBody = Json.decodeFromString<ResponseBody<String?>>(it)
                        onFailure(
                            RequestFailureException(
                                responseBody.code, responseBody.data ?: responseBody.msg
                            )
                        )
                    } catch (e: Exception) {
                        Timber.w(e,"decode failure")
                        onFailure(RequestFailureException(response.code(), "parse error body failure"))
                    }
                }?:run {
                    onFailure(RequestFailureException(response.code(), "parse error body failure"))
                }
            }
        } catch (e: Exception) {
            Timber.tag(BuildConfig.ELEVATOR_DIR).w(e, "请求失败")
            onFailure(e)
        }
    }

    fun getRetrofitClient() =
        RetrofitClient.createService(
            clientData = HttpClientData(
                baseUrl = "https://elevator.rmbot.cn",
                connectTimeout = 10,
                readTimeout = 10,
                tag = BuildConfig.ELEVATOR_DIR
            ),
            ElevatorAPI::class.java
        )
}