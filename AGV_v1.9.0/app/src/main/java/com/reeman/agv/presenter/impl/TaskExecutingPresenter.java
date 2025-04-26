package com.reeman.agv.presenter.impl;

import static com.reeman.agv.base.BaseApplication.dbRepository;
import static com.reeman.agv.base.BaseApplication.ros;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import io.reactivex.rxjava3.core.Completable;
import kotlin.Pair;

import com.google.gson.Gson;
import com.reeman.agv.R;
import com.reeman.agv.base.BaseApplication;
import com.reeman.agv.calling.CallingInfo;
import com.reeman.agv.calling.TakeElevatorStep;
import com.reeman.agv.calling.button.CallingHelper;
import com.reeman.agv.calling.event.CallingTaskEvent;
import com.reeman.agv.calling.event.NormalTaskEvent;
import com.reeman.agv.calling.event.QRCodeTaskEvent;
import com.reeman.agv.calling.event.RouteTaskEvent;
import com.reeman.agv.calling.event.StartTaskCountDownEvent;
import com.reeman.agv.calling.event.TaskEvent;
import com.reeman.agv.calling.model.TaskDetails;
import com.reeman.agv.calling.utils.CallingStateManager;
import com.reeman.agv.calling.utils.TaskExecutingCode;
import com.reeman.agv.utils.PointContentUtils;
import com.reeman.agv.utils.ToastUtils;
import com.reeman.commons.board.Board;
import com.reeman.commons.board.BoardFactory;
import com.reeman.commons.constants.Constants;
import com.reeman.commons.event.ApplyMapEvent;
import com.reeman.commons.event.CloseDoorFailedEvent;
import com.reeman.commons.event.CurrentMapEvent;
import com.reeman.commons.event.DoorClosedEvent;
import com.reeman.commons.event.DoorOpenedEvent;
import com.reeman.commons.event.FixedPathResultEvent;
import com.reeman.commons.event.OpenDoorFailedEvent;
import com.reeman.commons.event.SensorsEvent;
import com.reeman.commons.event.VersionInfoEvent;
import com.reeman.commons.event.WheelStatusEvent;
import com.reeman.commons.event.model.Room;
import com.reeman.agv.calling.model.TaskInfo;
import com.reeman.commons.exceptions.ReconnectUsbDeviceTimeoutException;
import com.reeman.commons.model.request.FaultRecord;
import com.reeman.commons.model.request.Msg;
import com.reeman.commons.model.request.Response;
import com.reeman.commons.settings.BackgroundMusicSetting;
import com.reeman.commons.settings.ElevatorSetting;
import com.reeman.commons.settings.ObstacleSetting;
import com.reeman.commons.state.NavigationMode;
import com.reeman.commons.state.RobotInfo;
import com.reeman.commons.state.SpecialAreaType;
import com.reeman.commons.state.StartTaskCode;
import com.reeman.commons.state.State;
import com.reeman.commons.state.TaskAction;
import com.reeman.commons.state.TaskMode;
import com.reeman.commons.utils.PausableCountDownTimer;
import com.reeman.commons.utils.PointUtils;
import com.reeman.commons.utils.SpManager;
import com.reeman.commons.utils.TimeUtil;
import com.reeman.commons.utils.WIFIUtils;
import com.reeman.agv.constants.Errors;
import com.reeman.agv.contract.TaskExecutingContract;
import com.reeman.agv.controller.DoorController;
import com.reeman.agv.elevator.exception.CustomException;
import com.reeman.agv.elevator.manager.ElevatorControlManager;
import com.reeman.agv.elevator.state.Step;
import com.reeman.agv.request.ServiceFactory;
import com.reeman.agv.request.notifier.Notifier;
import com.reeman.agv.request.notifier.NotifyConstant;
import com.reeman.agv.request.url.API;
import com.reeman.agv.task.Task;
import com.reeman.agv.task.TaskFactory;
import com.reeman.agv.task.model.PointModel;
import com.reeman.agv.utils.MediaPlayerHelper;
import com.reeman.agv.utils.PackageUtils;
import com.reeman.agv.utils.VoiceHelper;
import com.reeman.agv.viewModel.TaskPauseInfoModel;
import com.reeman.agv.widgets.EasyDialog;
import com.reeman.dao.repository.entities.DeliveryRecord;
import com.reeman.dispatch.DispatchManager;
import com.reeman.dispatch.constants.DispatchAction;
import com.reeman.dispatch.constants.ResponseCode;
import com.reeman.dispatch.constants.RobotState;
import com.reeman.dispatch.constants.TaskProcess;
import com.reeman.dispatch.constants.TaskType;
import com.reeman.dispatch.exception.RequestFailureException;
import com.reeman.elevator.utils.IdGenerateUtil;
import com.reeman.points.model.custom.GenericPoint;
import com.reeman.points.utils.PointCacheInfo;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

public class TaskExecutingPresenter implements TaskExecutingContract.Presenter, DoorController.OnAccessControlListener, VoiceHelper.OnCompleteListener, MediaPlayerHelper.OnCompleteListener {

    private final String AIR_SHOWER_DOOR_PREFIX = "air_shower_door_";
    private final String DOOR_PREFIX = "door_";
    private final String AIR_SHOWER_DOOR_SUFFIX = "_air_shower";
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Context context;
    private final TaskExecutingContract.View view;
    private final Gson gson;
    private final List<String> failedPoint = new ArrayList<>();
    private final RobotInfo robotInfo;
    private final CallingInfo callingInfo;
    private ObstacleSetting obstacleSetting;
    //上次遇到障碍物提示语播放时间
    private long lastObstaclePromptPlaybackTimeMills;
    //上次到达提示语播放时间
    private long lastArrivedPlayTimeMills;
    private int currentMindOutIndex = 0;
    private DeliveryRecord record;
    private ElevatorControlManager elevatorControlManager;
    private Task task;
    private int agvNavigationFailedCount = 0;
    private PausableCountDownTimer pauseCountDownTimer;
    private PausableCountDownTimer recallElevatorCountDownTimer;
    private int checkCanNavigationCount = 0;
    private int canNavigationCount = 0;
    private long lastPlayLeaveALittleSpaceTime = 0;
    private int relocationCount = 0;
    private int recallElevatorCount = 0;
    private boolean isFinished = false;
    private boolean isSwitchWifi = false;
    private Pair<String, String> currentConnectingWifi;
    private BackgroundMusicSetting backgroundMusicSetting;
    private final LinkedList<Pair<String, PointModel>> doorControlPositionList = new LinkedList<>();
    private Pair<String, PointModel> currentAction;
    private List<String> currentPath;
    private boolean isEnterElevatorFailedReturnToWaitingPoint = false;
    private double[] relocPosition;
    private boolean isWaitingGlobalPath = false;
    private boolean isRecallElevatorInside = false;
    private boolean isArrivedDoorPoint = false;
    private boolean liftManually = false;
    private volatile boolean isTimeout = false;
    private boolean isArrivedTargetPoint = false;

    private com.reeman.dispatch.model.response.Task currentOrder;

    public TaskExecutingPresenter(TaskExecutingContract.View view, Context context) {
        this.view = view;
//        isFinished = false;
        this.context = context;
        robotInfo = RobotInfo.INSTANCE;
        callingInfo = CallingInfo.INSTANCE;
        gson = new Gson();
    }

    @Override
    public void startTask(Context context, Intent intent) {
        if (robotInfo.isChargeDocking()) {
            ros.cancelCharge();
        }
        TaskMode taskMode = robotInfo.getMode();
        task = TaskFactory.create(taskMode, gson);
        task.initTask(intent);
        if (robotInfo.isDoorControlMode()) {
            if (robotInfo.getDoorControlSetting().communicationMethod == 0) {
                try {
                    DoorController.createInstance().init(this);
                } catch (Exception e) {
                    Timber.e(e, "打开门控串口失败");
                    onTaskFinished(-1, context.getString(R.string.text_door_control_serial_port_open_failed), "");
                    return;
                }
            } else {
                if (!CallingHelper.INSTANCE.isStart()) {
                    Timber.e("呼叫&门控串口未打开,任务异常结束");
                    onTaskFinished(-1, context.getString(R.string.text_door_control_serial_port_open_failed), "");
                    return;
                }
            }
        }
        if (robotInfo.isElevatorMode()) {
            ElevatorSetting elevatorSetting = robotInfo.getElevatorSetting();
            if (elevatorSetting.communicationMethod != 0) {
                if (elevatorSetting.communicationMethod == 2) {
                    if (elevatorSetting.gatewayId == null || elevatorSetting.gatewayId.length() != 8) {
                        onTaskFinished(-1, context.getString(R.string.text_please_set_gateway_id_first), "");
                        return;
                    }
                }
                if (!CallingHelper.INSTANCE.isElevatorModuleExists()) {
                    onTaskFinished(-1, context.getString(R.string.text_checked_lora_not_exist), "");
                    return;
                }
                if (!CallingHelper.INSTANCE.isStart()) {
                    try {
                        CallingHelper.INSTANCE.start();
                    } catch (Exception e) {
                        Timber.w(e, "打开梯控LoRa串口失败");
                        onTaskFinished(-1, context.getString(R.string.text_checked_lora_not_exist), "");
                        return;
                    }
                }

            }
            if (!elevatorSetting.isSingleNetwork) {
                String connectWifiSSID = WIFIUtils.getConnectWifiSSID(context);
                Timber.w("梯控设置 :  %s,当前网络 : %s", elevatorSetting.toString(), connectWifiSSID);
                if (TextUtils.isEmpty(connectWifiSSID) || !connectWifiSSID.equals(elevatorSetting.outsideNetwork.getFirst())) {
                    onTaskFinished(-1, context.getString(R.string.text_check_not_connect_to_outside_network_cannot_take_elevator), "");
                    return;
                }
            }
        }
        Timber.w(callingInfo.getCallingModeSetting().toString());
        initObstaclePromptSettings();
        initBackgroundMusic();
        int mode = 1;
        if (taskMode == TaskMode.MODE_NORMAL) {
            if (!VoiceHelper.isPlaying())
                playVoiceTip("voice_task_start");
        } else if (taskMode == TaskMode.MODE_ROUTE) {
            mode = 5;
            if (!VoiceHelper.isPlaying())
                playVoiceTip("voice_task_start");
        } else if (taskMode == TaskMode.MODE_QRCODE) {
            mode = 6;
            if (!VoiceHelper.isPlaying())
                playVoiceTip("voice_task_start");
        } else if (taskMode == TaskMode.MODE_START_POINT) {
            if (!VoiceHelper.isPlaying())
                playVoiceTip("voice_okay_goto_product_point");
        } else if (taskMode == TaskMode.MODE_CHARGE) {
            int chargeReason = intent.getIntExtra(Constants.TASK_TARGET, -1);
            if (!VoiceHelper.isPlaying()) {
                if (chargeReason == Constants.TASK_CHARGE_LOW_POWER) {
                    playVoiceTip("voice_power_insufficient_and_goto_charge");
                } else if (chargeReason != Constants.TASK_AUTO_WORK) {
                    playVoiceTip("voice_okay_goto_charge");
                }
            }
        }
        startTask(mode);
    }

    private void startTask(int mode) {
        DispatchManager.INSTANCE.setCurrentState(RobotState.TASK_EXECUTING);
        record = new DeliveryRecord(mode, System.currentTimeMillis(), WIFIUtils.getMacAddress(context), "v1.1", PackageUtils.getVersion(context));
        if (robotInfo.isElevatorMode()) {
            int communicationMethod = robotInfo.getElevatorSetting().communicationMethod;
            if (communicationMethod == 0) {
                elevatorControlManager = ElevatorControlManager.getInstance();
            }
        }
        if (robotInfo.isDispatchModeOpened()) {
            ros.avoidObstacle(true);
        }
        navigationToPoint();
        callingInfo.getHeartBeatInfo().setTaskExecuting(true);
        CallingStateManager.INSTANCE.setTaskExecutingEvent(TaskExecutingCode.TASK_EXECUTING);
        if (robotInfo.isLiftModelInstalled() && robotInfo.isSpaceShip()) {
            Timber.w("带顶升,查询顶升状态");
            ros.getAltitudeState();
        }
    }

    private void initObstaclePromptSettings() {
        String obstacleSettingStr = SpManager.getInstance().getString(Constants.KEY_OBSTACLE_CONFIG, null);
        if (TextUtils.isEmpty(obstacleSettingStr)) {
            obstacleSetting = ObstacleSetting.getDefault();
        } else {
            obstacleSetting = gson.fromJson(obstacleSettingStr, ObstacleSetting.class);
        }
    }

    //背景音乐相关初始化
    private void initBackgroundMusic() {
        if (backgroundMusicSetting == null) {
            String backgroundMusicSettingConfigStr = SpManager.getInstance().getString(Constants.KEY_BACKGROUND_MUSIC, null);
            if (TextUtils.isEmpty(backgroundMusicSettingConfigStr)) {
                backgroundMusicSetting = new BackgroundMusicSetting(false, new ArrayList<>(), new ArrayList<>());
            } else {
                backgroundMusicSetting = new Gson().fromJson(backgroundMusicSettingConfigStr, BackgroundMusicSetting.class);
            }
        }
    }

    /**
     * 导航去目标点
     */
    private void navigationToPoint() {
        robotInfo.setNavigationCancelable(false);
        robotInfo.setState(task.hasNext() ? State.DELIVERY : State.RETURNING);
        if (!task.isAbortTaskToProductionPoint()) {
            if (checkCanStartRemoteTask()) return;
            if (!task.hasNext() && task.getTaskFinishAction() == 2) {
                Timber.w("任务完成,直接结束");
                uploadTaskRecord(0);
                onTaskFinished(0, null, null);
                return;
            }
        }
        isArrivedTargetPoint = false;
        if (robotInfo.isElevatorMode()) {
            Pair<String, String> nextPoint = task.getNextPointWithElevator();
            Timber.w("目标点 %s %s", nextPoint.getFirst(), nextPoint.getSecond());
            CurrentMapEvent currentMapEvent = robotInfo.getCurrentMapEvent();
            if (currentMapEvent.getAlias().equals(nextPoint.getFirst())) {
                currentAction = new Pair<>(task.getAction(), new PointModel.Builder().name(nextPoint.getSecond()).build());
                Timber.w("currentAction : %s", currentAction);
                navigationTo(nextPoint.getSecond());
            } else {
                navigationToWaitingElevatorPoint();
            }
            view.updateRunningView(nextPoint.getFirst(), nextPoint.getSecond(), Step.IDLE);
        } else {
            String nextPoint = task.getNextPointWithoutElevator();
            currentAction = new Pair<>(task.getAction(), new PointModel.Builder().name(nextPoint).build());
            Timber.w("currentAction : %s", currentAction);
            if (robotInfo.isDispatchModeOpened()) {
                if (currentOrder != null && currentOrder.getFinalTargetPoint().equals(nextPoint) && currentPath != null && !currentPath.isEmpty()) {
                    Timber.w("恢复任务前往: %s", nextPoint);
                    if (currentOrder.getAction() == DispatchAction.AVOIDING) {
                        view.showAvoiding();
                    }
                    boolean isFinalPath = currentOrder.getAction() == DispatchAction.NAVIGATING_TO_FINAL;
                    if (isFinalPath && currentAction.getFirst().equals(TaskAction.agv_point)) {
                        ros.navigationByPathPoint(currentAction.getSecond().getName());
                    } else {
                        navigationByPositionList(currentPath, isFinalPath);
                    }
                    view.updateRunningView("", nextPoint, Step.IDLE);
                } else {
                    Timber.w("创建任务前往: %s", nextPoint);
                    TaskType taskType;
                    String nextPointShow;
                    if (task.getAction().equals(TaskAction.charge_point)) {
                        taskType = TaskType.TASK_CHARGE;
                        String chargePoint = robotInfo.getDispatchSetting().getChargePoint();
                        if (!TextUtils.isEmpty(chargePoint)) {
                            nextPoint = chargePoint;
                            nextPointShow = chargePoint;
                        } else {
                            nextPoint = null;
                            nextPointShow = context.getString(R.string.text_charge_point);
                        }
                    } else if (task.getAction().equals(TaskAction.production_point)) {
                        taskType = TaskType.TASK_PRODUCTION;
                        nextPointShow = context.getString(R.string.text_product_point);
                    } else {
                        taskType = TaskType.TASK_DELIVERY;
                        nextPointShow = nextPoint;
                    }
                    view.showCreatingTask(nextPointShow);
                    DispatchManager.INSTANCE.createTask(nextPoint, taskType);
                    currentOrder = null;
                }
            } else {
                navigationTo(nextPoint);
                view.updateRunningView("", nextPoint, Step.IDLE);
            }

        }
    }

    @Override
    public void onDispatchTaskCreateSuccess() {
        DispatchManager.INSTANCE.triggerHeartbeat(null);
    }

    @Override
    public void onDispatchTaskCreateFailure(Throwable throwable) {
        if (throwable instanceof IOException) {
            DispatchManager.INSTANCE.release();
            onTaskFinished(-1, context.getString(R.string.text_create_task_failure_cause_network), "");
        } else if (throwable instanceof RequestFailureException) {
            int code = ((RequestFailureException) throwable).getCode();
            if (code == ResponseCode.ALREADY_AT_TARGET_POINT_ERROR) {
                if (currentAction.getFirst().equals(TaskAction.charge_point) || currentAction.getFirst().equals(TaskAction.production_point)) {
                    String message = ((RequestFailureException) throwable).getMsg();
                    String nextPoint;
                    if (message.endsWith("Exception")) {
                        nextPoint = currentAction.getSecond().getName();
                    } else {
                        nextPoint = message;
                    }
                    currentOrder = new com.reeman.dispatch.model.response.Task(message);
                    robotInfo.setNavigationCancelable(true);
                    view.updateRunningView("", nextPoint, Step.IDLE);
                }
                detailNavigationSuccessResult(currentAction.getSecond().getName());
            } else if (code == ResponseCode.ROOM_NOT_FOUND_ERROR || code == ResponseCode.ROBOT_OFFLINE_ERROR) {
                DispatchManager.INSTANCE.release();
                onTaskFinished(-1, context.getString(R.string.text_create_task_failure_cause_robot_offline), "");
            } else if (code == ResponseCode.ROBOT_FAR_FROM_ROUTE_ERROR) {
                onTaskFinished(-1, context.getString(R.string.text_create_task_failure_cause_robot_far_from_route), "");
            } else if (code == ResponseCode.CREATE_PATH_TO_TARGET_FAILED_ERROR) {
                onTaskFinished(-1, context.getString(R.string.text_create_task_failure_cause_path_not_connected), "");
            } else if (code == ResponseCode.NOT_FOUND_FREE_CHARGE_POINT_ERROR) {
                onTaskFinished(-1, context.getString(R.string.text_create_task_failure_cause_not_found_free_charge_point), "");
            } else {
                onTaskFinished(-1, context.getString(R.string.text_create_task_failure_undefined_exception, code, ((RequestFailureException) throwable).getMsg()), "");
            }
        } else {
            DispatchManager.INSTANCE.release();
            onTaskFinished(-1, context.getString(R.string.text_create_task_failure_unknown_exception, throwable.getMessage()), "");
        }
    }

