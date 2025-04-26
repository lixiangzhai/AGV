package com.reeman.agv.fragments.setting;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableEmitter;
import io.reactivex.rxjava3.core.ObservableOnSubscribe;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import kotlin.Pair;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.reeman.agv.R;
import com.reeman.agv.activities.WiFiConnectActivity;
import com.reeman.agv.base.BaseFragment;
import com.reeman.agv.calling.CallingInfo;
import com.reeman.agv.utils.PackageUtils;
import com.reeman.commons.constants.Constants;
import com.reeman.agv.contract.ElevatorSettingContract;
import com.reeman.agv.presenter.impl.ElevatorSettingPresenter;
import com.reeman.commons.utils.FileMapUtils;
import com.reeman.points.model.request.MapVO;
import com.reeman.commons.utils.SpManager;
import com.reeman.agv.utils.ToastUtils;
import com.reeman.agv.widgets.EasyDialog;
import com.reeman.agv.widgets.FloatingCallingListView;
import com.reeman.agv.widgets.MapChooseDialog;
import com.reeman.points.utils.PointCacheInfo;
import com.reeman.points.utils.PointCacheUtil;
import com.warkiz.widget.IndicatorSeekBar;
import com.warkiz.widget.OnSeekChangeListener;
import com.warkiz.widget.SeekParams;

import java.io.File;
import java.util.HashMap;
import java.util.List;

public class ElevatorSettingFragment extends BaseFragment implements ElevatorSettingContract.View, MapChooseDialog.OnMapListItemSelectedListener, View.OnClickListener {

    private ElevatorSettingPresenter presenter;
    private LinearLayout layoutElevatorSettings, layoutEnterAndLeaveElevatorGeneratePathCount,
            layoutElevatorUnreachableRetryTimeInterval, layoutOutsideNetworkSetting, layoutInsideNetworkSetting,
            layoutNetworkSetting, layoutWaitingElevatorTimeoutRetryTimeInterval, layoutGatewayIdSetting;
    private TextView tvChargingPileMap, tvProductionPointMap;
    private IndicatorSeekBar isbWaitingElevatorTimeoutRetryTimeInterval;
    private IndicatorSeekBar isbEnterAndLeaveElevatorGeneratePathCount;
    private IndicatorSeekBar isbElevatorUnreachableRetryTimeInterval;
    private RadioGroup rgElevatorSwitchControl;
    private RadioGroup rgElevatorCommunicationMethod;
    private TextView tvOutsideNetwork;
    private TextView tvInsideNetwork;
    private EditText etGatewayId;
    private Gson gson;

    @Override
    protected int getLayoutRes() {
        return R.layout.fragment_elevator_setting;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        presenter = new ElevatorSettingPresenter(this);
        gson = new Gson();
        if (robotInfo.getElevatorSetting().waitingElevatorTimeoutRetryTimeInterval < 3) {
            robotInfo.getElevatorSetting().waitingElevatorTimeoutRetryTimeInterval = 3;
            SpManager.getInstance().edit().putString(Constants.KEY_ELEVATOR_SETTING, gson.toJson(robotInfo.getElevatorSetting())).apply();
        }

        initElevatorSetting();
    }

    @Override
    public void onPause() {
        super.onPause();
        updateCallingMap();
    }

