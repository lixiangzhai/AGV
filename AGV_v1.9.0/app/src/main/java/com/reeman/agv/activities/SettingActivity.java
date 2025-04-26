package com.reeman.agv.activities;


import static com.reeman.agv.base.BaseApplication.ros;

import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.reeman.agv.R;
import com.reeman.agv.base.BaseActivity;
import com.reeman.agv.constants.SettingPage;
import com.reeman.agv.fragments.setting.DispatchSettingFragment;
import com.reeman.commons.constants.Constants;
import com.reeman.commons.event.TimeStampEvent;
import com.reeman.commons.utils.PointUtils;
import com.reeman.agv.fragments.setting.ElevatorSettingFragment;
import com.reeman.agv.fragments.setting.BasicSettingFragment;
import com.reeman.agv.fragments.setting.DoorControlFragment;
import com.reeman.agv.fragments.setting.LanguageSettingFragment;
import com.reeman.agv.fragments.setting.ModeSettingFragment;
import com.reeman.agv.fragments.setting.NetSettingFragment;
import com.reeman.agv.fragments.setting.ReturningSettingFragment;
import com.reeman.agv.fragments.setting.VersionSettingFragment;
import com.reeman.agv.utils.ScreenUtils;
import com.reeman.agv.utils.ToastUtils;
import com.reeman.agv.widgets.EasyDialog;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

public class SettingActivity extends BaseActivity {

    private final Map<SettingPage, Fragment> fragmentMap = new HashMap<>();
    private SettingPage currentPage;
    private List<TextView> list;
    private Fragment fragment;

    @Override
    protected int getLayoutRes() {
        return R.layout.activity_setting;
    }


    @Override
    protected boolean shouldResponse2TimeEvent() {
        return true;
    }

    @Override
    protected boolean shouldResponseCallingEvent() {
        return true;
    }