    @Override
    public void onDispatchTaskReceived(com.reeman.dispatch.model.response.Task order) {
        if (isFinished) return;
        if (robotInfo.getState() == State.PAUSE) {
            if (isCountdownCauseSensorError) {
                if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss();
            }
            currentOrder = order;
            if (!order.getPath().isEmpty()) {
                currentPath = order.getPath();
            }
            return;
        }
        if (currentOrder == null) {
            view.updateRunningView("", order.getFinalTargetPoint(), Step.IDLE);
            robotInfo.setLastTarget(order.getFinalTargetPoint());
            TaskInfo currentTask = callingInfo.getHeartBeatInfo().getCurrentTask();
            if (currentTask != null) {
                currentTask.setTargetPoint(order.getFinalTargetPoint());
            }
        }
        if (order.getAction() == DispatchAction.AVOIDING) {
            view.showAvoiding();
        } else if (order.getAction() == DispatchAction.QUEUING) {
            view.showQueueing();
        } else {
            if (currentOrder != null) {
                if (
                        (currentOrder.getAction() == DispatchAction.AVOIDING || currentOrder.getAction() == DispatchAction.QUEUING)
                                && (order.getAction() == DispatchAction.NAVIGATING_TO_NODE || order.getAction() == DispatchAction.NAVIGATING_TO_FINAL)
                ) {
                    view.dismissAvoidingDialog();
                }
            }
        }
        List<String> newOrderPath = order.getPath();
        boolean shouldDetailNewOrderPath = true;
        if (!newOrderPath.isEmpty() && currentOrder != null && !currentOrder.getPath().isEmpty()) {
            if (newOrderPath.get(newOrderPath.size() - 1).equals(currentOrder.getPath().get(currentOrder.getPath().size() - 1))) {
                shouldDetailNewOrderPath = false;
            }
        }
        currentOrder = order;
        if (order.getAction() == DispatchAction.QUEUING) {
            return;
        }
        currentPath = newOrderPath;
        if (currentPath.isEmpty()) return;
        if (shouldDetailNewOrderPath) {
            boolean isFinalPath = isFinalPath(newOrderPath);
            if (isFinalPath && currentAction.getFirst().equals(TaskAction.agv_point)) {
                ros.navigationByPathPoint(currentAction.getSecond().getName());
            } else {
                navigationByPositionList(currentPath, isFinalPath);
            }
        } else {
            DispatchManager.INSTANCE.triggerHeartbeat(TaskProcess.COMPLETE);
        }
    }

    private boolean isFinalPath(List<String> newOrderPath) {
        boolean isFinalPath = false;
        if (currentOrder.getAction() == DispatchAction.NAVIGATING_TO_FINAL) {
            isFinalPath = true;
        } else if (currentOrder.getAction() == DispatchAction.AVOIDING) {
            if (!currentOrder.getGlobalAvoidPath().isEmpty() && newOrderPath.get(newOrderPath.size() - 1).equals(currentOrder.getGlobalAvoidPath().get(currentOrder.getGlobalAvoidPath().size() - 1))) {
                isFinalPath = true;
            }
        }
        return isFinalPath;
    }

    @Override
    public void onDispatchMqttDisconnected(boolean isRetry, int delay, Throwable throwable) {
        if (!isRetry) {
            DispatchManager.INSTANCE.release();
            onTaskFinished(-1, context.getString(R.string.text_task_finished_cause_mqtt_disconnected), "");
            return;
        }
        if (delay == 1) {
            if (robotInfo.getState() == State.PAUSE) {
                if (pauseCountDownTimer != null && !pauseCountDownTimer.isPaused()) {
                    pauseCountDownTimer.pause();
                }
            } else {
                robotInfo.setState(State.PAUSE);
                if (robotInfo.isNavigating()) ros.cancelNavigation();
                if (robotInfo.isQRCodeNavigating()) ros.agvStop();
                if (robotInfo.isChargeDocking()) ros.cancelCharge();
            }
            view.showReconnectingToDispatchServer();
        }
    }

    @Override
    public void onDispatchMqttReconnected() {
        if (robotInfo.getState() == State.PAUSE) {
            if (pauseCountDownTimer != null && pauseCountDownTimer.isPaused()) {
                pauseCountDownTimer.resume();
            }
            if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss();
        } else {
            if (currentOrder != null) {
                boolean isNavigatingAction = Arrays.asList(
                        DispatchAction.NAVIGATING_TO_NODE,
                        DispatchAction.NAVIGATING_TO_FINAL,
                        DispatchAction.AVOIDING
                ).contains(currentOrder.getAction());

                if (isNavigatingAction && currentPath != null && !currentPath.isEmpty()) {
                    if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss();
                    navigationToPoint();
                } else {
                    view.showReconnectedToResumeTask();
                    DispatchManager.INSTANCE.reconnectedDuringTaskExecuting(
                            currentOrder.getTaskId(),
                            currentOrder.getOrderId()
                    );
                }
            } else {
                if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss();
                navigationToPoint();
            }

        }
    }

    @Override
    public void onROSTimeJumpRelocationFailure() {
        onTaskFinished(-1, context.getString(R.string.text_time_jump_reloc_failure_task_finished), "");
    }

    @Override
    public void onROSTimeJumpRelocationSuccess() {
        Timber.w("时间戳跳变后重定位完成,恢复任务");
        task.resetStopNearByParameter();
        if (robotInfo.getLiftModelState() == 1) {
            task.setRobotWidthAndLidar(false);
        }
        if (robotInfo.isDispatchModeOpened()) {
            ros.avoidObstacle(true);
        }
        if (currentAction != null) {
            if (robotInfo.isEmergencyButtonDown()) {
                showPauseViewWithTip(context.getString(R.string.text_turn_off_emergency_stop_to_continue_task), context.getString(R.string.text_emergency_stop_on));
                return;
            }
            navigationToPoint();
        } else {
            if (pauseCountDownTimer != null && pauseCountDownTimer.isPaused()) {
                if (robotInfo.isEmergencyButtonDown()) {
                    showPauseViewWithTip(context.getString(R.string.text_turn_off_emergency_stop_to_continue_task), context.getString(R.string.text_emergency_stop_on));
                    return;
                }
                pauseCountDownTimer.resume();
            }
        }
    }

    @Override
    public void onROSTimeJump() {
        ros.stopMove();
        if (robotInfo.isNavigating()) ros.cancelNavigation();
        if (robotInfo.isQRCodeNavigating()) {
            ros.agvStop();
            Timber.w("对接二维码时时间戳跳变,任务结束");
            onTaskFinished(-4, context.getString(R.string.text_time_jump_during_qrcode_navigating), "");
            return;
        }
        if (robotInfo.isDoorControlMode()) {
            if (DoorController.getInstance().getCurrentState() != DoorController.State.CLOSED
                    || CallingHelper.INSTANCE.getDoorState() != CallingHelper.INIT) {
                Timber.w("通过门时时间戳跳变,任务结束");
                onTaskFinished(-4, context.getString(R.string.text_time_jump_during_through_door), "");
                return;
            }
        }
        if (robotInfo.isElevatorMode()) {
            if ((elevatorControlManager != null && elevatorControlManager.getCurrentStep() != Step.IDLE) || CallingHelper.INSTANCE.getTakeElevatorStep() != TakeElevatorStep.INIT || robotInfo.isSwitchingMap() || robotInfo.isRepositioning()) {
                Timber.w("乘梯时时间戳跳变任务结束");
                onTaskFinished(-4, context.getString(R.string.text_time_jump_during_take_elevator), "");
                return;
            }
        }
        if (robotInfo.isLifting()) {
            Timber.w("顶升时时间戳跳变结束任务");
            onTaskFinished(-4, context.getString(R.string.text_time_jump_during_lifting), "");
            return;
        }
        if (robotInfo.isChargeDocking()) {
            Timber.w("对接充电桩时时间戳跳变结束任务");
            onTaskFinished(-4, context.getString(R.string.text_time_jump_during_charge_docking), "");
            return;
        }
        if (pauseCountDownTimer != null && !pauseCountDownTimer.isPaused()) {
            pauseCountDownTimer.pause();
        }
        pauseBackgroundMusic();
        robotInfo.setState(State.PAUSE);
        view.showTimeJumpDialog();
    }

    @Override
    public void onDispatchServerMapUpdate() {
        onTaskFinished(-1, context.getString(R.string.text_finish_task_cause_dispatch_server_map_update), "");
    }

    @Override
    public void onDispatchServerRoomConfigUpdate() {
        onTaskFinished(-1, context.getString(R.string.text_finish_task_cause_dispatch_server_room_config_update), "");
    }

    @Override
    public void onDispatchFinishTaskSuccess() {
        ToastUtils.showShortToast(context.getString(R.string.text_finish_task_success));
    }

    @Override
    public void onDispatchFinishTaskFailure(Throwable throwable) {
        ToastUtils.showShortToast(context.getString(R.string.text_finish_task_failure));
    }

    /**
     * 设置无法响应移动端下发任务
     * 1:任务手动暂停
     * 3:通过门控区
     * 4:乘梯(包含乘梯,切换地图,重定位)
     */
    private void setCannotResponseTaskDuringTaskExecuting() {
        CallingStateManager.INSTANCE.setCanTakeTaskDuringTaskExecuting(false);
    }

    /**
     * 检查是否可以执行移动端任务
     */
    private boolean checkCanResponseTaskDuringTaskExecuting(String token, TaskMode taskMode) {
        if (robotInfo.getMode() == TaskMode.MODE_CALLING || robotInfo.getMode() == TaskMode.MODE_CHARGE || robotInfo.getMode() == TaskMode.MODE_START_POINT) {
            Timber.w("呼叫/回充/返航模式无法响应任务");
            if (token != null && taskMode != null) {
                CallingStateManager.INSTANCE.setStartTaskCountDownEvent(new StartTaskCountDownEvent(token, taskMode, StartTaskCode.CURRENT_TASK_MODE_NOT_SUPPORT_RESPONSE_TASK));
            }
            return false;
        }
        if (elevatorControlManager != null && elevatorControlManager.getCurrentStep() != Step.IDLE) {
            Timber.w("乘梯状态: %s,无法响应任务", elevatorControlManager.getCurrentStep());
            if (token != null && taskMode != null) {
                CallingStateManager.INSTANCE.setStartTaskCountDownEvent(new StartTaskCountDownEvent(token, taskMode, StartTaskCode.TASK_EXECUTING));
            }
            return false;
        }
        if (CallingHelper.INSTANCE.getTakeElevatorStep() != TakeElevatorStep.INIT) {
            Timber.w("乘梯状态: %s,无法响应任务", CallingHelper.INSTANCE.getTakeElevatorStep());
            if (token != null && taskMode != null) {
                CallingStateManager.INSTANCE.setStartTaskCountDownEvent(new StartTaskCountDownEvent(token, taskMode, StartTaskCode.TASK_EXECUTING));
            }
            return false;
        }
        if (DoorController.getInstance() != null) {
            DoorController.State currentState = DoorController.getInstance().getCurrentState();
            if (currentState != DoorController.State.CLOSED) {
                Timber.w("门控状态: %s,无法响应任务", currentState);
                if (token != null && taskMode != null) {
                    CallingStateManager.INSTANCE.setStartTaskCountDownEvent(new StartTaskCountDownEvent(token, taskMode, StartTaskCode.TASK_EXECUTING));
                }
                return false;
            }
        }
        if (robotInfo.isLowPower()) {
            Timber.w("低电,无法响应任务");
            if (token != null && taskMode != null) {
                CallingStateManager.INSTANCE.setStartTaskCountDownEvent(new StartTaskCountDownEvent(token, taskMode, StartTaskCode.LOW_POWER));
            }
            return false;
        }
        if (task.hasNext() || robotInfo.getState() != State.RETURNING) {
            Timber.w("本次任务未结束或任务处于暂停等状态,无法响应任务");
            if (token != null && taskMode != null) {
                CallingStateManager.INSTANCE.setStartTaskCountDownEvent(new StartTaskCountDownEvent(token, taskMode, StartTaskCode.TASK_EXECUTING));
            }
            return false;
        }
        if (task.isArrivedLastPointAndEndInPlace()) {
            Timber.w("配送模式配置到达最后一个任务点后原地停留,不返回出品点,无法响应任务");
            if (token != null && taskMode != null) {
                CallingStateManager.INSTANCE.setStartTaskCountDownEvent(new StartTaskCountDownEvent(token, taskMode, StartTaskCode.TASK_EXECUTING));
            }
            return false;
        }
        if (task.isExecuteAgainSwitch()) {
            Timber.w("路线模式下任务结束重新开始任务");
            if (token != null && taskMode != null) {
                CallingStateManager.INSTANCE.setStartTaskCountDownEvent(new StartTaskCountDownEvent(token, taskMode, StartTaskCode.EXECUTING_AGAIN_SWITCH_OPENED_IN_ROUTE_TASK));
            }
            return false;
        }
        CallingStateManager.INSTANCE.setCanTakeTaskDuringTaskExecuting(true);
        return true;
    }

    private boolean checkCanStartRemoteTask() {
        Timber.w("检查是否可以响应远程任务");
        TaskDetails firstTaskDetails = null;
        if (task.shouldRemoveFirstCallingTask()) {
            List<TaskDetails> taskDetailsList = callingInfo.getTaskDetailsList();
            if (taskDetailsList.size() > 1) {
                firstTaskDetails = taskDetailsList.get(1);
            }
        } else {
            firstTaskDetails = callingInfo.getFirstCallingDetails();
        }
        if (firstTaskDetails == null) {
            Timber.w("无可执行的任务");
            return false;
        }
        if (!checkCanResponseTaskDuringTaskExecuting(firstTaskDetails.getKey(), firstTaskDetails.getMode())) {
            Timber.w("当前状态无法响应远程任务");
            return false;
        }
        Timber.w("任务提前终止,执行远程任务 : %s", firstTaskDetails);
        if (task.shouldRemoveFirstCallingTask()) {
            callingInfo.removeFirstCallingDetails();
        }
        uploadTaskRecord(0);
        task = TaskFactory.create(firstTaskDetails.getMode(), gson);
        Intent intent = new Intent();
        intent.putExtra(Constants.TASK_TOKEN, firstTaskDetails.getKey());
        TaskEvent taskEvent = firstTaskDetails.getTaskEvent();
        if (taskEvent instanceof CallingTaskEvent) {
            intent.putExtra(Constants.TASK_TARGET, gson.toJson(taskEvent));
        } else if (taskEvent instanceof NormalTaskEvent) {
            intent.putExtra(Constants.TASK_TARGET, gson.toJson(((NormalTaskEvent) taskEvent).getPointList()));
        } else if (taskEvent instanceof RouteTaskEvent) {
            intent.putExtra(Constants.TASK_TARGET, ((RouteTaskEvent) taskEvent).getRoute());
        } else if (taskEvent instanceof QRCodeTaskEvent) {
            intent.putExtra(Constants.TASK_TARGET, gson.toJson(((QRCodeTaskEvent) taskEvent).getQrCodePointPairList()));
        }
        task.initTask(intent);
        int mode = 1;
        if (firstTaskDetails.getMode() == TaskMode.MODE_NORMAL) {
        } else if (firstTaskDetails.getMode() == TaskMode.MODE_ROUTE) {
            mode = 5;
        } else if (firstTaskDetails.getMode() == TaskMode.MODE_QRCODE) {
            mode = 6;
        }
        playVoiceTip("voice_remote_task_start_during_task_executing");
        robotInfo.setMode(firstTaskDetails.getMode());
        startTask(mode);
        return true;
    }


    @Override
    public void onNavigationCancelResult(int code) {
        //暂时允许在网页端取消任务
//            if (currentAction != null) {
//                Timber.w("导航中收到取消,重试,当前目标点 :  %s", currentAction);
//                String action = currentAction.getFirst();
//                PointModel pointModel = currentAction.getSecond();
//                if (action.startsWith("door_")) {
//                    if (action.endsWith("_position")) {
//                        navigationToDoorPointCoordinate(pointModel.getPosition());
//                    } else {
//                        navigationToDoorPoint(pointModel.getName());
//                    }
//                } else if (action.endsWith("_position")) {
//                    ros.navigationByCoordinate(pointModel.getPosition());
//                } else {
//                    navigationTo(pointModel.getName());
//                }
//            } else {
        if (shouldCancelNavigationWhenNavigationStart) {
            shouldCancelNavigationWhenNavigationStart = false;
            return;
        }
        Timber.w("取消导航");
        if (robotInfo.getState() == State.PAUSE) return;
        if (currentAction != null) {
            uploadTaskRecord(1);
            onTaskFinished(-1, context.getString(R.string.text_task_abnormal_cancel), "");
        }
//            }
    }