    private void initElevatorSetting() {
        layoutElevatorSettings = findView(R.id.layout_elevator_settings);
        layoutEnterAndLeaveElevatorGeneratePathCount = findView(R.id.layout_enter_and_leave_elevator_generate_path_count);
        layoutElevatorUnreachableRetryTimeInterval = findView(R.id.layout_elevator_unreachable_retry_time_interval);
        layoutWaitingElevatorTimeoutRetryTimeInterval = findView(R.id.layout_waiting_elevator_timeout_retry_time_interval);
        layoutOutsideNetworkSetting = findView(R.id.layout_outside_network_setting);
        layoutInsideNetworkSetting = findView(R.id.layout_inside_network_setting);
        layoutNetworkSetting = findView(R.id.layout_network_setting);
        layoutGatewayIdSetting = findView(R.id.layout_gateway_id);
        findView(R.id.ib_decrease_enter_and_leave_elevator_generate_path_count).setOnClickListener(this);
        findView(R.id.ib_increase_enter_and_leave_elevator_generate_path_count).setOnClickListener(this);
        isbEnterAndLeaveElevatorGeneratePathCount = findView(R.id.isb_enter_and_leave_elevator_generate_path_count);
        isbEnterAndLeaveElevatorGeneratePathCount.setProgress(robotInfo.getElevatorSetting().generatePathCount);
        isbEnterAndLeaveElevatorGeneratePathCount.setOnSeekChangeListener(new OnSeekChangeListener() {
            @Override
            public void onSeeking(SeekParams seekParams) {

            }

            @Override
            public void onStartTrackingTouch(IndicatorSeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(IndicatorSeekBar seekBar) {
                robotInfo.getElevatorSetting().generatePathCount = seekBar.getProgress();
                SpManager.getInstance().edit().putString(Constants.KEY_ELEVATOR_SETTING, gson.toJson(robotInfo.getElevatorSetting())).apply();
            }
        });
        findView(R.id.ib_decrease_waiting_elevator_time_out_retry_time_interval).setOnClickListener(this);
        findView(R.id.ib_increase_waiting_elevator_time_out_retry_time_interval).setOnClickListener(this);
        findView(R.id.ib_decrease_elevator_unreachable_retry_time_interval).setOnClickListener(this);
        findView(R.id.ib_increase_elevator_unreachable_retry_time_interval).setOnClickListener(this);
        isbWaitingElevatorTimeoutRetryTimeInterval = findView(R.id.isb_waiting_elevator_time_out_retry_time_interval);
        isbWaitingElevatorTimeoutRetryTimeInterval.setProgress(robotInfo.getElevatorSetting().waitingElevatorTimeoutRetryTimeInterval);
        isbWaitingElevatorTimeoutRetryTimeInterval.setOnSeekChangeListener(new OnSeekChangeListener() {
            @Override
            public void onSeeking(SeekParams seekParams) {

            }

            @Override
            public void onStartTrackingTouch(IndicatorSeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(IndicatorSeekBar seekBar) {
                robotInfo.getElevatorSetting().waitingElevatorTimeoutRetryTimeInterval = seekBar.getProgress();
                SpManager.getInstance().edit().putString(Constants.KEY_ELEVATOR_SETTING, gson.toJson(robotInfo.getElevatorSetting())).apply();
            }
        });
        isbElevatorUnreachableRetryTimeInterval = findView(R.id.isb_elevator_unreachable_retry_time_interval);
        isbElevatorUnreachableRetryTimeInterval.setProgress(robotInfo.getElevatorSetting().enterOrLeavePointUnreachableRetryTimeInterval);
        isbElevatorUnreachableRetryTimeInterval.setOnSeekChangeListener(new OnSeekChangeListener() {
            @Override
            public void onSeeking(SeekParams seekParams) {

            }

            @Override
            public void onStartTrackingTouch(IndicatorSeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(IndicatorSeekBar seekBar) {
                robotInfo.getElevatorSetting().enterOrLeavePointUnreachableRetryTimeInterval = seekBar.getProgress();
                SpManager.getInstance().edit().putString(Constants.KEY_ELEVATOR_SETTING, gson.toJson(robotInfo.getElevatorSetting())).apply();
            }
        });
        rgElevatorSwitchControl = findView(R.id.rg_elevator_switch_control);
        rgElevatorSwitchControl.check(robotInfo.getElevatorSetting().open ? R.id.rb_open_elevator : R.id.rb_close_elevator);
        layoutElevatorSettings.setVisibility(robotInfo.getElevatorSetting().open ? View.VISIBLE : View.GONE);
        rgElevatorSwitchControl.setOnCheckedChangeListener((radioGroup, i) -> {
            if (robotInfo.isDispatchModeOpened()) {
                rgElevatorSwitchControl.check(R.id.rb_close_elevator);
                EasyDialog.getInstance(requireContext()).warnError(getString(R.string.text_cannot_open_elevator_control_mode_when_use_dispatch_mode));
                return;
            }
            boolean elevatorControlOpened = i == R.id.rb_open_elevator;
            layoutElevatorSettings.setVisibility(elevatorControlOpened ? View.VISIBLE : View.GONE);
            robotInfo.getElevatorSetting().open = elevatorControlOpened;
            CallingInfo.INSTANCE.getHeartBeatInfo().setElevatorMode(robotInfo.getElevatorSetting().open);
            if (FloatingCallingListView.isShow()) FloatingCallingListView.getInstance().close();
            CallingInfo.INSTANCE.removeAllCallingPoints();
            SpManager.getInstance().edit().putString(Constants.KEY_ELEVATOR_SETTING, gson.toJson(robotInfo.getElevatorSetting())).apply();
            PointCacheInfo.INSTANCE.clearAllCacheData();
            PointCacheUtil.INSTANCE.clearAllLocalData();
            robotInfo.getReturningSetting().defaultProductionPoint = "";
            SpManager.getInstance().edit().putString(Constants.KEY_RETURNING_CONFIG, gson.toJson(robotInfo.getReturningSetting())).apply();
            Observable.create((ObservableOnSubscribe<Integer>) emitter -> {
                FileMapUtils.clear(
                        Environment.getExternalStorageDirectory()
                                .toString() + File.separator + PackageUtils.getAppName(
                                requireContext()
                        ) + File.separator + Constants.KEY_BUTTON_MAP_WITH_ELEVATOR_PATH
                );
                CallingInfo.INSTANCE.setCallingButtonMapWithElevator(new HashMap<>());
                FileMapUtils.clear(
                        Environment.getExternalStorageDirectory()
                                .toString() + File.separator + PackageUtils.getAppName(
                                requireContext()
                        ) + File.separator + Constants.KEY_BUTTON_MAP_PATH
                );
                CallingInfo.INSTANCE.setCallingButtonMap(new HashMap<>());
                        FileMapUtils.clear(
                                Environment.getExternalStorageDirectory()
                                        .toString() + File.separator + PackageUtils.getAppName(
                                        requireContext()
                                ) + File.separator + Constants.KEY_BUTTON_MAP_WITH_QRCODE_TASK_PATH
                        );
                        CallingInfo.INSTANCE.setCallingButtonWithQRCodeModelTaskMap(new HashMap<>());
            })
                    .subscribeOn(Schedulers.io())
                    .subscribe();
        });
        rgElevatorCommunicationMethod = findView(R.id.rg_elevator_switch_communication_method);
        int elevatorCommunicationMethodId = 0;
        int communicationMethod = robotInfo.getElevatorSetting().communicationMethod;
        if (communicationMethod == 0) {
            elevatorCommunicationMethodId = R.id.rb_network;
            updateViewByCommunicationMethod(View.VISIBLE);
            layoutGatewayIdSetting.setVisibility(View.GONE);
        } else if (communicationMethod == 1) {
            elevatorCommunicationMethodId = R.id.rb_network_and_lora;
            updateViewByCommunicationMethod(View.GONE);
            layoutGatewayIdSetting.setVisibility(View.GONE);
        } else if (communicationMethod == 2) {
            elevatorCommunicationMethodId = R.id.rb_lora;
            updateViewByCommunicationMethod(View.GONE);
            layoutGatewayIdSetting.setVisibility(View.VISIBLE);
        }
        rgElevatorCommunicationMethod.check(elevatorCommunicationMethodId);
        rgElevatorCommunicationMethod.setOnCheckedChangeListener((group, checkedId) -> {
            int communicationMethod1 = 0;
            if (checkedId == R.id.rb_network) {
                updateViewByCommunicationMethod(View.VISIBLE);
                layoutGatewayIdSetting.setVisibility(View.GONE);
            } else if (checkedId == R.id.rb_network_and_lora) {
                communicationMethod1 = 1;
                updateViewByCommunicationMethod(View.GONE);
                layoutGatewayIdSetting.setVisibility(View.GONE);
            } else if (checkedId == R.id.rb_lora) {
                communicationMethod1 = 2;
                updateViewByCommunicationMethod(View.GONE);
                layoutGatewayIdSetting.setVisibility(View.VISIBLE);
            }
            robotInfo.getElevatorSetting().communicationMethod = communicationMethod1;
            SpManager.getInstance().edit().putString(Constants.KEY_ELEVATOR_SETTING, gson.toJson(robotInfo.getElevatorSetting())).apply();
        });
        etGatewayId = findView(R.id.et_gateway_id);
        etGatewayId.setText(robotInfo.getElevatorSetting().gatewayId);
        etGatewayId.setOnFocusChangeListener(this::hideKeyBoard);
        findView(R.id.btn_save_gateway_id_setting).setOnClickListener(this);
        layoutEnterAndLeaveElevatorGeneratePathCount.setVisibility(robotInfo.getElevatorSetting().isDetectionSwitchOpen ? View.VISIBLE : View.GONE);
        layoutElevatorUnreachableRetryTimeInterval.setVisibility(robotInfo.getElevatorSetting().isDetectionSwitchOpen ? View.VISIBLE : View.GONE);
        RadioGroup rgEnterAndLeaveElevatorDetectionSwitch = findView(R.id.rg_enter_and_leave_elevator_detection_switch);
        rgEnterAndLeaveElevatorDetectionSwitch.check(robotInfo.getElevatorSetting().isDetectionSwitchOpen ? R.id.rb_open_enter_and_leave_elevator_detection_switch : R.id.rb_close_enter_and_leave_elevator_detection_switch);
        rgEnterAndLeaveElevatorDetectionSwitch.setOnCheckedChangeListener((group, checkedId) -> {
            robotInfo.getElevatorSetting().isDetectionSwitchOpen = checkedId == R.id.rb_open_enter_and_leave_elevator_detection_switch;
            layoutEnterAndLeaveElevatorGeneratePathCount.setVisibility(robotInfo.getElevatorSetting().isDetectionSwitchOpen ? View.VISIBLE : View.GONE);
            layoutElevatorUnreachableRetryTimeInterval.setVisibility(robotInfo.getElevatorSetting().isDetectionSwitchOpen ? View.VISIBLE : View.GONE);
            SpManager.getInstance().edit().putString(Constants.KEY_ELEVATOR_SETTING, gson.toJson(robotInfo.getElevatorSetting())).apply();
        });
        tvChargingPileMap = findView(R.id.tv_charging_pile_map);
        tvProductionPointMap = findView(R.id.tv_production_point_map);
        tvChargingPileMap.setText(robotInfo.getChargingPileMap() == null ? getString(R.string.text_please_choose_charging_pile_map) : robotInfo.getChargingPileMap().getFirst());
        tvChargingPileMap.setTextColor(robotInfo.getChargingPileMap() == null ? Color.RED : getResources().getColor(R.color.text_gray));
        tvProductionPointMap.setText(robotInfo.getProductionPointMap() == null ? getString(R.string.text_please_choose_production_point_map) : robotInfo.getProductionPointMap().getFirst());
        tvProductionPointMap.setTextColor(robotInfo.getChargingPileMap() == null ? Color.RED : getResources().getColor(R.color.text_gray));
        findView(R.id.btn_charging_pile_setting).setOnClickListener(this);
        findView(R.id.btn_production_point_map_setting).setOnClickListener(this);
        RadioGroup rgElevatorDoorDirectionSwitchControl = findView(R.id.rg_elevator_door_direction_switch_control);
        rgElevatorDoorDirectionSwitchControl.check(robotInfo.getElevatorSetting().isSingleDoor ? R.id.rb_single_door_elevator : R.id.rb_double_door_elevator);
        rgElevatorDoorDirectionSwitchControl.setOnCheckedChangeListener((group, checkedId) -> {
            robotInfo.getElevatorSetting().isSingleDoor = checkedId == R.id.rb_single_door_elevator;
            SpManager.getInstance().edit().putString(Constants.KEY_ELEVATOR_SETTING, gson.toJson(robotInfo.getElevatorSetting())).apply();
        });
        RadioGroup rgElevatorNetworkSetting = findView(R.id.rg_elevator_network_setting);
        tvOutsideNetwork = findView(R.id.tv_outside_network);
        tvInsideNetwork = findView(R.id.tv_inside_network);
        findView(R.id.btn_choose_outside_network).setOnClickListener(this);
        findView(R.id.btn_choose_inside_network).setOnClickListener(this);
        if (Build.PRODUCT.equals("rk312x")) {
            layoutNetworkSetting.setVisibility(View.GONE);
            layoutOutsideNetworkSetting.setVisibility(View.GONE);
            layoutInsideNetworkSetting.setVisibility(View.GONE);
        }else {
            layoutOutsideNetworkSetting.setVisibility(robotInfo.getElevatorSetting().isSingleNetwork ? View.GONE : View.VISIBLE);
            layoutInsideNetworkSetting.setVisibility(robotInfo.getElevatorSetting().isSingleNetwork ? View.GONE : View.VISIBLE);
        }
        rgElevatorNetworkSetting.check(robotInfo.getElevatorSetting().isSingleNetwork ? R.id.rb_single_network : R.id.rb_double_network);
        if (robotInfo.getElevatorSetting().outsideNetwork == null) {
            tvOutsideNetwork.setText(getString(R.string.text_please_choose_outside_network));
            tvOutsideNetwork.setTextColor(Color.RED);
        } else {
            tvOutsideNetwork.setText(robotInfo.getElevatorSetting().outsideNetwork.getFirst());
            tvOutsideNetwork.setTextColor(getResources().getColor(R.color.text_gray));
        }
        if (robotInfo.getElevatorSetting().insideNetwork == null) {
            tvInsideNetwork.setText(getString(R.string.text_please_choose_inside_network));
            tvInsideNetwork.setTextColor(Color.RED);
        } else {
            tvInsideNetwork.setText(robotInfo.getElevatorSetting().insideNetwork.getFirst());
            tvInsideNetwork.setTextColor(getResources().getColor(R.color.text_gray));
        }
        rgElevatorNetworkSetting.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_single_network) {
                if (!robotInfo.isElevatorMode()) {
                    EasyDialog.getInstance(requireContext()).warnError(getString(R.string.text_please_check_elevator_model_first));
                    return;
                }
                EasyDialog.getInstance(requireActivity()).confirm(getString(R.string.text_will_not_auto_switch_network_after_enter_elevator), (dialog, id) -> {
                    dialog.dismiss();
                    if (id == R.id.btn_confirm) {
                        layoutOutsideNetworkSetting.setVisibility(View.GONE);
                        layoutInsideNetworkSetting.setVisibility(View.GONE);
                        robotInfo.getElevatorSetting().isSingleNetwork = true;
                        robotInfo.getElevatorSetting().outsideNetwork = null;
                        robotInfo.getElevatorSetting().insideNetwork = new Pair<>("RBTEC6200", "TK62002@22");
                        SpManager.getInstance().edit().putString(Constants.KEY_ELEVATOR_SETTING, gson.toJson(robotInfo.getElevatorSetting())).apply();
                    } else {
                        group.check(R.id.rb_double_network);
                    }
                });
            } else {
                if (!robotInfo.isElevatorMode()) {
                    EasyDialog.getInstance(requireContext()).warnError(getString(R.string.text_please_check_elevator_model_first));
                    return;
                }
                EasyDialog.getInstance(requireActivity()).confirm(getString(R.string.text_will_auto_switch_network_after_enter_elevator), (dialog, id) -> {
                    dialog.dismiss();
                    if (id == R.id.btn_confirm) {
                        layoutOutsideNetworkSetting.setVisibility(View.VISIBLE);
                        layoutInsideNetworkSetting.setVisibility(View.VISIBLE);
                        robotInfo.getElevatorSetting().isSingleNetwork = false;
                        SpManager.getInstance().edit().putString(Constants.KEY_ELEVATOR_SETTING, gson.toJson(robotInfo.getElevatorSetting())).apply();
                    } else {
                        group.check(R.id.rb_single_network);
                    }
                });
            }
        });
        int languageType = SpManager.getInstance().getInt(Constants.KEY_LANGUAGE_TYPE, Constants.DEFAULT_LANGUAGE_TYPE);
        if (languageType != 1){
            rgElevatorCommunicationMethod.setOrientation(RadioGroup.VERTICAL);
            LinearLayout layoutProductionPointSetting = findView(R.id.layout_production_point_setting);
            layoutProductionPointSetting.setOrientation(LinearLayout.VERTICAL);
            LinearLayout layoutChargePointSetting = findView(R.id.layout_charge_point_setting);
            layoutChargePointSetting.setOrientation(LinearLayout.VERTICAL);
        }
    }

