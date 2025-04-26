package com.reeman.agv.activities

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.content.DialogInterface.OnDismissListener
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import com.google.gson.Gson
import com.reeman.agv.R
import com.reeman.agv.base.BaseActivity
import com.reeman.agv.base.BaseApplication.mApp
import com.reeman.agv.calling.button.CallingHelper.isStart
import com.reeman.agv.calling.button.CallingHelper.start
import com.reeman.agv.calling.event.MqttConnectionEvent
import com.reeman.agv.calling.mqtt.MqttClient
import com.reeman.agv.calling.utils.CallingStateManager
import com.reeman.agv.calling.utils.TaskExecutingCode
import com.reeman.agv.constants.Errors
import com.reeman.agv.constants.SettingPage
import com.reeman.agv.contract.MainContract
import com.reeman.agv.fragments.main.MainContentFragment
import com.reeman.agv.fragments.main.MainContentFragment.OnMainContentClickListener
import com.reeman.agv.fragments.main.ModeNormalFragment
import com.reeman.agv.fragments.main.ModeNormalFragment.ModeNormalClickListener
import com.reeman.agv.fragments.main.ModeNormalWithMapFragment
import com.reeman.agv.fragments.main.ModeNormalWithMapFragment.ModeNormalWithMapClickListener
import com.reeman.agv.fragments.main.ModeQRCodeFragment
import com.reeman.agv.fragments.main.ModeQRCodeWithMapFragment
import com.reeman.agv.fragments.main.ModeRouteAttributesEditFragment
import com.reeman.agv.fragments.main.ModeRouteFragment
import com.reeman.agv.fragments.main.ModeRoutePointEditFragment
import com.reeman.agv.fragments.main.listener.ModeQRCodeClickListener
import com.reeman.agv.fragments.main.listener.OnGreenButtonClickListener
import com.reeman.agv.presenter.impl.MainPresenter
import com.reeman.agv.request.notifier.Notifier
import com.reeman.agv.request.notifier.NotifyConstant
import com.reeman.agv.utils.DebounceClickListener
import com.reeman.agv.utils.ScreenUtils
import com.reeman.agv.utils.ToastUtils
import com.reeman.agv.utils.VoiceHelper
import com.reeman.agv.widgets.EasyDialog
import com.reeman.agv.widgets.EasyDialog.OnTimeStampListener
import com.reeman.agv.widgets.FloatingCountdown
import com.reeman.commons.board.BoardConstants
import com.reeman.commons.constants.Constants
import com.reeman.commons.event.AndroidNetWorkEvent
import com.reeman.commons.event.ApplyMapEvent
import com.reeman.commons.event.CallingModelDisconnectedEvent
import com.reeman.commons.event.CallingModelReconnectSuccessEvent
import com.reeman.commons.event.CurrentMapEvent
import com.reeman.commons.event.GreenButtonEvent
import com.reeman.commons.event.HostnameEvent
import com.reeman.commons.event.InitiativeLiftingModuleStateEvent
import com.reeman.commons.event.RobotTypeEvent
import com.reeman.commons.event.TimeStampEvent
import com.reeman.commons.eventbus.EventBus
import com.reeman.commons.model.request.Msg
import com.reeman.commons.settings.DispatchSetting
import com.reeman.commons.state.RobotInfo
import com.reeman.commons.state.TaskMode
import com.reeman.commons.utils.ClickHelper
import com.reeman.commons.utils.ClickHelper.OnFastClickListener
import com.reeman.commons.utils.PointUtils
import com.reeman.commons.utils.PrecisionUtils
import com.reeman.commons.utils.SpManager
import com.reeman.commons.utils.TimeUtil
import com.reeman.commons.utils.WIFIUtils
import com.reeman.dao.repository.DbRepository
import com.reeman.dao.repository.entities.CrashNotify
import com.reeman.dao.repository.entities.RouteWithPoints
import com.reeman.dispatch.DispatchManager
import com.reeman.dispatch.constants.ResponseCode
import com.reeman.dispatch.exception.RequestFailureException
import com.reeman.dispatch.model.response.MqttInfo
import com.reeman.points.model.custom.GenericPoint
import com.reeman.points.model.custom.GenericPointsWithMap
import com.reeman.points.model.dispatch.DispatchMapInfo
import com.reeman.points.utils.PointCacheInfo
import com.reeman.ros.ROSController
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber
import java.io.IOException
import java.util.Date
import java.util.Locale