    @Override
    public void onNavigationStartResult(int code, String name) {
        if (shouldCancelNavigationWhenNavigationStart) {
            cancelNavigation();
            return;
        }
        if (robotInfo.isDispatchModeOpened()) return;
        if (currentAction != null) {
            if (!robotInfo.isDispatchModeOpened()) {
                String action = currentAction.getFirst();
                PointModel pointModel = currentAction.getSecond();
                if (!TextUtils.isEmpty(pointModel.getName())) {
                    if (!pointModel.getName().equals(name)) {
                        Timber.w("导航中收到错误导航,重新发起导航");
                        if (action.equals(TaskAction.door_front_point)) {
                            navigationToDoorPoint(pointModel.getName());
                        } else {
                            navigationTo(pointModel.getName());
                        }
                        return;
                    }
                } else {
                    if (robotInfo.getNavigationMode() == NavigationMode.autoPathMode) {
                        String[] split = name.split(",");
                        double[] targetPosition = null;
                        boolean isNavigatingToDoor = action.equals(TaskAction.door_front_position) || action.equals(TaskAction.door_back_position);
                        if (action.equals(TaskAction.door_back_position) && robotInfo.getNavigationMode() == NavigationMode.fixPathMode && split.length == 6) {
                            targetPosition = new double[3];
                            for (int i = 0; i < 3; i++) {
                                targetPosition[i] = Double.parseDouble(split[i + 3]);
                            }
                        } else if (split.length != 3) {
                            Timber.w("导航中收到错误导航,重新发起导航");
                            if (isNavigatingToDoor) {
                                navigationToDoorPointCoordinate(pointModel.getPosition());
                            } else {
                                ros.navigationByCoordinate(pointModel.getPosition());
                            }
                            return;
                        }
                        if (targetPosition == null) {
                            targetPosition = new double[3];
                            for (int i = 0; i < 3; i++) {
                                targetPosition[i] = Double.parseDouble(split[i]);
                            }
                        }
                        double[] position = pointModel.getPosition();
                        for (int i = 0; i < split.length; i++) {
                            if (Math.abs(position[i] - targetPosition[i]) > 0.1) {
                                Timber.w("导航中收到错误导航,重新发起导航");
                                if (isNavigatingToDoor) {
                                    navigationToDoorPointCoordinate(pointModel.getPosition());
                                } else {
                                    ros.navigationByCoordinate(pointModel.getPosition());
                                }
                                return;
                            }
                        }
                    }
                }
            }
            if (code == 0) {
                playBackgroundMusic();
                return;
            }
            String navigationStartError = Errors.INSTANCE.getNavigationStartError(context, code);
            Notifier.notify(new Msg(NotifyConstant.HARDWARE_NOTIFY, "发起导航失败", "导航结果:" + navigationStartError, robotInfo.getROSHostname()));
            if (code == -4) {
                Pair<String, GenericPoint> chargePoint = PointCacheInfo.INSTANCE.getChargePoint();
                if (name.equals(chargePoint.getSecond().getName())) {
                    failedPoint.add(name);
                    uploadTaskRecord(1);
                    onTaskFinished(-1, context.getString(R.string.voice_not_found_charging_pile), "voice_not_found_charging_pile");
                } else {
                    failedPoint.add(name);
                    uploadTaskRecord(1);
                    onTaskFinished(-1, context.getString(R.string.voice_not_found_target_point), "voice_not_found_target_point");
                }
                return;
            }
            if (code == -2) {
                onEmergencyButtonDown();
                return;
            }
            uploadTaskRecord(1);
            onTaskFinished(-1, navigationStartError, "");
        } else {
            if (name.equals(robotInfo.getLastTarget()) && robotInfo.getState() == State.PAUSE) {
                Timber.d("处理发起导航后在收到导航回调前马上暂停任务,取消导航后又收到导航开始回调的情况");
                ros.cancelNavigation();
                return;
            }
            uploadTaskRecord(1);
            onTaskFinished(-1, context.getString(R.string.text_receive_navigation_from_web), "");
        }
    }

    @Override
    public void onNavigationCompleteResult(int code, String name, float mileage) {
        if (!robotInfo.isNavigationCancelable()) return;
        if (code == 0) {
            record.odom += mileage;
            if (robotInfo.getState() == State.PAUSE || isFinished) return;
            robotInfo.setNavigating(false);
            callingInfo.getHeartBeatInfo().setNavigating(false);
            if (robotInfo.isDispatchModeOpened()) {
                if (currentOrder != null) {
                    if (currentPath != null && !currentPath.isEmpty()) {
                        String last = currentPath.get(currentPath.size() - 1);
                        GenericPoint genericPoint = PointCacheInfo.INSTANCE.getPointByName(last);
                        double[] currentPosition = robotInfo.getCurrentPosition();
                        if (genericPoint != null && currentPosition != null) {
                            double distance = PointUtils.calculateDistance(currentPosition, genericPoint.getPosition());
                            if (distance < 1 && currentPath.size() > 1) {
                                currentPath = currentPath.subList(currentPath.size() - 1, currentPath.size());
                            }
                            if (distance > 0.3) {
                                Timber.w("距离目标点超过%s", distance);
                                boolean isFinalPath = currentOrder.getAction() == DispatchAction.NAVIGATING_TO_FINAL;
                                if (isFinalPath && currentAction.getFirst().equals(TaskAction.agv_point)) {
                                    ros.navigationByPathPoint(currentAction.getSecond().getName());
                                } else {
                                    navigationByPositionList(currentPath, isFinalPath);
                                }
                                return;
                            }
                        }
                    }
                    if (currentOrder.getAction().equals(DispatchAction.NAVIGATING_TO_FINAL)) {
                        DispatchManager.INSTANCE.setTask(null);
                        detailNavigationSuccessResult(currentOrder.getFinalTargetPoint());
                    }
                }
            } else {
                detailNavigationSuccessResult(name);
            }
        } else {
            if (robotInfo.getState() == State.PAUSE || isFinished) return;
            Timber.w("导航失败，重试");
            playVoiceTip("voice_mind_out_0");
            retryNavigation();
        }
    }

    private void retryNavigation() {
        if (currentAction != null) {
            String first = currentAction.getFirst();
            if (first.startsWith("door_")) {
                if (first.endsWith("_position")) {
                    navigationToDoorPointCoordinate(currentAction.getSecond().getPosition());
                } else {
                    navigationToDoorPoint(currentAction.getSecond().getName());
                }
            } else {
                navigationTo(currentAction.getSecond().getName());
            }
        }
    }

    //二维码模式 标签码导航结果
    @Override
    public void onQRCodeNavigationResult(boolean result, String tag) {
        Timber.w("标签码导航结果 : %s", (result ? "成功 " : "失败 "));
        if (robotInfo.getMode() != TaskMode.MODE_QRCODE && robotInfo.getMode() != TaskMode.MODE_ROUTE)
            return;
        robotInfo.setQRCodeNavigating(false);
        if (result) {
            isArrivedTargetPoint = true;
            agvNavigationFailedCount = 0;
            String currentPoint = currentAction.getSecond().getName();
            currentAction = null;
            long countDownTime = task.getCountDownTime();
            task.arrivedPoint(currentPoint);
            robotInfo.setNavigating(false);
            callingInfo.getHeartBeatInfo().setNavigating(false);
            if (task.withLiftFunction()) {
                if (robotInfo.isWithAntiCollisionStrip()) {
                    ros.ioControl(0x04);
                }
                if (robotInfo.getLiftModelState() == 1) {
                    Timber.w("顶升下降");
                    ros.liftDown();
                    view.updateRunningTip(context.getString(R.string.text_pickup_model_downing));
                } else {
                    Timber.w("顶升抬起");
                    ros.liftUp();
                    view.updateRunningTip(context.getString(R.string.text_pickup_model_lifting));
                }
                liftManually = false;
                callingInfo.setLifting(true);
            } else {
                if (robotInfo.getMode() == TaskMode.MODE_ROUTE) {
                    Timber.w("路线模式到达标签码");
                    arriveWorkingPoint(currentPoint, countDownTime);
                    return;
                }
                String nextPoint = task.getNextPointWithoutElevator();
                view.arrivedTargetPoint(
                        robotInfo.getMode(),
                        task.getRouteName(),
                        record.startTime,
                        "",
                        currentPoint,
                        "",
                        nextPoint,
                        task.showReturnBtn(),
                        task.isOpenedManualLiftUpControl(true),
                        task.isOpenedManualLiftDownControl(true),
                        task.hasNext(),
                        !task.isArrivedLastPointAndEndInPlace()
                );
            }
            playVoiceTip("voice_arrived_target_point");
            robotInfo.setState(State.PAUSE);
        } else {
            ros.agvStop();
            String retryPoint;
            if (robotInfo.isElevatorMode()) {
                retryPoint = PointCacheInfo.INSTANCE.getWaitingElevatorPoint().getName();
            } else {
                retryPoint = PointContentUtils.INSTANCE.getProductionPoint();
            }
            double[] currentPosition = robotInfo.getCurrentPosition();
            Timber.d("retryPoint : %s ,currentPosition : %s", retryPoint, Arrays.toString(currentPosition));
            if (currentPosition == null || ++agvNavigationFailedCount > 10) {
                uploadTaskRecord(1);
                onTaskFinished(-1, context.getString(R.string.text_not_found_qrcode_task_finish), "");
                return;
            }
            currentAction = new Pair<>(TaskAction.agv_retry_point, new PointModel.Builder().name(retryPoint).position(currentPosition).build());
            Timber.w("currentAction : %s", currentAction);
            Timber.w("离开一段距离再重试");
            navigationTo(retryPoint);
        }
    }

    @Override
    public void onLiftResult(int action, int state) {
        if (isFinished) return;
        Timber.w("顶升模块状态: 动作 : %s , 状态 : %s", action == 1 ? "上升" : "下降", state == 1 ? "完成" : "未完成");
        if (robotInfo.getGetAltitudeState()) {
            Timber.w("手动查询状态");
            robotInfo.setGetAltitudeState(false);
            if (task != null)
                task.setRobotWidthAndLidar(action == 0);
            return;
        }
        if (state == 0) return;
        mHandler.removeCallbacks(liftControlTimeOutRunnable);
        robotInfo.setLifting(false);
        robotInfo.setLiftModelState(action);
        callingInfo.setLifting(false);
        if (!task.hasNext() && task.getTaskFinishAction() == 2) {
            uploadTaskRecord(1);
            onTaskFinished(0, null, null);
            return;
        }
        if (liftManually) {
            view.dismissManualLiftControlTip(true);
        } else {
            view.dismissRunningTip();
            navigationToPoint();
        }
        task.setRobotWidthAndLidar(action == 0);
    }

    @Override
    public void onEmergencyButtonDown() {
        if (isFinished || robotInfo.isRebootingROSCauseTimeJump()) return;
        Timber.w("急停开关被按下");
        playVoiceTip("voice_scram_stop_turn_on");
        mHandler.removeCallbacksAndMessages(null);
        cancelCountdownTimer();
        showPauseViewWithTip(context.getString(R.string.text_turn_off_emergency_stop_to_continue_task), context.getString(R.string.text_emergency_stop_on));
        if (robotInfo.isDispatchModeOpened()) {
            DispatchManager.INSTANCE.setCurrentPoint(null);
            DispatchManager.INSTANCE.finishTask(1);
            currentOrder = null;
            currentPath = null;
        }
        if (isFinishDirectly(context.getString(R.string.text_emergency_stop_on_task_finish)))
            return;
        cancelNavigation();
        pauseBackgroundMusic();
    }

    @Override
    public void onEmergencyButtonUp() {
        if (EasyDialog.isShow() && EasyDialog.getInstance().isAutoDismissEnable())
            EasyDialog.getInstance().dismiss();
        playVoiceTip("voice_scram_stop_turn_off");
    }

    @Override
    public void onPauseClick() {
        if (isFinished || !robotInfo.isNavigationCancelable()) return;
        if (robotInfo.isQRCodeNavigating()) {
            view.showCannotPauseTip(context.getString(R.string.text_cannot_pause_during_qrcode_navigation));
            return;
        }
        if (robotInfo.isLifting()) {
            view.showCannotPauseTip(context.getString(R.string.text_cannot_pause_during_lifting));
            return;
        }
        if ((elevatorControlManager != null && elevatorControlManager.getCurrentStep() != Step.IDLE) || CallingHelper.INSTANCE.getTakeElevatorStep() != TakeElevatorStep.INIT) {
            view.showCannotPauseTip(context.getString(R.string.text_cannot_pause_during_take_elevator));
            return;
        }
        if (currentAction != null) {
            String first = currentAction.getFirst();
            if (first.equals(TaskAction.check_leave_elevator_point_reachable) || first.equals(TaskAction.leave_elevator) || first.equals(TaskAction.leave_elevator_position)) {
                view.showCannotPauseTip(context.getString(R.string.text_cannot_pause_during_take_elevator));
                return;
            }
        }
        if (robotInfo.isRepositioning() || robotInfo.isSwitchingMap()) {
            view.showCannotPauseTip(context.getString(R.string.text_cannot_pause_during_switch_map));
            return;
        }

        if (robotInfo.isDoorControlMode()) {
            if (robotInfo.getDoorControlSetting().communicationMethod == 0) {
                DoorController instance = DoorController.getInstance();
                if (instance != null) {
                    if (instance.getCurrentState() != DoorController.State.CLOSED) {
                        view.showCannotPauseTip(context.getString(R.string.text_cannot_pause_during_through_door));
                        return;
                    }
                }
            } else {
                int doorState = CallingHelper.INSTANCE.getDoorState();
                if (doorState != CallingHelper.INIT && doorState != CallingHelper.CLOSED) {
                    view.showCannotPauseTip(context.getString(R.string.text_cannot_pause_during_through_door));
                    return;
                }
            }
        }
        pauseTask(15, context.getString(R.string.text_task_pausing));
    }

    private boolean isCountdownCauseSensorError = false;

    private boolean shouldCancelNavigationWhenNavigationStart = false;

    private void pauseTask(long seconds, String tip) {
        setCannotResponseTaskDuringTaskExecuting();
        robotInfo.setState(State.PAUSE);
        if (robotInfo.isNavigationCancelable()) {
            cancelNavigation();
        } else if (robotInfo.isNavigating()) {
            shouldCancelNavigationWhenNavigationStart = true;
        }
        pauseBackgroundMusic();
        String mode = "";
        TaskMode taskMode = robotInfo.getMode();
        if (taskMode == TaskMode.MODE_NORMAL) {
            mode = context.getString(R.string.text_mode_normal);
        } else if (taskMode == TaskMode.MODE_ROUTE) {
            mode = context.getString(R.string.text_mode_route);
        } else if (taskMode == TaskMode.MODE_QRCODE) {
            mode = context.getString(R.string.text_mode_qrcode);
        } else if (taskMode == TaskMode.MODE_CALLING) {
            mode = context.getString(R.string.text_mode_calling);
        }
        String currentFloor = robotInfo.isElevatorMode() ? robotInfo.getCurrentMapEvent().getAlias() : "";
        String targetFloor = "";
        String targetPoint;
        if (robotInfo.isElevatorMode()) {
            targetFloor = task.getNextPointWithElevator().getFirst();
            targetPoint = task.getNextPointWithElevator().getSecond();
        } else {
            if (robotInfo.isDispatchModeOpened() && currentOrder != null) {
                targetPoint = currentOrder.getFinalTargetPoint();
            } else {
                targetPoint = task.getNextPointWithoutElevator();
            }
        }
        TaskPauseInfoModel model = new TaskPauseInfoModel.Builder(mode)
                .setRouteName(task.getRouteName())
                .setTaskStartTime(TimeUtil.formatHourAndMinute(record.startTime))
                .setTargetFloor(targetFloor)
                .setTargetPoint(targetPoint)
                .setCurrentFloor(currentFloor)
                .setShowReturnButton(task.hasNext() && task.showReturnBtn())
                .setShowCancelTaskButton(true)
                .setShowContinueTaskButton(true)
                .setShowSkipCurrentTargetButton(task.showSkipCurrentTargetBtn())
                .setCountDownTime(seconds)
                .setShowRecallElevatorButton(false)
                .setTaskPauseTip(tip)
                .build();
        view.showPauseTask(model, null);
        startCountDownTimer(seconds, true);
    }

    @Override
    public void onResumeClick() {
        if (isFinished) return;
        if (isEmergencyButtonDown()) return;
        if (isSensorError()) return;
        checkCanResponseTaskDuringTaskExecuting(null, null);
        cancelCountdownTimer();
        navigationToPoint();
    }

    @Override
    public void onGotoNextPointClick() {
        if (isFinished) return;
        if (isEmergencyButtonDown()) return;
        if (isSensorError()) return;
        if (isRouteModePointLiftActionComplete(true)) return;
        cancelCountdownTimer();
        navigationToPoint();
    }

    @Override
    public void onCancelClick() {
        if (isFinished) return;
        Timber.w("任务取消");
        uploadTaskRecord(1);
        onTaskFinished(1, null, null);
    }

    @Override
    public void onReturnProductPointClick() {
        if (isFinished) return;
        if (isEmergencyButtonDown()) return;
        if (isSensorError()) return;
        cancelCountdownTimer();
        task.clearAllPoints();
        task.setAbortTaskToProductionPoint(true);
        navigationToPoint();
    }

    @Override
    public void onReturnClick() {
        if (isFinished) return;
        if (isEmergencyButtonDown()) return;
        if (isSensorError()) return;
        cancelCountdownTimer();
        task.clearAllPoints();
        task.setAbortTaskToProductionPoint(true);
        navigationToPoint();
    }

    @Override
    public void onSkipCurrentTargetClick() {
        if (isFinished) return;
        if (isEmergencyButtonDown()) return;
        if (isSensorError()) return;
        if (isRouteModePointLiftActionComplete(false)) return;
        cancelCountdownTimer();
        task.skipCurrentTarget();
        navigationToPoint();
    }