    private void updateViewByCommunicationMethod(int visibility) {
        if (Build.PRODUCT.equals("rk312x")) {
            layoutNetworkSetting.setVisibility(View.GONE);
            layoutOutsideNetworkSetting.setVisibility(View.GONE);
            layoutInsideNetworkSetting.setVisibility(View.GONE);
        } else {
            layoutNetworkSetting.setVisibility(visibility);
            layoutOutsideNetworkSetting.setVisibility(visibility);
            layoutInsideNetworkSetting.setVisibility(visibility);
        }
        layoutWaitingElevatorTimeoutRetryTimeInterval.setVisibility(visibility);
    }

    @Override
    public void onMapListLoadedSuccess(List<MapVO> mapVOList, boolean checkChargingPile) {
        if (EasyDialog.isShow())
            EasyDialog.getInstance(requireContext()).dismiss();
        MapChooseDialog mapChooseDialog = new MapChooseDialog(requireContext(), mapVOList, false, checkChargingPile, this);
        mapChooseDialog.show();
    }

    @Override
    public void onMapListLoadedFailed(String msg) {
        if (EasyDialog.isShow())
            EasyDialog.getInstance(requireContext()).dismiss();
        EasyDialog.getInstance(requireContext()).warnError(msg);
        robotInfo.getElevatorSetting().chargingPileMap = null;
        robotInfo.getElevatorSetting().productionPointMap = null;
        SpManager.getInstance().edit().putString(Constants.KEY_ELEVATOR_SETTING, gson.toJson(robotInfo.getElevatorSetting())).apply();
        tvChargingPileMap.setText(getString(R.string.text_please_choose_charging_pile_map));
        tvChargingPileMap.setTextColor(Color.RED);
        tvProductionPointMap.setText(getString(R.string.text_please_choose_production_point_map));
        tvProductionPointMap.setTextColor(Color.RED);

    }

