package com.reeman.agv.fragments.setting

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.google.gson.Gson
import com.reeman.agv.R
import com.reeman.agv.activities.NetworkTestActivity
import com.reeman.agv.base.BaseFragment
import com.reeman.agv.constants.Errors
import com.reeman.agv.utils.DebounceClickListener
import com.reeman.agv.utils.MulticastClient
import com.reeman.agv.utils.PointContentUtils
import com.reeman.agv.utils.ToastUtils
import com.reeman.agv.utils.ViewUtils
import com.reeman.agv.widgets.DispatchAuthDialog
import com.reeman.agv.widgets.EasyDialog
import com.reeman.agv.widgets.ExpandableLayout
import com.reeman.agv.widgets.ExpandableLayout.OnHeadClickListener
import com.reeman.agv.widgets.PointChooseDialog
import com.reeman.commons.constants.Constants
import com.reeman.commons.settings.DispatchSetting
import com.reeman.commons.state.NavigationMode
import com.reeman.commons.state.RobotInfo
import com.reeman.commons.utils.PointUtils
import com.reeman.commons.utils.PrecisionUtils
import com.reeman.commons.utils.SpManager
import com.reeman.commons.utils.WIFIUtils
import com.reeman.dispatch.DispatchManager
import com.reeman.dispatch.constants.ResponseCode
import com.reeman.dispatch.exception.RequestFailureException
import com.reeman.dispatch.model.request.MapInfoUploadReq
import com.reeman.dispatch.model.request.RoomConfigReq
import com.reeman.dispatch.model.request.RoomLoginReq
import com.reeman.dispatch.model.response.RoomConfigResp
import com.reeman.dispatch.request.Urls
import com.reeman.points.model.custom.GenericPoint
import com.reeman.points.model.dispatch.DispatchMapInfo
import com.reeman.points.model.dispatch.GenericMap
import com.reeman.points.process.PointRefreshProcessor
import com.reeman.points.process.callback.RefreshPointDataCallback
import com.reeman.points.process.impl.FixedDeliveryPointsRefreshProcessingStrategy
import com.reeman.points.utils.PointCacheInfo
import com.reeman.ros.ROSController
import com.warkiz.widget.IndicatorSeekBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException

class DispatchSettingFragment : BaseFragment(), DebounceClickListener {

    private lateinit var rgDispatchControl: RadioGroup
    private lateinit var layoutDispatchSettings: LinearLayout
    private lateinit var etServerAddress: EditText
    private lateinit var rgDispatchServerSetting: RadioGroup
    private lateinit var layoutRoomSetting: LinearLayout
    private lateinit var tvRoom: TextView
    private lateinit var tvOnlineLocation: TextView
    private lateinit var tvDefaultChargePoint: TextView
    private lateinit var elRoomConfig: ExpandableLayout
    private lateinit var rgDynamicPlanningPathControl: RadioGroup
    private lateinit var isbQueuingTimeoutDuration: IndicatorSeekBar
    private lateinit var isbAvoidingDepth: IndicatorSeekBar
    private lateinit var isbPositionRange: IndicatorSeekBar
    private lateinit var btnRelocate: Button
    private var multicastClient: MulticastClient? = null
    private var roomConfigReq: RoomConfigReq? = null