    @Override
    public void onRecallElevatorClick() {
        cancelRecallElevatorCountDownTimer();
        int communicationMethod = robotInfo.getElevatorSetting().communicationMethod;
        if (isRecallElevatorInside) {
            if (communicationMethod == 0) {
                elevatorControlManager.takeElevatorInside();
            } else {
                CallingHelper.INSTANCE.init(
                        robotInfo.getROSHostname(),
                        Integer.parseInt(robotInfo.getCurrentMapEvent().getAlias()),
                        Integer.parseInt(task.getNextPointWithElevator().getFirst()),
                        new ElevatorWithLoraCallback()
                );
                CallingHelper.INSTANCE.callElevatorInside();
            }
        } else {
            if (communicationMethod == 0) {
                elevatorControlManager.takeElevator();
            } else if (communicationMethod == 1) {
                CallingHelper.INSTANCE.init(
                        robotInfo.getROSHostname(),
                        Integer.parseInt(robotInfo.getCurrentMapEvent().getAlias()),
                        Integer.parseInt(task.getNextPointWithElevator().getFirst()),
                        new ElevatorWithLoraCallback()
                );
                CallingHelper.INSTANCE.callElevatorByNetwork();
            } else if (communicationMethod == 2) {
                CallingHelper.INSTANCE.init(
                        robotInfo.getROSHostname(),
                        Integer.parseInt(robotInfo.getCurrentMapEvent().getAlias()),
                        Integer.parseInt(task.getNextPointWithElevator().getFirst()),
                        new ElevatorWithLoraCallback()
                );
                CallingHelper.INSTANCE.callElevatorByLoRa(robotInfo.getElevatorSetting().gatewayId);
            }
        }
        Pair<String, String> nextPointWithElevator = task.getNextPointWithElevator();
        view.updateRunningView(nextPointWithElevator.getFirst(), nextPointWithElevator.getSecond(), Step.REQUEST_ID);

    }

    @Override
    public void onLiftUpClick() {
        if (robotInfo.getLiftModelState() == 1) {
            view.showLiftModelState(1);
            return;
        }
        if (robotInfo.isWithAntiCollisionStrip()) {
            ros.ioControl(0x04);
        }
        if (isEmergencyButtonDown()) return;
        ros.liftUp();
        liftManually = true;
        callingInfo.setLifting(true);
        mHandler.postDelayed(liftControlTimeOutRunnable, 30_000);
        view.showManualLiftControlTip(true);
    }

    @Override
    public void onLiftDownClick() {
        if (robotInfo.getLiftModelState() == 0) {
            view.showLiftModelState(0);
            return;
        }
        if (robotInfo.isWithAntiCollisionStrip()) {
            ros.ioControl(0x04);
        }
        if (isEmergencyButtonDown()) return;
        ros.liftDown();
        liftManually = true;
        callingInfo.setLifting(true);
        mHandler.postDelayed(liftControlTimeOutRunnable, 30_000);
        view.showManualLiftControlTip(false);
    }

    @Override
    public void onPowerConnect() {
        Timber.w("对接充电桩成功");
        uploadTaskRecord(0);
        onTaskFinished(0, null, null);
    }

    @Override
    public void onCustomInitPose(double[] currentPosition) {
        if (!robotInfo.isSwitchingMap() && !robotInfo.isRepositioning()) {
            return;
        }
        if (!robotInfo.isElevatorMode()) return;
        Timber.w("currentAction : %s", currentAction);
        if (robotInfo.isSwitchingMap()) {
            Timber.tag("onCustomInitPose").w("切换地图完成");
            robotInfo.setSwitchingMap(false);
            if (currentAction != null && currentAction.getFirst().equals(TaskAction.check_leave_elevator_point_reachable)) {
                double[] position = PointCacheInfo.INSTANCE.getEnterElevatorPoint().getPosition();
                relocPosition = Arrays.copyOf(position, position.length);

                if (relocPosition[2] >= 0) {
                    relocPosition[2] -= Math.PI;
                } else {
                    relocPosition[2] += Math.PI;
                }
            } else {
                relocPosition = PointCacheInfo.INSTANCE.getLeaveElevatorPoint().getPosition();
            }
            ros.relocateByCoordinate(relocPosition);
            robotInfo.setRepositioning(true);
            return;
        }
        if (relocPosition == null) {
            Timber.d("reloc position is null");
            return;
        }
        if (robotInfo.isRepositioning()) {
            if (!PointUtils.INSTANCE.isPositionError(relocPosition, currentPosition, 0.7, 1.04)) {
                mHandler.postDelayed(() -> {
                    relocationCount = 0;
                    if (EasyDialog.isShow() && EasyDialog.getInstance().isAutoDismissEnable())
                        EasyDialog.getInstance().dismiss();
                    view.updateTakeElevatorStep(Step.IDLE, task.getNextPointWithElevator().getFirst());
                    robotInfo.setRepositioning(false);
                    Timber.tag("onCustomInitPose").w("重定位完成");
                    task.resetStopNearByParameter();
                    if (robotInfo.getLiftModelState() == 1) {
                        task.setRobotWidthAndLidar(false);
                    }
                    navigationToPoint();
                }, 3000);
                return;
            }
            if (++relocationCount > 3) {
                if (EasyDialog.isShow() && EasyDialog.getInstance().isAutoDismissEnable())
                    EasyDialog.getInstance().dismiss();
                uploadTaskRecord(1);
                onTaskFinished(-1, context.getString(R.string.text_relocate_failed_task_finish), "");
                return;
            }
            if (robotInfo.getRosVersionCode() >= 322) {
                ros.relocAbPoint(relocPosition);
                return;
            }
            ros.relocateByCoordinate(relocPosition);
        }
    }

    @Override
    public void onEncounterObstacle() {
        if (obstacleSetting == null || !obstacleSetting.enableObstaclePrompt
                || System.currentTimeMillis() - lastObstaclePromptPlaybackTimeMills < 6000
                || VoiceHelper.isPlaying())
            return;
        try {
            List<Integer> targetObstaclePrompts = obstacleSetting.targetObstaclePrompts;
            if (targetObstaclePrompts != null && !targetObstaclePrompts.isEmpty()) {
                if (obstacleSetting.obstaclePromptAudioList != null && !obstacleSetting.obstaclePromptAudioList.isEmpty()) {
                    int currentPlayback = currentMindOutIndex++ % targetObstaclePrompts.size();
                    VoiceHelper.playFile(obstacleSetting.obstaclePromptAudioList.get(targetObstaclePrompts.get(currentPlayback)), this);
                }
            } else {
                playVoiceTip("voice_mind_out_" + (currentMindOutIndex++ % 3));
            }
            lastObstaclePromptPlaybackTimeMills = System.currentTimeMillis();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPositionUpdate(double[] position) {
        if (robotInfo.getState() == State.PAUSE) return;
        if (currentAction == null) return;
        if (robotInfo.isDispatchModeOpened()) {
            if (currentOrder != null && currentPath != null && !currentPath.isEmpty()) {
                int lastSize = currentPath.size();
                PointCacheInfo.INSTANCE.updatePath(position, currentPath);
                int currentSize = currentPath.size();
                if (currentSize < lastSize) {
                    Timber.w("路线更新: %s", currentPath);
                    DispatchManager.INSTANCE.setCurrentPoint(currentPath.get(0));
                }
                if (currentSize == 1) {
                    DispatchManager.INSTANCE.triggerHeartbeat(TaskProcess.COMPLETE);
                    currentPath.clear();
                }
            }
        } else if (robotInfo.getNavigationMode() == NavigationMode.fixPathMode) {
            if (currentPath != null) {
                int lastSize = currentPath.size();
                PointCacheInfo.INSTANCE.updatePath(position, currentPath);
                int currentSize = currentPath.size();
                if (currentSize < lastSize) {
                    Timber.w("路线更新: %s", currentPath);
                    if (currentSize > 1) {
                        double pathWidth = PointCacheInfo.INSTANCE.getPathWidth(currentPath.get(0), currentPath.get(1));
                        ros.maxPlanDist(pathWidth);
                    }
                }
            }
        }
        String action = currentAction.getFirst();
        PointModel pointModel = currentAction.getSecond();
        if (action.equals(TaskAction.agv_retry_point)) {
            double distanceToAgvPoint = PointUtils.calculateDistance(position, pointModel.getPosition());
            if (distanceToAgvPoint > 1) {
                Timber.w("离开失败点位超过1米,重试");
                currentAction = null;
                navigationToPoint();
            }
        }
    }

    @Override
    public void onTimeUpdate(long currentTimeMills) {
        if (robotInfo.isElevatorMode() && elevatorControlManager != null) {
            if (elevatorControlManager.getCurrentStep() == Step.CALL_ELEVATOR
                    || elevatorControlManager.getCurrentStep() == Step.CALL_ELEVATOR_INSIDE
                    || elevatorControlManager.getCurrentStep() == Step.ENTER_ELEVATOR_COMPLETE) {
                if (elevatorControlManager.getWaitElevatorTime() != 0
                        && currentTimeMills - elevatorControlManager.getWaitElevatorTime() > (long) robotInfo.getElevatorSetting().waitingElevatorTimeoutRetryTimeInterval * 60 * 1000) {
                    if (!TextUtils.isEmpty(elevatorControlManager.getElevatorId())
                            && !TextUtils.isEmpty(elevatorControlManager.getThingId())) {
                        Notifier.notify(new Msg(NotifyConstant.SYSTEM_NOTIFY, "乘梯异常", "乘梯超时 , elevatorId : " + elevatorControlManager.getElevatorId() + ", thingId : " + elevatorControlManager.getThingId(), robotInfo.getROSHostname()));
                        if (++recallElevatorCount > 5) {
                            Timber.w("乘梯重试5次");
                            uploadTaskRecord(1);
                            onTaskFinished(-1, context.getString(R.string.text_recall_elevator_exceeded_maximum_number), "");
                            return;
                        }
                        elevatorControlManager.cancelTakeElevator(elevatorControlManager.getElevatorId(), elevatorControlManager.getThingId(), "retry : timeout")
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .doOnNext(response -> {
                                    elevatorControlManager.setElevatorId("");
                                    elevatorControlManager.setThingId("");
                                })
                                .flatMapCompletable(action ->
                                        Completable.fromAction(() -> {
                                                    if (elevatorControlManager.getCurrentStep() == Step.ENTER_ELEVATOR_COMPLETE
                                                            || elevatorControlManager.getCurrentStep() == Step.CALL_ELEVATOR_INSIDE) {
                                                        elevatorControlManager.takeElevatorInside();
                                                    } else {
                                                        elevatorControlManager.takeElevator();
                                                    }
                                                })
                                                .delay(3, TimeUnit.SECONDS)
                                                .observeOn(AndroidSchedulers.mainThread())
                                                .doOnComplete(() -> {
                                                    Pair<String, String> nextPointWithElevator = task.getNextPointWithElevator();
                                                    view.updateRunningView(nextPointWithElevator.getFirst(), nextPointWithElevator.getSecond(), Step.REQUEST_ID);
                                                })
                                ).subscribe(() -> {
                                }, throwable -> {
                                    uploadTaskRecord(1);
                                    onTaskFinished(-1, context.getString(R.string.exception_waiting_elevator_timeout), "");
                                });
                    }
                }
            }
        }
    }

    @Override
    public void onMissPose() {
        Timber.w("定位异常,结束任务");
        Notifier.notify(new Msg(NotifyConstant.LOCATE_NOTIFY, "定位异常", "定位异常", robotInfo.getROSHostname()));
    }

    @Override
    public void onShortDij(FixedPathResultEvent event) {
        Timber.w("更新全局路线 %s", event.getPathPointList());
        if (robotInfo.getState() != State.DELIVERY && robotInfo.getState() != State.RETURNING)
            return;
        currentPath = event.getPathPointList();
        if (!robotInfo.isDoorControlMode()) return;
        if (!doorControlPositionList.isEmpty()) return;
        List<String> pathPointList = event.getPathPointList();
        for (int i = 0; i < pathPointList.size(); i++) {
            String s = pathPointList.get(i);
            if (s.startsWith(AIR_SHOWER_DOOR_PREFIX) || s.startsWith(DOOR_PREFIX)) {
                boolean isAirShowerDoor = false;
                int index = 9;
                if (s.startsWith(AIR_SHOWER_DOOR_PREFIX)) {
                    index = 20;
                    isAirShowerDoor = true;
                }
                if (s.length() > index && s.substring(index - 4, index).matches("\\d+")) {
                    Pair<GenericPoint, GenericPoint> door = PointCacheInfo.INSTANCE.getDoorControlPoint(event.getPathPointList().subList(i, pathPointList.size()), s.substring(0, index));
                    if (door != null) {
                        double radian = PointUtils.INSTANCE.calculateRadian(door.getFirst().getPosition(), door.getSecond().getPosition());
                        Timber.w("门控 : %s , radian : %s , 风淋门 : %s", door, radian, isAirShowerDoor);
                        double[] doorBackPosition = door.getSecond().getPosition();
                        doorBackPosition[2] = radian;
                        doorControlPositionList.add(new Pair<>(TaskAction.door_front_point, new PointModel.Builder().room(door.getFirst().getName().substring(index - 4, index)).name(door.getFirst().getName()).isAirShowerDoor(isAirShowerDoor).build()));
                        doorControlPositionList.add(new Pair<>(TaskAction.door_back_position, new PointModel.Builder().room(door.getSecond().getName().substring(index - 4, index)).position(doorBackPosition).isAirShowerDoor(isAirShowerDoor).build()));
                    }
                }
            }
        }
        if (!doorControlPositionList.isEmpty()) {
            navigationToDoorFront();
        }
    }

    @Override
    public void onDockFailed() {
        Timber.w("充电对接失败 :%s", robotInfo.getChargeFailedCount());
//        if (robotInfo.getChargeFailedCount() < 2) {
//            playVoiceTip("voice_retry_charge");
//            robotInfo.setChargeFailedCount(robotInfo.getChargeFailedCount() + 1);
//            ros.dockChargingPile();
//            return;
//        }
        ros.cancelCharge();
        robotInfo.setNavigating(false);
        callingInfo.getHeartBeatInfo().setNavigating(false);
        Notifier.notify(new Msg(NotifyConstant.CHARGE_NOTIFY, "充电状态", "充电对接失败", robotInfo.getROSHostname()));
        uploadTaskRecord(1);
        onTaskFinished(-1, context.getString(R.string.voice_charge_failed), "voice_charge_failed");
    }

    @Override
    public void onAntiFall() {
        Timber.w("触发防跌");
        mHandler.removeCallbacksAndMessages(null);
        cancelNavigation();
        pauseBackgroundMusic();
        cancelCountdownTimer();
        if (EasyDialog.isShow() && EasyDialog.getInstance().isAutoDismissEnable())
            EasyDialog.getInstance().dismiss();
        Notifier.notify(new Msg(NotifyConstant.TASK_NOTIFY, "防跌状态", "机器人任务过程中检测到跌落风险，请检查机器定位是否正确", robotInfo.getROSHostname()));
        if (isFinishDirectly(context.getString(R.string.text_drop_during_through_door))) return;
        showPauseViewWithTip(context.getString(R.string.text_recovery_falling_state), context.getString(R.string.text_trigger_anti_fall_task_pause));
    }

    @Override
    public void onSpecialPlan(List<Room> roomList) {
        if (roomList == null || roomList.isEmpty()) return;
        Timber.w("roomList : %s", roomList);
        if (robotInfo.getState() != State.DELIVERY && robotInfo.getState() != State.RETURNING)
            return;
        if (robotInfo.getNavigationMode() == NavigationMode.autoPathMode && robotInfo.isDoorControlMode()) {
            if (!doorControlPositionList.isEmpty()) return;
            for (Room room : roomList) {
                List<Double> coordination = room.coordination;
                if (coordination == null || coordination.size() != 6) continue;
                if (room.type == SpecialAreaType.door_control_area) {
                    boolean isAirShowerDoor = false;
                    String name = room.name;
                    if (name.endsWith(AIR_SHOWER_DOOR_SUFFIX)) {
                        isAirShowerDoor = true;
                        name = name.replace(AIR_SHOWER_DOOR_SUFFIX, "");
                    }
                    if (!name.replace("N-", "").matches("^\\d{4}$")) continue;
                    double[] doorFrontPosition = {coordination.get(0), coordination.get(1), coordination.get(2)};
                    double[] doorBackPosition = {coordination.get(3), coordination.get(4), coordination.get(5)};
                    if (PointUtils.calculateDistance(doorFrontPosition, doorBackPosition) < 0.3)
                        continue;
                    Timber.w("经过门控区 %s 门前 : %s ,门后 : %s", name, Arrays.toString(doorFrontPosition), Arrays.toString(doorBackPosition));
                    doorControlPositionList.add(new Pair<>(TaskAction.door_front_position, new PointModel.Builder().isAirShowerDoor(isAirShowerDoor).room(name).position(doorFrontPosition).build()));
                    doorControlPositionList.add(new Pair<>(TaskAction.door_back_position, new PointModel.Builder().isAirShowerDoor(isAirShowerDoor).room(name).position(doorBackPosition).build()));
                }
            }
            if (!doorControlPositionList.isEmpty()) {
                navigationToDoorFront();
            }
        }
    }

    @Override
    public void onApplyMapResult(ApplyMapEvent event) {
        if (!robotInfo.isSwitchingMap()) {
            Timber.e("任务重地图切换异常");
            uploadTaskRecord(1);
            onTaskFinished(-1, context.getString(R.string.text_cannot_switch_map_during_task_executing), "");
            return;
        }
        if (event.isSuccess()) {
            Timber.w("切换地图成功");
            PointCacheInfo.INSTANCE.updateCurrentMapPointsByMap(robotInfo.supportEnterElevatorPoint(), event.getMap());
            view.updateTakeElevatorStep(Step.APPLY_MAP, task.getNextPointWithElevator().getFirst());
        } else {
            Timber.w("切换地图失败");
            uploadTaskRecord(1);
            onTaskFinished(-1, context.getString(R.string.text_switch_map_failure, task.getNextPointWithElevator().getFirst()), "");
        }
    }

    @Override
    public void onWheelError(WheelStatusEvent event) {
        if (isFinished) return;
        cancelNavigation();
        Notifier.notify(new Msg(NotifyConstant.HARDWARE_NOTIFY, "轮子状态异常", event.toString(), robotInfo.getROSHostname()));
        uploadTaskRecord(1);
        onTaskFinished(-3, context.getString(R.string.text_check_wheel_state_error_cannot_use_application, "0x" + String.format(Locale.CHINA, "%02X", event.getState())), "");

    }

    @Override
    public void onSensorsError(SensorsEvent event) {
        if (isFinished || robotInfo.isElevatorMode() || robotInfo.isRepositioning() || robotInfo.isRebootingROSCauseTimeJump())
            return;
        if (System.currentTimeMillis() - robotInfo.getReloctionSuccessAfterTimeJumpTimestamp() < 30_000) {
            if (robotInfo.getState() == State.PAUSE) return;
            isCountdownCauseSensorError = true;
            if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss();
            pauseTask(30, Errors.INSTANCE.getSensorErrorTip(context, event));
            return;
        }
        String hardwareError = Errors.INSTANCE.getSensorError(context, event);
        cancelNavigation();
        uploadHardwareError(event, hardwareError);
        Notifier.notify(new Msg(NotifyConstant.HARDWARE_NOTIFY, "传感器状态", event.getBaseData(), robotInfo.getROSHostname()));
        uploadTaskRecord(1);
        onTaskFinished(-3, hardwareError, "");
    }

    @Override
    public void onKeyUpCodeF2() {
        if (isEmergencyButtonDown()) return;
        if (isSensorError()) return;
        if (recallElevatorCountDownTimer != null) {
            if (isFinished) return;
            cancelRecallElevatorCountDownTimer();
            int communicationMethod = robotInfo.getElevatorSetting().communicationMethod;
            if (isRecallElevatorInside) {
                if (communicationMethod == 0) {
                    elevatorControlManager.takeElevatorInside();
                } else {
                    CallingHelper.INSTANCE.callElevatorInside();
                }
            } else {
                if (communicationMethod == 0) {
                    elevatorControlManager.takeElevator();
                } else if (communicationMethod == 1) {
                    CallingHelper.INSTANCE.callElevatorByNetwork();
                } else if (communicationMethod == 2) {
                    CallingHelper.INSTANCE.callElevatorByLoRa(robotInfo.getElevatorSetting().gatewayId);
                }
            }
            Pair<String, String> nextPointWithElevator = task.getNextPointWithElevator();
            view.updateRunningView(nextPointWithElevator.getFirst(), nextPointWithElevator.getSecond(), Step.REQUEST_ID);
            return;
        }
        if (robotInfo.getState() == State.PAUSE) {
            if (isFinished) return;
            if (isRouteModePointLiftActionComplete(isArrivedTargetPoint))
                return;
            cancelCountdownTimer();
            if (!task.isArrivedLastPointAndEndInPlace() || (robotInfo.getMode() == TaskMode.MODE_ROUTE && task.hasNext())) {
                navigationToPoint();
            } else {
                uploadTaskRecord(1);
                onTaskFinished(0, null, null);
            }
        }
    }

    @Override
    public void detailCustomCallingTask(CallingTaskEvent event) {
        checkCanStartRemoteTask();
    }

    @Override
    public void detailCustomNormalTask(NormalTaskEvent event) {
        checkCanStartRemoteTask();
    }

    @Override
    public void detailCustomRouteTask(RouteTaskEvent event) {
        checkCanStartRemoteTask();
    }

    @Override
    public void detailCustomQRCodeTask(QRCodeTaskEvent event) {
        checkCanStartRemoteTask();
    }

    @Override
    public void detailCustomChargeTask(String token) {
        checkCanStartRemoteTask();
    }

    @Override
    public void detailCustomReturnTask(String token) {
        checkCanStartRemoteTask();
    }

    @SuppressLint("CheckResult")
    @Override
    public void onGetPlanResult(boolean success) {
        if (!success) {
            Timber.w("get plan failed");
        }
        Timber.w("state : %s, checkCanNavigationCount : %s, canNavigationCount : %s, currentAction : %s", robotInfo.getState(), checkCanNavigationCount, canNavigationCount, currentAction);
        if (robotInfo.getState() == State.PAUSE || robotInfo.getState() == State.IDLE || currentAction == null)
            return;
        int generatePathCount = robotInfo.getElevatorSetting().generatePathCount;
        String action = currentAction.getFirst();
        PointModel pointModel = currentAction.getSecond();
        if (++checkCanNavigationCount > generatePathCount) {
            if (!robotInfo.isSpaceShip() || !robotInfo.isLiftModelInstalled()) {
                task.updateRobotSize(false);
            }
            if (success && (generatePathCount < 5 || ++canNavigationCount >= 3)) {
                if (action.equals(TaskAction.check_inside_elevator_point_reachable)) {
                    navigationToTakeElevatorPoint();
                } else if (action.equals(TaskAction.check_leave_elevator_point_reachable)) {
                    navigationToLeaveElevatorPoint();
                } else if (action.equals(TaskAction.check_can_leave_elevator)) {
                    leaveElevatorNavigationToEnterElevatorPoint(Arrays.copyOf(pointModel.getPosition(), pointModel.getPosition().length));
                }
                return;
            }
            if (action.equals(TaskAction.check_leave_elevator_point_reachable)) {
                Timber.w("无法到达出梯点,乘梯结束");
                switchMap();
            } else {
                int communicationMethod = robotInfo.getElevatorSetting().communicationMethod;
                if (communicationMethod == 0) {
                    elevatorControlManager.cancelTakeElevator(elevatorControlManager.getElevatorId(), elevatorControlManager.getThingId(), "cannot enter elevator")
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(response -> {
                                elevatorControlManager.setElevatorId("");
                                elevatorControlManager.setThingId("");
                                if (robotInfo.supportEnterElevatorPoint() && PointCacheInfo.INSTANCE.isEnterElevatorPointInitialized()) {
                                    if (action.equals(TaskAction.check_inside_elevator_point_reachable)) {
                                        isEnterElevatorFailedReturnToWaitingPoint = true;
                                        view.updateTakeElevatorStep(Step.ENTER_ELEVATOR_FAILED_RETURN_TO_WAITING_POINT, task.getNextPointWithElevator().getFirst());
                                        navigationToWaitingElevatorPoint();
                                    } else if (action.equals(TaskAction.check_can_leave_elevator)) {
                                        startRecallElevatorCountDownTimer(context.getString(R.string.text_check_can_not_leave_elevator), true);
                                    }
                                } else {
                                    startRecallElevatorCountDownTimer(context.getString(R.string.text_generate_path_to_enter_elevator_failed), false);
                                }
                            }, throwable -> {
                                uploadTaskRecord(1);
                                onTaskFinished(-1, context.getString(R.string.exception_waiting_elevator_timeout), "");
                            });
                } else {
                    CallingHelper.INSTANCE.abortTask();
                    if (robotInfo.supportEnterElevatorPoint() && PointCacheInfo.INSTANCE.isEnterElevatorPointInitialized()) {
                        if (action.equals(TaskAction.check_inside_elevator_point_reachable)) {
                            isEnterElevatorFailedReturnToWaitingPoint = true;
                            view.updateTakeElevatorStep(Step.ENTER_ELEVATOR_FAILED_RETURN_TO_WAITING_POINT, task.getNextPointWithElevator().getFirst());
                            navigationToWaitingElevatorPoint();
                        } else if (action.equals(TaskAction.check_can_leave_elevator)) {
                            startRecallElevatorCountDownTimer(context.getString(R.string.text_check_can_not_leave_elevator), true);
                        }
                    } else {
                        startRecallElevatorCountDownTimer(context.getString(R.string.text_generate_path_to_enter_elevator_failed), false);
                    }
                }
            }
            return;
        }
        canNavigationCount = success ? canNavigationCount + 1 : 0;
        mHandler.postDelayed(() -> ros.getPlanPoint(pointModel.getPosition()), 1000);
        if (checkCanNavigationCount < generatePathCount - 3) {
            if (VoiceHelper.isPlaying()) return;
            if (System.currentTimeMillis() - lastPlayLeaveALittleSpaceTime < 3000) return;
            playVoiceTip("voice_please_leave_a_little_space_" + checkCanNavigationCount % 2, () -> {
                lastPlayLeaveALittleSpaceTime = System.currentTimeMillis();
                MediaPlayerHelper.getInstance().updateVolume(true);
            });
        }
    }

    private void navigationToWaitingElevatorPoint() {
        Pair<String, String> nextPoint = task.getNextPointWithElevator();
        CurrentMapEvent currentMapEvent = robotInfo.getCurrentMapEvent();
        GenericPoint waitingElevatorPoint = PointCacheInfo.INSTANCE.getWaitingElevatorPoint();
        currentAction =
                new Pair<>(TaskAction.waiting_elevator,
                        new PointModel.Builder()
                                .name(waitingElevatorPoint.getName())
                                .currentMap(currentMapEvent.getAlias())
                                .targetMap(nextPoint.getFirst())
                                .build()
                );
        Timber.w("currentAction : %s", currentAction);
        navigationTo(waitingElevatorPoint.getName());
        ros.setTolerance(0);
    }

    private void navigationToEnterElevatorPoint() {
        GenericPoint enterElevatorPoint = PointCacheInfo.INSTANCE.getEnterElevatorPoint();
        currentAction = new Pair<>(TaskAction.enter_elevator, new PointModel.Builder().name(enterElevatorPoint.getName()).build());
        Timber.w("currentAction : %s", currentAction);
        ros.setGlobalCost(0);
        if (robotInfo.getNavigationMode() == NavigationMode.autoPathMode) {
            ros.navigationByPoint(enterElevatorPoint.getName());
        } else {
            ros.navigationByPathPoint(enterElevatorPoint.getName());
        }
        navigationTo(enterElevatorPoint.getName());
        isWaitingGlobalPath = true;
        view.updateTakeElevatorStep(Step.ENTER_ELEVATOR_ACK, task.getNextPointWithElevator().getFirst());
    }

    private void leaveElevatorNavigationToEnterElevatorPoint(double[] position) {
        if (position[2] >= 0) {
            position[2] = position[2] - Math.PI;
        } else {
            position[2] = position[2] + Math.PI;
        }
        Timber.w("离开电梯,前往进梯点: %s", Arrays.toString(position));
        currentAction = new Pair<>(TaskAction.leave_elevator_to_enter_elevator_point, new PointModel.Builder().position(position).build());
        view.updateTakeElevatorStep(Step.LEAVE_ELEVATOR_ACK, task.getNextPointWithElevator().getFirst());
        ros.navigationByCoordinate(position);
        isTimeout = false;
        mHandler.postDelayed(leaveElevatorTimeoutRunnable, 60_000);
    }

    @SuppressLint("CheckResult")
    private final Runnable navigationToEnterElevatorPointTimeOutRunnable = new Runnable() {
        @Override
        public void run() {
            isTimeout = true;
            int communicationMethod = robotInfo.getElevatorSetting().communicationMethod;
            if (communicationMethod == 0) {
                elevatorControlManager.cancelTakeElevator(elevatorControlManager.getElevatorId(), elevatorControlManager.getThingId(), "navigation to enter elevator point timeout")
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(response -> {
                            elevatorControlManager.setElevatorId("");
                            elevatorControlManager.setThingId("");
                        }, throwable -> {
                            uploadTaskRecord(1);
                            onTaskFinished(-1, context.getString(R.string.exception_navigation_to_enter_elevator_point_timeout), "");
                        });
            } else {
                CallingHelper.INSTANCE.abortTask();
            }
            isEnterElevatorFailedReturnToWaitingPoint = true;
            view.updateTakeElevatorStep(Step.ENTER_ELEVATOR_FAILED_RETURN_TO_WAITING_POINT, task.getNextPointWithElevator().getFirst());
            navigationToWaitingElevatorPoint();
        }
    };

    private final Runnable enterElevatorTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            isTimeout = true;
            cancelNavigation();
            int communicationMethod = robotInfo.getElevatorSetting().communicationMethod;
            if (communicationMethod == 0) {
                elevatorControlManager.cancelTakeElevator(elevatorControlManager.getElevatorId(), elevatorControlManager.getThingId(), "navigation to take elevator point timeout")
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(response -> {
                            uploadTaskRecord(1);
                            onTaskFinished(-1, context.getString(R.string.exception_navigation_to_take_elevator_point_timeout), "");
                        }, throwable -> {
                            uploadTaskRecord(1);
                            onTaskFinished(-1, context.getString(R.string.exception_navigation_to_take_elevator_point_timeout_and_cancel_take_elevator_task_failed), "");
                        });
            } else {
                uploadTaskRecord(1);
                onTaskFinished(-1, context.getString(R.string.exception_navigation_to_take_elevator_point_timeout), "");
            }
        }
    };