    @Override
    public void onPointsLoadedFailed(String msg, boolean checkChargingPile) {
        if (EasyDialog.isShow())
            EasyDialog.getInstance(requireContext()).dismiss();
        EasyDialog.getInstance(requireContext()).warnError(msg);
        if (checkChargingPile) {
            robotInfo.getElevatorSetting().chargingPileMap = null;
            tvChargingPileMap.setText(getString(R.string.text_please_choose_charging_pile_map));
            tvChargingPileMap.setTextColor(Color.RED);
        } else {
            robotInfo.getElevatorSetting().productionPointMap = null;
            tvProductionPointMap.setText(getString(R.string.text_please_choose_production_point_map));
            tvProductionPointMap.setTextColor(Color.RED);
        }
        SpManager.getInstance().edit().putString(Constants.KEY_ELEVATOR_SETTING, gson.toJson(robotInfo.getElevatorSetting())).apply();
    }

    @Override
    public void onPointsLoadedSuccess(String alias, String map, boolean checkChargingPile) {
        if (EasyDialog.isShow())
            EasyDialog.getInstance(requireContext()).dismiss();
        if (checkChargingPile) {
            robotInfo.getElevatorSetting().chargingPileMap = new Pair<>(alias, map);
            tvChargingPileMap.setText(alias);
            tvChargingPileMap.setTextColor(getResources().getColor(R.color.text_gray));
        } else {
            robotInfo.getElevatorSetting().productionPointMap = new Pair<>(alias, map);
            tvProductionPointMap.setText(alias);
            tvProductionPointMap.setTextColor(getResources().getColor(R.color.text_gray));
        }
        SpManager.getInstance().edit().putString(Constants.KEY_ELEVATOR_SETTING, gson.toJson(robotInfo.getElevatorSetting())).apply();
        ToastUtils.showShortToast(getString(R.string.text_choose_default_map_success));
    }