class MainActivity : BaseActivity(), MainContract.View, OnFastClickListener,
    OnMainContentClickListener, DebounceClickListener {
    private var isFirstEnter = true
    private lateinit var presenter: MainPresenter
    private lateinit var tvHostname: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvBattery: TextView
    private lateinit var tvSSID: TextView
    private lateinit var tvCall: TextView
    private lateinit var tvCallButton: TextView
    private lateinit var ivDispatchState: ImageView
    private lateinit var ivHome: AppCompatImageView
    private lateinit var ivTaskWarn: ImageView
    private lateinit var layoutHeader: LinearLayout
    private lateinit var clickHelper: ClickHelper
    private var isAutoWork = false
    private var androidWifiConnectCount = 0

    override fun initData() {
        presenter = MainPresenter(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            ScreenUtils.hideBottomUIMenu(this)
        }
    }

    override fun getLayoutRes(): Int {
        return R.layout.activity_main
    }

    override fun initCustomView() {
        clickHelper = ClickHelper(this)
        ivDispatchState = `$`(R.id.iv_dispatch_state)
        tvHostname = `$`(R.id.tv_hostname)
        tvTime = `$`(R.id.tv_time)
        tvBattery = `$`(R.id.tv_battery)
        tvSSID = `$`(R.id.tv_ssid)
        ivHome = `$`(R.id.iv_home)
        tvCall = `$`(R.id.tv_call)
        layoutHeader = `$`(R.id.layout_header)
        tvCallButton = `$`(R.id.tv_call_button)
        ivTaskWarn = `$`(R.id.iv_task_warn)
        tvHostname.setOnClickListener { clickHelper.fastClick() }
        ivHome.setDebounceClickListener { switchMainContentFragment() }
        ivTaskWarn.setDebounceClickListener {
            val taskWarnTip = ivTaskWarn.tag.toString()
            if (taskWarnTip.isNotBlank()) {
                EasyDialog.getInstance(this).warnError(taskWarnTip)
            }
            ivTaskWarn.apply {
                visibility = View.GONE
                tag = ""
            }
        }
        switchMainContentFragment()
    }

    private fun switchMainContentFragment() {
        ivHome.visibility = View.INVISIBLE
        val mainContentFragment = MainContentFragment(this)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.scale_in, R.anim.scale_out)
            .replace(R.id.main_fragment_view, mainContentFragment).commit()
    }

    private fun switchModeNormalFragment(pointList: List<GenericPoint>) {
        val modeNormalFragment = ModeNormalFragment(pointList, modeNormalClickListener)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.scale_in, R.anim.scale_out)
            .replace(R.id.main_fragment_view, modeNormalFragment).commit()
        ivHome.postDelayed({ ivHome.visibility = View.VISIBLE }, 100)
    }

    private fun switchModeNormalWithMapFragment(
        pointsWithMapList: List<GenericPointsWithMap>
    ) {
        val modeNormalWithMapFragment = ModeNormalWithMapFragment(
            pointsWithMapList,
            modeNormalWithMapClickListener
        )
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.scale_in, R.anim.scale_out)
            .replace(R.id.main_fragment_view, modeNormalWithMapFragment).commit()
        ivHome.postDelayed({ ivHome.visibility = View.VISIBLE }, 100)
    }

    private fun switchModeQRCodeFragment(pointList: List<GenericPoint>) {
        val modeQRCodeFragment = ModeQRCodeFragment(pointList, modeQRCodeClickListener)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.scale_in, R.anim.scale_out)
            .replace(R.id.main_fragment_view, modeQRCodeFragment).commit()
        ivHome.postDelayed({ ivHome.visibility = View.VISIBLE }, 100)
    }

    private fun switchModeQRCodeWithMapFragment(
        pointsWithMapList: List<GenericPointsWithMap>
    ) {
        val modeQRCodeWithMapFragment = ModeQRCodeWithMapFragment(
            pointsWithMapList,
            modeQRCodeClickListener
        )
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.scale_in, R.anim.scale_out)
            .replace(R.id.main_fragment_view, modeQRCodeWithMapFragment).commit()
        ivHome.postDelayed({ ivHome.visibility = View.VISIBLE }, 100)
    }

    private fun switchModeRouteFragment(
        isEditMode: Boolean
    ) {
        val modeRouteFragment = ModeRouteFragment(isEditMode, modeRouteClickListener)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.scale_in, R.anim.scale_out)
            .replace(R.id.main_fragment_view, modeRouteFragment).commit()
        layoutHeader.postDelayed({ layoutHeader.visibility = View.VISIBLE }, 200)
        ivHome.postDelayed({ ivHome.visibility = View.VISIBLE }, 100)

    }

    private fun switchModeRouteEditPointFragment(routeWithPoints: RouteWithPoints) {
        val modeRouteEditPointFragment =
            ModeRoutePointEditFragment(RouteWithPoints(routeWithPoints), modeRoutePointEditListener)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.scale_in, R.anim.scale_out)
            .replace(R.id.main_fragment_view, modeRouteEditPointFragment).commit()
    }

    private fun switchModeRouteAttributesEditFragment(
        routeWithPoints: RouteWithPoints
    ) {
        val modeRouteAttributesEditFragment = ModeRouteAttributesEditFragment(
            routeWithPoints,
            modeRouteAttributesEditClickListener
        )

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.scale_in, R.anim.scale_out)
            .replace(R.id.main_fragment_view, modeRouteAttributesEditFragment).commit()
        layoutHeader.visibility = View.GONE
    }

    private val modeRoutePointEditListener =
        object : ModeRoutePointEditFragment.OnModeRoutePointEditListener {
            override fun onReturnClick(routeWithPoints: RouteWithPoints) {
                switchModeRouteAttributesEditFragment(routeWithPoints)
            }

        }

    private val modeRouteAttributesEditClickListener = object :
        ModeRouteAttributesEditFragment.ModeRouteAttributesEditClickListener {

        override fun onReturnClick() {
            switchModeRouteFragment(true)
        }

        override fun onStartTestClick(routeWithPoints: RouteWithPoints) {
            startRouteTask(routeWithPoints, true)
        }

        override fun onEditPoints(routeWithPoints: RouteWithPoints) {
            switchModeRouteEditPointFragment(routeWithPoints)
        }


    }

    private val modeRouteClickListener = object : ModeRouteFragment.ModeRouteClickListener {

        override fun onGetRouteFailed() {
            switchMainContentFragment()
        }

        override fun onAddClick(routeWithPoints: RouteWithPoints) {
            switchModeRouteAttributesEditFragment(routeWithPoints)
        }

        override fun onEditClick(routeWithPoints: RouteWithPoints) {
            switchModeRouteAttributesEditFragment(routeWithPoints)
        }

        override fun onStart(routeWithPoints: RouteWithPoints) {
            startRouteTask(routeWithPoints, false)
        }
    }

    private fun startRouteTask(routeWithPoints: RouteWithPoints, isTest: Boolean) {
        val modeRouteSetting = RobotInfo.modeRouteSetting
        if (!modeRouteSetting.startTaskCountDownSwitch) {
            if (!canStartTask()) return
            presenter.startRouteModeTask(this@MainActivity, routeWithPoints, isTest)
            return
        }
        if (!canStartTask()) return
        EasyDialog.getInstance(this@MainActivity).warnWithScheduledUpdateDetail(
            getString(
                R.string.text_will_start_task_after_count_down,
                modeRouteSetting.startTaskCountDownTime
            ),
            R.string.text_start_right_now,
            R.string.text_cancel,
            { dialog: Dialog, id: Int ->
                RobotInfo.isCountdownToTask = false
                dialog.dismiss()
                if (id == R.id.btn_confirm) {
                    if (!canStartTask()) return@warnWithScheduledUpdateDetail
                    presenter.startRouteModeTask(this@MainActivity, routeWithPoints, isTest)
                } else {
                    CallingStateManager.setTaskExecutingEvent(TaskExecutingCode.FREE)
                }
            },
            object : OnTimeStampListener {
                override fun onTimestamp(
                    title: TextView,
                    content: TextView,
                    cancelBtn: Button,
                    neutralBtn: Button,
                    confirmBtn: Button,
                    current: Int
                ) {
                    content.text = getString(
                        R.string.text_will_start_task_after_count_down,
                        modeRouteSetting.startTaskCountDownTime - current
                    )
                }

                override fun onTimeOut(dialog: EasyDialog) {
                    dialog.dismiss()
                    RobotInfo.isCountdownToTask = false
                    if (!canStartTask()) return
                    presenter.startRouteModeTask(this@MainActivity, routeWithPoints, isTest)
                }
            },
            1000,
            modeRouteSetting.startTaskCountDownTime * 1000
        )
        RobotInfo.isCountdownToTask = true
        CallingStateManager.setTaskExecutingEvent(TaskExecutingCode.COUNTING_DOWN)
    }


    private val modeQRCodeClickListener = ModeQRCodeClickListener { qrCodeModelList ->
        val modeQRCodeSetting = RobotInfo.modeQRCodeSetting
        if (!modeQRCodeSetting.startTaskCountDownSwitch) {
            startQRCodeModeTask(qrCodeModelList)
            return@ModeQRCodeClickListener
        }
        if (!canStartTask()) return@ModeQRCodeClickListener
        EasyDialog.getInstance(this@MainActivity).warnWithScheduledUpdateDetail(
            getString(
                R.string.text_will_start_task_after_count_down,
                modeQRCodeSetting.startTaskCountDownTime
            ),
            R.string.text_start_right_now,
            R.string.text_cancel,
            { dialog: Dialog, id: Int ->
                RobotInfo.isCountdownToTask = false
                dialog.dismiss()
                if (id == R.id.btn_confirm) {
                    startQRCodeModeTask(qrCodeModelList)
                } else {
                    CallingStateManager.setTaskExecutingEvent(TaskExecutingCode.FREE)
                }
            },
            object : OnTimeStampListener {
                override fun onTimestamp(
                    title: TextView,
                    content: TextView,
                    cancelBtn: Button,
                    neutralBtn: Button,
                    confirmBtn: Button,
                    current: Int
                ) {
                    content.text = getString(
                        R.string.text_will_start_task_after_count_down,
                        modeQRCodeSetting.startTaskCountDownTime - current
                    )
                }

                override fun onTimeOut(dialog: EasyDialog) {
                    RobotInfo.isCountdownToTask = false
                    dialog.dismiss()
                    startQRCodeModeTask(qrCodeModelList)
                }
            },
            1000,
            modeQRCodeSetting.startTaskCountDownTime * 1000
        )
        RobotInfo.isCountdownToTask = true
        CallingStateManager.setTaskExecutingEvent(TaskExecutingCode.COUNTING_DOWN)
    }

    private fun startQRCodeModeTask(
        qrCodeModelList: List<Pair<Pair<String, String>, Pair<String, String>>>
    ) {
        if (!canStartTask()) return
        if (RobotInfo.liftModelState == 1) {
            EasyDialog.getInstance(this@MainActivity)
                .warn(getString(R.string.text_check_altitude_up)) { dialog: Dialog, id: Int ->
                    if (id == R.id.btn_confirm) {
                        ROSController.ioControl(0x4)
                        mHandler.postDelayed({
                            if (RobotInfo.isEmergencyButtonDown) {
                                VoiceHelper.play("voice_scram_stop_turn_on")
                                EasyDialog.getInstance(this@MainActivity)
                                    .warnError(getString(R.string.voice_scram_stop_turn_on))
                                return@postDelayed
                            }
                            ROSController.liftDown()
                            callingInfo.isLifting = true
                            EasyDialog.getLoadingInstance(this@MainActivity)
                                .loading(getString(R.string.text_pickup_model_resetting))
                        }, 200)
                    }
                    dialog.dismiss()
                }
            CallingStateManager.setTaskExecutingEvent(TaskExecutingCode.FREE)
            return
        }
        presenter.startQRCodeModeTask(this@MainActivity, qrCodeModelList)
    }

    private val modeNormalWithMapClickListener = ModeNormalWithMapClickListener { points ->
        val modeNormalSetting = RobotInfo.modeNormalSetting
        if (!modeNormalSetting.startTaskCountDownSwitch) {
            if (!canStartTask()) return@ModeNormalWithMapClickListener
            presenter.startNormalModeTask(this@MainActivity, points)
            return@ModeNormalWithMapClickListener
        }
        if (!canStartTask()) return@ModeNormalWithMapClickListener
        EasyDialog.getInstance(this@MainActivity).warnWithScheduledUpdateDetail(
            getString(
                R.string.text_will_start_task_after_count_down,
                modeNormalSetting.startTaskCountDownTime
            ),
            R.string.text_start_right_now,
            R.string.text_cancel,
            { dialog: Dialog, id: Int ->
                RobotInfo.isCountdownToTask = false
                dialog.dismiss()
                if (id == R.id.btn_confirm) {
                    if (!canStartTask()) return@warnWithScheduledUpdateDetail
                    presenter.startNormalModeTask(this@MainActivity, points)
                } else {
                    CallingStateManager.setTaskExecutingEvent(TaskExecutingCode.FREE)
                }
            },
            object : OnTimeStampListener {
                override fun onTimestamp(
                    title: TextView,
                    content: TextView,
                    cancelBtn: Button,
                    neutralBtn: Button,
                    confirmBtn: Button,
                    current: Int
                ) {
                    content.text = getString(
                        R.string.text_will_start_task_after_count_down,
                        modeNormalSetting.startTaskCountDownTime - current
                    )
                }

                override fun onTimeOut(dialog: EasyDialog) {
                    RobotInfo.isCountdownToTask = false
                    dialog.dismiss()
                    if (!canStartTask()) return
                    presenter.startNormalModeTask(this@MainActivity, points)
                }
            },
            1000,
            modeNormalSetting.startTaskCountDownTime * 1000
        )
        RobotInfo.isCountdownToTask = true
        CallingStateManager.setTaskExecutingEvent(TaskExecutingCode.COUNTING_DOWN)
    }
    private val modeNormalClickListener = ModeNormalClickListener { points ->
        val modeNormalSetting = RobotInfo.modeNormalSetting
        if (!modeNormalSetting.startTaskCountDownSwitch) {
            if (!canStartTask()) return@ModeNormalClickListener
            presenter.startNormalModeTask(this@MainActivity, points)
            return@ModeNormalClickListener
        }
        if (!canStartTask()) return@ModeNormalClickListener
        EasyDialog.getInstance(this@MainActivity).warnWithScheduledUpdateDetail(
            getString(
                R.string.text_will_start_task_after_count_down,
                modeNormalSetting.startTaskCountDownTime
            ),
            R.string.text_start_right_now,
            R.string.text_cancel,
            { dialog: Dialog, id: Int ->
                RobotInfo.isCountdownToTask = false
                dialog.dismiss()
                if (id == R.id.btn_confirm) {
                    if (!canStartTask()) return@warnWithScheduledUpdateDetail
                    presenter.startNormalModeTask(this@MainActivity, points)
                } else {
                    CallingStateManager.setTaskExecutingEvent(TaskExecutingCode.FREE)
                }
            },
            object : OnTimeStampListener {
                override fun onTimestamp(
                    title: TextView,
                    content: TextView,
                    cancelBtn: Button,
                    neutralBtn: Button,
                    confirmBtn: Button,
                    current: Int
                ) {
                    content.text = getString(
                        R.string.text_will_start_task_after_count_down,
                        modeNormalSetting.startTaskCountDownTime - current
                    )
                }

                override fun onTimeOut(dialog: EasyDialog) {
                    RobotInfo.isCountdownToTask = false
                    dialog.dismiss()
                    if (!canStartTask()) return
                    presenter.startNormalModeTask(this@MainActivity, points)
                }
            },
            1000,
            modeNormalSetting.startTaskCountDownTime * 1000
        )
        RobotInfo.isCountdownToTask = true
        CallingStateManager.setTaskExecutingEvent(TaskExecutingCode.COUNTING_DOWN)
    }

    private fun refreshState() {
        tvHostname.text = RobotInfo.ROSHostname
        tvTime.text = TimeUtil.formatHourAndMinute(Date())
        tvBattery.text = String.format(Locale.CHINA,"%s%%", RobotInfo.powerLevel)
        var connectWifiSSID = WIFIUtils.getConnectWifiSSID(this)
        if ("" == connectWifiSSID) connectWifiSSID = getString(R.string.text_not_connected)
        tvSSID.text = connectWifiSSID
        Timber.w("android ip : ${WIFIUtils.getIpAddress(this)}")
    }

    override fun onRestart() {
        super.onRestart()
        isFirstEnter = false
    }

    override fun shouldRegisterDispatchCallback(): Boolean {
        return true
    }

    override fun onResume() {
        super.onResume()
        refreshState()
        ROSController.getHostIP()
        ROSController.modelRequest()
        ROSController.getHostname()
        ROSController.getCurrentMap()
        ROSController.getRobotType()
        if (isFirstEnter) {
            ROSController.heartBeat()
            mHandler.postDelayed(chargeRunnable, 10000)
            CallingStateManager.setTimeTickEvent(System.currentTimeMillis())
        }
        if (!isStart()) {
            try {
                start()
                tvCallButton.visibility = View.VISIBLE
            } catch (e: Exception) {
                tvCallButton.visibility = View.GONE
                Timber.w(e, "打开呼叫串口失败")
            }
        } else {
            tvCallButton.visibility = View.VISIBLE
        }
        updateCallingMap()
        uploadCrashLog()
        if (RobotInfo.isDispatchModeOpened()) {
            val resId = if (DispatchManager.isActive()) {
                R.drawable.icon_dispatch_active
            } else {
                R.drawable.icon_dispatch_inactive
            }
            ivDispatchState.visibility = View.VISIBLE
            ivDispatchState.setImageResource(resId)
        } else {
            ivDispatchState.visibility = View.GONE
        }
        registerObservers()
    }

    private fun registerObservers() {
        EventBus.registerObserver(this, object : EventBus.EventObserver<AndroidNetWorkEvent> {
            override fun onEvent(event: AndroidNetWorkEvent) {
                onAndroidNetworkChangeEvent(event)
            }
        })
        EventBus.registerObserver(this, object : EventBus.EventObserver<GreenButtonEvent> {
            override fun onEvent(event: GreenButtonEvent) {
                onGreenButtonEvent(event)
            }
        })
        EventBus.registerObserver(this, object : EventBus.EventObserver<MqttConnectionEvent> {
            override fun onEvent(event: MqttConnectionEvent) {
                OnMqttConnectEvent(event)
            }
        })
    }

    override fun onPause() {
        super.onPause()
        mHandler.removeCallbacks(connectMqttRunnable)
    }

    override fun onTaskFailureTipDismiss() {
        super.onTaskFailureTipDismiss()
        initDispatch()
    }

    override fun onCurrentMapEvent(event: CurrentMapEvent) {
        Timber.w("onCurrentMapEvent,resultCode: $resultCode , active state: ${DispatchManager.isActive()}")
        super.onCurrentMapEvent(event)
        if (resultCode !in listOf(0, 1)) return
        initDispatch()
    }

    private fun initDispatch() {
        if (RobotInfo.isDispatchModeOpened() && !DispatchManager.isActive()) {
            val dispatchParamErrorTip = Errors.getDispatchParamError(this, RobotInfo.dispatchSetting)
            if (!dispatchParamErrorTip.isNullOrBlank()) {
                Timber.w("dispatch param error: $dispatchParamErrorTip")
                EasyDialog.getInstance(this).confirm(
                    getString(R.string.text_dispatch_setting),
                    getString(R.string.text_close_dispatch_mode),
                    dispatchParamErrorTip
                ) { dialog, id ->
                    dialog.dismiss()
                    if (id == R.id.btn_confirm) {
                        startActivity(Intent(this, SettingActivity::class.java).apply { putExtra(Constants.SETTING_PAGE, SettingPage.PAGE_DISPATCH_SETTING) })
                    } else {
                        closeDispatchMode()
                    }
                }
                return
            }
            RobotInfo.dispatchSetting.let { dispatchSetting ->
                DispatchManager.init(dispatchSetting.serverAddress, dispatchSetting.roomName, dispatchSetting.roomPwd)
                DispatchManager.robotOnline(this)
                EasyDialog.getLoadingInstance(this).loading(getString(R.string.text_check_dispatch_mode_opened_and_go_online))
            }
        }
    }

    private fun showNoneInitLocation() {
        EasyDialog.getInstance(this).confirm(
            getString(R.string.text_dispatch_setting),
            getString(R.string.text_close_dispatch_mode),
            getString(R.string.text_go_online_failure_cause_none_init_location)
        ) { dialog, id ->
            dialog.dismiss()
            if (id == R.id.btn_confirm) {
                startActivity(Intent(this, SettingActivity::class.java).apply { putExtra(Constants.SETTING_PAGE, SettingPage.PAGE_DISPATCH_SETTING) })
            } else {
                closeDispatchMode()
            }
        }
    }

    override fun onOnlineSuccess(mapsInfo: MutableList<DispatchMapInfo>, mqttInfo: MqttInfo) {
        super.onOnlineSuccess(mapsInfo, mqttInfo)
        val dispatchSetting = RobotInfo.dispatchSetting

        fun updateDispatchSetting(update: DispatchSetting.() -> Unit) {
            dispatchSetting.apply {
                update()
                Timber.w("修改调度设置: $this")
                SpManager.getInstance().edit().putString(Constants.KEY_DISPATCH_SETTING, Gson().toJson(this)).apply()
            }
        }
        mapsInfo.first().let { mapInfo ->

            val pointType = setOf(GenericPoint.PRODUCT, GenericPoint.CHARGE)
            if (mapInfo.pointList.none { it.type in pointType && it.name == dispatchSetting.initPoint }) {
                if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
                DispatchManager.release()
                showNoneInitLocation()
                updateDispatchSetting {
                    this.initMap = ""
                    this.initPoint = ""
                }
                return
            }
            if (dispatchSetting.run { this.chargePointMap.isNotBlank() && this.chargePoint.isNotBlank() } && mapInfo.pointList.none { it.type == GenericPoint.CHARGE && it.name == dispatchSetting.chargePoint }) {
                updateDispatchSetting {
                    this.chargePointMap = ""
                    this.chargePoint = ""
                }
            }
            PointCacheInfo.addDispatchPoints(mapInfo)
        }
        if (EasyDialog.isShow()) EasyDialog.getInstance().updateLoadingMessage(getString(R.string.text_online_success_and_start_make_mqtt_connection))
        DispatchManager.connectMqtt(mqttInfo)
    }

    override fun onOnlineFailure(throwable: Throwable) {
        super.onOnlineFailure(throwable)
        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
        when (throwable) {
            is IOException -> {
                EasyDialog.getInstance(this).confirm(
                    getString(R.string.text_check_network),
                    getString(R.string.text_close_dispatch_mode),
                    getString(R.string.text_go_online_failure_cause_network)
                ) { dialog, id ->
                    dialog.dismiss()
                    if (id == R.id.btn_confirm) {
                        startActivity(Intent(this, WiFiConnectActivity::class.java))
                    } else {
                        closeDispatchMode()
                    }
                }
            }

            is RequestFailureException -> {
                val tip = when (throwable.code) {
                    ResponseCode.UNAUTHORIZED, ResponseCode.PARSE_TOKEN_FAILED_ERROR -> getString(R.string.text_go_online_failure_cause_unauthorized)
                    ResponseCode.MAP_LIST_EMPTY_ERROR -> getString(R.string.text_go_online_failure_cause_no_map)
                    ResponseCode.MAP_NOT_FOUND_ERROR -> getString(R.string.text_go_online_failure_cause_current_map_not_in_server)
                    //                ResponseCode.ELEVATOR_SETTING_ERROR->{}// TODO:  添加梯控后处理
                    ResponseCode.ROBOT_NOT_BOUND_TO_ROOM_ERROR -> getString(R.string.text_go_online_failure_cause_not_bound_to_room)
                    else -> getString(R.string.text_go_online_failure_cause_undefined_error, throwable.code, throwable.msg)
                }
                EasyDialog.getInstance(this).confirm(
                    getString(R.string.text_dispatch_setting),
                    getString(R.string.text_close_dispatch_mode),
                    tip
                ) { dialog, id ->
                    dialog.dismiss()
                    if (id == R.id.btn_confirm) {
                        startActivity(Intent(this, SettingActivity::class.java).apply { putExtra(Constants.SETTING_PAGE, SettingPage.PAGE_DISPATCH_SETTING) })
                    } else {
                        closeDispatchMode()
                    }
                }
            }

            else -> {
                EasyDialog.getInstance(this).confirm(
                    getString(R.string.text_dispatch_setting),
                    getString(R.string.text_close_dispatch_mode),
                    getString(R.string.text_go_online_failure_cause_unknown_exception)
                ) { dialog, id ->
                    dialog.dismiss()
                    if (id == R.id.btn_confirm) {
                        startActivity(Intent(this, SettingActivity::class.java).apply { putExtra(Constants.SETTING_PAGE, SettingPage.PAGE_DISPATCH_SETTING) })
                    } else {
                        closeDispatchMode()
                    }
                }
            }
        }
    }

    override fun onMqttConnectSuccess() {
        super.onMqttConnectSuccess()
        if (EasyDialog.isShow()) EasyDialog.getInstance().updateLoadingMessage(getString(R.string.text_make_mqtt_connection_success_and_start_subscribe_topic))
    }

    override fun onMqttConnectFailure(throwable: Throwable) {
        super.onMqttConnectFailure(throwable)
        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
        EasyDialog.getInstance(this).confirm(
            getString(R.string.text_check_network),
            getString(R.string.text_close_dispatch_mode),
            getString(R.string.text_mqtt_connect_failure),
        ) { dialog, id ->
            dialog.dismiss()
            if (id == R.id.btn_confirm) {
                startActivity(Intent(this, WiFiConnectActivity::class.java))
            } else {
                closeDispatchMode()
            }
        }
    }

    override fun onMqttSubscribeFailure(throwable: Throwable?) {
        super.onMqttSubscribeFailure(throwable)
        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
        EasyDialog.getInstance(this).confirm(
            getString(R.string.text_check_network),
            getString(R.string.text_close_dispatch_mode),
            getString(R.string.text_mqtt_subscribe_failure),
        ) { dialog, id ->
            dialog.dismiss()
            if (id == R.id.btn_confirm) {
                startActivity(Intent(this, WiFiConnectActivity::class.java))
            } else {
                closeDispatchMode()
            }
        }
    }

    override fun onMqttSubscribeSuccess() {
        super.onMqttSubscribeSuccess()
        ivDispatchState.setImageResource(R.drawable.icon_dispatch_active)
        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
        ToastUtils.showShortToast(getString(R.string.text_mqtt_subscribe_success))
        val initLocation = PointCacheInfo.getPointByName(RobotInfo.dispatchSetting.initPoint)
        if (initLocation == null) {
            DispatchManager.release()
            showNoneInitLocation()
        } else {
            RobotInfo.currentPosition?.let { currentPosition ->
                val distance = PointUtils.calculateDistance(currentPosition, initLocation.position)
                val angle = PointUtils.calculateAngle(currentPosition, initLocation.position)
                if (distance > 1 || angle > 30) {
                    Timber.w("上线位置: $initLocation, 当前位置: ${currentPosition.contentToString()}")
                    showPositioningError(initLocation, distance, angle)
                }
            }
        }
    }

    private fun showPositioningError(
        initLocation: GenericPoint, distance: Double, angle: Double
    ) {
        EasyDialog.getInstance(this)
            .confirm(
                getString(R.string.text_relocate),
                getString(R.string.text_cancel),
                getString(R.string.text_check_position_deviate_from_init_location, PrecisionUtils.setScale(distance).toString(), PrecisionUtils.setScale(angle).toString(), initLocation.name)
            ) { dialog, id ->
                dialog.dismiss()
                if (id == R.id.btn_confirm) {
                    EasyDialog.getLoadingInstance(this).loading(getString(R.string.text_relocating))
                    RobotInfo.isRelocatingAfterInitDispatch = true
                    ROSController.relocateByCoordinate(initLocation.position)
                }
            }
    }

    override fun onCustomInitPose(currentPosition: DoubleArray) {
        super.onCustomInitPose(currentPosition)
        if (RobotInfo.isRelocatingAfterInitDispatch) {
            Timber.w("重定位后坐标: ${currentPosition.contentToString()}")
            if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
            RobotInfo.isRelocatingAfterInitDispatch = false
            val initLocation = PointCacheInfo.getPointByName(RobotInfo.dispatchSetting.initPoint)!!
            val distance = PointUtils.calculateDistance(currentPosition, initLocation.position)
            val angle = PointUtils.calculateAngle(currentPosition, initLocation.position)
            if (distance > 1 || angle > 30) {
                showPositioningError(initLocation, distance, angle)
            } else {
                ToastUtils.showShortToast(getString(R.string.text_relocate_success))
            }
        }
    }

    override fun onMqttReconnected() {
        super.onMqttReconnected()
        ivDispatchState.setImageResource(R.drawable.icon_dispatch_active)
        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
        ToastUtils.showShortToast(getString(R.string.text_dispatch_mqtt_reconnect_success))
    }


    override fun onMqttDisconnect(isRetry: Boolean, reconnectCount: Int, throwable: Throwable) {
        super.onMqttDisconnect(isRetry, reconnectCount, throwable)
        ivDispatchState.setImageResource(R.drawable.icon_dispatch_inactive)
        if (!isRetry) {
            DispatchManager.release()
            if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
            val currentFragment = supportFragmentManager.findFragmentById(R.id.main_fragment_view)
            if (currentFragment !is MainContentFragment) {
                switchMainContentFragment()
            }
            EasyDialog.getInstance(this).neutral(
                getString(R.string.text_retry),
                getString(R.string.text_check_network),
                getString(R.string.text_close_dispatch_mode),
                getString(R.string.text_disconnect_from_dispatch_server)
            ) { dialog, id ->
                dialog.dismiss()
                when (id) {
                    R.id.btn_confirm -> {
                        DispatchManager.robotOnline(this)
                        EasyDialog.getLoadingInstance(this).loading(getString(R.string.text_retry_go_online))
                    }

                    R.id.btn_neutral -> startActivity(Intent(this, WiFiConnectActivity::class.java))

                    else -> closeDispatchMode()
                }
            }
            return
        }
        if (reconnectCount == 1) {
            if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
            EasyDialog.getLoadingInstance(this).loading(getString(R.string.text_mqtt_disconnect_and_reconnecting))
        }
    }

    override fun onMapUpdate() {
        super.onMapUpdate()
        ivDispatchState.setImageResource(R.drawable.icon_dispatch_inactive)
        val currentFragment = supportFragmentManager.findFragmentById(R.id.main_fragment_view)
        if (currentFragment !is MainContentFragment) {
            switchMainContentFragment()
        }
        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
        DispatchManager.robotOnline(this)
        EasyDialog.getLoadingInstance(this).loading(getString(R.string.text_rooms_map_update_retry_go_online))
        ToastUtils.showShortToast(getString(R.string.text_rooms_map_update_retry_go_online))
    }

    override fun onConfigUpdate() {
        super.onConfigUpdate()
        ivDispatchState.setImageResource(R.drawable.icon_dispatch_inactive)
        val currentFragment = supportFragmentManager.findFragmentById(R.id.main_fragment_view)
        if (currentFragment !is MainContentFragment) {
            switchMainContentFragment()
        }
        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
        DispatchManager.robotOnline(this)
        EasyDialog.getLoadingInstance(this).loading(getString(R.string.text_rooms_config_update_retry_go_online))
        ToastUtils.showShortToast(getString(R.string.text_rooms_config_update_retry_go_online))
    }

    private fun closeDispatchMode() {
        RobotInfo.dispatchSetting.apply {
            isOpened = false
            SpManager.getInstance().edit().putString(Constants.KEY_DISPATCH_SETTING, gson.toJson(this)).apply()
        }
        ivDispatchState.visibility = View.GONE
        ToastUtils.showShortToast(getString(R.string.text_already_close_dispatch_mode))
        val currentFragment = supportFragmentManager.findFragmentById(R.id.main_fragment_view)
        if (currentFragment !is MainContentFragment) {
            switchMainContentFragment()
        }
    }


    override fun shouldResponse2TimeEvent(): Boolean {
        return true
    }

    override fun shouldResponseCallingEvent(): Boolean {
        return true
    }

    override fun onCustomBatteryChange(level: Int) {
        super.onCustomBatteryChange(level)
        tvBattery.text = String.format(Locale.CHINA,"%s%%", level)
    }

    override fun onCustomTimeStamp(event: TimeStampEvent) {
        tvTime.text = TimeUtil.formatHourAndMinute(Date())
        val commutingTimeSetting = RobotInfo.commutingTimeSetting
        if (!RobotInfo.isCountdownToTask && !FloatingCountdown.isShow() && commutingTimeSetting.open && !RobotInfo.isEmergencyButtonDown && !RobotInfo.isACCharging) {
            if (TimeUtil.isCurrentInTimeScope(
                    commutingTimeSetting.workingTime,
                    commutingTimeSetting.afterWorkTime
                )
            ) {
                if (RobotInfo.isWirelessCharging && RobotInfo.powerLevel >= commutingTimeSetting.autoWorkPower) {
                    if (RobotInfo.isDispatchModeOpened()) {
                        if (DispatchManager.isActive()) {
                            startTask(false)
                        }
                        return
                    }
                    hideChargingView()
                    EasyDialog.getLoadingInstance(this)
                        .loading(getString(R.string.text_init_product_point))
                    isAutoWork = true
                    VoiceHelper.play("voice_work_start_and_go_to_product_point") {
                        presenter.refreshProductModePoint(
                            this@MainActivity
                        )
                    }
                    return
                }
            } else {
                if (!RobotInfo.isCharging && RobotInfo.chargeFailedCount < 2) {
                    if (RobotInfo.isDispatchModeOpened()) {
                        if (DispatchManager.isActive()) {
                            startTask(true)
                        }
                        return
                    }
                    EasyDialog.getLoadingInstance(this)
                        .loading(getString(R.string.text_init_charging_point))
                    isAutoWork = true
                    VoiceHelper.play("voice_work_end_and_go_to_charge") {
                        presenter.refreshChargeModePoint(
                            this@MainActivity
                        )
                    }
                    return
                }
            }
        }
        super.onCustomTimeStamp(event)
    }

    override fun onCustomEmergencyStopStateChange(emergencyStopState: Int) {
        super.onCustomEmergencyStopStateChange(emergencyStopState)
        if (RobotInfo.isRebootingROSCauseTimeJump) return
        if (RobotInfo.isLifting && emergencyStopState == 0) {
            if ((!RobotInfo.isSelfChecking || !RobotInfo.isMapping) && EasyDialog.isShow()) EasyDialog.getInstance()
                .dismiss()
        } else if (emergencyStopState == 1) {
            if (RobotInfo.isSpaceShip() && RobotInfo.isLiftModelInstalled) {
                ROSController.getAltitudeState()
            }
        }
    }

    override fun onUpdateTaskWarnTip(tip: String) {
        ivTaskWarn.visibility = View.VISIBLE
        ivTaskWarn.tag = tip
    }

    override fun onTimeJumpRelocationFailure() {
        super.onTimeJumpRelocationFailure()
        if (robotInfo.isDispatchModeOpened()) {
            ivDispatchState.setImageResource(R.drawable.icon_dispatch_inactive)
            EasyDialog.getInstance(this).neutral(
                getString(R.string.text_goto_reloc),
                getString(R.string.text_close_dispatch_mode),
                getString(R.string.text_cancel),
                getString(R.string.text_time_jump_reloc_failure_please_pull_to_init_location_reloc_manually),
            ) { dialog, id ->
                dialog.dismiss()
                when (id) {
                    R.id.btn_confirm -> startActivity(Intent(this, SettingActivity::class.java).apply { putExtra(Constants.SETTING_PAGE, SettingPage.PAGE_DISPATCH_SETTING) })
                    R.id.btn_neutral -> closeDispatchMode()
                    else -> {}
                }
            }
        } else {
            EasyDialog.getInstance(this).confirm(
                getString(R.string.text_goto_reloc),
                getString(R.string.text_cancel),
                getString(R.string.text_time_jump_reloc_failure_please_pull_to_charge_point_reloc_manually),
            ) { dialog, id ->
                dialog.dismiss()
                if (id == R.id.btn_confirm) {
                    startActivity(Intent(this, SettingActivity::class.java).apply { putExtra(Constants.SETTING_PAGE, SettingPage.PAGE_BASIC_SETTING) })
                }
            }
        }
    }

    override fun onTimeJumpRelocationSuccess() {
        super.onTimeJumpRelocationSuccess()
        ToastUtils.showShortToast(getString(R.string.text_relocate_success))
    }

    override fun onRobotTypeEvent(event: RobotTypeEvent) {
        RobotInfo.robotType = event.robotType
        callingInfo.heartBeatInfo.robotType = event.robotType
        if (event.robotType in setOf(4, 6, 7, 10, 12)) {
            val liftModelInstalled =
                SpManager.getInstance().getBoolean(Constants.KEY_LIFT_MODEL_INSTALLATION, true)
            RobotInfo.isLiftModelInstalled = liftModelInstalled
            if (liftModelInstalled) {
                mHandler.postDelayed({ ROSController.getAltitudeState() }, 200)
            }
        } else {
            RobotInfo.isLiftModelInstalled = false
        }
    }

    override fun onApplyMapEvent(event: ApplyMapEvent) {
        if (RobotInfo.currentMapEvent.map == event.map && !RobotInfo.isRebootingROSCauseTimeJump) {
            ROSController.resetAllROSParameter()
            ROSController.positionAutoUploadControl(true)
            return
        }
        super.onApplyMapEvent(event)
        val currentFragment = supportFragmentManager.findFragmentById(R.id.main_fragment_view)
        if (RobotInfo.isRebootingROSCauseTimeJump) return
        if (RobotInfo.isDispatchModeOpened()) {
            ivDispatchState.setImageResource(R.drawable.icon_dispatch_inactive)
            if (currentFragment !is MainContentFragment) {
                switchMainContentFragment()
            }
            DispatchManager.release()
            if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
            EasyDialog.getInstance(this).confirm(
                getString(R.string.text_retry),
                getString(R.string.text_close_dispatch_mode),
                getString(R.string.text_check_map_change_please_retry_go_online)
            ) { dialog, id ->
                dialog.dismiss()
                if (id == R.id.btn_confirm) {
                    DispatchManager.robotOnline(this)
                    EasyDialog.getLoadingInstance(this).loading(getString(R.string.text_retry_go_online))
                } else {
                    closeDispatchMode()
                }
            }
            return
        }
        if (currentFragment !is MainContentFragment) {
            switchMainContentFragment()
            if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
            EasyDialog.getInstance(this)
                .warnError(getString(R.string.text_check_apply_map_please_reload_points))
        }
    }

    override fun onHostNameEvent(event: HostnameEvent) {
        tvHostname.text = event.hostname
        RobotInfo.ROSHostname = event.hostname
        callingInfo.heartBeatInfo.hostname = event.hostname
        mHandler.postDelayed(connectMqttRunnable, 500)
    }

    private fun onAndroidNetworkChangeEvent(event: AndroidNetWorkEvent) {
        when (event.intent.action) {
            WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                val info =
                    event.intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                if (info?.state == NetworkInfo.State.DISCONNECTED) {
                    Timber.v("DISCONNECTED")
                    refreshState()
                    if (DispatchManager.isActive()) {
                        DispatchManager.release()
                        ivDispatchState.setImageResource(R.drawable.icon_dispatch_inactive)
                        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
                        EasyDialog.getInstance(this).confirm(
                            getString(R.string.text_check_network),
                            getString(R.string.text_close_dispatch_mode),
                            getString(R.string.text_check_wifi_disconnected_and_offline_from_dispatch_server)
                        ) { dialog, id ->
                            dialog.dismiss()
                            if (id == R.id.btn_confirm) {
                                startActivity(Intent(this, WiFiConnectActivity::class.java))
                            } else {
                                closeDispatchMode()
                            }
                        }
                    }
                } else if (info?.state == NetworkInfo.State.CONNECTED) {
                    Timber.v("CONNECTED")
                    if (++androidWifiConnectCount >= BoardConstants.WIFI_CONNECT_THRESHOLD) {
                        androidWifiConnectCount = 0
                        mHandler.postDelayed(connectMqttRunnable, 3000)
                        refreshState()
                    }
                }
            }

            WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                val wifiState = event.intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0)
                if (wifiState == WifiManager.WIFI_STATE_DISABLED) {
                    refreshState()
                }
            }
        }
    }

    override fun onNormalModePointsDataLoadSuccess(pointList: List<GenericPoint>) {
        if (EasyDialog.isShow() && EasyDialog.getInstance().isAutoDismissEnable) EasyDialog.getInstance()
            .dismiss()
        switchModeNormalFragment(pointList)
    }

    override fun onNormalModeMapsWithPointsDataLoadSuccess(
        pointsWithMapList: List<GenericPointsWithMap>
    ) {
        if (EasyDialog.isShow() && EasyDialog.getInstance().isAutoDismissEnable) EasyDialog.getInstance()
            .dismiss()
        switchModeNormalWithMapFragment(pointsWithMapList)
    }

    override fun onRouteModelDataLoadSuccess(pointList: List<GenericPoint>) {
        if (EasyDialog.isShow() && EasyDialog.getInstance().isAutoDismissEnable) EasyDialog.getInstance()
            .dismiss()
        switchModeRouteFragment(false)
    }

    override fun onQRCodeModelDataLoadSuccess(pointList: List<GenericPoint>) {
        if (EasyDialog.isShow() && EasyDialog.getInstance().isAutoDismissEnable) EasyDialog.getInstance()
            .dismiss()
        switchModeQRCodeFragment(pointList)
    }

    override fun onQRCodeModelMapsWithPointsDataLoadSuccess(
        pointsWithMapList: List<GenericPointsWithMap>
    ) {
        if (EasyDialog.isShow() && EasyDialog.getInstance().isAutoDismissEnable) EasyDialog.getInstance()
            .dismiss()
        switchModeQRCodeWithMapFragment(pointsWithMapList)
    }

    override fun onChargeModelDataLoadSuccess() {
        if (EasyDialog.isShow() && EasyDialog.getInstance().isAutoDismissEnable) EasyDialog.getInstance()
            .dismiss()
        startTask(true)
    }

    override fun onProductModelDataLoadSuccess() {
        if (EasyDialog.isShow() && EasyDialog.getInstance().isAutoDismissEnable) EasyDialog.getInstance()
            .dismiss()
        startTask(false)
    }

    override fun onDataLoadFailed(errorTip: String) {
        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
        EasyDialog.getInstance(this).warnError(errorTip)
    }

    override fun onDataLoadFailed(throwable: Throwable) {
        Timber.w(throwable, "拉取点位失败")
        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
        EasyDialog.getInstance(this).warnError(Errors.getDataLoadFailedTip(this, throwable))
    }

    override fun onFastClick() {
        EasyDialog.getInstance(this)
            .confirm(getString(R.string.text_exit_app)) { dialog: Dialog, id: Int ->
                dialog.dismiss()
                if (id == R.id.btn_confirm) {
                    Timber.w("退出应用")
                    mApp.exit()
                }
            }
    }

    override fun onModeClick(mode: TaskMode) {
        if (RobotInfo.isDispatchModeOpened() && !DispatchManager.isActive()) {
            EasyDialog.getInstance(this).neutral(
                getString(R.string.text_reonline),
                getString(R.string.text_dispatch_setting),
                getString(R.string.text_cancel),
                getString(R.string.text_offline_from_dispatch_server_cannot_start_task)
            ) { dialog, id ->
                dialog.dismiss()
                if (id == R.id.btn_confirm) {
                    DispatchManager.robotOnline(this)
                    EasyDialog.getLoadingInstance(this).loading(getString(R.string.text_retry_go_online))
                } else if (id == R.id.btn_neutral) {
                    startActivity(Intent(this, SettingActivity::class.java).apply { putExtra(Constants.SETTING_PAGE, SettingPage.PAGE_DISPATCH_SETTING) })
                }
            }
            return
        }
        when (mode) {
            TaskMode.MODE_NORMAL -> {
                if (RobotInfo.isDispatchModeOpened()) {
                    val pointList = PointCacheInfo.getPointByType(listOf(GenericPoint.DELIVERY))
                    switchModeNormalFragment(pointList)
                    return
                }
                presenter.refreshNormalModePoints(this)
            }

            TaskMode.MODE_START_POINT -> {
                isAutoWork = false
                if (RobotInfo.isDispatchModeOpened()) {
                    startTask(false)
                    return
                }
                presenter.refreshProductModePoint(this)
            }

            TaskMode.MODE_CHARGE -> {
                isAutoWork = false
                if (RobotInfo.isDispatchModeOpened()) {
                    startTask(true)
                    return
                }
                presenter.refreshChargeModePoint(this)
            }

            TaskMode.MODE_ROUTE -> {
                if (RobotInfo.isDispatchModeOpened()) {
                    val pointTypes = if (RobotInfo.isSpaceShip()) {
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
                    val pointList = PointCacheInfo.getPointByType(pointTypes)
                    PointCacheInfo.routeModelPoints.clear()
                    PointCacheInfo.routeModelPoints.addAll(pointList)
                    switchModeRouteFragment(false)
                    return
                }
                presenter.refreshRouteModePoints(this)
            }

            TaskMode.MODE_QRCODE -> {
                if (!RobotInfo.isSpaceShip()) {
                    EasyDialog.getInstance(this).warnError(getString(R.string.text_robot_type_not_support_qr_code_mode))
                    return
                }
                if (RobotInfo.isDispatchModeOpened()) {
                    val pointList = PointCacheInfo.getPointByType(listOf(GenericPoint.AGV_TAG))
                    switchModeQRCodeFragment(pointList)
                    return
                }
                presenter.refreshQRCodeModePoints(this)
            }

            else -> {
                Timber.d("unknown task mode")
            }
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_F2) {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.main_fragment_view)
            if (currentFragment is OnGreenButtonClickListener) {
                currentFragment.onKeyUpEvent()
            }
            return false
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun onGreenButtonEvent(event: GreenButtonEvent) {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.main_fragment_view)
        if (currentFragment is OnGreenButtonClickListener) {
            currentFragment.onKeyUpEvent()
        }
    }

    /**
     * 开始任务
     */
    private fun startTask(isChargePoint: Boolean) {
        val returningSetting = RobotInfo.returningSetting
        if (!returningSetting.startTaskCountDownSwitch) {
            if (!canStartTask()) return
            if (isChargePoint) {
                presenter.gotoChargePoint(this, isAutoWork)
            } else {
                presenter.gotoProductPoint(this, isAutoWork)
            }
            return
        }
        if (!canStartTask()) return
        EasyDialog.getInstance(this@MainActivity).warnWithScheduledUpdateDetail(
            getString(
                if (isChargePoint) R.string.text_will_goto_charging_pile_after_count_down else R.string.text_will_goto_production_point_after_count_down,
                returningSetting.startTaskCountDownTime
            ),
            R.string.text_start_right_now,
            R.string.text_cancel,
            { dialog: Dialog, id: Int ->
                RobotInfo.isCountdownToTask = false
                dialog.dismiss()
                if (id == R.id.btn_confirm) {
                    if (!canStartTask()) return@warnWithScheduledUpdateDetail
                    if (isChargePoint) {
                        presenter.gotoChargePoint(this, isAutoWork)
                    } else {
                        presenter.gotoProductPoint(this, isAutoWork)
                    }
                } else {
                    CallingStateManager.setTaskExecutingEvent(TaskExecutingCode.FREE)
                }
            },
            object : OnTimeStampListener {
                override fun onTimestamp(
                    title: TextView,
                    content: TextView,
                    cancelBtn: Button,
                    neutralBtn: Button,
                    confirmBtn: Button,
                    current: Int
                ) {
                    content.text = getString(
                        if (isChargePoint) R.string.text_will_goto_charging_pile_after_count_down else R.string.text_will_goto_production_point_after_count_down,
                        returningSetting.startTaskCountDownTime - current
                    )
                }

                override fun onTimeOut(dialog: EasyDialog) {
                    RobotInfo.isCountdownToTask = false
                    dialog.dismiss()
                    if (!canStartTask()) return
                    if (isChargePoint) {
                        presenter.gotoChargePoint(this@MainActivity, isAutoWork)
                    } else {
                        presenter.gotoProductPoint(this@MainActivity, isAutoWork)
                    }
                }
            },
            1000,
            returningSetting.startTaskCountDownTime * 1000
        )
        RobotInfo.isCountdownToTask = true
        CallingStateManager.setTaskExecutingEvent(TaskExecutingCode.COUNTING_DOWN)
    }

    private fun canStartTask(): Boolean {
        if (RobotInfo.isEmergencyButtonDown) {
            EasyDialog.getInstance(this).warnError(getString(R.string.voice_scram_stop_turn_on))
            VoiceHelper.play("voice_scram_stop_turn_on")
            CallingStateManager.setTaskExecutingEvent(TaskExecutingCode.FREE)
            return false
        }
        if (RobotInfo.isACCharging) {
            VoiceHelper.play("voice_charging_and_can_not_move")
            EasyDialog.getInstance(this)
                .warnError(getString(R.string.voice_charging_and_can_not_move))
            CallingStateManager.setTaskExecutingEvent(TaskExecutingCode.FREE)
            return false
        }
        Errors.getSensorError(this, RobotInfo.lastSensorsData)?.let {
            EasyDialog.getInstance(this).warnError(it)
            return false
        }
        if (RobotInfo.isLifting) {
            EasyDialog.getInstance(this)
                .warn(getString(R.string.text_check_altitude_moving_click_confirm_to_continue)) { dialog: Dialog, did: Int ->
                    if (did == R.id.btn_confirm) {
                        ROSController.ioControl(4)
                        mHandler.postDelayed({
                            if (RobotInfo.isEmergencyButtonDown) {
                                VoiceHelper.play("voice_scram_stop_turn_on")
                                EasyDialog.getInstance(this)
                                    .warnError(getString(R.string.voice_scram_stop_turn_on))
                                return@postDelayed
                            }
                            if (RobotInfo.isCharging) {
                                EasyDialog.getInstance(this)
                                    .warnError(getString(R.string.voice_charging_and_can_not_move))
                                return@postDelayed
                            }
                            if (RobotInfo.liftModelState == 0) {
                                ROSController.liftDown()
                            } else {
                                ROSController.liftUp()
                            }
                            callingInfo.isLifting = true
                            EasyDialog.getLoadingInstance(this)
                                .loading(getString(R.string.text_pickup_model_resetting))
                        }, 200)
                    }
                    dialog.dismiss()
                }
            CallingStateManager.setTaskExecutingEvent(TaskExecutingCode.FREE)
            return false
        }
        return true
    }

    private fun OnMqttConnectEvent(event: MqttConnectionEvent) {
        tvCall.visibility =
            if (event.isConnected) View.VISIBLE else View.GONE
    }

    override fun onInitiativeLiftingModuleStateEvent(event: InitiativeLiftingModuleStateEvent) {
        super.onInitiativeLiftingModuleStateEvent(event)
        Timber.w(
            "是否安装顶升模块: %s ,顶升模块状态: 动作 : %s , 状态 : %s",
            RobotInfo.isLiftModelInstalled,
            if (event.action == 1) "上升" else "下降",
            if (event.state == 1) "完成" else "未完成"
        )
        Timber.w(
            "dialogIsShow : %s , dialogIsAutoDismissEnable : %s , taskAbnormalFinishPrompt : %s",
            EasyDialog.isShow(),
            EasyDialog.isShow() && EasyDialog.getInstance().isAutoDismissEnable,
            RobotInfo.taskAbnormalFinishPrompt
        )
        if (!RobotInfo.isLiftModelInstalled || !RobotInfo.isSpaceShip()) return
        RobotInfo.isLifting = event.state == 0
        RobotInfo.liftModelState = event.action
        callingInfo.isLifting = event.state == 0
        Timber.i(
            "isEmergencyButtonDown : %s, isRouteTaskWaitingForStart : %s",
            RobotInfo.isEmergencyButtonDown,
            isRouteTaskWaitingForStart
        )
        if (RobotInfo.isEmergencyButtonDown || isRouteTaskWaitingForStart || RobotInfo.isCharging) return
        val currentFragment = supportFragmentManager.findFragmentById(R.id.main_fragment_view)
        if (currentFragment !is ModeQRCodeFragment && currentFragment !is ModeQRCodeWithMapFragment) {
            if (EasyDialog.isShow() && !EasyDialog.getInstance().isTiming && RobotInfo.taskAbnormalFinishPrompt == null) EasyDialog.getInstance()
                .dismiss()
            return
        }
        if (EasyDialog.isShow()) {
            if (!EasyDialog.getInstance().isAutoDismissEnable) {
                return
            }
            EasyDialog.getInstance().dismiss()
        }
        if (event.action == 1) {
            EasyDialog.getInstance(this)
                .warn(getString(R.string.text_check_altitude_up)) { dialog: Dialog, id: Int ->
                    if (id == R.id.btn_confirm) {
                        ROSController.ioControl(4)
                        mHandler.postDelayed({
                            if (RobotInfo.isEmergencyButtonDown) {
                                VoiceHelper.play("voice_scram_stop_turn_on")
                                EasyDialog.getInstance(this)
                                    .warnError(getString(R.string.voice_scram_stop_turn_on))
                                return@postDelayed
                            }
                            ROSController.liftDown()
                            callingInfo.isLifting = true
                            EasyDialog.getLoadingInstance(this)
                                .loading(getString(R.string.text_pickup_model_resetting))
                        }, 200)
                    }
                    dialog.dismiss()
                }
            return
        }
        if (event.state == 0) {
            EasyDialog.getInstance(this)
                .warn(getString(R.string.text_check_altitude_moving)) { dialog: Dialog, id: Int ->
                    if (id == R.id.btn_confirm) {
                        ROSController.ioControl(4)
                        mHandler.postDelayed({
                            if (RobotInfo.isEmergencyButtonDown) {
                                VoiceHelper.play("voice_scram_stop_turn_on")
                                EasyDialog.getInstance(this)
                                    .warnError(getString(R.string.voice_scram_stop_turn_on))
                                return@postDelayed
                            }
                            ROSController.liftDown()
                            callingInfo.isLifting = true
                            EasyDialog.getLoadingInstance(this)
                                .loading(getString(R.string.text_pickup_model_resetting))
                        }, 200)
                    }
                    dialog.dismiss()
                }
            return
        }
        if (EasyDialog.isShow() && !EasyDialog.getInstance().isTiming && RobotInfo.taskAbnormalFinishPrompt == null) {
            EasyDialog.getInstance().dismiss()
        }
    }

    private val connectMqttRunnable = Runnable {
        val mqttClient = MqttClient.getInstance()
        tvCall.visibility = if (mqttClient.isConnected) View.VISIBLE else View.GONE
        if (!mqttClient.isConnected && !mqttClient.isConnecting && WIFIUtils.isNetworkConnected(this@MainActivity)) {
            mqttClient
                .connect(RobotInfo.ROSHostname, this@MainActivity)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .doOnNext { v: Int? -> Timber.w("mqtt connected") }
                .observeOn(Schedulers.io())
                .flatMap { v: Int? -> mqttClient.subscribeToTopic() }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<Boolean> {
                    override fun onSubscribe(d: Disposable) {
                    }

                    override fun onNext(aBoolean: Boolean) {
                        if (!this@MainActivity.isFinishing && !this@MainActivity.isDestroyed) {
                            tvCall.visibility = View.VISIBLE
                        }
                    }

                    override fun onError(e: Throwable) {
                        if (!this@MainActivity.isFinishing && !this@MainActivity.isDestroyed) {
                            tvCall.visibility = View.GONE
                            if (!mqttClient.isConnected) {
                                Timber.w(e, "建立mqtt连接失败")
                            } else {
                                Timber.w(e, "订阅失败")
                            }
                        }
                    }

                    override fun onComplete() {}
                })
        }
    }

    override fun onCallingModelDisconnectEvent(event: CallingModelDisconnectedEvent?) {
        super.onCallingModelDisconnectEvent(event)
        tvCallButton.visibility = View.GONE
    }

    override fun onCallingModelReconnectedEvent(event: CallingModelReconnectSuccessEvent?) {
        super.onCallingModelReconnectedEvent(event)
        tvCallButton.visibility = View.VISIBLE
    }

    @SuppressLint("CheckResult")
    private fun uploadCrashLog() {
        if (WIFIUtils.isNetworkConnected(mApp)) {
            var wifiSSID = WIFIUtils.getConnectWifiSSID(mApp)
            if (wifiSSID.isBlank()) wifiSSID = getString(R.string.text_not_connected)
            if (null != wifiSSID && wifiSSID == RobotInfo.ROSWifi) {
                DbRepository.getInstance().allCrashNotify
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .subscribe({ crashNotifies: List<CrashNotify> ->
                        if (crashNotifies.isNotEmpty()) {
                            val crashNotify = crashNotifies[0]
                            val notify2 = Notifier.notify2(
                                Msg(
                                    NotifyConstant.SYSTEM_NOTIFY,
                                    "application crash(应用崩溃)",
                                    crashNotify.notify,
                                    RobotInfo.ROSHostname
                                )
                            )
                            notify2?.subscribe({ _: Map<String?, Any?>? ->
                                Timber.w("上传crash日志成功")
                                DbRepository.getInstance().deleteNotify(crashNotify.id)
                            }) { throwable: Throwable? -> Timber.w(throwable, "上传crash日志失败") }
                        }
                    }) { throwable: Throwable? ->
                        Timber.tag("selectCrash").w(throwable, "查询本地通知失败")
                    }
            }
        }
    }
}