    @Override
    protected boolean shouldRegisterDispatchCallback() {
        return true;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            ScreenUtils.hideBottomUIMenu(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ros.getCurrentMap();
    }

    @Override
    protected void initCustomView() {
        setOnClickListeners(
                R.id.tv_back,
                R.id.tv_basic_setting,
                R.id.tv_language_setting,
                R.id.tv_wifi_setting,
                R.id.tv_mode_setting,
                R.id.tv_returning_setting,
                R.id.tv_elevator_setting,
                R.id.tv_door_control_setting,
                R.id.tv_dispatch_setting,
                R.id.tv_version_setting);

//        if (BuildConfig.APP_FORCE_USE_ZH) {
//            $(R.id.tv_language_setting).setVisibility(View.GONE);
//        }
        list = Arrays.asList($(R.id.tv_basic_setting), $(R.id.tv_language_setting), $(R.id.tv_wifi_setting), $(R.id.tv_mode_setting), $(R.id.tv_returning_setting),$(R.id.tv_elevator_setting), $(R.id.tv_door_control_setting),$(R.id.tv_dispatch_setting), $(R.id.tv_version_setting));
        Intent intent = getIntent();
        if (intent.hasExtra(Constants.SETTING_PAGE)) {
            SettingPage settingPage =(SettingPage) getIntent().getSerializableExtra(Constants.SETTING_PAGE);
            switchPage(settingPage);
        }else {
            switchPage(SettingPage.PAGE_BASIC_SETTING);
        }
    }

    public void switchPage(SettingPage page) {
        if (this.currentPage == page) return;
        this.currentPage = page;
        for (int i = 0; i < list.size(); i++) {
            TextView textView = list.get(i);
            if (i == page.ordinal()) {
                textView.setTextColor(Color.WHITE);
                textView.setBackgroundResource(R.drawable.bg_setting_banner_active);
            } else {
                textView.setTextColor(Color.parseColor("#777777"));
                textView.setBackgroundResource(R.drawable.bg_setting_banner);
            }
        }

        fragment = fragmentMap.get(page);
        if (fragment == null) {
            switch (page) {
                case PAGE_LANGUAGE_SETTING:
                    fragment = new LanguageSettingFragment();
                    break;
                case PAGE_NETWORK_SETTING:
                    fragment = new NetSettingFragment();
                    break;
                case PAGE_MODE_SETTING:
                    fragment = new ModeSettingFragment();
                    break;
                case PAGE_RETURNING_SETTING:
                    fragment = new ReturningSettingFragment();
                    break;
                case PAGE_ELEVATOR_SETTING:
                    fragment = new ElevatorSettingFragment();
                    break;
                case PAGE_DOOR_SETTING:
                    fragment = new DoorControlFragment();
                    break;
                case PAGE_DISPATCH_SETTING:
                    fragment = new DispatchSettingFragment();
                    break;
                case PAGE_VERSION_SETTING:
                    fragment = new VersionSettingFragment();
                    break;
                default:
                    fragment = new BasicSettingFragment();
            }
            fragmentMap.put(page, fragment);
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fl_setting_container, fragment)
                .commit();
    }

    @Override
    public void onClick(View v) {
        super.onClick(v);
    }

    @Override
    protected void onCustomClickResult(int id) {
        super.onCustomClickResult(id);
        switch (id) {
            case R.id.tv_basic_setting:
                switchPage(SettingPage.PAGE_BASIC_SETTING);
                break;
            case R.id.tv_language_setting:
                switchPage(SettingPage.PAGE_LANGUAGE_SETTING);
                break;
            case R.id.tv_wifi_setting:
                switchPage(SettingPage.PAGE_NETWORK_SETTING);
                break;
            case R.id.tv_mode_setting:
                switchPage(SettingPage.PAGE_MODE_SETTING);
                break;
            case R.id.tv_returning_setting:
                switchPage(SettingPage.PAGE_RETURNING_SETTING);
                return;
            case R.id.tv_version_setting:
                switchPage(SettingPage.PAGE_VERSION_SETTING);
                break;
            case R.id.tv_elevator_setting:
                switchPage(SettingPage.PAGE_ELEVATOR_SETTING);
                break;
            case R.id.tv_door_control_setting:
                switchPage(SettingPage.PAGE_DOOR_SETTING);
                break;
            case R.id.tv_dispatch_setting:
                switchPage(SettingPage.PAGE_DISPATCH_SETTING);
                break;
            case R.id.tv_back:
                finish();
                break;
        }
    }

    @Override
    protected void onCustomTimeStamp(TimeStampEvent event) {
        if (EasyDialog.isShow() ) return;
        super.onCustomTimeStamp(event);
    }
    @Override
    protected void onCustomInitPose(double[] currentPosition) {
        super.onCustomInitPose(currentPosition);
        if (currentPage == SettingPage.PAGE_BASIC_SETTING && this.fragment != null) {
            BasicSettingFragment fragment = (BasicSettingFragment) this.fragment;
            double[] lastRelocateCoordinate = fragment.getLastRelocateCoordinate();

            if (lastRelocateCoordinate == null) {
                return;
            }

            if (EasyDialog.isShow())
                EasyDialog.getInstance().dismiss();

            double radius = Math.abs(lastRelocateCoordinate[2] - currentPosition[2]);
            double distance = PointUtils.calculateDistance(lastRelocateCoordinate[0], lastRelocateCoordinate[1], currentPosition[0], currentPosition[1]);
            Timber.w("重定位结果: radius : %s , distance : %s", radius, distance);
            if (radius < 1.04f
                    && distance < 0.7) {
                ToastUtils.showLongToast(getString(R.string.text_locate_finish));
            } else {
                ToastUtils.showLongToast(getString(R.string.text_relocate_failed));
            }
            fragment.setLastRelocateCoordinate(null);
        } else if (currentPage == SettingPage.PAGE_DISPATCH_SETTING && this.fragment != null) {
            ((DispatchSettingFragment)this.fragment).onRelocationResult(currentPosition);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fragmentMap != null) {
            fragmentMap.clear();
        }
    }
}