    @Override
    public void onMapListItemSelected(MapChooseDialog mapChooseDialog, MapVO map, boolean checkChargingPile) {
        mapChooseDialog.dismiss();
        presenter.onPointsLoadByMap(requireContext(), map.name, map.alias, checkChargingPile);

    }

    @Override
    public void onNoMapSelected(boolean checkChargingPile) {
        if (robotInfo.isElevatorMode()) {
            ToastUtils.showShortToast(getString(checkChargingPile ? R.string.text_please_choose_charging_pile_map_or_cannot_start_task : R.string.text_please_choose_production_point_map_or_cannot_start_task));
            if (checkChargingPile) {
                robotInfo.getElevatorSetting().chargingPileMap = null;
                tvChargingPileMap.setText(getString(R.string.text_please_choose_charging_pile_map));
                tvChargingPileMap.setTextColor(Color.RED);
            } else {
                robotInfo.getElevatorSetting().productionPointMap = null;
                tvProductionPointMap.setText(getString(R.string.text_please_choose_production_point_map));
                tvProductionPointMap.setTextColor(Color.RED);
            }
            SpManager.getInstance().edit().putString(Constants.KEY_ELEVATOR_SETTING, gson.toJson(robotInfo.getElevatorSetting())).apply();
        }
    }

    private boolean isValidHex(String input) {
        return input != null && input.matches("^[0-9a-fA-F]{8}$");
    }

