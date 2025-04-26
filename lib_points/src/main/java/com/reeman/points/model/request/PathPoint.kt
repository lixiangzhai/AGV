package com.reeman.points.model.request

import com.google.gson.annotations.SerializedName

data class PathPoint(
    @SerializedName("type")
     var type: String,

    @SerializedName(value = "a", alternate = ["vehicleOrientationAngle"])
    private var a: String,

    @SerializedName(value = "x", alternate = ["xPosition"])
    private var x: String,

    @SerializedName(value = "y", alternate = ["yPosition"])
    private var y: String,

    @SerializedName(value = "ex", alternate = ["expand"])
     var expand: String?,
    @SerializedName(value = "name")
     var name: String
) {
    fun getAngle() =
         a.toDouble() / 1000

    fun getXPosition() =
         x.toDouble() / 1000


    fun getYPosition() =
        y.toDouble() / 1000

    fun getPosition() =
        doubleArrayOf(getXPosition(), getYPosition(), getAngle())


    override fun toString(): String {
        return "PathPoint(type='$type', vehicleOrientationAngle='$a', xPosition='$x', yPosition='$y', expand=$expand, name='$name')"
    }
}