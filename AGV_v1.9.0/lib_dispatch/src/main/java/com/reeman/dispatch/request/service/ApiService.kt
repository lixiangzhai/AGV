package com.reeman.dispatch.request.service

import com.reeman.dispatch.model.request.FinishTaskReq
import com.reeman.dispatch.model.request.MapInfoUploadReq
import com.reeman.dispatch.model.response.ResponseBody
import com.reeman.dispatch.model.request.RobotHeartbeat
import com.reeman.dispatch.model.request.RoomConfigReq
import com.reeman.dispatch.model.response.RobotOnlineResp
import com.reeman.dispatch.model.request.RoomLoginReq
import com.reeman.dispatch.model.request.TaskCreateReq
import com.reeman.dispatch.model.response.MqttTestInfo
import com.reeman.dispatch.model.response.RoomConfigResp
import com.reeman.points.model.dispatch.DispatchMapInfo
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Url

interface ApiService {

    @POST
    suspend fun loginRoomSync(
        @Url url: String,
        @Body roomLoginReq: RoomLoginReq
    ): Response<ResponseBody<Map<String, String>>>

    @POST
    suspend fun uploadMap(
        @Url url: String,
        @Body mapInfoUploadReq: MapInfoUploadReq
    ): Response<ResponseBody<String?>>

    @POST
    suspend fun robotOnline(
        @Url url: String,
        @Body heartbeat: RobotHeartbeat
    ): Response<ResponseBody<RobotOnlineResp>>

    @POST
    suspend fun createTask(
        @Url url: String,
        @Body taskCreateReq: TaskCreateReq
    ): Response<ResponseBody<Any>>

    @POST
    suspend fun finishTask(
        @Url url: String,
        @Body finishTaskReq: FinishTaskReq
    ): Response<ResponseBody<String?>>

    @GET
    suspend fun getRoomConfig(
    @Url url: String
    ):Response<ResponseBody<RoomConfigResp>>

    @POST
    suspend fun updateRoomConfig(
        @Url url: String,
        @Body roomConfigReq: RoomConfigReq
    ):Response<ResponseBody<String>>

    @GET
    suspend fun getMapInfo(
        @Url url: String
    ):Response<ResponseBody<List<DispatchMapInfo>>>

    @GET
    suspend fun getMqttTestInfo(
        @Url url: String,
    ):Response<ResponseBody<MqttTestInfo>>


}
