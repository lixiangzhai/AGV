package com.reeman.agv.calling.http.api

import com.reeman.agv.calling.model.ElevatorState
import com.reeman.agv.calling.model.MqttInfo
import com.reeman.agv.calling.model.ResponseBody
import com.reeman.agv.calling.model.TaskCancelBody
import com.reeman.agv.calling.model.TaskCreateBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface ElevatorAPI {

    @GET("/elevatorcontrol/gateway/{hostname}/elevator/state")
    suspend fun getElevatorState(
        @Path("hostname") hostname: String
    ): Response<ResponseBody<List<ElevatorState>>>

    @POST("/elevatorcontrol/task/{hostname}/online")
    suspend fun online(@Header("ELEVATOR-SN") sn: String, @Path("hostname") hostname: String
    ): Response<ResponseBody<MqttInfo>>

    @POST("/elevatorcontrol/task/create")
    suspend fun createTask(@Header("ELEVATOR-SN") sn: String,@Body taskCreateBody: TaskCreateBody): Response<ResponseBody<String?>>

    @POST("/elevatorcontrol/task/{hostname}/enter")
    suspend fun enterElevator(@Header("ELEVATOR-SN") sn: String,@Path("hostname") hostname: String): Response<ResponseBody<Any?>>

    @POST("/elevatorcontrol/task/{hostname}/leave")
    suspend fun leaveElevator(@Header("ELEVATOR-SN") sn: String,@Path("hostname") hostname: String): Response<ResponseBody<Any?>>

    @POST("/elevatorcontrol/task/cancel")
    suspend fun cancelTask(@Header("ELEVATOR-SN") sn: String,@Body taskCancelBody: TaskCancelBody): Response<ResponseBody<String?>>
}