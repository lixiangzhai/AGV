package com.reeman.agv.activities;


import static com.reeman.agv.base.BaseApplication.isFirstEnter;
import static com.reeman.agv.base.BaseApplication.mApp;
import static com.reeman.agv.base.BaseApplication.ros;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableOnSubscribe;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import kotlin.Pair;

import com.google.gson.Gson;
import com.reeman.agv.BuildConfig;
import com.reeman.agv.R;
import com.reeman.agv.base.BaseActivity;
import com.reeman.agv.calling.CallingInfo;
import com.reeman.agv.utils.TimeSettingUtils;
import com.reeman.commons.constants.Constants;
import com.reeman.commons.event.CurrentMapEvent;
import com.reeman.commons.event.HostnameEvent;
import com.reeman.commons.event.RobotTypeEvent;
import com.reeman.commons.settings.CommutingTimeSetting;
import com.reeman.commons.settings.DispatchSetting;
import com.reeman.commons.settings.DoorControlSetting;
import com.reeman.agv.calling.setting.ModeCallingSetting;
import com.reeman.commons.settings.ModeNormalSetting;
import com.reeman.commons.settings.ModeQRCodeSetting;
import com.reeman.commons.settings.ModeRouteSetting;
import com.reeman.commons.settings.ReturningSetting;
import com.reeman.commons.state.NavigationMode;
import com.reeman.commons.utils.AESUtil;
import com.reeman.commons.utils.AndroidInfoUtil;
import com.reeman.agv.contract.SplashContract;
import com.reeman.dispatch.DispatchManager;
import com.reeman.ros.ROSController;
import com.reeman.agv.presenter.impl.SplashPresenter;
import com.reeman.commons.settings.ElevatorSetting;
import com.reeman.commons.utils.MMKVManager;
import com.reeman.ros.filter.ROSFilter;
import com.reeman.agv.utils.PackageUtils;
import com.reeman.agv.utils.ScreenUtils;
import com.reeman.commons.utils.SpManager;
import com.reeman.agv.widgets.EasyDialog;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import timber.log.Timber;

public class SplashActivity extends BaseActivity implements SplashContract.View {

    private SplashPresenter presenter;
    private static final int REQUEST_CODE = 1001;
    private static final int STEP_WAITING_POSITION = 0;
    private static final int STEP_WAITING_HOSTNAME = 1;
    private static final int STEP_WAITING_CURRENT_MAP = 2;
    private static final int STEP_WAITING_ROBOT_TYPE = 3;
    private static final int STEP_WAITING_INIT_CACHE = 4;
    private int initStep = STEP_WAITING_POSITION;


    @Override
    protected int getLayoutRes() {
        return R.layout.activity_splash;
    }

    @Override
    protected void initCustomView() {
    }