    @Override
    protected void onCustomClickResult(int id) {
        switch (id) {
            case R.id.btn_save_gateway_id_setting:
                Editable text = etGatewayId.getText();
                if (text == null) {
                    etGatewayId.setError(getString(R.string.text_please_input_gateway_id));
                    ToastUtils.showShortToast(getString(R.string.text_please_input_gateway_id));
                    return;
                }
                String textString = text.toString();
                if (!isValidHex(textString)) {
                    etGatewayId.setError(getString(R.string.text_please_input_valid_gateway_id));
                    ToastUtils.showShortToast(getString(R.string.text_please_input_valid_gateway_id));
                    return;
                }
                robotInfo.getElevatorSetting().gatewayId = textString;
                SpManager.getInstance().edit().putString(Constants.KEY_ELEVATOR_SETTING, gson.toJson(robotInfo.getElevatorSetting())).apply();
                break;
            case R.id.btn_choose_outside_network:
                Intent intent1 = new Intent(getActivity(), WiFiConnectActivity.class);
                intent1.putExtra(Constants.START_FROM_ELEVATOR_SETTING, true);
                startActivityForResult(intent1, Constants.REQUEST_OF_OUTSIDE_NETWORK);
                break;
            case R.id.btn_choose_inside_network:
                Intent intent2 = new Intent(getActivity(), WiFiConnectActivity.class);
                intent2.putExtra(Constants.START_FROM_ELEVATOR_SETTING, true);
                startActivityForResult(intent2, Constants.REQUEST_OF_INSIDE_NETWORK);
                break;
            case R.id.ib_decrease_waiting_elevator_time_out_retry_time_interval:
            case R.id.ib_increase_waiting_elevator_time_out_retry_time_interval:
                onAdjustRecallTimeBtnClick(id);
                break;
            case R.id.ib_decrease_elevator_unreachable_retry_time_interval:
            case R.id.ib_increase_elevator_unreachable_retry_time_interval:
                onAdjustRecallTime2BtnClick(id);
                break;
            case R.id.ib_decrease_enter_and_leave_elevator_generate_path_count:
            case R.id.ib_increase_enter_and_leave_elevator_generate_path_count:
                onAdjustgeneratePathCountBtnClick(id);
                break;
            case R.id.btn_charging_pile_setting:
            case R.id.btn_production_point_map_setting:
                if (!robotInfo.isElevatorMode()) {
                    EasyDialog.getInstance(requireContext()).warnError(getString(R.string.text_please_check_elevator_model_first));
                    return;
                }
                presenter.onLoadMaps(requireContext(), id == R.id.btn_charging_pile_setting ? robotInfo.getChargingPileMap() : robotInfo.getProductionPointMap(), id == R.id.btn_charging_pile_setting);
                break;
        }
    }

