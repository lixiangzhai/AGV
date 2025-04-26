package com.reeman.agv.activities;

import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.gson.Gson;
import com.reeman.agv.R;
import com.reeman.agv.base.BaseActivity;
import com.reeman.agv.calling.event.CallingTaskEvent;
import com.reeman.agv.calling.event.ChargeTaskEvent;
import com.reeman.agv.calling.event.NormalTaskEvent;
import com.reeman.agv.calling.event.QRCodeTaskEvent;
import com.reeman.agv.calling.event.ReturnTaskEvent;
import com.reeman.agv.calling.event.RouteTaskEvent;
import com.reeman.commons.constants.Constants;
import com.reeman.commons.event.AGVDockResultEvent;
import com.reeman.commons.event.ApplyMapEvent;
import com.reeman.commons.event.CallingModelDisconnectedEvent;
import com.reeman.commons.event.CallingModelReconnectSuccessEvent;
import com.reeman.commons.event.CloseDoorFailedEvent;
import com.reeman.commons.event.DoorClosedEvent;
import com.reeman.commons.event.DoorOpenedEvent;
import com.reeman.commons.event.FixedPathResultEvent;
import com.reeman.commons.event.GetPlanResultEvent;
import com.reeman.commons.event.GlobalPathEvent;
import com.reeman.commons.event.InitiativeLiftingModuleStateEvent;
import com.reeman.commons.event.MissPoseEvent;
import com.reeman.commons.event.MoveStatusEvent;
import com.reeman.commons.event.OpenDoorFailedEvent;
import com.reeman.commons.event.SensorsEvent;
import com.reeman.commons.event.SpecialPlanEvent;
import com.reeman.commons.event.WheelStatusEvent;
import com.reeman.agv.constants.TaskResult;
import com.reeman.agv.contract.TaskExecutingContract;
import com.reeman.agv.elevator.exception.CustomException;
import com.reeman.agv.elevator.state.Step;
import com.reeman.agv.fragments.task.ArrivedFragment;
import com.reeman.agv.fragments.task.PauseFragment;
import com.reeman.agv.fragments.task.RunningFragment;
import com.reeman.agv.presenter.impl.TaskExecutingPresenter;
import com.reeman.commons.event.AndroidNetWorkEvent;
import com.reeman.commons.event.GreenButtonEvent;
import com.reeman.commons.event.TimeStampEvent;
import com.reeman.commons.eventbus.EventBus;
import com.reeman.dao.repository.entities.RouteWithPoints;
import com.reeman.commons.state.TaskMode;
import com.reeman.commons.utils.TimeUtil;
import com.reeman.agv.utils.ToastUtils;
import com.reeman.agv.viewModel.TaskArrivedInfoModel;
import com.reeman.agv.viewModel.TaskPauseInfoModel;
import com.reeman.agv.viewModel.TaskRunningInfoModel;
import com.reeman.agv.widgets.EasyDialog;
import com.reeman.dispatch.model.response.Task;

import java.util.Date;
import java.util.Locale;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;


public class TaskExecutingActivity extends BaseActivity implements TaskExecutingContract.View {

    private LinearLayout layoutHeader;

    private TextView tvHostname;

    private TextView tvTime;

    private TextView tvBattery;

    private TaskExecutingPresenter presenter;


    @Override
    protected int getLayoutRes() {
        return R.layout.activity_task_executing;
    }

    @Override
    protected void initData() {
        presenter = new TaskExecutingPresenter(this, this);
    }

    @Override
    protected void initCustomView() {
        layoutHeader = $(R.id.layout_task_header);
        tvHostname = $(R.id.tv_hostname);
        tvTime = $(R.id.tv_time);
        tvBattery = $(R.id.tv_battery);
    }

    @Override
    protected boolean shouldRegisterDispatchCallback() {
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshState();
        presenter.startTask(this, getIntent());
        registerObservers();
    }