    @Override
    protected void initData() {
        presenter = new SplashPresenter(this);
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (BuildConfig.IS3128) {
            TimeSettingUtils.INSTANCE.setRemoteAdb();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                ScreenUtils.setImmersive(this);
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                return;
            }
            if (!Settings.System.canWrite(this)) {
                ScreenUtils.setImmersive(this);
                Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return;
            }
        }
        ScreenUtils.hideBottomUIMenu(this);
        checkPermission();
    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            checkROS();
            return;
        }
        PackageInfo packageInfo;
        try {
            packageInfo = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalStateException();
        }
        String[] permissions = packageInfo.requestedPermissions;
        List<String> unGrantedPerms = new ArrayList<>();
        for (String perm : permissions) {
            if ("android.permission.SYSTEM_OVERLAY_WINDOW".equals(perm) ||
                    "android.permission.WRITE_SETTINGS".equals(perm) ||
                    "android.permission.REQUEST_INSTALL_PACKAGES".equals(perm)) continue;
            if (checkSelfPermission(perm) == PackageManager.PERMISSION_DENIED) {
                unGrantedPerms.add(perm);
            }
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            unGrantedPerms.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (unGrantedPerms.isEmpty()) {
            checkROS();
            return;
        }
        String[] unGrantedPermsArr = new String[unGrantedPerms.size()];
        for (int i = 0; i < unGrantedPerms.size(); i++) {
            unGrantedPermsArr[i] = unGrantedPerms.get(i);
        }
        requestPermissions(unGrantedPermsArr, REQUEST_CODE);
    }

    private void checkROS() {
        if (isFirstEnter) {
            isFirstEnter = false;
            ros = ROSController.INSTANCE;
            try {
                ros.init(BuildConfig.IS3128, BuildConfig.APP_LOG_DIR, BuildConfig.CRASH_LOG_DIR, com.reeman.agv.elevator.BuildConfig.ELEVATOR_DIR, com.reeman.serialport.BuildConfig.LOG_POWER_BOARD, com.reeman.dispatch.BuildConfig.DISPATCH_DIR, com.reeman.agv.calling.BuildConfig.ELEVATOR_DIR);
                ros.registerListener(this);
                ros.closeLaserReport();
                mCommunicationRunnable = () -> EasyDialog.getInstance(SplashActivity.this).warn(getString(R.string.exception_communicate_failed_with_ros_not_receive_position), (dialog, id) -> {
                    dialog.dismiss();
                    mApp.exit();
                });
                mHandler.postDelayed(mCommunicationRunnable, 5000);
                mApp.startCallingService();
            } catch (Exception e) {
                e.printStackTrace();
                Timber.e(e, "串口初始化失败");
                EasyDialog.getInstance(SplashActivity.this).warn(getString(R.string.text_communicate_failed_with_ros), (dialog, id) -> {
                    dialog.dismiss();
                    mApp.exit();
                });
            }
        } else {
            robotInfo.setCurrentPosition(null);
            robotInfo.setROSHostname("");
            robotInfo.setRobotType(-1);
            robotInfo.setCurrentMapEvent(new CurrentMapEvent("",""));
            ros.positionAutoUploadControl(true);
            Timber.w("current step: %s",initStep);
            mCommunicationRunnable = () -> EasyDialog.getInstance(SplashActivity.this).warn(getString(R.string.exception_communicate_failed_with_ros_not_receive_position), (dialog, id) -> {
                dialog.dismiss();
                mApp.exit();
            });
            mHandler.postDelayed(mCommunicationRunnable, 5000);
            mApp.startCallingService();
        }
    }

    private Runnable mCommunicationRunnable;

    private void showInsufficientPermissionDialog() {
        EasyDialog.getInstance(this).confirm(getString(R.string.text_insufficient_permission_detail), (dialog, id) -> {
            dialog.dismiss();
            if (id == R.id.btn_confirm) {
                navigateToGrantPermission();
            } else {
                mApp.exit();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @androidx.annotation.NonNull String[] permissions, @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Timber.w("onRequestPermissionsResult ,requestCode: %s ,permissions :%s ,grantResults :%s", requestCode, Arrays.toString(permissions), Arrays.toString(grantResults));
        if (requestCode != REQUEST_CODE) return;
        for (int grantResult : grantResults) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) continue;
            showInsufficientPermissionDialog();
            return;
        }
        checkROS();
    }

    /**
     * 跳转应用详情界面授权
     */
    private void navigateToGrantPermission() {
        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    @Override
    protected void onCustomPosition(double[] position) {
        super.onCustomPosition(position);
        if (initStep != STEP_WAITING_POSITION)return;
        Timber.d("init position complete,get hostname");
        mHandler.removeCallbacks(mCommunicationRunnable);
        mCommunicationRunnable = () -> EasyDialog.getInstance(SplashActivity.this).warn(getString(R.string.exception_communicate_failed_with_get_hostname_timeout), (dialog, id) -> {
            dialog.dismiss();
            mApp.exit();
        });
        mHandler.postDelayed(mCommunicationRunnable, 5000);
        initStep = STEP_WAITING_HOSTNAME;
        ros.getHostname();
    }

    @Override
    public void onHostNameEvent(@NonNull HostnameEvent event) {
        if (initStep != STEP_WAITING_HOSTNAME)return;
        Timber.d("get hostname complete,get current map");
        mHandler.removeCallbacks(mCommunicationRunnable);
        mCommunicationRunnable = () -> EasyDialog.getInstance(SplashActivity.this).warn(getString(R.string.exception_communicate_failed_with_get_map_info_timeout), (dialog, id) -> {
            dialog.dismiss();
            mApp.exit();
        });
        mHandler.postDelayed(mCommunicationRunnable, 5000);
        initStep = STEP_WAITING_CURRENT_MAP;
        robotInfo.setROSHostname(event.getHostname());
        callingInfo.getHeartBeatInfo().setHostname(event.getHostname());
        String alias = SpManager.getInstance().getString(Constants.KEY_ROBOT_ALIAS, event.getHostname());
        callingInfo.getHeartBeatInfo().setAlias(alias);
        robotInfo.setRobotAlias(alias);
        DispatchManager.INSTANCE.setHostname(event.getHostname());
        ros.getCurrentMap();
    }

    @Override
    public void onCurrentMapEvent(CurrentMapEvent event) {
        super.onCurrentMapEvent(event);
        if (initStep != STEP_WAITING_CURRENT_MAP)return;
        Timber.d("get current map complete, get robot type");
        mHandler.removeCallbacks(mCommunicationRunnable);
        mCommunicationRunnable = () -> EasyDialog.getInstance(SplashActivity.this).warn(getString(R.string.exception_communicate_failed_with_get_robot_type_timeout), (dialog, id) -> {
            dialog.dismiss();
            mApp.exit();
        });
        initStep = STEP_WAITING_ROBOT_TYPE;
        mHandler.postDelayed(mCommunicationRunnable, 5000);
        ros.getRobotType();
    }

    @Override
    public void onRobotTypeEvent(RobotTypeEvent event) {
        super.onRobotTypeEvent(event);
        if (initStep != STEP_WAITING_ROBOT_TYPE)return;
        robotInfo.setRobotType(event.getRobotType());
        DispatchManager.INSTANCE.setRobotType(event.getRobotType());
        Timber.d("get robot type complete, get version info");
        mHandler.removeCallbacks(mCommunicationRunnable);
        initStep = STEP_WAITING_INIT_CACHE;
        initCacheInfo();
    }

    private void initCacheInfo() {
        Observable.create((ObservableOnSubscribe<Void>) emitter -> {
                    ros.cpuPerformance();
                    Gson gson = new Gson();
                    SharedPreferences sharedPreferences = SpManager.getInstance();
                    int currentNavigationModel = sharedPreferences.getInt(Constants.KEY_NAVIGATION_MODEL, NavigationMode.autoPathMode);
                    String normalModeConfigStr = sharedPreferences.getString(Constants.KEY_NORMAL_MODE_CONFIG, null);
                    String routeModeConfigStr = sharedPreferences.getString(Constants.KEY_ROUTE_MODE_CONFIG, null);
                    String qrCodeModeConfigStr = sharedPreferences.getString(Constants.KEY_QRCODE_MODE_CONFIG, null);
                    String returningConfigStr = sharedPreferences.getString(Constants.KEY_RETURNING_CONFIG, null);
                    String elevatorSettingJsonStr = sharedPreferences.getString(Constants.KEY_ELEVATOR_SETTING, null);
                    String doorControlSettingJsonStr = sharedPreferences.getString(Constants.KEY_DOOR_CONTROL, null);
                    String commutingTimeSettingStr = sharedPreferences.getString(Constants.KEY_COMMUTING_TIME_SETTING, null);
                    String callingSettingConfigStr = SpManager.getInstance().getString(Constants.KEY_CALLING_MODE_CONFIG, null);
                    String dispatchSettingStr = sharedPreferences.getString(Constants.KEY_DISPATCH_SETTING, "");
                    robotInfo.setAutoChargePowerLevel(sharedPreferences.getInt(Constants.KEY_LOW_POWER, Constants.DEFAULT_LOW_POWER));
                    robotInfo.setWithAntiCollisionStrip(sharedPreferences.getBoolean(Constants.KEY_ANTI_COLLISION_STRIP_SWITCH, false));
                    robotInfo.setPointScrollShow(sharedPreferences.getBoolean(Constants.KEY_POINT_SHOW_MODE, true));
                    if (!TextUtils.isEmpty(dispatchSettingStr)) {
                        DispatchSetting dispatchSetting = gson.fromJson(dispatchSettingStr, DispatchSetting.class);
                        robotInfo.setDispatchSetting(dispatchSetting);
                        DispatchManager.INSTANCE.setRoomName(dispatchSetting.getRoomName());
                        DispatchManager.INSTANCE.setRoomPwd(dispatchSetting.getRoomPwd());
                    }
                    if (!TextUtils.isEmpty(normalModeConfigStr)) {
                        robotInfo.setModeNormalSetting(gson.fromJson(normalModeConfigStr, ModeNormalSetting.class));
                    }
                    if (!TextUtils.isEmpty(routeModeConfigStr)) {
                        robotInfo.setModeRouteSetting(gson.fromJson(routeModeConfigStr, ModeRouteSetting.class));
                    }
                    if (!TextUtils.isEmpty(qrCodeModeConfigStr)) {
                        ModeQRCodeSetting modeQRCodeSetting = gson.fromJson(qrCodeModeConfigStr, ModeQRCodeSetting.class);
                        robotInfo.setModeQRCodeSetting(modeQRCodeSetting);
                        CallingInfo.INSTANCE.setQRCodeTaskUseCallingButton(modeQRCodeSetting.callingBind);
                    }
                    if (!TextUtils.isEmpty(returningConfigStr)) {
                        robotInfo.setReturningSetting(gson.fromJson(returningConfigStr, ReturningSetting.class));
                    }
                    if (!TextUtils.isEmpty(elevatorSettingJsonStr)) {
                        robotInfo.setElevatorSetting(gson.fromJson(elevatorSettingJsonStr, ElevatorSetting.class));
                        callingInfo.getHeartBeatInfo().setElevatorMode(robotInfo.getElevatorSetting().open);
                    }
                    if (!TextUtils.isEmpty(doorControlSettingJsonStr)) {
                        robotInfo.setDoorControlSetting(gson.fromJson(doorControlSettingJsonStr, DoorControlSetting.class));
                    }
                    if (!TextUtils.isEmpty(commutingTimeSettingStr)) {
                        robotInfo.setCommutingTimeSetting(gson.fromJson(commutingTimeSettingStr, CommutingTimeSetting.class));
                    }
                    if (TextUtils.isEmpty(callingSettingConfigStr)) {
                        ModeCallingSetting modeCallingSetting = ModeCallingSetting.getDefault();
                        try {
                            String encrypt = AESUtil.encrypt("a123456", PackageUtils.getVersion(SplashActivity.this) + AndroidInfoUtil.getSerialNumber() + System.currentTimeMillis());
                            String mKey = encrypt.replaceAll("[^A-Za-z0-9]", "").substring(0, 8);
                            String token = encrypt.substring(8);
                            List<String> tokens = new ArrayList<>();
                            tokens.add(token);
                            modeCallingSetting.key = new Pair<>(mKey, tokens);
                            sharedPreferences.edit().putString(Constants.KEY_CALLING_MODE_CONFIG, gson.toJson(modeCallingSetting)).apply();
                        } catch (GeneralSecurityException e) {
                            Timber.w(e, "生成key失败");
                        }
                        callingInfo.setCallingModeSetting(modeCallingSetting);
                    } else {
                        ModeCallingSetting modeCallingSetting = gson.fromJson(callingSettingConfigStr, ModeCallingSetting.class);
                        Timber.w("key : %s ", modeCallingSetting.key);
                        callingInfo.setCallingModeSetting(modeCallingSetting);
                    }
                    robotInfo.setNavigationMode(currentNavigationModel);
                    emitter.onComplete();
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Void>() {
                    @Override
                    public void onSubscribe(@io.reactivex.rxjava3.annotations.NonNull Disposable d) {

                    }

                    @Override
                    public void onNext(@io.reactivex.rxjava3.annotations.NonNull Void unused) {

                    }

                    @Override
                    public void onError(@io.reactivex.rxjava3.annotations.NonNull Throwable e) {

                    }

                    @Override
                    public void onComplete() {
                        startUp();
                    }
                });
    }

    private void startUp() {
        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss();
        mHandler.removeCallbacks(mCommunicationRunnable);
        presenter.startup(SplashActivity.this);
    }
}