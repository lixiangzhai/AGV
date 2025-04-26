package com.reeman.dispatch.model.request


data class MapInfoUploadReq(
    val useElevator: Boolean = false,
    val hostname: String,
    val maps: List<Any>,
    ){
    override fun toString(): String {
        return "MapInfoUpload(useElevator=$useElevator, hostname='$hostname', maps=$maps)"
    }
}