    private void registerObservers() {
        EventBus.INSTANCE.registerObserver(
                this,
                AndroidNetWorkEvent.class,
                this::onAndroidNetworkChangeEvent,
                Schedulers.io(),
                AndroidSchedulers.mainThread()
        );
        EventBus.INSTANCE.registerObserver(
                this,
                GreenButtonEvent.class,
                this::onGreenButtonEvent,
                Schedulers.io(),
                AndroidSchedulers.mainThread()
        );
        if (robotInfo.getDoorControlSetting().open && robotInfo.getDoorControlSetting().communicationMethod == 1) {
            EventBus.INSTANCE.registerObserver(
                    this,
                    DoorOpenedEvent.class,
                    this::onOpenDoorSuccess,
                    Schedulers.io(),
                    AndroidSchedulers.mainThread()

            );
            EventBus.INSTANCE.registerObserver(
                    this,
                    DoorClosedEvent.class,
                    this::onCloseDoorSuccess,
                    Schedulers.io(),
                    AndroidSchedulers.mainThread()

            );
            EventBus.INSTANCE.registerObserver(
                    this,
                    OpenDoorFailedEvent.class,
                    this::onOpenDoorFailed,
                    Schedulers.io(),
                    AndroidSchedulers.mainThread()

            );
            EventBus.INSTANCE.registerObserver(
                    this,
                    CloseDoorFailedEvent.class,
                    this::onCloseDoorFailed,
                    Schedulers.io(),
                    AndroidSchedulers.mainThread()

            );
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_F2) {
            presenter.onKeyUpCodeF2();
            return false;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void onOpenDoorSuccess(DoorOpenedEvent event) {
        presenter.onOpenDoorSuccess(event);
    }

    private void onCloseDoorSuccess(DoorClosedEvent event) {
        presenter.onCloseDoorSuccess(event);
    }

    private void onOpenDoorFailed(OpenDoorFailedEvent event) {
        presenter.onOpenDoorFailed(event);
    }

    private void onCloseDoorFailed(CloseDoorFailedEvent event) {
        presenter.onCloseDoorFailed(event);
    }

    @Override
    protected void onCallingModelDisconnectEvent(CallingModelDisconnectedEvent event) {
        presenter.onCallingModelDisconnected(event.getEvent());
    }

    @Override
    protected void onCallingModelReconnectedEvent(CallingModelReconnectSuccessEvent event) {
        presenter.onCallingModelReconnected();
    }

    private void onGreenButtonEvent(GreenButtonEvent event) {
        presenter.onKeyUpCodeF2();
    }

    private void refreshState() {
        tvHostname.setText(robotInfo.getROSHostname());
        tvTime.setText(TimeUtil.formatHourAndMinute(new Date()));
        tvBattery.setText(String.format(Locale.CHINA,"%s%%", robotInfo.getPowerLevel()));
    }

    @Override
    public void onGlobalPathEvent(@NonNull GlobalPathEvent event) {
        presenter.onGlobalPathEvent(event.getPathList());
    }

    @Override
    protected void onCustomTimeStamp(TimeStampEvent event) {
        tvTime.setText(TimeUtil.formatHourAndMinute(event.getTime()));
        presenter.onTimeUpdate(event.getTime());
    }

    @Override
    protected void onCustomBatteryChange(int level) {
        tvBattery.setText(String.format(Locale.CHINA,"%s%%", level));
    }

    @Override
    protected void onNavigationStartResult(int code, String name) {
        super.onNavigationStartResult(code, name);
        presenter.onNavigationStartResult(code, name);
    }

    @Override
    protected void onNavigationCompleteResult(int code, String name, float mileage) {
        presenter.onNavigationCompleteResult(code, name, mileage);
    }

    @Override
    protected void onNavigationCancelResult(int code) {
        presenter.onNavigationCancelResult(code);
    }

    @Override
    protected void onCustomEmergencyStopStateChange(int emergencyStopState) {
        if (emergencyStopState == 0) {
            presenter.onEmergencyButtonDown();
        } else {
            presenter.onEmergencyButtonUp();
        }
    }

    @Override
    protected void onCustomDockFailed() {
        presenter.onDockFailed();
    }

    @Override
    protected void onCustomAntiFallEvent() {
        presenter.onAntiFall();
    }

    @Override
    protected void onCustomPosition(double[] position) {
        presenter.onPositionUpdate(position);
    }

    @Override
    protected void onCustomInitPose(double[] currentPosition) {
        super.onCustomInitPose(currentPosition);
        presenter.onCustomInitPose(currentPosition);
    }

    @Override
    protected void onCustomPowerConnected() {
        presenter.onPowerConnect();
    }

    @Override
    protected void detailWheelState(WheelStatusEvent event) {
        if (event.getState() == WheelStatusEvent.offline || event.getState() == WheelStatusEvent.wheelFailure) {
            presenter.onWheelError(event);
        }
    }

    @Override
    protected void detailSensorState(SensorsEvent event) {
        if (event.isSensorWarn()) {
            presenter.onSensorsError(event);
        }
    }

    @Override
    public void onGetPlanResultEvent(@NonNull GetPlanResultEvent event) {
        super.onGetPlanResultEvent(event);
        ToastUtils.showShortToast(event.isSuccess() ? "success" : "failed");
        presenter.onGetPlanResult(event.isSuccess());
    }

    @Override
    public void onApplyMapEvent(@NonNull ApplyMapEvent event) {
        if (robotInfo.getCurrentMapEvent().getMap().equals(event.getMap()))return;
        super.onApplyMapEvent(event);
        if (robotInfo.isRebootingROSCauseTimeJump())return;
        presenter.onApplyMapResult(event);
    }

    @Override
    public void onMissPoseEvent(@NonNull MissPoseEvent event) {
        super.onMissPoseEvent(event);
        if (event.getResult() == 1)
            presenter.onMissPose();
    }

    @Override
    public void onSpecialPlanEvent(@NonNull SpecialPlanEvent event) {
        super.onSpecialPlanEvent(event);
        presenter.onSpecialPlan(event.getRoomList());
    }

    @Override
    public void onFixedPathResultEvent(@NonNull FixedPathResultEvent event) {
        presenter.onShortDij(event);
    }

    @Override
    public void onMoveStatusEvent(@NonNull MoveStatusEvent event) {
        presenter.onEncounterObstacle();
    }

    @Override
    public void onInitiativeLiftingModuleStateEvent(@NonNull InitiativeLiftingModuleStateEvent event) {
        super.onInitiativeLiftingModuleStateEvent(event);
        presenter.onLiftResult(event.getAction(), event.getState());
    }

    @Override
    public void onAGVDockResultEvent(@NonNull AGVDockResultEvent event) {
        presenter.onQRCodeNavigationResult(event.getResult() == AGVDockResultEvent.AGV_DOCK_SUCCESS, event.getTarget());
    }

    private void switchRunningFragment(TaskRunningInfoModel model) {
        layoutHeader.setVisibility(View.GONE);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.KEY_TASK_RUNNING_INFO, new Gson().toJson(model));
        RunningFragment runningFragment = new RunningFragment(onRunningClickListener);
        runningFragment.setArguments(bundle);
        getSupportFragmentManager().beginTransaction().setCustomAnimations(R.anim.alpha_in, R.anim.alpha_out).replace(R.id.task_fragment_view, runningFragment).commit();
        Timber.w("start running :\n %s", model.toString());
    }

    private void switchPauseFragment(TaskPauseInfoModel model) {
        layoutHeader.setVisibility(View.VISIBLE);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.KEY_TASK_PAUSE_INFO, new Gson().toJson(model));
        PauseFragment pauseFragment = new PauseFragment(onPauseClickListener);
        pauseFragment.setArguments(bundle);
        getSupportFragmentManager().beginTransaction().setCustomAnimations(R.anim.alpha_in, R.anim.alpha_out).replace(R.id.task_fragment_view, pauseFragment).commit();
        Timber.w("pause task :\n %s", model.toString());
    }