    @Override
    public void onClick(View view) {
        super.onClick(view);
    }

    private void onAdjustgeneratePathCountBtnClick(int id) {
        int progress = isbEnterAndLeaveElevatorGeneratePathCount.getProgress();
        if (id == R.id.ib_decrease_enter_and_leave_elevator_generate_path_count) {
            if (progress <= 1) return;
            progress--;
        } else {
            if (progress >= 60) return;
            progress++;
        }
        isbEnterAndLeaveElevatorGeneratePathCount.setProgress(progress);
        robotInfo.getElevatorSetting().generatePathCount = progress;
        SpManager.getInstance().edit().putString(Constants.KEY_ELEVATOR_SETTING, gson.toJson(robotInfo.getElevatorSetting())).apply();
    }

    private void onAdjustRecallTime2BtnClick(int id) {
        int progress = isbElevatorUnreachableRetryTimeInterval.getProgress();
        if (id == R.id.ib_decrease_elevator_unreachable_retry_time_interval) {
            if (progress <= 1) return;
            progress--;
        } else {
            if (progress >= 10) return;
            progress++;
        }
        isbElevatorUnreachableRetryTimeInterval.setProgress(progress);
        robotInfo.getElevatorSetting().enterOrLeavePointUnreachableRetryTimeInterval = progress;
        SpManager.getInstance().edit().putString(Constants.KEY_ELEVATOR_SETTING, gson.toJson(robotInfo.getElevatorSetting())).apply();
    }

