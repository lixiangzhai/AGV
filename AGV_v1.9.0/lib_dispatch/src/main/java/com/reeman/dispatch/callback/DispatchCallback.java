package com.reeman.dispatch.callback;

import com.reeman.dispatch.model.response.MqttInfo;
import com.reeman.dispatch.model.response.Task;
import com.reeman.points.model.dispatch.DispatchMapInfo;

import java.util.List;

public interface DispatchCallback {
//    default void onLoginRoomSuccess() {
//    }
//
//    default void onLoginRoomFailure(Throwable throwable) {
//    }

    default void onOnlineSuccess(List<DispatchMapInfo> mapsInfo, MqttInfo mqttInfo) {
    }

    default void onOnlineFailure(Throwable throwable) {
    }

    default void onMqttConnectSuccess() {
    }

    default void onMqttConnectFailure(Throwable throwable) {
    }

    default void onMqttSubscribeSuccess() {
    }

    default void onMqttSubscribeFailure(Throwable throwable) {
    }

    default void onMqttDisconnect(boolean isRetry,int delay,Throwable throwable) {
    }

    default void onMqttReconnected(){}

    default void onCreateTaskSuccess() {
    }

    default void onCreateTaskFailure(Throwable throwable) {
    }

    default void onTaskReceived(Task task) {
    }

    default void onRobotOffline(List<String> robots) {
    }

    default void onMapUpdate(){

    }

    default void onConfigUpdate(){

    }

    default void onFinishTaskSuccess(){}

    default void onFinishTaskFailure(Throwable throwable){}
}