    override fun getLayoutRes() = R.layout.fragment_dispatch_setting

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rgDispatchControl = findView(R.id.rg_dispatch_control)
        layoutDispatchSettings = findView(R.id.layout_dispatch_settings)
        rgDispatchServerSetting = findView(R.id.rg_dispatch_server_setting)
        layoutRoomSetting = findView(R.id.layout_room_setting)
        etServerAddress = findView(R.id.et_server_address)
        etServerAddress.setOnFocusChangeListener(this::hideKeyBoard)
        tvRoom = findView(R.id.tv_room)
        tvOnlineLocation = findView(R.id.tv_online_location)
        tvDefaultChargePoint = findView(R.id.tv_default_charge_point)
        btnRelocate = findView(R.id.btn_relocate)
        elRoomConfig = findView(R.id.el_room_config)
        elRoomConfig.setOnHeadClickListener(object : OnHeadClickListener {
            override fun onExpand(expandableLayout: ExpandableLayout) {
                onRoomConfigExpandClick(expandableLayout)
            }

            override fun onCollapse(expandableLayout: ExpandableLayout) {
                onRoomConfigHideClick(expandableLayout)
            }

        })
        findView<RadioButton>(R.id.rb_open_dispatch).setDebounceClickListener(::onDebounceClick)
        findView<RadioButton>(R.id.rb_close_dispatch).setDebounceClickListener(::onDebounceClick)
        findView<Button>(R.id.btn_save_server_address).setDebounceClickListener(::onDebounceClick)
        findView<RadioButton>(R.id.rb_cloud_server).setDebounceClickListener(::onDebounceClick)
        findView<RadioButton>(R.id.rb_local_server).setDebounceClickListener(::onDebounceClick)
        findView<Button>(R.id.btn_room_setting).setDebounceClickListener(::onDebounceClick)
        findView<Button>(R.id.btn_start_multicast).setDebounceClickListener(::onDebounceClick)
        findView<Button>(R.id.btn_join_multicast_group).setDebounceClickListener(::onDebounceClick)
        findView<Button>(R.id.btn_upload_map).setDebounceClickListener(::onDebounceClick)
        findView<TextView>(R.id.tv_save_room_config).setDebounceClickListener(::onDebounceClick)
        findView<TextView>(R.id.tv_online_location_title).setDebounceClickListener(::onDebounceClick)
        findView<Button>(R.id.btn_online_location_setting).setDebounceClickListener(::onDebounceClick)
        findView<Button>(R.id.btn_relocate).setDebounceClickListener(::onDebounceClick)
        findView<TextView>(R.id.tv_default_charge_point_title).setDebounceClickListener(::onDebounceClick)
        findView<Button>(R.id.btn_default_charge_point_setting).setDebounceClickListener(::onDebounceClick)
        findView<Button>(R.id.btn_network_test).setDebounceClickListener(::onDebounceClick)
        initData(RobotInfo.dispatchSetting)
    }

    private fun initData(dispatchSetting: DispatchSetting) {
        rgDispatchControl.check(
            if (dispatchSetting.isOpened) {
                R.id.rb_open_dispatch
            } else {
                R.id.rb_close_dispatch
            }
        )
        layoutDispatchSettings.visibility = if (dispatchSetting.isOpened) {
            View.VISIBLE
        } else {
            View.GONE
        }
        rgDispatchServerSetting.check(
            if (dispatchSetting.isLocalServer) {
                R.id.rb_local_server
            } else {
                R.id.rb_cloud_server
            }
        )
        layoutRoomSetting.visibility = if (dispatchSetting.isLocalServer) {
            View.GONE
        } else {
            View.VISIBLE
        }
        etServerAddress.setText(dispatchSetting.serverAddress)
        tvRoom.text = dispatchSetting.roomName
        tvOnlineLocation.text =
            if (dispatchSetting.initPoint.isBlank() || dispatchSetting.initMap.isBlank()) {
                getString(R.string.text_not_choose_point)
            } else {
                dispatchSetting.initPoint
            }
        tvDefaultChargePoint.text =
            if (dispatchSetting.chargePointMap.isBlank() || dispatchSetting.chargePoint.isBlank()) {
                getString(R.string.text_not_choose_point)
            } else {
                dispatchSetting.chargePoint
            }
    }

    private fun updateDispatchSetting(update: DispatchSetting.() -> Unit) {
        RobotInfo.dispatchSetting.apply {
            update()
            Timber.w("修改调度设置: $this")
            SpManager.getInstance().edit().putString(Constants.KEY_DISPATCH_SETTING, Gson().toJson(this)).apply()
        }
    }

    private fun showRoomConfig(view: ExpandableLayout, roomConfigResp: RoomConfigResp) {
        fun initView() {
            rgDynamicPlanningPathControl = view.findViewById(R.id.rg_dynamic_planning_path_control)
            isbQueuingTimeoutDuration = view.findViewById(R.id.isb_queuing_timeout_duration)
            isbAvoidingDepth = view.findViewById(R.id.isb_avoiding_depth)
            isbPositionRange = view.findViewById(R.id.isb_position_range)
            view.findViewById<RadioButton>(R.id.rb_open_dynamic_planning_path).setDebounceClickListener(::onDebounceClick)
            view.findViewById<RadioButton>(R.id.rb_close_dynamic_planning_path).setDebounceClickListener(::onDebounceClick)
            view.findViewById<ImageButton>(R.id.ib_decrease_queuing_timeout_duration).setDebounceClickListener(::onDebounceClick)
            view.findViewById<ImageButton>(R.id.ib_increase_queuing_timeout_duration).setDebounceClickListener(::onDebounceClick)
            view.findViewById<ImageButton>(R.id.ib_decrease_avoiding_depth).setDebounceClickListener(::onDebounceClick)
            view.findViewById<ImageButton>(R.id.ib_increase_avoiding_depth).setDebounceClickListener(::onDebounceClick)
            view.findViewById<ImageButton>(R.id.ib_decrease_position_range).setDebounceClickListener(::onDebounceClick)
            view.findViewById<ImageButton>(R.id.ib_increase_position_range).setDebounceClickListener(::onDebounceClick)
        }

        fun initData() {
            rgDynamicPlanningPathControl.check(
                if (roomConfigResp.dynamicPlanningPath) {
                    R.id.rb_open_dynamic_planning_path
                } else {
                    R.id.rb_close_dynamic_planning_path
                }
            )
            isbQueuingTimeoutDuration.setProgress((roomConfigResp.queuingTimeoutDuration / 60).toFloat())
            isbAvoidingDepth.setProgress(roomConfigResp.avoidingDepth.toFloat())
            isbPositionRange.setProgress(roomConfigResp.positionRange)
        }

        if (root.tag == null) {
            initView()
            root.tag = "initialized"
        }
        initData()
    }

    private fun onRoomConfigExpandClick(view: ExpandableLayout) {
        val dispatchSetting = RobotInfo.dispatchSetting
        if (dispatchSetting.serverAddress.isBlank()) {
            EasyDialog.getInstance(requireContext()).warnError(getString(R.string.text_please_add_server_address_first))
            return
        }
        if (dispatchSetting.roomName.isBlank() || dispatchSetting.roomPwd.isBlank()) {
            EasyDialog.getInstance(requireContext()).warnError(getString(R.string.text_please_enter_room_first))
            return
        }
        val connectWifiSSID = WIFIUtils.getConnectWifiSSID(requireContext())
        if (connectWifiSSID.isNullOrBlank()) {
            EasyDialog.getInstance(requireContext()).warnError(getString(R.string.text_please_connect_wifi_first))
            return
        }
        EasyDialog.getLoadingInstance(requireContext()).loading(getString(R.string.text_loading_room_config))
        CoroutineScope(Dispatchers.IO).launch {
            DispatchManager.request(
                networkCall = {
                    DispatchManager.getApiService().getRoomConfig(Urls.getOrUpdateRoomConfig(dispatchSetting.serverAddress))
                },
                onSuccess = {
                    val roomConfigResp = it!!.data!!
                    DispatchManager.apply {
                        this.positionRange = roomConfigResp.positionRange
                    }
                    roomConfigReq = RoomConfigReq(
                        dynamicPlanningPath = roomConfigResp.dynamicPlanningPath,
                        queuingTimeoutDuration = roomConfigResp.queuingTimeoutDuration,
                        avoidingDepth = roomConfigResp.avoidingDepth,
                        positionRange = roomConfigResp.positionRange
                    )
                    withContext(Dispatchers.Main) {
                        view.apply {
                            show()
                            headerLayout.findViewById<ImageButton>(R.id.ib_expand_indicator).animate().rotation(90f).setDuration(200).start()
                        }
                        showRoomConfig(view, roomConfigResp)
                        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
                    }
                },
                onFailure = {
                    val failureTip = when (it) {
                        is IOException -> getString(R.string.text_get_room_config_failure_cause_network)
                        else -> getString(R.string.text_get_room_config_failure_unknown_exception, it.message)
                    }
                    withContext(Dispatchers.Main) {
                        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
                        EasyDialog.getInstance(requireContext()).warnError(failureTip)
                    }
                })
        }
    }

    private fun onRoomConfigHideClick(view: ExpandableLayout) {
        view.apply {
            hide()
            headerLayout.findViewById<ImageButton>(R.id.ib_expand_indicator).animate().rotation(0f).setDuration(200).start()
        }
    }

    private fun onDebounceClick(view: View) {
        when (view.id) {
            R.id.tv_default_charge_point_title -> EasyDialog.getInstance(requireContext()).warnError(getString(R.string.text_dispatch_server_will_only_allocation_selected_point_when_choose_default_charge_point))
            R.id.tv_online_location_title -> EasyDialog.getInstance(requireContext()).warnError(getString(R.string.text_check_robot_position_after_online_to_dispatch_server))
            R.id.rb_open_dynamic_planning_path -> roomConfigReq?.dynamicPlanningPath = true
            R.id.rb_close_dynamic_planning_path -> roomConfigReq?.dynamicPlanningPath = false
            R.id.ib_decrease_queuing_timeout_duration -> ViewUtils.onIntValueChange(view, false)
            R.id.ib_increase_queuing_timeout_duration -> ViewUtils.onIntValueChange(view, true)
            R.id.ib_decrease_avoiding_depth -> ViewUtils.onIntValueChange(view, false)
            R.id.ib_increase_avoiding_depth -> ViewUtils.onIntValueChange(view, true)
            R.id.ib_decrease_position_range -> ViewUtils.onFloatValueChange(view, false)
            R.id.ib_increase_position_range -> ViewUtils.onFloatValueChange(view, true)
            R.id.tv_save_room_config -> {
                if (roomConfigReq == null) return
                roomConfigReq!!.queuingTimeoutDuration
                roomConfigReq?.apply {
                    queuingTimeoutDuration = isbQueuingTimeoutDuration.progress * 60
                    avoidingDepth = isbAvoidingDepth.progress
                    positionRange = isbPositionRange.progressFloat
                    EasyDialog.getLoadingInstance(requireContext()).loading(getString(R.string.text_updating_room_config))
                    CoroutineScope(Dispatchers.IO).launch {
                        DispatchManager.request(
                            networkCall = {
                                DispatchManager.getApiService().updateRoomConfig(
                                    url = Urls.getOrUpdateRoomConfig(RobotInfo.dispatchSetting.serverAddress),
                                    roomConfigReq = this@apply,
                                )
                            },
                            onSuccess = {
                                DispatchManager.release()
                                withContext(Dispatchers.Main) {
                                    ToastUtils.showShortToast(getString(R.string.text_save_success))
                                    if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
                                }

                            },
                            onFailure = {
                                val failureTip = when (it) {
                                    is IOException -> getString(R.string.text_update_room_config_failure_cause_network)
                                    else -> getString(R.string.text_update_room_config_failure_unknown_exception, it.message)
                                }
                                withContext(Dispatchers.Main) {
                                    if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
                                    EasyDialog.getInstance(requireContext()).warnError(failureTip)
                                }
                            }
                        )
                    }
                }
            }

            R.id.rb_open_dispatch -> {
                if (RobotInfo.dispatchSetting.isOpened) return
                EasyDialog.getInstance(requireContext())
                    .confirm(getString(R.string.text_dispatch_mode_not_compatible_with_elevator_control_door_control_auto_path_model))
                    { dialog, id ->
                        dialog.dismiss()
                        if (id == R.id.btn_confirm) {
                            updateDispatchSetting {
                                isOpened = true
                                layoutDispatchSettings.visibility = View.VISIBLE
                            }
                            RobotInfo.navigationMode = NavigationMode.fixPathMode
                            RobotInfo.elevatorSetting.open = false
                            RobotInfo.doorControlSetting.open = false
                            val gson = Gson()
                            SpManager.getInstance().edit()
                                .putInt(Constants.KEY_NAVIGATION_MODEL, RobotInfo.navigationMode)
                                .putString(Constants.KEY_ELEVATOR_SETTING, gson.toJson(RobotInfo.elevatorSetting))
                                .putString(Constants.KEY_DOOR_CONTROL, gson.toJson(RobotInfo.doorControlSetting))
                                .apply()
                        } else {
                            rgDispatchControl.check(R.id.rb_close_dispatch)
                            layoutDispatchSettings.visibility = View.GONE
                        }
                    }
            }

            R.id.rb_close_dispatch -> {
                if (!RobotInfo.dispatchSetting.isOpened) return
                if (DispatchManager.isActive()) DispatchManager.release()
                updateDispatchSetting {
                    isOpened = false
                    layoutDispatchSettings.visibility = View.GONE
                }
            }

            R.id.btn_save_server_address -> {
                val address = etServerAddress.text?.toString()
                if (address.isNullOrBlank() || !isValidServerAddress(address)) {
                    ToastUtils.showShortToast(getString(R.string.text_please_input_server_address))
                    return
                }
                updateDispatchSetting {
                    if (serverAddress != address) {
                        serverAddress = address
                        ToastUtils.showShortToast(getString(R.string.text_save_success))
                        DispatchManager.release()
                        DispatchManager.host = serverAddress
                    } else {
                        ToastUtils.showShortToast(getString(R.string.text_please_do_not_save_repeat))
                    }
                }
            }

            R.id.rb_cloud_server -> {
                if (!RobotInfo.dispatchSetting.isLocalServer) return
                layoutRoomSetting.visibility = View.VISIBLE
                updateDispatchSetting {
                    isLocalServer = false
                    roomName = ""
                    roomPwd = ""
                    tvRoom.text = ""
                }
            }

            R.id.rb_local_server -> {
                if (RobotInfo.dispatchSetting.isLocalServer) return
                if (RobotInfo.dispatchSetting.serverAddress.isBlank()) {
                    rgDispatchServerSetting.check(R.id.rb_cloud_server)
                    EasyDialog.getInstance(requireContext()).warnError(getString(R.string.text_please_add_server_address_first))
                    return
                }
                DispatchManager.release()
                startEnterRoom("25D2CC", "4E2E3199", true)
            }

            R.id.btn_room_setting -> {
                if (RobotInfo.dispatchSetting.serverAddress.isBlank()) {
                    EasyDialog.getInstance(requireContext()).warnError(getString(R.string.text_please_add_server_address_first))
                    return
                }
                DispatchAuthDialog(requireContext()).apply {
                    setName(RobotInfo.dispatchSetting.roomName)
                    setPassword(RobotInfo.dispatchSetting.roomPwd)
                    setOnViewClickListener { dialog, view, roomName, roomPwd ->
                        if (view.id == R.id.btn_login) {
                            if (roomName.isBlank()) {
                                ToastUtils.showShortToast(getString(R.string.text_room_name_cannot_be_empty))
                                return@setOnViewClickListener
                            }
                            if (roomPwd.isBlank()) {
                                ToastUtils.showShortToast(getString(R.string.text_room_pwd_cannot_be_empty))
                                return@setOnViewClickListener
                            }
                            dialog.dismiss()
                            DispatchManager.release()
                            startEnterRoom(roomName, roomPwd)
                        } else {
                            dialog.dismiss()
                        }
                    }
                }.show()
            }

            R.id.btn_start_multicast -> {
                val dispatchSetting = RobotInfo.dispatchSetting
                if (dispatchSetting.serverAddress.isBlank()) {
                    EasyDialog.getInstance(requireContext()).warnError(getString(R.string.text_please_add_server_address_first))
                    return
                }
                if (dispatchSetting.roomName.isBlank() || dispatchSetting.roomPwd.isBlank()) {
                    EasyDialog.getInstance(requireContext()).warnError(getString(R.string.text_please_enter_room_first))
                    return
                }
                val connectWifiSSID = WIFIUtils.getConnectWifiSSID(requireContext())
                if (connectWifiSSID.isNullOrBlank()) {
                    ToastUtils.showShortToast(getString(R.string.text_please_connect_wifi_first))
                    return
                }
                EasyDialog.getInstance(requireContext())
                    .confirm(getString(R.string.text_confirm_to_multicast_dispatch_config, connectWifiSSID))
                    { dialog, id ->
                        dialog.dismiss()
                        if (id == R.id.btn_confirm) {
                            multicastClient = MulticastClient()
                            multicastClient?.sendMulticastMessage(Gson().toJson(dispatchSetting), onResult = { success ->
                                multicastClient?.closeMulticast()
                                val resultId = if (success) {
                                    R.string.text_send_success
                                } else {
                                    R.string.text_send_failure
                                }
                                ToastUtils.showShortToast(getString(resultId))
                            })
                        }
                    }
            }

            R.id.btn_join_multicast_group -> {
                val connectWifiSSID = WIFIUtils.getConnectWifiSSID(requireContext())
                if (connectWifiSSID.isNullOrBlank()) {
                    ToastUtils.showShortToast(getString(R.string.text_please_connect_wifi_first))
                    return
                }
                multicastClient = MulticastClient()
                multicastClient?.startReceivingMulticast {
                    try {
                        val dispatchSetting = Gson().fromJson(it, DispatchSetting::class.java)
                        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
                        EasyDialog.getInstance(requireContext())
                            .confirm(getString(R.string.text_confirm_to_apply_new_dispatch_setting, dispatchSetting.serverAddress, dispatchSetting.roomName))
                            { dialog, id ->
                                dialog.dismiss()
                                if (id == R.id.btn_confirm) {
                                    updateDispatchSetting {
                                        this.serverAddress = dispatchSetting.serverAddress
                                        etServerAddress.setText(dispatchSetting.serverAddress)
                                        ToastUtils.showShortToast(getString(R.string.text_apply_server_address_success))
                                    }
                                    DispatchManager.release()
                                    startEnterRoom(dispatchSetting.roomName, dispatchSetting.roomPwd)
                                }
                            }
                        multicastClient?.closeMulticast()
                    } catch (e: Exception) {
                        Timber.w(e, "解析调度配置失败")
                    }
                }
                EasyDialog.getInstance(requireContext())
                    .onlyCancel(getString(R.string.text_join_multicast_group_success_waiting_receive_config))
                    { dialog, id ->
                        dialog.dismiss()
                        multicastClient?.closeMulticast()
                    }
            }

            R.id.btn_upload_map -> refreshPoints()

            R.id.btn_online_location_setting -> getMapInfo(onSuccess = ::selectInitLocation)

            R.id.btn_default_charge_point_setting -> getMapInfo(onSuccess = ::selectDefaultChargePoint)

            R.id.btn_relocate -> {
                if (RobotInfo.dispatchSetting.run { initPoint.isBlank() || initMap.isBlank() }) {
                    ToastUtils.showShortToast(getString(R.string.text_please_select_init_point_point_first))
                    return
                }
                getMapInfo(onSuccess = ::relocate)
            }

            R.id.btn_network_test -> {
                val dispatchSetting = RobotInfo.dispatchSetting
                if (dispatchSetting.serverAddress.isBlank()) {
                    EasyDialog.getInstance(requireContext()).warnError(getString(R.string.text_please_add_server_address_first))
                    return
                }
                if (dispatchSetting.roomName.isBlank() || dispatchSetting.roomPwd.isBlank()) {
                    EasyDialog.getInstance(requireContext()).warnError(getString(R.string.text_please_enter_room_first))
                    return
                }
                val connectWifiSSID = WIFIUtils.getConnectWifiSSID(requireContext())
                if (connectWifiSSID.isNullOrBlank()) {
                    ToastUtils.showShortToast(getString(R.string.text_please_connect_wifi_first))
                    return
                }
                EasyDialog.getInstance(requireContext()).confirm(
                    getString(R.string.text_start_test),
                    getString(R.string.text_cancel),
                    getString(R.string.text_please_confirm_network_first_before_test)
                ) { dialog, id ->
                    dialog.dismiss()
                    if (id == R.id.btn_confirm) {
                        startActivity(Intent(requireActivity(), NetworkTestActivity::class.java))
                    }
                }
            }
        }
    }

    fun isValidServerAddress(input: String): Boolean {
        val ipv4Regex = Regex("""^(https?://)((25[0-5]|2[0-4]\d|1\d{2}|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d{2}|[1-9]?\d):([1-9][0-9]{0,4})$""")
        val hostnameRegex = Regex("""^(https?://)([a-zA-Z0-9][a-zA-Z0-9\-]*\.)*[a-zA-Z0-9\-]+(:[1-9][0-9]{0,4})$""")
        val ipv6Regex = Regex("""^(https?://)\[([0-9a-fA-F:]+)\]:([1-9][0-9]{0,4})$""")

        fun isValidPort(port: String): Boolean {
            return port.toIntOrNull()?.let { it in 1..65535 } == true
        }

        return when {
            ipv4Regex.matches(input) -> isValidPort(input.split(":").last())
            hostnameRegex.matches(input) -> isValidPort(input.substringAfterLast(":"))
            ipv6Regex.matches(input) -> isValidPort(input.substringAfterLast(":"))
            else -> false
        }
    }


    @SuppressLint("CheckResult")
    private fun startEnterRoom(roomName: String, roomPwd: String, isLocalServer: Boolean = false) {
        if (!isLocalServer && DispatchManager.isActive()) {
            if (DispatchManager.roomName == roomName && DispatchManager.roomPwd == roomPwd) {
                ToastUtils.showShortToast(getString(R.string.text_already_enter_room))
                return
            }
        }
        EasyDialog.getLoadingInstance(requireContext()).loading(getString(R.string.text_try_login_room))
        CoroutineScope(Dispatchers.IO).launch {
            DispatchManager.request(
                networkCall = {
                    DispatchManager.getApiService()
                        .loginRoomSync(
                            Urls.getLoginUrl(RobotInfo.dispatchSetting.serverAddress), RoomLoginReq(
                                roomName = roomName,
                                roomPwd = roomPwd,
                                hostname = RobotInfo.ROSHostname,
                                robotType = 1,
                            )
                        )
                },
                onSuccess = {
                    withContext(Dispatchers.Main) {
                        DispatchManager.apply {
                            this.roomName = roomName
                            this.roomPwd = roomPwd
                            this.token = it!!.data!!["token"]
                        }
                        updateDispatchSetting {
                            this.roomName = roomName
                            this.roomPwd = roomPwd
                            if (isLocalServer) {
                                this.isLocalServer = true
                                layoutRoomSetting.visibility = View.GONE
                            }
                            tvRoom.text = roomName
                        }
                        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
                        if (isLocalServer) {
                            EasyDialog.getInstance(requireContext()).warnError(getString(R.string.text_join_room_success))
                        } else {
                            EasyDialog.getInstance(requireContext()).warnError(getString(R.string.text_join_room_success_and_will_enter_room_after_enter_main_activity, roomName))
                        }
                    }
                },
                onFailure = {
                    val failureTip = when (it) {
                        is IOException -> getString(R.string.text_enter_room_failure_cause_network)
                        is RequestFailureException -> {
                            when (it.code) {
                                ResponseCode.ROOM_PASSWORD_ERROR -> getString(R.string.text_enter_room_failure_cause_name_or_password_error)
                                ResponseCode.ROBOT_BOUND_TO_TOO_MANY_ROOMS_ERROR, ResponseCode.ROBOT_ALREADY_BOUND_TO_ANOTHER_ROOM_ERROR -> getString(R.string.text_enter_room_failure_cause_already_bound_to_other_room)
                                else -> getString(R.string.text_enter_room_failure_undefined_exception, it.code, it.msg)
                            }
                        }

                        else -> getString(R.string.text_enter_room_failure_unknown_exception, it.message)
                    }
                    withContext(Dispatchers.Main) {
                        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
                        if (isLocalServer) {
                            rgDispatchServerSetting.check(R.id.rb_cloud_server)
                        }
                        EasyDialog.getInstance(requireContext()).warnError(failureTip)
                    }
                }
            )
        }
    }

    var initPoint: GenericPoint? = null

    private fun relocate(mapInfo: DispatchMapInfo) {
        val dispatchSetting = RobotInfo.dispatchSetting
        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
//        if (mapInfo.name != dispatchSetting.initMap){
//            EasyDialog.getInstance(requireContext()).warnError(getString(R.string.text_init_map_not_found))
//            return
//        }
        val pointTypes = setOf(GenericPoint.PRODUCT, GenericPoint.CHARGE)
        val initPointInfo = mapInfo.pointList.find { it.type in pointTypes && it.name == dispatchSetting.initPoint }
        if (initPointInfo == null) {
            EasyDialog.getInstance(requireContext()).warnError(getString(R.string.text_not_found_init_point_cannot_relocate))
            updateDispatchSetting {
                initMap = ""
                initPoint = ""
            }
            DispatchManager.release()
            return
        }
        val currentPosition = robotInfo.currentPosition!!
        val distance = PointUtils.calculateDistance(currentPosition, initPointInfo.position)
        val angle = PointUtils.calculateAngle(currentPosition, initPointInfo.position)
        showRelocateDialog(distance, angle, initPointInfo)
    }

    fun showRelocateDialog(distance: Double, angle: Double, initPointInfo: GenericPoint) {
        EasyDialog.getInstance(requireContext())
            .confirm(
                getString(R.string.text_relocate),
                getString(R.string.text_cancel),
                getString(R.string.text_check_position_deviate_from_init_location, PrecisionUtils.setScale(distance).toString(), PrecisionUtils.setScale(angle).toString(), initPointInfo.name)
            ) { dialog, id ->
                dialog.dismiss()
                if (id == R.id.btn_confirm) {
                    EasyDialog.getLoadingInstance(requireContext()).loading(getString(R.string.text_relocating))
                    RobotInfo.isRelocatingAtInitPoint = true
                    initPoint = initPointInfo
                    ROSController.relocateByCoordinate(initPointInfo.position)
                }
            }
    }

    fun onRelocationResult(position: DoubleArray) {
        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
        if (initPoint == null) {
            return
        }
        val distance = PointUtils.calculateDistance(position, initPoint!!.position)
        val angle = PointUtils.calculateAngle(position, initPoint!!.position)
        if (distance > 1 || angle > 30) {
            showRelocateDialog(distance, angle, initPoint!!)
        } else {
            ToastUtils.showShortToast(getString(R.string.text_relocate_success))
        }
    }

    private fun selectDefaultChargePoint(mapInfo: DispatchMapInfo) {
        val dispatchSetting = RobotInfo.dispatchSetting
        val pointList = mapInfo.pointList.filter { it.type == GenericPoint.CHARGE }
        val currentChargePoint = if (dispatchSetting.chargePointMap != mapInfo.name || dispatchSetting.chargePoint.isBlank() || pointList.none { it.name == dispatchSetting.chargePoint }) {
            null
        } else {
            pointList.find { it.name == dispatchSetting.chargePoint }
        }
        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
        PointChooseDialog(
            context = requireContext(),
            allPoints = pointList.groupBy { it.type }.map {
                PointContentUtils.getTypeShowContent(
                    requireContext(),
                    it.key
                ) to it.value
            },
            selectedPoint = currentChargePoint,
            listener = object : PointChooseDialog.OnPointChooseResultListener {
                override fun onPointChooseResult(
                    points: Pair<String, GenericPoint>
                ) {
                    updateDispatchSetting {
                        this.chargePointMap = mapInfo.name
                        this.chargePoint = points.second.name
                        tvDefaultChargePoint.text = points.second.name
                        ToastUtils.showShortToast(getString(R.string.text_save_success))
                    }
                }

                override fun onPointNotChoose() {
                    updateDispatchSetting {
                        this.chargePointMap = ""
                        this.chargePoint = ""
                        tvDefaultChargePoint.text = getString(R.string.text_not_choose_point)
                    }
                }
            }
        ).show()
    }

    private fun selectInitLocation(mapInfo: DispatchMapInfo) {
        val dispatchSetting = RobotInfo.dispatchSetting
        val pointTypes = setOf(GenericPoint.PRODUCT, GenericPoint.CHARGE)
        val pointList = mapInfo.pointList.filter { it.type in pointTypes }
        val currentInitPoint = if (dispatchSetting.initMap != mapInfo.name || dispatchSetting.initPoint.isBlank() || pointList.none { it.name == dispatchSetting.initPoint }) {
            null
        } else {
            pointList.find { it.name == dispatchSetting.initPoint }
        }
        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
        PointChooseDialog(
            context = requireContext(),
            allPoints = pointList.groupBy { it.type }.map {
                PointContentUtils.getTypeShowContent(
                    requireContext(),
                    it.key
                ) to it.value
            },
            selectedPoint = currentInitPoint,
            listener = object : PointChooseDialog.OnPointChooseResultListener {
                override fun onPointChooseResult(
                    points: Pair<String, GenericPoint>
                ) {
                    updateDispatchSetting {
                        this.initMap = mapInfo.name
                        this.initPoint = points.second.name
                        tvOnlineLocation.text = points.second.name
                        ToastUtils.showShortToast(getString(R.string.text_save_success))
                    }
                }

                override fun onPointNotChoose() {

                }
            }
        ).show()
    }

    private fun getMapInfo(onSuccess: (mapInfo: DispatchMapInfo) -> Unit) {
        val dispatchSetting = RobotInfo.dispatchSetting
        if (dispatchSetting.serverAddress.isBlank()) {
            EasyDialog.getInstance(requireContext()).warnError(getString(R.string.text_please_add_server_address_first))
            return
        }
        if (dispatchSetting.roomName.isBlank() || dispatchSetting.roomPwd.isBlank()) {
            EasyDialog.getInstance(requireContext()).warnError(getString(R.string.text_please_enter_room_first))
            return
        }
        EasyDialog.getLoadingInstance(requireContext()).loading(getString(R.string.text_loadding_points_info))
        CoroutineScope(Dispatchers.IO).launch {
            DispatchManager.request(
                networkCall = {
                    DispatchManager.getApiService().getMapInfo(url = Urls.getMapInfo(dispatchSetting.serverAddress))
                },
                onSuccess = {
                    val mapInfo = it!!.data!!.first()
                    withContext(Dispatchers.Main) {
                        onSuccess(mapInfo)
                    }
                },
                onFailure = {
                    Timber.w(it, "拉取地图信息失败")
                    val failureTip = when (it) {
                        is IOException -> getString(R.string.text_get_map_info_failure_cause_network)
                        is RequestFailureException -> {
                            when (it.code) {
                                ResponseCode.PARSE_TOKEN_FAILED_ERROR, ResponseCode.ROOM_NOT_FOUND_ERROR -> getString(R.string.text_get_map_info_failure_cause_parse_token_failed)
                                ResponseCode.MAP_LIST_EMPTY_ERROR -> getString(R.string.text_get_map_info_failure_cause_map_empty)

                                else -> getString(R.string.text_get_map_info_failure_undefined_exception, it.code, it.message)
                            }
                        }

                        else -> getString(R.string.text_get_map_info_failure_unknown_exception, it.message)
                    }
                    withContext(Dispatchers.Main) {
                        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
                        EasyDialog.getInstance(requireContext()).warnError(failureTip)

                    }
                }
            )
        }


    }

    private fun refreshPoints() {
        RobotInfo.dispatchSetting.let { dispatchSetting ->
            if (dispatchSetting.serverAddress.isBlank()) {
                EasyDialog.getInstance(requireContext()).warnError(getString(R.string.text_please_add_server_address_first))
                return
            }
            if (dispatchSetting.roomName.isBlank() || dispatchSetting.roomPwd.isBlank()) {
                EasyDialog.getInstance(requireContext()).warnError(getString(R.string.text_please_enter_room_first))
                return
            }
        }

        EasyDialog.getLoadingInstance(requireContext()).loading(getString(R.string.text_loadding_points_info))
        PointRefreshProcessor(FixedDeliveryPointsRefreshProcessingStrategy(), object :
            RefreshPointDataCallback {
            override fun onPointsLoadSuccess(pointList: List<GenericPoint>) {
                if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
                showUploadMapDialog()
            }

            override fun onThrowable(throwable: Throwable) {
                Timber.w(throwable, "获取点位信息失败")
                if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
                EasyDialog.getInstance(requireContext()).warnError(Errors.getDataLoadFailedTip(requireContext(), throwable))
            }
        }).process(
            ip = RobotInfo.ROSIPAddress,
            useLocalData = false,
            checkEnterElevatorPoint = false,
            pointTypes = listOf(GenericPoint.DELIVERY)
        )

    }

    private fun showUploadMapDialog() {
        EasyDialog.getInstance(requireContext()).confirm(getString(R.string.text_confirm_to_upload_map_info_to_server)) { dialog, id ->
            dialog.dismiss()
            if (id == R.id.btn_confirm) {
                DispatchManager.release()
                EasyDialog.getLoadingInstance(requireContext()).loading(getString(R.string.text_is_upload_map_info_to_server))
                CoroutineScope(Dispatchers.IO).launch {
                    DispatchManager.request(
                        networkCall = {
                            DispatchManager.getApiService().uploadMap(
                                Urls.getMapUploadUrl(RobotInfo.dispatchSetting.serverAddress),
                                MapInfoUploadReq(
                                    useElevator = false,
                                    hostname = RobotInfo.ROSHostname,
                                    maps = listOf(GenericMap(RobotInfo.currentMapEvent.map, RobotInfo.currentMapEvent.alias, PointCacheInfo.pathModelPoint!!))
                                )
                            )
                        },
                        onSuccess = {
                            withContext(Dispatchers.Main) {
                                if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
                                EasyDialog.getInstance(requireContext()).warnError(getString(R.string.text_upload_map_info_success))
                            }
                        },
                        onFailure = {
                            Timber.w(it, "上传地图信息失败")
                            val failureTip = when (it) {
                                is IOException -> getString(R.string.text_upload_map_info_failure_cause_network)
                                is RequestFailureException -> {
                                    when (it.code) {
                                        ResponseCode.PARSE_TOKEN_FAILED_ERROR, ResponseCode.ROBOT_NOT_BOUND_TO_ROOM_ERROR -> getString(R.string.text_upload_map_info_failure_cause_parse_token_failed)
                                        ResponseCode.MAP_LIST_EMPTY_ERROR, ResponseCode.MAP_ILLEGAL_ERROR -> getString(R.string.text_upload_map_info_failure_cause_map_empty)
                                        ResponseCode.MISS_CHARGE_AND_PRODUCTION_POINT_ERROR -> getString(R.string.text_uplaod_map_failure_cause_miss_charge_and_production_point)
                                        ResponseCode.MISS_CHARGE_POINT_ERROR -> getString(R.string.text_upload_map_info_failure_cause_miss_charge_point)
                                        ResponseCode.MISS_PRODUCTION_POINT_ERROR -> getString(R.string.text_upload_map_info_failure_cause_miss_production_point)
                                        ResponseCode.POINT_CONNECTION_ERROR -> {
                                            val list = Json.decodeFromString<List<Pair<String, String>>>(it.msg)
                                            val unConnectionPaths = list.map { "${it.first} -> ${it.second}" }.toString().replace("[", "").replace("]", "").replace(",", "\n")
                                            getString(R.string.text_upload_map_info_failure_cause_point_connection_error_, unConnectionPaths)
                                        }

                                        else -> getString(R.string.text_upload_map_info_failure_undefined_exception, it.code, it.message)
                                    }
                                }

                                else -> getString(R.string.text_upload_map_info_failure_unknown_exception, it.message)

                            }
                            withContext(Dispatchers.Main) {
                                if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
                                EasyDialog.getInstance(requireContext()).warnError(failureTip)

                            }
                        }
                    )
                }
            }
        }
    }
}