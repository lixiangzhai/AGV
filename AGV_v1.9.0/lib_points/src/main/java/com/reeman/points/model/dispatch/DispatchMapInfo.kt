package com.reeman.points.model.dispatch

import com.reeman.points.model.custom.GenericPoint

data class DispatchMapInfo(
    val name:String,
    val alias:String?,
    val pointList:List<GenericPoint>
) {
    override fun toString(): String {
        return "DispatchMapInfo(name='$name', alias='$alias', pointList=$pointList)"
    }
}