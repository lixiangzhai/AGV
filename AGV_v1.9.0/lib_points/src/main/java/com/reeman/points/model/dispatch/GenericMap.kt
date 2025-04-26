package com.reeman.points.model.dispatch

import com.reeman.points.model.request.Node
import com.reeman.points.model.request.Path
import com.reeman.points.model.request.PathModelPoint
import com.reeman.points.model.request.PathPoint

data class GenericMap(
    val name: String,
    val alias: String?,
    val point: List<PathPoint>,
    val path: List<Path>,
    val node: List<Node>
){

    constructor(map:String,alias:String?,pathModelPoint: PathModelPoint):this(
            name = map,
            alias = alias,
            point = pathModelPoint.point!!,
            path =  pathModelPoint.path!!,
            node =  pathModelPoint.node!!,
    )
    override fun toString(): String {
        return "GenericMap(name='$name', alias=$alias, point=$point, path=$path, node=$node)"
    }
}
