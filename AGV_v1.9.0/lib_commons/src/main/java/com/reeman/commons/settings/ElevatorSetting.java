package com.reeman.commons.settings;

import kotlin.Pair;


public class ElevatorSetting {

    public boolean open;

    /**
     * 0:网络;
     * 1:网络+LoRa;
     * 2:LoRa
     */
    public int communicationMethod;


    public String gatewayId;

    //first:alias second:MD5
    public Pair<String, String> chargingPileMap;

    //first:alias second:MD5
    public Pair<String, String> productionPointMap;

    public int waitingElevatorTimeoutRetryTimeInterval;

    public int enterOrLeavePointUnreachableRetryTimeInterval;

    public boolean isDetectionSwitchOpen;

    public int generatePathCount;

    //电梯单开门
    public boolean isSingleDoor;

    //单网络
    public boolean isSingleNetwork;
    //梯外网络 first:ssid,second:pwd
    public Pair<String, String> outsideNetwork;
    //梯内网络 first:ssid,second:pwd
    public Pair<String, String> insideNetwork;

    public ElevatorSetting(boolean open, int communicationMethod, String gatewayId, Pair<String, String> chargingPileMap, Pair<String, String> productionPointMap, int waitingElevatorTimeoutRetryTimeInterval, int enterOrLeavePointUnreachableRetryTimeInterval, boolean isDetectionSwitchOpen, int generatePathCount, boolean isSingleDoor, boolean isSingleNetwork, Pair<String, String> outsideNetwork, Pair<String, String> insideNetwork) {
        this.open = open;
        this.communicationMethod = communicationMethod;
        this.gatewayId = gatewayId;
        this.chargingPileMap = chargingPileMap;
        this.productionPointMap = productionPointMap;
        this.waitingElevatorTimeoutRetryTimeInterval = waitingElevatorTimeoutRetryTimeInterval;
        this.enterOrLeavePointUnreachableRetryTimeInterval = enterOrLeavePointUnreachableRetryTimeInterval;
        this.isDetectionSwitchOpen = isDetectionSwitchOpen;
        this.generatePathCount = generatePathCount;
        this.isSingleDoor = isSingleDoor;
        this.isSingleNetwork = isSingleNetwork;
        this.outsideNetwork = outsideNetwork;
        this.insideNetwork = insideNetwork;
    }

    public static ElevatorSetting getDefault() {
        return new ElevatorSetting(
                false,
                0,
                null,
                null,
                null,
                3,
                1,
                true,
                3,
                true,
                true,
                null,
                new Pair<>("RBTEC6200", "TK62002@22")

        );
    }

    @Override
    public String toString() {
        return "{" +
                "梯控开关=" + open +
                ", 通讯方式=" + communicationMethod +
                ", 网关id=" + gatewayId +
                ", 充电桩地图=" + chargingPileMap +
                ", 出品点地图=" + productionPointMap +
                ", 超时重新呼梯时间=" + waitingElevatorTimeoutRetryTimeInterval +
                ", 检测到无法进入电梯重新进入电梯时间=" + enterOrLeavePointUnreachableRetryTimeInterval +
                ", 是否开启进梯检测=" + isDetectionSwitchOpen +
                ", 检测是否可以进入电梯次数=" + generatePathCount +
                ", 电梯是否单向开门=" + isSingleDoor +
                ", 是否使用单网络=" + isSingleNetwork +
                ", 梯外网络=" + outsideNetwork +
                ", 梯内网络=" + insideNetwork +
                '}';
    }
}
