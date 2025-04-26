package com.reeman.agv.presenter.impl

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.reeman.agv.R
import com.reeman.agv.activities.TaskExecutingActivity
import com.reeman.agv.contract.MainContract
import com.reeman.agv.widgets.EasyDialog
import com.reeman.commons.constants.Constants
import com.reeman.commons.exceptions.ElevatorNetworkNotSettException
import com.reeman.commons.state.NavigationMode
import com.reeman.commons.state.RobotInfo
import com.reeman.commons.state.TaskMode
import com.reeman.dao.repository.entities.RouteWithPoints
import com.reeman.points.model.custom.GenericPoint
import com.reeman.points.model.custom.GenericPointsWithMap
import com.reeman.points.process.PointRefreshProcessor
import com.reeman.points.process.callback.RefreshPointDataCallback
import com.reeman.points.process.impl.DeliveryPointsRefreshProcessingStrategy
import com.reeman.points.process.impl.DeliveryPointsWithMapsRefreshProcessingStrategy
import com.reeman.points.process.impl.FixedDeliveryPointsRefreshProcessingStrategy
import com.reeman.points.process.impl.FixedDeliveryPointsWithMapsRefreshProcessingStrategy
import com.reeman.points.process.impl.FixedQRCodePointsRefreshProcessingStrategy
import com.reeman.points.process.impl.FixedQRCodePointsWithMapsRefreshProcessingStrategy
import com.reeman.points.process.impl.QRCodePointsRefreshProcessingStrategy
import com.reeman.points.process.impl.QRCodePointsWithMapsRefreshProcessingStrategy
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MainPresenter(val view: MainContract.View) : MainContract.Presenter {

    private fun getPointRefreshProcessingStrategy(checkCurrentMapPointTypes: Boolean) =
        if (RobotInfo.isElevatorMode) {
            if (RobotInfo.navigationMode == NavigationMode.autoPathMode) {
                DeliveryPointsWithMapsRefreshProcessingStrategy(checkCurrentMapPointTypes)
            } else {
                FixedDeliveryPointsWithMapsRefreshProcessingStrategy(checkCurrentMapPointTypes)
            }
        } else {
            if (RobotInfo.navigationMode == NavigationMode.autoPathMode) {
                DeliveryPointsRefreshProcessingStrategy()
            } else {
                FixedDeliveryPointsRefreshProcessingStrategy()
            }
        }

    private fun isElevatorNetWorkNotSet(): Boolean {
        if (RobotInfo.isElevatorMode && !RobotInfo.elevatorSetting.isSingleNetwork) {
            if (RobotInfo.elevatorSetting.outsideNetwork == null || RobotInfo.elevatorSetting.insideNetwork == null) {
                view.onDataLoadFailed(
                    ElevatorNetworkNotSettException(
                        RobotInfo.elevatorSetting.outsideNetwork == null,
                        RobotInfo.elevatorSetting.insideNetwork == null
                    )
                )
                return true
            }
        }
        return false
    }

    override fun refreshProductModePoint(context: Context) {
        if (isElevatorNetWorkNotSet()) return
        EasyDialog.getLoadingInstance(context)
            .loading(context.getString(R.string.text_init_product_point))

        PointRefreshProcessor(getPointRefreshProcessingStrategy(false),
            object : RefreshPointDataCallback {
                override fun onPointsLoadSuccess(pointList: List<GenericPoint>) {
                    view.onProductModelDataLoadSuccess()
                }

                override fun onPointsWithMapsLoadSuccess(pointsWithMapList: List<GenericPointsWithMap>) {
                    view.onProductModelDataLoadSuccess()
                }

                override fun onThrowable(throwable: Throwable) {
                    view.onDataLoadFailed(throwable)
                }
            }).process(
            ip = RobotInfo.ROSIPAddress,
            useLocalData = false,
            checkEnterElevatorPoint = RobotInfo.supportEnterElevatorPoint(),
            pointTypes = listOf(GenericPoint.PRODUCT)
        )
    }

    override fun refreshChargeModePoint(context: Context) {
        if (isElevatorNetWorkNotSet()) return
        EasyDialog.getLoadingInstance(context)
            .loading(context.getString(R.string.text_init_charging_point))

        PointRefreshProcessor(getPointRefreshProcessingStrategy(false),
            object : RefreshPointDataCallback {
                override fun onPointsLoadSuccess(pointList: List<GenericPoint>) {
                    view.onChargeModelDataLoadSuccess()
                }

                override fun onPointsWithMapsLoadSuccess(pointsWithMapList: List<GenericPointsWithMap>) {
                    view.onChargeModelDataLoadSuccess()
                }

                override fun onThrowable(throwable: Throwable) {
                    view.onDataLoadFailed(throwable)
                }
            }).process(
            ip = RobotInfo.ROSIPAddress,
            useLocalData = false,
            checkEnterElevatorPoint = RobotInfo.supportEnterElevatorPoint(),
            pointTypes = listOf(GenericPoint.CHARGE)
        )
    }

    override fun refreshQRCodeModePoints(context: Context) {
        if (isElevatorNetWorkNotSet()) return
        val qrCodePointRefreshProcessingStrategy = if (RobotInfo.isElevatorMode) {
            if (RobotInfo.navigationMode == NavigationMode.autoPathMode) {
                QRCodePointsWithMapsRefreshProcessingStrategy(false)
            } else {
                FixedQRCodePointsWithMapsRefreshProcessingStrategy(false)
            }
        } else {
            if (RobotInfo.navigationMode == NavigationMode.autoPathMode) {
                QRCodePointsRefreshProcessingStrategy()
            } else {
                FixedQRCodePointsRefreshProcessingStrategy()
            }
        }
        EasyDialog.getLoadingInstance(context)
            .loading(context.getString(R.string.text_refresh_qrcode_mode_info))

        PointRefreshProcessor(qrCodePointRefreshProcessingStrategy,
            object : RefreshPointDataCallback {
                override fun onPointsLoadSuccess(pointList: List<GenericPoint>) {
                    view.onQRCodeModelDataLoadSuccess(pointList)
                }

                override fun onPointsWithMapsLoadSuccess(pointsWithMapList: List<GenericPointsWithMap>) {
                    view.onQRCodeModelMapsWithPointsDataLoadSuccess(pointsWithMapList)
                }

                override fun onThrowable(throwable: Throwable) {
                    view.onDataLoadFailed(throwable)
                }
            }).process(
            ip = RobotInfo.ROSIPAddress,
            useLocalData = false,
            checkEnterElevatorPoint = RobotInfo.supportEnterElevatorPoint(),
            pointTypes = listOf(GenericPoint.AGV_TAG)
        )
    }

    override fun refreshRouteModePoints(context: Context) {
        if (RobotInfo.isElevatorMode) {
            view.onDataLoadFailed(context.getString(R.string.text_route_mode_not_support_elevator_mode))
            return
        }
        EasyDialog.getLoadingInstance(context)
            .loading(context.getString(R.string.text_refresh_route_mode_info))

        PointRefreshProcessor(getPointRefreshProcessingStrategy(false),
            object : RefreshPointDataCallback {
                override fun onPointsLoadSuccess(pointList: List<GenericPoint>) {
                    view.onRouteModelDataLoadSuccess(pointList)
                }

                override fun onThrowable(throwable: Throwable) {
                    view.onDataLoadFailed(throwable)
                }
            }).process(
            ip = RobotInfo.ROSIPAddress,
            useLocalData = false,
            checkEnterElevatorPoint = RobotInfo.supportEnterElevatorPoint(),
            pointTypes = if (RobotInfo.isSpaceShip()) {
                listOf(
                    GenericPoint.DELIVERY,
                    GenericPoint.PRODUCT,
                    GenericPoint.AGV_TAG
                )
            } else {
                listOf(
                    GenericPoint.DELIVERY,
                    GenericPoint.PRODUCT
                )
            }
        )
    }

    override fun refreshNormalModePoints(context: Context) {
        if (isElevatorNetWorkNotSet()) return
        EasyDialog.getLoadingInstance(context)
            .loading(context.getString(R.string.text_refresh_normal_mode_info))
        PointRefreshProcessor(getPointRefreshProcessingStrategy(false),
            object : RefreshPointDataCallback {
                override fun onPointsLoadSuccess(pointList: List<GenericPoint>) {
                    view.onNormalModePointsDataLoadSuccess(pointList)
                }

                override fun onPointsWithMapsLoadSuccess(pointsWithMapList: List<GenericPointsWithMap>) {
                    view.onNormalModeMapsWithPointsDataLoadSuccess(pointsWithMapList)
                }

                override fun onThrowable(throwable: Throwable) {
                    view.onDataLoadFailed(throwable)
                }
            }).process(
            ip = RobotInfo.ROSIPAddress,
            useLocalData = false,
            checkEnterElevatorPoint = RobotInfo.supportEnterElevatorPoint(),
            pointTypes = listOf(GenericPoint.DELIVERY)
        )
    }

    override fun startRouteModeTask(
        activity: Activity,
        routeWithPoints: RouteWithPoints,
        isTest: Boolean
    ) {
        RobotInfo.mode = TaskMode.MODE_ROUTE
        val intent = Intent(activity, TaskExecutingActivity::class.java)
        intent.putExtra(Constants.TASK_TARGET, routeWithPoints)
        intent.putExtra(Constants.TASK_TEST, isTest)
        activity.startActivityForResult(intent, Constants.RESULT_CODE_OF_TASK)
    }

    override fun startQRCodeModeTask(
        activity: Activity,
        qrCodeModelPointList: List<Pair<Pair<String, String>, Pair<String, String>>>
    ) {
        RobotInfo.mode = TaskMode.MODE_QRCODE
        val intent = Intent(activity, TaskExecutingActivity::class.java)
        intent.putExtra(Constants.TASK_TARGET, Gson().toJson(qrCodeModelPointList))
        activity.startActivityForResult(intent, Constants.RESULT_CODE_OF_TASK)
    }

    override fun startNormalModeTask(activity: Activity, points: List<Pair<String, String>>) {
        RobotInfo.mode = TaskMode.MODE_NORMAL
        val intent = Intent(activity, TaskExecutingActivity::class.java)
        intent.putExtra(Constants.TASK_TARGET, Json.encodeToString(points))
        activity.startActivityForResult(intent, Constants.RESULT_CODE_OF_TASK)
    }

    override fun gotoProductPoint(activity: Activity, isAutoWork: Boolean) {
        RobotInfo.mode = TaskMode.MODE_START_POINT
        val intent = Intent(activity, TaskExecutingActivity::class.java)
        if (isAutoWork) {
            intent.putExtra(Constants.TASK_TARGET, Constants.TASK_AUTO_WORK)
        }
        activity.startActivityForResult(intent, Constants.RESULT_CODE_OF_TASK)
    }

    override fun gotoChargePoint(activity: Activity, isAutoWork: Boolean) {
        RobotInfo.mode = TaskMode.MODE_CHARGE
        val intent = Intent(activity, TaskExecutingActivity::class.java)
        if (isAutoWork) {
            intent.putExtra(Constants.TASK_TARGET, Constants.TASK_AUTO_WORK)
        }
        activity.startActivityForResult(intent, Constants.RESULT_CODE_OF_TASK)
    }
}