    private void switchArrivedFragment(TaskArrivedInfoModel model) {
        layoutHeader.setVisibility(View.VISIBLE);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.KEY_TASK_ARRIVED_INFO, new Gson().toJson(model));
        ArrivedFragment arrivedFragment = new ArrivedFragment(onArrivedBtnListener);
        arrivedFragment.setArguments(bundle);
        getSupportFragmentManager().beginTransaction().setCustomAnimations(R.anim.alpha_in, R.anim.alpha_out).replace(R.id.task_fragment_view, arrivedFragment).commit();
        Timber.w("arrive target point :\n %s", model.toString());
    }

    private final RunningFragment.OnRunningClickListener onRunningClickListener = new RunningFragment.OnRunningClickListener() {
        @Override
        public void onClick() {
            if (isFinishing()) return;
            presenter.onPauseClick();
        }
    };

    private final ArrivedFragment.OnArrivedBtnListener onArrivedBtnListener = new ArrivedFragment.OnArrivedBtnListener() {
        @Override
        public void onReturnBtnClick() {
            presenter.onReturnClick();
        }

        @Override
        public void onLiftUpBtnClick() {
            presenter.onLiftUpClick();
        }

        @Override
        public void onLiftDownBtnClick() {
            presenter.onLiftDownClick();
        }

        @Override
        public void onGotoNextPointBtnClick() {
            presenter.onGotoNextPointClick();
        }

        @Override
        public void onCancelBtnClick() {
            presenter.onCancelClick();
        }
    };

    private final PauseFragment.OnPauseClickListener onPauseClickListener = new PauseFragment.OnPauseClickListener() {
        @Override
        public void onCallElevatorBtnClick() {
            presenter.onRecallElevatorClick();
        }

        @Override
        public void onReturnBtnClick() {
            presenter.onReturnProductPointClick();
        }

        @Override
        public void onContinueBtnClick() {
            presenter.onResumeClick();
        }

        @Override
        public void onCancelBtnClick() {
            presenter.onCancelClick();
        }

        @Override
        public void onSkipCurrentTargetBtnClick() {
            presenter.onSkipCurrentTargetClick();
        }

        @Override
        public void onLiftUpBtnClick() {
            presenter.onLiftUpClick();
        }

        @Override
        public void onLiftDownBtnClick() {
            presenter.onLiftDownClick();
        }

    };

    @Override
    public void updateRunningView(String targetFloor, String targetPoint, Step step) {
        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss();
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.task_fragment_view);
        if (!(currentFragment instanceof RunningFragment)) {
            TaskRunningInfoModel model = new TaskRunningInfoModel.Builder()
                    .setElevatorStep(step)
                    .setTargetFloor(targetFloor)
                    .setTargetPoint(targetPoint)
                    .build();
            switchRunningFragment(model);
        } else {
            runOnUiThread(() -> ((RunningFragment) currentFragment).updateTargetPoint(targetFloor, targetPoint));
        }
    }

    @Override
    public void arrivedTargetPoint(TaskMode taskMode, String routeName, long startTime, String currentFloor, String currentPoint, String nextFloor, String nextPoint, boolean showReturnBtn, boolean showLiftUpBtn, boolean showLiftDownBtn, boolean hasNextPoint, boolean autoReturn) {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.task_fragment_view);
        if (!(currentFragment instanceof ArrivedFragment)) {
            String mode = "";
            if (taskMode == TaskMode.MODE_NORMAL) {
                mode = getString(R.string.text_mode_normal);
            } else if (taskMode == TaskMode.MODE_ROUTE) {
                mode = getString(R.string.text_mode_route);
            } else if (taskMode == TaskMode.MODE_QRCODE) {
                mode = getString(R.string.text_mode_qrcode);
            }
            if (!autoReturn) {
                nextFloor = "";
                nextPoint = "";
                showReturnBtn = false;
            }
            TaskArrivedInfoModel model = new TaskArrivedInfoModel.Builder(mode)
                    .setRouteName(routeName)
                    .setTaskStartTime(TimeUtil.formatHourAndMinute(startTime))
                    .setCurrentPoint(currentPoint)
                    .setNextFloor(nextFloor)
                    .setNextPoint(nextPoint)
                    .setNowFloor(currentFloor)
                    .setShowReturnButton(showReturnBtn)
                    .setShowLiftUpButton(showLiftUpBtn)
                    .setShowLiftDownButton(showLiftDownBtn)
                    .setShowGotoNextPointButton(hasNextPoint)
                    .setShowCancelTaskButton(true)
                    .setCountDownTime(0)
                    .build();
            switchArrivedFragment(model);
        }
    }

    @Override
    public void showPauseTask(TaskPauseInfoModel model, String dialogContent) {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.task_fragment_view);
        if (!(currentFragment instanceof PauseFragment)) {
            switchPauseFragment(model);
        } else {
            ((PauseFragment) currentFragment).updateCountDownTimer(model.getCountDownTime());
        }
        if (!TextUtils.isEmpty(dialogContent)) {
            if (EasyDialog.isShow() && EasyDialog.getInstance().isAutoDismissEnable())
                EasyDialog.getInstance().dismiss();
            EasyDialog.getInstance(this).warnError(dialogContent);
        }
    }

    @Override
    public void updatePauseTip(String tip, boolean popupDialog, long countDownTime) {
        if (popupDialog) {
            if (EasyDialog.isShow() && EasyDialog.getInstance().isAutoDismissEnable())
                EasyDialog.getInstance().dismiss();
            EasyDialog.getInstance(this).warnError(tip);
        }
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.task_fragment_view);
        if (currentFragment instanceof PauseFragment) {
            ((PauseFragment) currentFragment).updateTip(tip);
            ((PauseFragment) currentFragment).updateCountDownTimer(countDownTime);
        } else if (currentFragment instanceof ArrivedFragment) {
            ((ArrivedFragment) currentFragment).updateCountDownTimer(countDownTime);
        }
    }

    @Override
    public void updateCountDownTimer(long seconds) {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.task_fragment_view);
        if (currentFragment instanceof ArrivedFragment) {
            ((ArrivedFragment) currentFragment).updateCountDownTimer(seconds);
        } else if (currentFragment instanceof PauseFragment) {
            ((PauseFragment) currentFragment).updateCountDownTimer(seconds);
        }
    }

    @Override
    public void showCannotPauseTip(String tip) {
        ToastUtils.showShortToast(tip);
    }

    @Override
    public void showTaskFinishedView(int result, String prompt, String voice, RouteWithPoints routeWithPoints, TaskMode taskMode) {
        Intent intent = new Intent();
        intent.putExtra(Constants.TASK_RESULT, new Gson().toJson(new TaskResult(prompt, voice, routeWithPoints, taskMode)));
        setResult(result, intent);
        finish();
    }

    @Override
    public void showManualLiftControlTip(boolean liftUp) {
        if (EasyDialog.isShow() && EasyDialog.getInstance().isAutoDismissEnable())
            EasyDialog.getInstance().dismiss();
        EasyDialog.getLoadingInstance(this).loading(getString(R.string.text_is_lifting));
    }

    @Override
    public void dismissManualLiftControlTip(boolean success) {
        if (EasyDialog.isShow() && EasyDialog.getInstance().isAutoDismissEnable())
            EasyDialog.getInstance().dismiss();
    }

    @Override
    public void updateTakeElevatorStep(Step step, String targetFloor) {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.task_fragment_view);
        if (currentFragment instanceof RunningFragment) {
            ((RunningFragment) currentFragment).updateElevatorTip(step, targetFloor);
        }
    }

    @Override
    public void updateRunningTip(String tip) {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.task_fragment_view);
        if (currentFragment instanceof RunningFragment) {
            runOnUiThread(() -> ((RunningFragment) currentFragment).updateRunningTip(tip));
        }
    }

    @Override
    public void dismissRunningTip() {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.task_fragment_view);
        if (currentFragment instanceof RunningFragment) {
            runOnUiThread(() -> ((RunningFragment) currentFragment).dismissRunningTip());
        }
    }

    @Override
    public void showLiftModelState(int state) {
        EasyDialog.getInstance(this).warnError(getString(state == 0 ? R.string.text_lift_model_already_down : R.string.text_lift_model_already_up));
    }

    public void showLeaveElevatorFailed(Throwable throwable) {
        if (throwable instanceof CustomException) {
            if (((CustomException) throwable).step == Step.LEAVE_ELEVATOR_ACK) {
                ToastUtils.showShortToast(getString(R.string.text_leave_elevator_ack_failed));
            } else if (((CustomException) throwable).step == Step.LEAVE_ELEVATOR_COMPLETE) {
                ToastUtils.showShortToast(getString(R.string.text_release_elevator_failed));
            }
        }
    }

    @Override
    public void onCreateTaskSuccess() {
        super.onCreateTaskSuccess();
        if (EasyDialog.isShow() && EasyDialog.getInstance().isLoading()) {
            EasyDialog.getInstance().updateLoadingMessage(getString(R.string.text_create_task_success_waiting_for_dispatch));
        }
        presenter.onDispatchTaskCreateSuccess();
    }

    @Override
    public void onCreateTaskFailure(Throwable throwable) {
        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss();
        presenter.onDispatchTaskCreateFailure(throwable);
    }

    @Override
    public void onTaskReceived(Task task) {
        super.onTaskReceived(task);
        presenter.onDispatchTaskReceived(task);
    }

    @Override
    public void onFinishTaskSuccess() {
        presenter.onDispatchFinishTaskSuccess();
    }

    @Override
    public void onFinishTaskFailure(Throwable throwable) {
        presenter.onDispatchFinishTaskFailure(throwable);
    }

    @Override
    public void onMqttDisconnect(boolean isRetry, int reconnectCount, Throwable throwable) {
        super.onMqttDisconnect(isRetry, reconnectCount, throwable);
        presenter.onDispatchMqttDisconnected(isRetry, reconnectCount, throwable);
    }

    @Override
    public void onMqttReconnected() {
        super.onMqttReconnected();
        presenter.onDispatchMqttReconnected();
    }

    @Override
    public void onMapUpdate() {
        super.onMapUpdate();
        presenter.onDispatchServerMapUpdate();
    }

    @Override
    public void onConfigUpdate() {
        super.onConfigUpdate();
        presenter.onDispatchServerRoomConfigUpdate();
    }

    @Override
    public void showCreatingTask(String point) {
        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss();
        EasyDialog.getLoadingInstance(this).loading(getString(R.string.text_is_creating_task_to_, point));
    }

    @Override
    public void showReconnectingToDispatchServer() {
        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss();
        EasyDialog.getLoadingInstance(this).loading(getString(R.string.text_mqtt_disconnect_and_reconnecting));
    }

    @Override
    public void showReconnectedToResumeTask() {
        if (EasyDialog.isShow()) {
            if (EasyDialog.getInstance().isLoading()) {
                EasyDialog.getInstance().updateLoadingMessage(getString(R.string.text_is_resume_task));
            } else {
                EasyDialog.getInstance().dismiss();
                EasyDialog.getLoadingInstance(this).loading(getString(R.string.text_is_resume_task));
            }
        } else {
            EasyDialog.getLoadingInstance(this).loading(getString(R.string.text_is_resume_task));
        }
    }

    @Override
    public void showAvoiding() {
        if (EasyDialog.isShow()) {
            if (EasyDialog.getInstance().isLoading()) {
                EasyDialog.getInstance().updateLoadingMessage(getString(R.string.text_is_avoiding));
            } else {
                EasyDialog.getInstance().dismiss();
                EasyDialog.getLoadingInstance(this).loading(getString(R.string.text_is_avoiding));
            }
        } else {
            EasyDialog.getLoadingInstance(this).loading(getString(R.string.text_is_avoiding));
        }
    }

    @Override
    public void showQueueing() {
        if (EasyDialog.isShow()) {
            if (EasyDialog.getInstance().isLoading()) {
                EasyDialog.getInstance().updateLoadingMessage(getString(R.string.text_is_queuing));
            } else {
                EasyDialog.getInstance().dismiss();
                EasyDialog.getLoadingInstance(this).loading(getString(R.string.text_is_queuing));
            }
        } else {
            EasyDialog.getLoadingInstance(this).loading(getString(R.string.text_is_queuing));
        }
    }

    @Override
    public void dismissAvoidingDialog() {
        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss();
    }

    @Override
    public void showDialog(String content) {
        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss();
        EasyDialog.getInstance(this).warnError(content);
    }

    @Override
    protected void onCustomCallingTask(CallingTaskEvent event) {
        presenter.detailCustomCallingTask(event);
    }

    @Override
    protected void onCustomNormalTask(NormalTaskEvent event) {
        presenter.detailCustomNormalTask(event);
    }

    @Override
    protected void onCustomRouteTask(RouteTaskEvent event) {
        presenter.detailCustomRouteTask(event);
    }

    @Override
    protected void onCustomQRCodeTask(QRCodeTaskEvent event) {
        presenter.detailCustomQRCodeTask(event);
    }

    @Override
    protected void onCustomChargeTask(ChargeTaskEvent event) {
        presenter.detailCustomChargeTask(event.getToken());
    }

    @Override
    protected void onCustomReturnTask(ReturnTaskEvent event) {
        presenter.detailCustomReturnTask(event.getToken());
    }

    private void onAndroidNetworkChangeEvent(AndroidNetWorkEvent event) {
        String action = event.getIntent().getAction();
        switch (action) {
            case WifiManager.NETWORK_STATE_CHANGED_ACTION:
                NetworkInfo info = event.getIntent().getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (info.getState().equals(NetworkInfo.State.DISCONNECTED)) {
                    Timber.w("WIFI DISCONNECTED");
                } else if (info.getState().equals(NetworkInfo.State.CONNECTED)) {
                    Timber.w("WIFI CONNECTED");
                    presenter.onWifiConnectionSuccess();
                }
                break;
            case WifiManager.WIFI_STATE_CHANGED_ACTION:
                int wifiState = event.getIntent().getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0);
                if (wifiState == WifiManager.WIFI_STATE_DISABLED) {
                    refreshState();
                }
                break;
        }
    }

    @Override
    protected void onTimeJumpRelocationFailure() {
        super.onTimeJumpRelocationFailure();
        presenter.onROSTimeJumpRelocationFailure();
    }

    @Override
    protected void onTimeJumpRelocationSuccess() {
        super.onTimeJumpRelocationSuccess();
        presenter.onROSTimeJumpRelocationSuccess();
    }

    @Override
    protected void onCustomROSTimeJumpEvent() {
        presenter.onROSTimeJump();
    }

    @Override
    public void showTimeJumpDialog() {
        EasyDialog.getLoadingInstance(this).loading(getString(R.string.text_check_ros_time_jump_rebooting));
    }
}
