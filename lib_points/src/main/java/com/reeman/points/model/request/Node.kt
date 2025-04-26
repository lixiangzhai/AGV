package com.reeman.points.model.request

import com.google.gson.annotations.SerializedName

data class Node(
    val name: String,
    @SerializedName(value = "sourcePoint", alternate = ["sp"])
    var sourcePoint: String,
    @SerializedName(value = "destinationPoint", alternate = ["dp"])
    var destinationPoint: String,

    var path:List<Path>,
) {

    override fun toString(): String {
        return "Node(name='$name', sourcePoint='$sourcePoint', destinationPoint='$destinationPoint', path=$path)"
    }
}