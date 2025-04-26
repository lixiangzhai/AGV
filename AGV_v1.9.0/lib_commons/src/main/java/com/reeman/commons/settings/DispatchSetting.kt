package com.reeman.commons.settings

data class DispatchSetting(
    var isOpened: Boolean = false,
    var isLocalServer:Boolean = false,
    var serverAddress: String = "",
    var roomName: String = "",
    var roomPwd: String = "",
    var initMap: String = "",
    var initPoint: String = "",
    var chargePointMap:String = "",
    var chargePoint:String=""
) {
    override fun toString(): String {
        return "DispatchSetting(isOpened=$isOpened, isLocalServer=$isLocalServer, serverAddress='$serverAddress', roomName='$roomName', roomPwd='$roomPwd', initMap='$initMap', initPoint='$initPoint', chargePointMap='$chargePointMap', chargePoint='$chargePoint')"
    }
}
