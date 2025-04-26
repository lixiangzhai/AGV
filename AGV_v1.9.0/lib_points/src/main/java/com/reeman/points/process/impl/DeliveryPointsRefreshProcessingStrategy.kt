package com.reeman.points.process.impl

import android.annotation.SuppressLint
import com.reeman.commons.utils.SpManager
import com.reeman.points.exception.PointListEmptyException
import com.reeman.points.model.custom.GenericPoint
import com.reeman.points.process.PointRefreshProcessingStrategy
import com.reeman.points.process.callback.RefreshPointDataCallback
import com.reeman.points.utils.PointCacheConstants
import com.reeman.points.utils.PointCacheInfo
import com.reeman.points.utils.PointCacheUtil
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber

class DeliveryPointsRefreshProcessingStrategy : PointRefreshProcessingStrategy {

    @SuppressLint("CheckResult")
    override fun refreshPointList(
        ipAddress: String,
        useLocalData: Boolean,
        checkEnterElevatorPoint: Boolean,
        pointTypes: List<String>,
        callback: RefreshPointDataCallback
    ) {
        PointCacheUtil.refreshPoints(ipAddress, useLocalData)
            .flatMap {
                val isROSData = it.first
                val pointsMap = it.second
                if (pointsMap.isEmpty() || !pointsMap.containsKey("waypoints")) {
                    Timber.d("数据为空")
                    throwNoPointException(isROSData)
                }
                if (isROSData) {
                    Timber.d("更新本地数据")
                    PointCacheUtil.savePoints(pointsMap)
                }
                val pointList = pointsMap["waypoints"]
                if (pointList.isNullOrEmpty()) {
                    Timber.d("数据为空")
                    throwNoPointException(isROSData)
                }
                val deliveryPointList = PointCacheInfo.checkPoints(pointList!!, pointTypes)
                if (deliveryPointList.isEmpty()) {
                    var code = PointListEmptyException.LOCAL_NO_TARGET_TYPE_POINTS
                    if (isROSData) code = PointListEmptyException.ROS_NO_TARGET_TYPE_POINTS
                    throw PointListEmptyException(code)
                }
                Timber.d("点位信息: $deliveryPointList")
                if (GenericPoint.AGV_TAG in pointTypes) {
                    val agvTagPoints =
                        deliveryPointList.filter { point -> point.type == GenericPoint.AGV_TAG }
                    if (agvTagPoints.isNotEmpty()) {
                        val qrCodePointPairList =
                            try {
                                PointCacheUtil.refreshQRCodePoints(ipAddress, useLocalData)
                            } catch (e: Exception) {
                                null
                            }
                        qrCodePointPairList?.let { qrCodePointPair ->
                            val isQRCodePointsUseROSData = qrCodePointPair.first
                            val qrCodePointListMap = qrCodePointPair.second
                            qrCodePointListMap["agvTags"]?.let { qrCodePoints ->
                                val validQRCodePoints =
                                    PointCacheUtil.getValidQRCodePoints(
                                        agvTagPoints,
                                        qrCodePoints
                                    )
                                PointCacheInfo.routeModelPoints.clear()
                                if (validQRCodePoints.isNotEmpty()) {
                                    if (isQRCodePointsUseROSData) {
                                        Timber.d("更新本地二维码数据")
                                        PointCacheUtil.saveQRCodePoints(qrCodePointListMap)
                                    }
                                    val points =
                                        deliveryPointList.filter { point -> point.type != GenericPoint.AGV_TAG || point in validQRCodePoints }
                                    PointCacheInfo.routeModelPoints.addAll(points)
                                    return@flatMap Observable.just(points)
                                }
                            }
                        }
                    }
                }
                PointCacheInfo.routeModelPoints.clear()
                PointCacheInfo.routeModelPoints.addAll(deliveryPointList)
                Observable.just(deliveryPointList)
            }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ callback.onPointsLoadSuccess(it) }) { callback.onThrowable(it) }
    }

    private fun throwNoPointException(isROSData: Boolean) {
        var code = PointListEmptyException.LOCAL_POINTS_EMPTY
        if (isROSData) {
            code = PointListEmptyException.ROS_POINTS_EMPTY
            SpManager.getInstance().edit().remove(PointCacheConstants.KEY_POINT_INFO).apply()
        }
        throw PointListEmptyException(code)
    }
}