    private void onAdjustRecallTimeBtnClick(int id) {
        int progress = isbWaitingElevatorTimeoutRetryTimeInterval.getProgress();
        if (id == R.id.ib_decrease_waiting_elevator_time_out_retry_time_interval) {
            if (progress <= 1) return;
            progress--;
        } else {
            if (progress >= 10) return;
            progress++;
        }
        isbWaitingElevatorTimeoutRetryTimeInterval.setProgress(progress);
        robotInfo.getElevatorSetting().waitingElevatorTimeoutRetryTimeInterval = progress;
        SpManager.getInstance().edit().putString(Constants.KEY_ELEVATOR_SETTING, gson.toJson(robotInfo.getElevatorSetting())).apply();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.REQUEST_OF_OUTSIDE_NETWORK) {
            if (resultCode == Constants.RESULT_OF_SUCCESS) {
                String stringExtra = data.getStringExtra(Constants.RESULT_WIFI_INFO);
                Pair<String, String> wifiInfo = gson.fromJson(stringExtra, new TypeToken<Pair<String, String>>() {
                }.getType());
                tvOutsideNetwork.setText(wifiInfo.getFirst());
                tvOutsideNetwork.setTextColor(getResources().getColor(R.color.text_gray));
                robotInfo.getElevatorSetting().outsideNetwork = wifiInfo;
            }
            SpManager.getInstance().edit().putString(Constants.KEY_ELEVATOR_SETTING, gson.toJson(robotInfo.getElevatorSetting())).apply();
        } else if (requestCode == Constants.REQUEST_OF_INSIDE_NETWORK) {
            if (resultCode == Constants.RESULT_OF_SUCCESS) {
                String stringExtra = data.getStringExtra(Constants.RESULT_WIFI_INFO);
                Pair<String, String> wifiInfo = gson.fromJson(stringExtra, new TypeToken<Pair<String, String>>() {
                }.getType());
                tvInsideNetwork.setText(wifiInfo.getFirst());
                tvInsideNetwork.setTextColor(getResources().getColor(R.color.text_gray));
                robotInfo.getElevatorSetting().insideNetwork = wifiInfo;
            }
            SpManager.getInstance().edit().putString(Constants.KEY_ELEVATOR_SETTING, gson.toJson(robotInfo.getElevatorSetting())).apply();
        }
    }
}