    private final Runnable leaveElevatorTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            isTimeout = true;
            cancelNavigation();
            int communicationMethod = robotInfo.getElevatorSetting().communicationMethod;
            if (communicationMethod == 0) {
                elevatorControlManager.cancelTakeElevator(elevatorControlManager.getElevatorId(), elevatorControlManager.getThingId(), "navigation to leave elevator point timeout")
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(response -> {
                            uploadTaskRecord(1);
                            onTaskFinished(-1, context.getString(R.string.exception_navigation_to_leave_elevator_point_timeout), "");
                        }, throwable -> {
                            uploadTaskRecord(1);
                            onTaskFinished(-1, context.getString(R.string.exception_navigation_to_leave_elevator_point_timeout_and_cancel_take_elevator_task_failed), "");
                        });
            } else {
                uploadTaskRecord(1);
                onTaskFinished(-1, context.getString(R.string.exception_navigation_to_leave_elevator_point_timeout), "");
            }
        }
    };

    private void navigationToTakeElevatorPoint() {
        GenericPoint takeElevatorPoint = PointCacheInfo.INSTANCE.getTakeElevatorPoint();
        currentAction = new Pair<>(TaskAction.inside_elevator, new PointModel.Builder().name(takeElevatorPoint.getName()).build());
        Timber.w("currentAction : %s", currentAction);
        navigationTo(takeElevatorPoint.getName());
        view.updateTakeElevatorStep(Step.ENTER_ELEVATOR_ACK, task.getNextPointWithElevator().getFirst());
        isTimeout = false;
        mHandler.postDelayed(enterElevatorTimeoutRunnable, 60_000);
    }

    private void navigationToLeaveElevatorPoint() {
        if (robotInfo.getElevatorSetting().isSingleDoor) {
            GenericPoint leaveElevatorPoint = PointCacheInfo.INSTANCE.getLeaveElevatorPoint();
            Timber.d("单向开门,出梯点: %s", leaveElevatorPoint);
            currentAction = new Pair<>(TaskAction.leave_elevator, new PointModel.Builder().name(leaveElevatorPoint.getName()).build());
            navigationTo(leaveElevatorPoint.getName());
        } else {
            GenericPoint leaveElevatorPoint = PointCacheInfo.INSTANCE.getPointByAliasAndPointType(task.getNextPointWithElevator().getFirst(), GenericPoint.LEAVE_ELEVATOR);
            Timber.d("双向开门,出梯点: %s", leaveElevatorPoint);
            currentAction = new Pair<>(TaskAction.leave_elevator_position, new PointModel.Builder().position(leaveElevatorPoint.getPosition()).build());
            ros.navigationByCoordinate(leaveElevatorPoint.getPosition());
        }
    }

    private void enterElevator() {
        if (robotInfo.getElevatorSetting().isDetectionSwitchOpen) {
            GenericPoint takeElevatorPoint = PointCacheInfo.INSTANCE.getTakeElevatorPoint();
            currentAction = new Pair<>(TaskAction.check_inside_elevator_point_reachable, new PointModel.Builder().position(takeElevatorPoint.getPosition()).build());
            ros.getPlanPoint(takeElevatorPoint.getPosition());
            view.updateTakeElevatorStep(Step.CHECK_PATH_TO_ELEVATOR, task.getNextPointWithElevator().getFirst());
        } else {
            navigationToTakeElevatorPoint();
        }
    }

    private void takeElevatorComplete() {
        int communicationMethod = robotInfo.getElevatorSetting().communicationMethod;
        if (communicationMethod == 0) {
            if (elevatorControlManager.getCurrentStep() == Step.IDLE) {
                recallElevatorCount = 0;
                switchMap();
                if (elevatorControlManager != null) {
                    elevatorControlManager.release();
                }
                switchOutSideWifi(true);
            } else {
                elevatorControlManager.complete(0, false);
                view.updateTakeElevatorStep(Step.LEAVE_ELEVATOR_COMPLETE, task.getNextPointWithElevator().getFirst());
            }
        } else {
            if (CallingHelper.INSTANCE.getTakeElevatorStep() == TakeElevatorStep.INIT) {
                recallElevatorCount = 0;
                switchMap();
                if (elevatorControlManager != null) {
                    elevatorControlManager.release();
                }
                switchOutSideWifi(true);
            } else {
                CallingHelper.INSTANCE.taskComplete();
                view.updateTakeElevatorStep(Step.LEAVE_ELEVATOR_COMPLETE, task.getNextPointWithElevator().getFirst());
            }
        }
    }

    private void detailNavigationSuccessResult(String name) {
        pauseBackgroundMusic();
        Timber.w("到达 :%s", name);
        if (currentAction != null) {
            Timber.w("currentAction : %s", currentAction);
            String action = currentAction.getFirst();
            PointModel pointModel = currentAction.getSecond();
            if (action.startsWith("door_")) {//门控
                currentAction = null;
                isArrivedDoorPoint = true;
                boolean opposite = pointModel.getRoom().startsWith("N-");
                String doorNum = pointModel.getRoom().replace("N-", "");
                if (action.equals(TaskAction.door_front_position) || action.equals(TaskAction.door_front_point)) {
                    view.updateRunningTip(context.getString(R.string.text_arrive_at_door_front, doorNum));
                    setCannotResponseTaskDuringTaskExecuting();
                    openDoor(doorNum, opposite);
                    playVoiceTip("voice_open_door");
                } else if (action.equals(TaskAction.door_back_position)) {
                    boolean waitingCloseResp = robotInfo.getDoorControlSetting().closeDoorAction == 1;
                    closeDoor(doorNum, opposite, waitingCloseResp);
                    playVoiceTip("voice_close_door");
                    if (!waitingCloseResp) {
                        Pair<String, PointModel> first = doorControlPositionList.getFirst();
                        doorControlPositionList.removeFirst();
                        view.dismissRunningTip();
                        if (first.getSecond().isAirShowerDoor() && !doorControlPositionList.isEmpty() && doorControlPositionList.getFirst().getSecond().isAirShowerDoor()) {
                            startCloseDoorCountDownTimer(robotInfo.getDoorControlSetting().waitingTime, doorNum);
                            return;
                        }
                        if (!doorControlPositionList.isEmpty()) {
                            navigationToDoorFront();
                        } else {
                            ros.setTolerance(robotInfo.getReturningSetting().stopNearBy ? 1 : 0);
                            navigationToPoint();
                        }
                    } else {
                        view.updateRunningTip(context.getString(R.string.text_arrive_at_door_back, doorNum));
                    }
                }
            } else if (action.equals(TaskAction.leave_elevator_to_enter_elevator_point)) {//出梯时到达进梯点
                if (isTimeout) {
                    Timber.w("出梯已超时");
                    return;
                }
                mHandler.removeCallbacks(leaveElevatorTimeoutRunnable);
                checkCanNavigationCount = 0;
                canNavigationCount = 0;
                GenericPoint leaveElevatorPoint;
                if (robotInfo.getElevatorSetting().isSingleDoor) {
                    leaveElevatorPoint = PointCacheInfo.INSTANCE.getLeaveElevatorPoint();
                } else {
                    leaveElevatorPoint = PointCacheInfo.INSTANCE.getPointByAliasAndPointType(task.getNextPointWithElevator().getFirst(), GenericPoint.LEAVE_ELEVATOR);
                }
                currentAction = new Pair<>(TaskAction.check_leave_elevator_point_reachable, new PointModel.Builder().position(leaveElevatorPoint.getPosition()).build());
                takeElevatorComplete();
            } else if (action.startsWith("elevator_")) {//梯控
                currentAction = null;
                if (action.equals(TaskAction.enter_elevator)) {
                    if (isTimeout) {
                        Timber.w("前往进梯点已超时");
                        return;
                    }
                    mHandler.removeCallbacks(navigationToEnterElevatorPointTimeOutRunnable);
                    enterElevator();
                } else if (action.equals(TaskAction.waiting_elevator)) {
                    if (!WIFIUtils.isNetworkConnected(context) && robotInfo.getElevatorSetting().communicationMethod != 2) {
                        onTaskFinished(-1, context.getString(R.string.text_check_not_connect_network_cannot_take_elevator), "");
                        return;
                    }
                    ElevatorSetting elevatorSetting = robotInfo.getElevatorSetting();
                    if (!elevatorSetting.isSingleNetwork) {
                        Pair<String, String> outsideNetwork = elevatorSetting.outsideNetwork;
                        String connectWifiSSID = WIFIUtils.getConnectWifiSSID(context);
                        if (TextUtils.isEmpty(connectWifiSSID) || !connectWifiSSID.equals(outsideNetwork.getFirst())) {
                            onTaskFinished(-1, context.getString(R.string.text_check_not_connect_to_outside_network_cannot_take_elevator), "");
                            return;
                        }
                    }
                    int communicationMethod = elevatorSetting.communicationMethod;
                    if (communicationMethod == 0) {
                        elevatorControlManager.init(
                                robotInfo.getROSHostname(),
                                robotInfo.getCurrentMapEvent().getAlias(),
                                task.getNextPointWithElevator().getFirst(),
                                new ElevatorCallback()
                        );
                    } else {
                        CallingHelper.INSTANCE.init(
                                robotInfo.getROSHostname(),
                                Integer.parseInt(robotInfo.getCurrentMapEvent().getAlias()),
                                Integer.parseInt(task.getNextPointWithElevator().getFirst()),
                                new ElevatorWithLoraCallback()
                        );
                    }
                    if (isEnterElevatorFailedReturnToWaitingPoint) {
                        isEnterElevatorFailedReturnToWaitingPoint = false;
                        startRecallElevatorCountDownTimer(context.getString(R.string.text_generate_path_to_enter_elevator_failed), false);
                    } else {
                        if (communicationMethod == 0) {
                            elevatorControlManager.takeElevator();
                        } else if (communicationMethod == 1) {
                            CallingHelper.INSTANCE.callElevatorByNetwork();
                        } else if (communicationMethod == 2) {
                            CallingHelper.INSTANCE.callElevatorByLoRa(robotInfo.getElevatorSetting().gatewayId);
                        }
                        view.updateTakeElevatorStep(Step.REQUEST_ID, task.getNextPointWithElevator().getFirst());
                    }
                } else if (action.equals(TaskAction.inside_elevator)) {
                    if (isTimeout) {
                        Timber.w("进梯已超时");
                        return;
                    }
                    mHandler.removeCallbacks(enterElevatorTimeoutRunnable);
                    playVoiceTip("voice_enter_elevator");
                    ElevatorSetting elevatorSetting = robotInfo.getElevatorSetting();
                    int communicationMethod = elevatorSetting.communicationMethod;
                    if (communicationMethod == 0) {
                        elevatorControlManager.complete(0, true);
                    } else {
                        CallingHelper.INSTANCE.gotoTargetFloor();
                    }
                    view.updateTakeElevatorStep(Step.ENTER_ELEVATOR_COMPLETE, task.getNextPointWithElevator().getFirst());
                } else {
                    if (isTimeout) {
                        Timber.w("出梯已超时");
                        return;
                    }
                    mHandler.removeCallbacks(leaveElevatorTimeoutRunnable);
                    takeElevatorComplete();
                }
            } else if (action.equals(TaskAction.calling_point)) {//呼叫模式
                playVoiceTip("voice_calling_robot_arrived");
                uploadTaskRecord(0);
                onTaskFinished(0, null, null);
            } else if (action.equals(TaskAction.production_point)) {//到达出品点
                playVoiceTip("voice_arrived_at_product_point");
                uploadTaskRecord(0);
                onTaskFinished(0, null, null);
            } else if (action.equals(TaskAction.charge_point)) {//到达充电桩
                playVoiceTip("voice_start_docking_charging_pile");
                if (robotInfo.isDispatchModeOpened()) {
                    ros.dockChargingPile();
                }
            } else if (action.equals(TaskAction.agv_retry_point)) {
                Timber.w("导航到重试点成功,重新导航去agv点");
                navigationToPoint();
            } else if (action.equals(TaskAction.agv_point)) {//到达agv对接点
                if (robotInfo.isDispatchModeOpened()) {
                    ros.agvStart(name);
                }
                robotInfo.setQRCodeNavigating(true);
                task.setOrientationAndDistanceCalibration();
            } else {
                currentAction = null;
                long countDownTime = task.getCountDownTime();
                task.arrivedPoint(name);
                if (action.equals(TaskAction.delivery_point)) {
                    //普通模式设置任务完成不返回出品点且关闭倒计时
                    Timber.w("isArrivedLastPointAndStay : %s , countDownTime : %s", task.isArrivedLastPointAndEndInPlace(), countDownTime);
                    if (task.isArrivedLastPointAndEndInPlace() && countDownTime == 0) {
                        playVoiceTip("voice_arrived_target_point", () -> {
                            if (robotInfo.getState() == State.PAUSE || robotInfo.getState() == State.IDLE)
                                return;
                            MediaPlayerHelper.getInstance().updateVolume(true);
                            uploadTaskRecord(0);
                            onTaskFinished(0, null, null);
                        });
                    } else {
                        arriveWorkingPoint(name, countDownTime);
                    }
                } else if (action.equals(TaskAction.route_point)) {
                    arriveWorkingPoint(name, countDownTime);
                }
            }
        }
    }

    private void arriveWorkingPoint(String name, long time) {
        isArrivedTargetPoint = true;
        robotInfo.setState(State.PAUSE);
        String currentFloor = robotInfo.isElevatorMode() ? robotInfo.getCurrentMapEvent().getAlias() : "";
        String nextFloor = "";
        String nextPoint;
        if (robotInfo.isElevatorMode()) {
            nextFloor = task.getNextPointWithElevator().getFirst();
            nextPoint = task.getNextPointWithElevator().getSecond();
        } else {
            if (robotInfo.isDispatchModeOpened()) {
                if (task.getAction().equals(TaskAction.charge_point)) {
                    nextPoint = context.getString(R.string.text_charge_point);
                } else if (task.getAction().equals(TaskAction.production_point)) {
                    nextPoint = context.getString(R.string.text_product_point);
                } else {
                    nextPoint = task.getNextPointWithoutElevator();
                }
            } else {
                nextPoint = task.getNextPointWithoutElevator();
            }
        }
        view.arrivedTargetPoint(
                robotInfo.getMode(),
                task.getRouteName(),
                record.startTime,
                currentFloor,
                name,
                nextFloor,
                nextPoint,
                task.showReturnBtn(),
                task.isOpenedManualLiftUpControl(true),
                task.isOpenedManualLiftDownControl(true),
                task.hasNext(),
                !task.isArrivedLastPointAndEndInPlace()
        );
        if (time > 0) {
            playArrivedNormalModePointTip();
            startCountDownTimer(time, false);
        } else if (task.isArrivedLastPointAndEndInPlace()) {
            playVoiceTip("voice_arrived_target_point");
        } else {
            playArrivedNormalModePointTip();
        }
    }

    private void switchInsideWifi() {
        ElevatorSetting elevatorSetting = robotInfo.getElevatorSetting();
        if (!elevatorSetting.isSingleNetwork) {
            Board board = BoardFactory.create(context, Build.PRODUCT);
            Pair<String, String> insideNetwork = elevatorSetting.insideNetwork;
            String connectWifiSSID = WIFIUtils.getConnectWifiSSID(context);
            if (TextUtils.isEmpty(connectWifiSSID) || !connectWifiSSID.equals(insideNetwork.getFirst())) {
                isSwitchWifi = true;
                currentConnectingWifi = insideNetwork;
                Timber.w("切换wifi : %s", insideNetwork);
                board.connectWiFi(context, insideNetwork.getFirst(), insideNetwork.getSecond(), null);
                mHandler.postDelayed(switchInsideWifiTimeoutRunnable, 10_000);
            }
        }
    }

    private void switchOutSideWifi(boolean checkTimeout) {
        ElevatorSetting elevatorSetting = robotInfo.getElevatorSetting();
        if (!elevatorSetting.isSingleNetwork) {
            Board board = BoardFactory.create(context, Build.PRODUCT);
            Pair<String, String> outsideNetwork = elevatorSetting.outsideNetwork;
            String connectWifiSSID = WIFIUtils.getConnectWifiSSID(context);
            if (TextUtils.isEmpty(connectWifiSSID) || !connectWifiSSID.equals(outsideNetwork.getFirst())) {
                isSwitchWifi = true;
                currentConnectingWifi = outsideNetwork;
                Timber.w("切换wifi : %s", outsideNetwork);
                board.connectWiFi(context, outsideNetwork.getFirst(), outsideNetwork.getSecond(), null);
                if (checkTimeout) {
                    mHandler.postDelayed(switchOutsideWifiTimeoutRunnable, 10_000);
                }
            }
        }
    }

    private final Runnable switchOutsideWifiTimeoutRunnable = () -> {
        if (isSwitchWifi) {
            isSwitchWifi = false;
            Timber.w("切换梯外wifi超时");
        }
    };

    private final Runnable switchInsideWifiTimeoutRunnable = () -> {
        if (isSwitchWifi) {
            isSwitchWifi = false;
            Timber.w("切换梯内wifi超时");
            switchOutSideWifi(false);
        }
    };


    @Override
    public void onWifiConnectionSuccess() {
        if (!isSwitchWifi) return;
        String connectWifiSSID = WIFIUtils.getConnectWifiSSID(context);
        Timber.w("切换到网络: %s", connectWifiSSID);
        if (currentConnectingWifi != null && currentConnectingWifi.getFirst().equals(connectWifiSSID)) {
            isSwitchWifi = false;
            currentConnectingWifi = null;
            mHandler.removeCallbacks(switchInsideWifiTimeoutRunnable);
            mHandler.removeCallbacks(switchOutsideWifiTimeoutRunnable);
        }
    }

    @Override
    public void onGlobalPathEvent(List<Double> path) {
        ros.setGlobalCost(1);
        if (currentAction != null && isWaitingGlobalPath) {
            isWaitingGlobalPath = false;
            String action = currentAction.getFirst();
            if (action.equals(TaskAction.enter_elevator)) {
                long delayMillis = Math.max((int) (path.size() * Float.parseFloat(task.getSpeed()) * 1000), 15000);
                isTimeout = false;
                mHandler.postDelayed(navigationToEnterElevatorPointTimeOutRunnable, delayMillis);
                Timber.w("生成前往进梯点的路线,预计耗时: %s ms", delayMillis);
            }
        }
    }

    @Override
    public void onComplete() {
        MediaPlayerHelper.getInstance().updateVolume(true);
    }

    @Override
    public void onPlayComplete() {

    }

    @Override
    public void onCorrupted() {
        ToastUtils.showShortToast(context.getString(R.string.text_backgroud_music_file_corrupted));

    }

    private class ElevatorWithLoraCallback implements com.reeman.agv.calling.callback.ElevatorCallback {

        @Override
        public void onComplete() {
            try {
                CallingHelper.INSTANCE.resetState();
                if (elevatorControlManager != null) {
                    elevatorControlManager.release();
                }
                if (currentAction != null && currentAction.getFirst().equals(TaskAction.check_leave_elevator_point_reachable)) {
                    PointModel pointModel = currentAction.getSecond();
                    ros.getPlanPoint(pointModel.getPosition());
                    return;
                }
                recallElevatorCount = 0;
                switchMap();

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onInitiateTaskSuccess() {
            view.updateTakeElevatorStep(Step.CALL_ELEVATOR, task.getNextPointWithElevator().getFirst());
        }

        @Override
        public void onArrivedStartFloor() {
            onElevatorArrivedCurrentFloor();
        }

        @Override
        public void onGotoTargetFloor() {

        }

        @Override
        public void onArrivedTargetFloor(@NonNull String floor) {
            onElevatorArrivedTargetFloor(floor);
        }

        @Override
        public void onQueuing() {
            startRecallElevatorCountDownTimer(context.getString(R.string.text_check_other_robot_take_elevator), false);
        }

        @Override
        public void onInitiateTaskFailure(@NonNull Throwable throwable) {
            Timber.w(throwable, "发起乘梯失败");
            if (throwable instanceof com.reeman.agv.calling.exception.RequestFailureException) {
//                int code = ((com.reeman.agv.calling.exception.RequestFailureException) throwable).getCode();
                String errorTip = Errors.INSTANCE.getElevatorTaskInitiateErrorTip(context, (com.reeman.agv.calling.exception.RequestFailureException) throwable);
                uploadTaskRecord(1);
                onTaskFinished(-1, errorTip, "");
            } else {
                uploadTaskRecord(1);
                onTaskFinished(-1, context.getString(R.string.exception_unknown_exception), "");
            }
        }

        @Override
        public void onCheckLoRaNotExist() {
            uploadTaskRecord(1);
            onTaskFinished(-1, context.getString(R.string.text_checked_lora_not_exist), "");
        }

        @Override
        public void onLoRaDisconnectDuringTaskExecuting() {
            uploadTaskRecord(1);
            onTaskFinished(-1, context.getString(R.string.text_checked_lora_disconnect), "");
        }

        @Override
        public void onSendStartTaskCommandTimeout() {
            uploadTaskRecord(1);
            onTaskFinished(-1, context.getString(R.string.exception_waiting_start_task_response_timeout), "");
        }

        @Override
        public void onErrorCodeFromGateway(int code) {
            uploadTaskRecord(1);
            onTaskFinished(-1, Errors.INSTANCE.getElevatorTaskErrorTip(context, code), "");
        }
    }

    private void onElevatorArrivedCurrentFloor() {
        try {
            playVoiceTip("voice_elevator_arrive_current_floor");
            if (!robotInfo.isSpaceShip() || !robotInfo.isLiftModelInstalled()) {
                task.updateRobotSize(true);
            }
            checkCanNavigationCount = 0;
            canNavigationCount = 0;
            if (robotInfo.supportEnterElevatorPoint() && PointCacheInfo.INSTANCE.isEnterElevatorPointInitialized()) {
                navigationToEnterElevatorPoint();
            } else {
                enterElevator();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void onElevatorArrivedTargetFloor(String floor) {
        checkCanNavigationCount = 0;
        canNavigationCount = 0;
        try {
            playVoiceTip("voice_elevator_arrive_target_floor");
            if (robotInfo.getElevatorSetting().isDetectionSwitchOpen && robotInfo.supportEnterElevatorPoint() && PointCacheInfo.INSTANCE.isEnterElevatorPointInitialized()) {
                GenericPoint enterElevatorPoint;
                if (robotInfo.getElevatorSetting().isSingleDoor) {
                    enterElevatorPoint = PointCacheInfo.INSTANCE.getEnterElevatorPoint();
                } else {
                    enterElevatorPoint = PointCacheInfo.INSTANCE.getPointByAliasAndPointType(floor, GenericPoint.ENTER_ELEVATOR);
                }
                currentAction = new Pair<>(TaskAction.check_can_leave_elevator, new PointModel.Builder().position(enterElevatorPoint.getPosition()).build());
                Timber.w("出梯,检查能否到达进梯点 : %s", enterElevatorPoint);
                ros.getPlanPoint(enterElevatorPoint.getPosition());
            } else {
                navigationToLeaveElevatorPoint();
                isTimeout = false;
                mHandler.postDelayed(leaveElevatorTimeoutRunnable, 60_000);
            }
            view.updateTakeElevatorStep(Step.LEAVE_ELEVATOR_ACK, task.getNextPointWithElevator().getFirst());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private class ElevatorCallback implements ElevatorControlManager.CallBack {

        @Override
        public void onFinish() {
            try {
                if (currentAction != null && currentAction.getFirst().equals(TaskAction.check_leave_elevator_point_reachable)) {
                    PointModel pointModel = currentAction.getSecond();
                    ros.getPlanPoint(pointModel.getPosition());
                    return;
                }
                recallElevatorCount = 0;
                switchMap();
                if (elevatorControlManager != null) {
                    elevatorControlManager.release();
                }
                switchOutSideWifi(true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onCallElevatorSuccess() {
            view.updateTakeElevatorStep(Step.CALL_ELEVATOR, task.getNextPointWithElevator().getFirst());
        }

        @Override
        public void onCallElevatorInsideSuccess() {
            view.updateTakeElevatorStep(Step.CALL_ELEVATOR_INSIDE, task.getNextPointWithElevator().getFirst());
        }

        @Override
        public void onErrorAfterReceiveLeaveElevator(Exception exception) {
            Timber.w(exception, "出梯失败");
            if (exception instanceof CustomException) {
                if (((CustomException) exception).step == Step.LEAVE_ELEVATOR_ACK || ((CustomException) exception).step == Step.LEAVE_ELEVATOR_COMPLETE) {
                    onArriveTargetFloor(task.getNextPointWithElevator().getFirst());
                    if (elevatorControlManager != null) {
                        elevatorControlManager.release();
                    }
                    switchOutSideWifi(true);
                } else {
                    onFinish();
                }
            }
            view.showLeaveElevatorFailed(exception);
        }

        @Override
        public void onArriveCurrentFloor() {
            onElevatorArrivedCurrentFloor();
        }

        @Override
        public void onEnterElevatorComplete() {
            switchInsideWifi();
        }


        @Override
        public void onArriveTargetFloor(String floor) {
            onElevatorArrivedTargetFloor(floor);
        }

        @Override
        public void onError(Exception exception) {
            uploadTaskRecord(1);
            if (exception instanceof CustomException) {
                CustomException customException = (CustomException) exception;
                onTaskFinished(-2, customException.code, null);
            } else {
                Timber.w(exception, "未知异常乘梯失败");
                onTaskFinished(-2, context.getString(R.string.exception_unknown_exception), null);
            }
        }

        @Override
        public void onErrorTaskExist(String elevatorId, String thingId) {
            Notifier.notify(new Msg(NotifyConstant.SYSTEM_NOTIFY, "乘梯异常", "乘梯任务已存在,elevatorId : " + elevatorId + " , thingId : " + thingId, robotInfo.getROSHostname()));
        }

        @Override
        public void onQueue() {
            startRecallElevatorCountDownTimer(context.getString(R.string.text_check_other_robot_take_elevator), false);
        }

        @Override
        public void onElevatorFree() {
        }
    }

    /**
     * 判断急停开关是否按下
     *
     * @return
     */
    private boolean isEmergencyButtonDown() {
        if (robotInfo.isEmergencyButtonDown()) {
            showPauseViewWithTip(context.getString(R.string.text_turn_off_emergency_stop_to_continue_task), context.getString(R.string.text_emergency_stop_on));
            return true;
        }
        return false;
    }

    private boolean isSensorError() {
        String sensorErrorTip = Errors.INSTANCE.getSensorErrorTip(context, robotInfo.getLastSensorsData());
        if (TextUtils.isEmpty(sensorErrorTip)) {
            isCountdownCauseSensorError = false;
            shouldCancelNavigationWhenNavigationStart = false;
            return false;
        }
        if (isCountdownCauseSensorError) {
            ToastUtils.showShortToast(sensorErrorTip);
            return true;
        }
        if (System.currentTimeMillis() - robotInfo.getReloctionSuccessAfterTimeJumpTimestamp() < 30_000) {
            isCountdownCauseSensorError = true;
            pauseTask(30, sensorErrorTip);
            return true;
        }
        String hardwareError = Errors.INSTANCE.getSensorError(context, robotInfo.getLastSensorsData());
        cancelNavigation();
        uploadHardwareError(robotInfo.getLastSensorsData(), hardwareError);
        Notifier.notify(new Msg(NotifyConstant.HARDWARE_NOTIFY, "传感器状态", robotInfo.getLastSensorsData().getBaseData(), robotInfo.getROSHostname()));
        uploadTaskRecord(1);
        onTaskFinished(-3, hardwareError, "");
        return true;
    }

    private boolean isRouteModePointLiftActionComplete(boolean isArrived) {
        if (robotInfo.getMode() == TaskMode.MODE_ROUTE) {
            if (task.isOpenedManualLiftUpControl(isArrived) || task.isOpenedManualLiftDownControl(isArrived)) {
                if (robotInfo.isLifting()) {
                    view.showDialog(context.getString(R.string.text_check_lifting));
                    return true;
                }
            }
            if (task.isOpenedManualLiftUpControl(isArrived) && robotInfo.getLiftModelState() == 0) {
                view.showDialog(context.getString(R.string.text_check_open_manual_lift_down_but_lift_model_is_up));
                return true;
            }
            if (task.isOpenedManualLiftDownControl(isArrived) && robotInfo.getLiftModelState() == 1) {
                view.showDialog(context.getString(R.string.text_check_open_manual_lift_up_but_lift_model_is_down));
                return true;
            }
        }
        return false;
    }

    /**
     * 直接结束任务
     *
     * @param finishPrompt 结束任务提示
     * @return
     */
    private boolean isFinishDirectly(String finishPrompt) {
        if (robotInfo.isDoorControlMode()) {
            if (robotInfo.getDoorControlSetting().communicationMethod == 0) {
                if (DoorController.getInstance() != null) {
                    DoorController instance = DoorController.getInstance();
                    if (instance != null) {
                        if (instance.getCurrentState() != DoorController.State.CLOSED) {
                            Timber.w("穿过门时按下急停/触发防跌,任务强制结束");
                            uploadTaskRecord(1);
                            onTaskFinished(-1, finishPrompt, "");
                            return true;
                        }
                    }
                }
            } else {
                if (CallingHelper.INSTANCE.getDoorState() != CallingHelper.INIT && CallingHelper.INSTANCE.getDoorState() != CallingHelper.CLOSED) {
                    Timber.w("穿过门时按下急停/触发防跌,任务强制结束");
                    uploadTaskRecord(1);
                    onTaskFinished(-1, finishPrompt, "");
                    return true;
                }
            }
        }
        if (robotInfo.isLifting() || robotInfo.isQRCodeNavigating()) {
            Timber.w("顶升/二维码对接时按下急停/触发防跌,任务强制结束");
            uploadTaskRecord(1);
            onTaskFinished(-1, finishPrompt, "");
            return true;
        }
        if (robotInfo.isElevatorMode()) {
            if ((elevatorControlManager != null && elevatorControlManager.getCurrentStep() != Step.IDLE) ||
                    CallingHelper.INSTANCE.getTakeElevatorStep() != TakeElevatorStep.INIT
            ) {
                Timber.w("乘梯时按下急停/触发防跌,任务强制结束");
                uploadTaskRecord(1);
                onTaskFinished(-1, finishPrompt, "");
                return true;
            }

            if (recallElevatorCountDownTimer != null) {
                Timber.w("乘梯倒计时期间时按下急停/触发防跌,任务强制结束");
                cancelRecallElevatorCountDownTimer();
                uploadTaskRecord(1);
                onTaskFinished(-1, finishPrompt, "");
                return true;
            }
            if (robotInfo.isRepositioning() || robotInfo.isSwitchingMap()) {
                Timber.w("切换地图时按下急停/触发防跌,任务强制结束");
                uploadTaskRecord(1);
                onTaskFinished(-1, finishPrompt, "");
                return true;
            }
            if (currentAction != null) {
                String first = currentAction.getFirst();
                if (first.equals(TaskAction.check_leave_elevator_point_reachable) || first.equals(TaskAction.leave_elevator) || first.equals(TaskAction.leave_elevator_position)) {
                    Timber.w("前往出梯点时按下急停/触发防跌,任务强制结束");
                    uploadTaskRecord(1);
                    onTaskFinished(-1, finishPrompt, "");
                    return true;
                }
            }

        }
        return false;
    }

    private void showPauseViewWithTip(String pauseTip, String dialogContent) {
        if (robotInfo.getState() == State.PAUSE) {
            view.updatePauseTip(pauseTip, true, 0);
        } else {
            String mode = "";
            TaskMode taskMode = robotInfo.getMode();
            if (taskMode == TaskMode.MODE_NORMAL) {
                mode = context.getString(R.string.text_mode_normal);
            } else if (taskMode == TaskMode.MODE_ROUTE) {
                mode = context.getString(R.string.text_mode_route);
            } else if (taskMode == TaskMode.MODE_QRCODE) {
                mode = context.getString(R.string.text_mode_qrcode);
            } else if (taskMode == TaskMode.MODE_CALLING) {
                mode = context.getString(R.string.text_mode_calling);
            }
            robotInfo.setState(State.PAUSE);
            String currentFloor = robotInfo.isElevatorMode() ? robotInfo.getCurrentMapEvent().getAlias() : "";
            String targetFloor = "";
            String targetPoint;
            if (robotInfo.isElevatorMode()) {
                targetFloor = task.getNextPointWithElevator().getFirst();
                targetPoint = task.getNextPointWithElevator().getSecond();
            } else {
                if (currentOrder != null) {
                    targetPoint = currentOrder.getFinalTargetPoint();
                } else {
                    if (task.getAction().equals(TaskAction.charge_point)) {
                        targetPoint = context.getString(R.string.text_charge_point);
                    } else if (task.getAction().equals(TaskAction.production_point)) {
                        targetPoint = context.getString(R.string.text_product_point);
                    } else {
                        targetPoint = task.getNextPointWithoutElevator();
                    }
                }
            }
            TaskPauseInfoModel model = new TaskPauseInfoModel.Builder(mode)
                    .setRouteName(task.getRouteName())
                    .setTaskStartTime(TimeUtil.formatHourAndMinute(record.startTime))
                    .setTargetFloor(targetFloor)
                    .setTargetPoint(targetPoint)
                    .setCurrentFloor(currentFloor)
                    .setShowReturnButton(task.hasNext() && task.showReturnBtn())
                    .setShowCancelTaskButton(true)
                    .setShowContinueTaskButton(true)
                    .setShowLiftUpButton(false)
                    .setShowLiftDownButton(false)
                    .setCountDownTime(0)
                    .setShowRecallElevatorButton(false)
                    .setTaskPauseTip(pauseTip)
                    .build();
            view.showPauseTask(model, dialogContent);
        }
    }

    private void navigationByPositionList(List<String> pathPointList, boolean isFinalPath) {
        ros.setSpeed(task.getSpeed());
        ros.setTolerance(0);
        ros.maxPlanDist(0.5);
        List<double[]> positionListByPointName = PointCacheInfo.INSTANCE.getPositionListByPointName(pathPointList, robotInfo.getCurrentPosition(), isFinalPath);
        List<String> segmentationPositionList = PointUtils.INSTANCE.segmentationPositionList(positionListByPointName);
        ros.navigationListPoint(segmentationPositionList);
    }

    private void navigationTo(String point) {
        isArrivedDoorPoint = false;
        ros.setSpeed(task.getSpeed());
        TaskInfo currentTask = callingInfo.getHeartBeatInfo().getCurrentTask();
        if (currentTask != null) {
            currentTask.setTargetPoint(point);
        }
        if (robotInfo.isWithAntiCollisionStrip()) {
            ros.ioControl(0x01);
        }
        if (robotInfo.isDoorControlMode()) {
            ros.setGlobalCost(0);
        } else {
            ros.setGlobalCost(1);
        }
        if (robotInfo.getNavigationMode() == NavigationMode.autoPathMode) {
            ros.navigationByPoint(point);
        } else {
            ros.navigationByPathPoint(point);
        }
    }

    private void navigationToDoorPoint(String point) {
        isArrivedDoorPoint = false;
        ros.setGlobalCost(1);
        ros.setTolerance(0);
        if (robotInfo.isWithAntiCollisionStrip()) {
            ros.ioControl(0x01);
        }
        if (robotInfo.getNavigationMode() == NavigationMode.autoPathMode) {
            ros.resetLastSpeed();
            ros.navigationByPoint(point);
        } else {
            ros.setSpeed(task.getSpeed());
            ros.navigationByPathPoint(point);
        }
    }

    private void navigationToDoorPointCoordinate(double[] coordinate) {
        isArrivedDoorPoint = false;
        ros.setGlobalCost(1);
        ros.setTolerance(0);
        if (robotInfo.isWithAntiCollisionStrip()) {
            ros.ioControl(0x01);
        }
        if (robotInfo.getNavigationMode() == NavigationMode.autoPathMode) {
            Timber.w("门控导航 : %s", Arrays.toString(coordinate));
            ros.resetLastSpeed();
            ros.navigationByCoordinate(coordinate);
        } else {
            ros.setSpeed(task.getSpeed());
            double[] currentPosition = robotInfo.getCurrentPosition();
            assert currentPosition != null;
            double[] position = new double[6];
            System.arraycopy(currentPosition, 0, position, 0, currentPosition.length);
            System.arraycopy(coordinate, 0, position, currentPosition.length, coordinate.length);
            Timber.w("门控导航 : %s", Arrays.toString(position));
            ros.navigateListPoint(position);
        }
    }

    private void cancelNavigation() {
        currentAction = null;
        doorControlPositionList.clear();
        ros.stopMove();
        if (robotInfo.isNavigating()) ros.cancelNavigation();
        if (robotInfo.isQRCodeNavigating()) ros.agvStop();
        if (robotInfo.isChargeDocking()) ros.cancelCharge();
        if (robotInfo.isLifting()) {
            robotInfo.setLifting(false);
            callingInfo.setLifting(false);
        }
    }

    private void pauseBackgroundMusic() {
        MediaPlayerHelper.getInstance().pause();
    }

    private void startCountDownTimer(long seconds, boolean isPause) {
        if (isFinished) return;
        cancelCountdownTimer();
        pauseCountDownTimer = new PausableCountDownTimer(seconds * 1000L, 1000) {
            @Override
            public void onTick(long mills) {
                if (isFinished) {
                    cancelCountdownTimer();
                    return;
                }
                view.updateCountDownTimer(mills / 1000);
                if (isPause) return;
                record.getMealTime += 1;
                if (mills < 10000 || task.getPlayArrivedTipTime() == -1 || seconds - mills / 1000 > task.getPlayArrivedTipTime())
                    return;
                playArrivedNormalModePointTip();
            }

            @Override
            public void onFinish() {
                if (isFinished) return;
                isCountdownCauseSensorError = false;
                if (!task.isArrivedLastPointAndEndInPlace() || robotInfo.getMode() == TaskMode.MODE_ROUTE) {
                    navigationToPoint();
                } else {
                    uploadTaskRecord(0);
                    onTaskFinished(0, null, null);
                }
            }
        };
        pauseCountDownTimer.start();
    }

    private void cancelCountdownTimer() {
        if (pauseCountDownTimer != null) {
            pauseCountDownTimer.cancel();
            pauseCountDownTimer = null;
        }
    }

    /**
     * 播放普通模式下到达点位提示语
     */
    private void playArrivedNormalModePointTip() {
        if (VoiceHelper.isPlaying()
                || System.currentTimeMillis() - lastArrivedPlayTimeMills < 5000
                || isFinished
        ) return;
        lastArrivedPlayTimeMills = System.currentTimeMillis();
        playVoiceTip("voice_normal_task_arrive", () -> {
            lastArrivedPlayTimeMills = System.currentTimeMillis();
            MediaPlayerHelper.getInstance().updateVolume(true);
        });
    }

    private void startRecallElevatorCountDownTimer(String tip, boolean isTakeElevatorInside) {
        if (isFinished) return;
        if (recallElevatorCountDownTimer != null) {
            recallElevatorCountDownTimer.cancel();
        }
        isRecallElevatorInside = isTakeElevatorInside;
        Pair<String, String> nextPointWithElevator = task.getNextPointWithElevator();
        String mode = "";
        TaskMode taskMode = robotInfo.getMode();
        if (taskMode == TaskMode.MODE_NORMAL) {
            mode = context.getString(R.string.text_mode_normal);
        } else if (taskMode == TaskMode.MODE_ROUTE) {
            mode = context.getString(R.string.text_mode_route);
        } else if (taskMode == TaskMode.MODE_QRCODE) {
            mode = context.getString(R.string.text_mode_qrcode);
        } else if (taskMode == TaskMode.MODE_CALLING) {
            mode = context.getString(R.string.text_mode_calling);
        }
        TaskPauseInfoModel model = new TaskPauseInfoModel.Builder(mode)
                .setRouteName(task.getRouteName())
                .setTaskStartTime(TimeUtil.formatHourAndMinute(record.startTime))
                .setTargetFloor(nextPointWithElevator.getFirst())
                .setTargetPoint(nextPointWithElevator.getSecond())
                .setCurrentFloor(robotInfo.getCurrentMapEvent().getAlias())
                .setShowReturnButton(false)
                .setShowCancelTaskButton(true)
                .setShowContinueTaskButton(false)
                .setCountDownTime(robotInfo.getElevatorSetting().enterOrLeavePointUnreachableRetryTimeInterval * 60L)
                .setShowRecallElevatorButton(true)
                .setTaskPauseTip(tip)
                .build();
        view.showPauseTask(model, null);
        recallElevatorCountDownTimer = new PausableCountDownTimer(robotInfo.getElevatorSetting().enterOrLeavePointUnreachableRetryTimeInterval * 60 * 1000L, 1000) {
            @Override
            public void onTick(long mills) {
                view.updateCountDownTimer(mills / 1000);
            }

            @Override
            public void onFinish() {
                int communicationMethod = robotInfo.getElevatorSetting().communicationMethod;
                if (isTakeElevatorInside) {
                    if (communicationMethod == 0) {
                        elevatorControlManager.takeElevatorInside();
                    } else {
                        CallingHelper.INSTANCE.init(
                                robotInfo.getROSHostname(),
                                Integer.parseInt(robotInfo.getCurrentMapEvent().getAlias()),
                                Integer.parseInt(task.getNextPointWithElevator().getFirst()),
                                new ElevatorWithLoraCallback()
                        );
                        CallingHelper.INSTANCE.callElevatorInside();
                    }
                } else {
                    if (communicationMethod == 0) {
                        elevatorControlManager.takeElevator();
                    } else if (communicationMethod == 1) {
                        CallingHelper.INSTANCE.init(
                                robotInfo.getROSHostname(),
                                Integer.parseInt(robotInfo.getCurrentMapEvent().getAlias()),
                                Integer.parseInt(task.getNextPointWithElevator().getFirst()),
                                new ElevatorWithLoraCallback()
                        );
                        CallingHelper.INSTANCE.callElevatorByNetwork();
                    } else if (communicationMethod == 2) {
                        CallingHelper.INSTANCE.init(
                                robotInfo.getROSHostname(),
                                Integer.parseInt(robotInfo.getCurrentMapEvent().getAlias()),
                                Integer.parseInt(task.getNextPointWithElevator().getFirst()),
                                new ElevatorWithLoraCallback()
                        );
                        CallingHelper.INSTANCE.callElevatorByLoRa(robotInfo.getElevatorSetting().gatewayId);
                    }
                }
                view.updateRunningView(nextPointWithElevator.getFirst(), nextPointWithElevator.getSecond(), Step.REQUEST_ID);
            }
        };
        recallElevatorCountDownTimer.start();
    }

    private void cancelRecallElevatorCountDownTimer() {
        if (recallElevatorCountDownTimer != null) {
            recallElevatorCountDownTimer.cancel();
            recallElevatorCountDownTimer = null;
        }
    }

    /**
     * 结束任务
     *
     * @param result 0:正常;1:人工取消;-1:异常;-2:乘梯异常;-3:硬件(轮子/传感器等)异常;-4:时间戳跳变;
     * @param prompt 提示
     * @param voice  提示语
     */
    public void onTaskFinished(int result, String prompt, String voice) {
        if (isFinished) return;
        isFinished = true;
        boolean shouldRemoveFirstCallingTask = task.shouldRemoveFirstCallingTask();
        if (shouldRemoveFirstCallingTask) {
            callingInfo.removeFirstCallingDetails();
        }
        DispatchManager.INSTANCE.setTask(null);
        DispatchManager.INSTANCE.setCurrentPoint(null);
        CallingHelper.INSTANCE.cancelOpenDoorRetry();
        CallingHelper.INSTANCE.cancelCloseDoorRetry();
        CallingHelper.INSTANCE.setDoorState(CallingHelper.INIT);
        CallingStateManager.INSTANCE.setCanTakeTaskDuringTaskExecuting(false);
        if (result != 0) {
            robotInfo.setTaskAbnormalFinishPrompt(prompt);
        }
        cancelNavigation();
        robotInfo.setChargeFailedCount(0);
        MediaPlayerHelper.getInstance().release();
        if (EasyDialog.isShow() && EasyDialog.getInstance().isAutoDismissEnable())
            EasyDialog.getInstance().dismiss();
        mHandler.removeCallbacksAndMessages(null);
        task.resetAllParameter(robotInfo, callingInfo);
        if (DoorController.getInstance() != null) {
            DoorController.getInstance().unInit();
        }
        if (elevatorControlManager != null) {
            elevatorControlManager.release();
            elevatorControlManager = null;
        }
        if (CallingHelper.INSTANCE.getTakeElevatorStep() != TakeElevatorStep.INIT) {
            CallingHelper.INSTANCE.abortTask();
        }
        CallingHelper.INSTANCE.resetState();
        cancelRecallElevatorCountDownTimer();
        cancelCountdownTimer();
        view.showTaskFinishedView(result, prompt, voice, task.getRouteTask(), shouldRemoveFirstCallingTask ? TaskMode.MODE_CALLING : robotInfo.getMode());
        task = null;
    }

    /**
     * 上传硬件异常
     *
     * @param event
     * @param hardwareError
     */
    @SuppressLint("CheckResult")
    private void uploadHardwareError(SensorsEvent event, String hardwareError) {
        Timber.w("硬件异常 :%s", hardwareError);
        String hostname = robotInfo.getROSHostname();
        VersionInfoEvent versionEvent = robotInfo.getVersionEvent();
        if (TextUtils.isEmpty(hostname)) return;
        if (versionEvent == null) return;
        long mills = System.currentTimeMillis();
        FaultRecord faultRecord = new FaultRecord(Errors.INSTANCE.getFaultReason(event), hardwareError, BaseApplication.macAddress, "v1.1", BaseApplication.appVersion, versionEvent.getSoftVer(), mills, mills);
        String url = API.hardwareFaultAPI(hostname);
        ServiceFactory.getRobotService()
                .reportHardwareError(url, faultRecord)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Response>() {
                    @Override
                    public void accept(Response response) throws Throwable {
                        Timber.d("上传硬件异常成功:%s", response.toString());
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Throwable {
                        Timber.w(throwable, "上传硬件异常失败");
                    }
                });
    }


    /**
     * 上传任务结果
     */
    @SuppressLint("CheckResult")
    private void uploadTaskRecord(int result) {
        if (isFinished) return;
        String hostname = robotInfo.getROSHostname();
        if (TextUtils.isEmpty(hostname)) return;
        long uploadTime = System.currentTimeMillis();
        Set<String> set = new HashSet<>(failedPoint);
        record.deliveryLayers = task.getDeliveryPointSize();
        record.deliveryTables = task.getDeliveryPointSize();
        record.endTime = uploadTime;
        record.uploadTime = uploadTime;
        record.deliveryResult = result == 1 ? 1 : (set.isEmpty() ? 0 : 2);
        record.faultReason = set.toString();
        Timber.w("上传任务记录 %s", record);

        ServiceFactory.getRobotService()
                .reportTaskResult(API.taskRecordAPI(hostname), record)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe(new Consumer<Response>() {
                    @Override
                    public void accept(Response response) throws Throwable {
                        int code = response.code;
                        if (code == 0) {
                            Log.i("uploadTask", "上传任务记录成功");
                        } else if (code >= 400) {
                            Timber.w("上传任务记录失败 %s", code);
                            dbRepository.addDeliveryRecord(record);
                        }
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable e) throws Throwable {
                        dbRepository.addDeliveryRecord(record);
                        Timber.w(e, "上传任务记录失败");
                    }
                });
    }

    private final Runnable liftControlTimeOutRunnable = new Runnable() {
        @Override
        public void run() {
            view.dismissManualLiftControlTip(false);
        }
    };

    private void switchMap() {
        playVoiceTip("voice_leave_elevator");
        String mapAlias = task.getNextPointWithElevator().getFirst();
        String map = PointCacheInfo.INSTANCE.getMapByAlias(mapAlias);
        Timber.w("switchMap : %s", map);
        if (TextUtils.isEmpty(map)) {
            uploadTaskRecord(1);
            onTaskFinished(-1, context.getString(R.string.text_get_map_name_failed), "");
            return;
        }
        robotInfo.setSwitchingMap(true);
        ros.applyMap(map);
        view.updateTakeElevatorStep(Step.APPLY_MAP, task.getNextPointWithElevator().getFirst());

    }

    @Override
    public void onOpenDoorSuccess() {
        if (!isArrivedDoorPoint || doorControlPositionList.isEmpty() || DoorController.getInstance().getCurrentState() == DoorController.State.OPENED)
            return;
        isArrivedDoorPoint = false;
        DoorController.getInstance().setCurrentState(DoorController.State.OPENED);
        Timber.w("门控列表 : %s", doorControlPositionList);
        mHandler.post(() -> {
            doorControlPositionList.removeFirst();
            Pair<String, PointModel> doorBack = doorControlPositionList.getFirst();
            String doorNum = doorBack.getSecond().getRoom().replace("N-", "");
            view.updateRunningTip(context.getString(R.string.text_open_door_success, doorNum));
            double[] positionDoorBack = doorBack.getSecond().getPosition();
            currentAction = new Pair<>(TaskAction.door_back_position, new PointModel.Builder().room(doorBack.getSecond().getRoom()).position(positionDoorBack).build());
            navigationToDoorPointCoordinate(positionDoorBack);
        });
    }

    private void navigationToDoorFront() {
        Pair<String, PointModel> doorFront = doorControlPositionList.getFirst();
        if (doorFront.getFirst().equals(TaskAction.door_front_position)) {
            double[] positionDoorFront = doorFront.getSecond().getPosition();
            currentAction = new Pair<>(TaskAction.door_front_position, new PointModel.Builder().room(doorFront.getSecond().getRoom()).position(positionDoorFront).build());
            navigationToDoorPointCoordinate(positionDoorFront);
        } else {
            String nameDoorFront = doorFront.getSecond().getName();
            currentAction = new Pair<>(TaskAction.door_front_point, new PointModel.Builder().room(doorFront.getSecond().getRoom()).name(nameDoorFront).build());
            navigationToDoorPoint(nameDoorFront);
        }
    }

    @Override
    public void onCloseDoorSuccess(String currentClosingDoor) {
        if (!isArrivedDoorPoint || doorControlPositionList.isEmpty() || DoorController.getInstance().getCurrentState() == DoorController.State.CLOSED)
            return;
        isArrivedDoorPoint = false;
        DoorController.getInstance().setCurrentState(DoorController.State.CLOSED);
        Timber.tag("door control").w("关门成功");
        if (robotInfo.getDoorControlSetting().closeDoorAction == 0) {
            return;
        }
        mHandler.post(() -> {
            Timber.w("门控列表 : %s", doorControlPositionList);
            Pair<String, PointModel> first = doorControlPositionList.getFirst();
            doorControlPositionList.removeFirst();//移除门后点
            view.dismissRunningTip();
            if (first.getSecond().isAirShowerDoor() && !doorControlPositionList.isEmpty() && doorControlPositionList.getFirst().getSecond().isAirShowerDoor()) {
                startCloseDoorCountDownTimer(robotInfo.getDoorControlSetting().waitingTime, currentClosingDoor);
                return;
            }
            if (!doorControlPositionList.isEmpty()) {
                navigationToDoorFront();
                Timber.tag("door control").w("关门成功,导航去下一个门");
            } else {
                navigationToPoint();
                Timber.tag("door control").w("关门成功,导航去目标点");
            }
        });
    }

    @Override
    public void onThrowable(boolean isOpenDoor, Throwable throwable) {
        uploadTaskRecord(1);
        String content;
        if (throwable instanceof ReconnectUsbDeviceTimeoutException) {
            content = context.getString(R.string.exception_door_control_model_disconnected_task_finished);
        } else if (isOpenDoor) {
            content = context.getString(R.string.exception_open_door_failed_task_finished);
        } else {
            content = context.getString(R.string.exception_close_door_failed_task_finished);
        }
        onTaskFinished(-1, content, "");
    }

    @Override
    public void onOpenDoorSuccess(DoorOpenedEvent event) {
        if (!isArrivedDoorPoint || doorControlPositionList.isEmpty() || CallingHelper.INSTANCE.getDoorState() == CallingHelper.OPENED)
            return;
        CallingHelper.INSTANCE.setDoorState(CallingHelper.OPENED);
        isArrivedDoorPoint = false;
        Timber.w("门控列表 : %s", doorControlPositionList);
        doorControlPositionList.removeFirst();
        Pair<String, PointModel> doorBack = doorControlPositionList.getFirst();
        String doorNum = doorBack.getSecond().getRoom().replace("N-", "");
        view.updateRunningTip(context.getString(R.string.text_open_door_success, doorNum));
        double[] positionDoorBack = doorBack.getSecond().getPosition();
        currentAction = new Pair<>(TaskAction.door_back_position, new PointModel.Builder().room(doorBack.getSecond().getRoom()).position(positionDoorBack).build());
        navigationToDoorPointCoordinate(positionDoorBack);
    }

    @Override
    public void onCloseDoorSuccess(DoorClosedEvent event) {
        if (!isArrivedDoorPoint || doorControlPositionList.isEmpty() || CallingHelper.INSTANCE.getDoorState() == CallingHelper.CLOSED)
            return;
        CallingHelper.INSTANCE.setDoorState(CallingHelper.CLOSED);
        isArrivedDoorPoint = false;
        Timber.tag("door control").w("关门成功");
        if (robotInfo.getDoorControlSetting().closeDoorAction == 0) {
            return;
        }
        Timber.w("门控列表 : %s", doorControlPositionList);
        Pair<String, PointModel> first = doorControlPositionList.getFirst();
        doorControlPositionList.removeFirst();//移除门后点
        view.dismissRunningTip();
        if (first.getSecond().isAirShowerDoor() && !doorControlPositionList.isEmpty() && doorControlPositionList.getFirst().getSecond().isAirShowerDoor()) {
            startCloseDoorCountDownTimer(robotInfo.getDoorControlSetting().waitingTime, String.valueOf(event.getNumber()));
            return;
        }
        if (!doorControlPositionList.isEmpty()) {
            navigationToDoorFront();
            Timber.tag("door control").w("关门成功,导航去下一个门");
        } else {
            navigationToPoint();
            Timber.tag("door control").w("关门成功,导航去目标点");
        }
    }

    @Override
    public void onOpenDoorFailed(OpenDoorFailedEvent event) {
        uploadTaskRecord(1);
        onTaskFinished(-1, context.getString(R.string.exception_open_door_failed_task_finished), "");
    }

    @Override
    public void onCloseDoorFailed(CloseDoorFailedEvent event) {
        uploadTaskRecord(1);
        onTaskFinished(-1, context.getString(R.string.exception_close_door_failed_task_finished), "");
    }

    @Override
    public void onCallingModelDisconnected(int event) {
        int doorState = CallingHelper.INSTANCE.getDoorState();
        if (doorState == CallingHelper.WAITING_CLOSE_RESULT || doorState == CallingHelper.WAITING_OPEN_RESULT) {
            if (event == 0) {
                ToastUtils.showShortToast(context.getString(R.string.text_door_control_model_reconnecting));
            } else {
                uploadTaskRecord(1);
                onTaskFinished(-1, context.getString(doorState == CallingHelper.WAITING_OPEN_RESULT ? R.string.exception_open_door_failed_task_finished : R.string.exception_close_door_failed_task_finished), "");
            }
        } else {
            String content;
            if (event == 0) {
                content = context.getString(R.string.text_calling_model_reconnecting);
            } else {
                if (robotInfo.getDoorControlSetting().open && robotInfo.getDoorControlSetting().communicationMethod == 1) {
                    uploadTaskRecord(1);
                    onTaskFinished(-1, context.getString(R.string.exception_door_control_model_disconnected_task_finished), "");
                    return;
                }
                content = context.getString(R.string.text_calling_model_reconnect_failed);
            }
            ToastUtils.showShortToast(content);
        }
    }

    @Override
    public void onCallingModelReconnected() {
        int doorState = CallingHelper.INSTANCE.getDoorState();
        String content;
        if (doorState == CallingHelper.WAITING_CLOSE_RESULT || doorState == CallingHelper.WAITING_OPEN_RESULT) {
            content = context.getString(R.string.text_door_control_model_reconnect_success);
        } else {
            content = context.getString(R.string.text_calling_model_reconnect_success);
        }
        ToastUtils.showShortToast(content);
    }

    private void startCloseDoorCountDownTimer(int seconds, String currentDoor) {
        doorControlPositionList.clear();
        startCountDownTimer(seconds, true);
        robotInfo.setState(State.PAUSE);
        cancelNavigation();
        pauseBackgroundMusic();
        String mode = "";
        TaskMode taskMode = robotInfo.getMode();
        if (taskMode == TaskMode.MODE_NORMAL) {
            mode = context.getString(R.string.text_mode_normal);
        } else if (taskMode == TaskMode.MODE_ROUTE) {
            mode = context.getString(R.string.text_mode_route);
        } else if (taskMode == TaskMode.MODE_QRCODE) {
            mode = context.getString(R.string.text_mode_qrcode);
        } else if (taskMode == TaskMode.MODE_CALLING) {
            mode = context.getString(R.string.text_mode_calling);
        }
        String currentFloor = robotInfo.isElevatorMode() ? robotInfo.getCurrentMapEvent().getAlias() : "";
        String targetFloor = "";
        String targetPoint;
        if (robotInfo.isElevatorMode()) {
            targetFloor = task.getNextPointWithElevator().getFirst();
            targetPoint = task.getNextPointWithElevator().getSecond();
        } else {
            if (robotInfo.isDispatchModeOpened()) {
                targetPoint = currentOrder.getFinalTargetPoint();
            } else {
                targetPoint = task.getNextPointWithoutElevator();
            }
        }
        TaskPauseInfoModel model = new TaskPauseInfoModel.Builder(mode)
                .setRouteName(task.getRouteName())
                .setTaskStartTime(TimeUtil.formatHourAndMinute(record.startTime))
                .setTargetFloor(targetFloor)
                .setTargetPoint(targetPoint)
                .setCurrentFloor(currentFloor)
                .setShowReturnButton(false)
                .setShowCancelTaskButton(true)
                .setShowContinueTaskButton(true)
                .setShowSkipCurrentTargetButton(false)
                .setCountDownTime(seconds)
                .setShowRecallElevatorButton(false)
                .setTaskPauseTip(context.getString(R.string.text_already_through_door_and_count_down, currentDoor))
                .build();
        view.showPauseTask(model, null);
    }

    //播放音乐 音乐播放
    public void playBackgroundMusic() {
        if (!backgroundMusicSetting.enableBackgroundMusic) return;
        if (backgroundMusicSetting.backgroundMusicFileNames != null && backgroundMusicSetting.backgroundMusicPaths != null) {
            MediaPlayerHelper mediaPlayerHelper = MediaPlayerHelper.getInstance();
            mediaPlayerHelper.updateVolume(!VoiceHelper.isPlaying());
            if (mediaPlayerHelper.isPaused()) {
                mediaPlayerHelper.resume();
            } else {
                mediaPlayerHelper.playFileList(backgroundMusicSetting.backgroundMusicPaths, true, this);
            }
        }

    }

    private void playVoiceTip(String tip, VoiceHelper.OnCompleteListener onCompleteListener) {
        MediaPlayerHelper.getInstance().updateVolume(false);
        Observable.timer(100, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(aLong -> VoiceHelper.play(tip, onCompleteListener), throwable -> Timber.w(throwable, "无法播放音频: %s", tip));
    }

    private void playVoiceTip(String tip) {
        playVoiceTip(tip, this);
    }

    private void openDoor(String doorNum, boolean opposite) {
        if (robotInfo.getDoorControlSetting().communicationMethod == 0) {
            DoorController.getInstance().openDoor(doorNum, opposite);
        } else {
            CallingHelper.INSTANCE.openDoor(doorNum, opposite);
        }
    }

    private void closeDoor(String doorNum, boolean opposite, boolean waitingCloseResp) {
        if (robotInfo.getDoorControlSetting().communicationMethod == 0) {
            DoorController.getInstance().closeDoor(doorNum, opposite);
            if (!waitingCloseResp) {
                DoorController.getInstance().setCurrentState(DoorController.State.CLOSED);
            }
        } else {
            CallingHelper.INSTANCE.closeDoor(doorNum, opposite);
            if (!waitingCloseResp) {
                CallingHelper.INSTANCE.setDoorState(CallingHelper.CLOSED);
            }
        }
    }

}
