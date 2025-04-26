package com.reeman.agv.widgets;

import android.app.Dialog;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import androidx.annotation.NonNull;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.reeman.agv.R;
import com.reeman.commons.utils.SoftKeyboardStateWatcher;

public class DispatchAuthDialog extends BaseDialog implements View.OnClickListener, SoftKeyboardStateWatcher.SoftKeyboardStateListener {

    private TextInputEditText roomName;
    private TextInputEditText roomPwd;
    private Button cancelBtn;
    private Button loginBtn;
    private SoftKeyboardStateWatcher watcher;

    public void setName(String name) {
        roomName.setText(name);
        roomName.setSelection(name.length());
    }

    public void setPassword(String password) {
        roomPwd.setText(password);
        roomPwd.setSelection(roomPwd.length());
    }

    public TextInputEditText getRoomName() {
        return roomName;
    }

    public TextInputEditText getRoomPwd() {
        return roomPwd;
    }

    public Button getLoginBtn() {
        return loginBtn;
    }

    public Button getCancelBtn() {
        return cancelBtn;
    }

    public DispatchAuthDialog(@NonNull Context context) {
        super(context);
        init(context);

    }

    private void init(Context context) {
        View root = LayoutInflater.from(context).inflate(R.layout.layout_wifi_auth, null);
        roomName = root.findViewById(R.id.et_wifi_name);
        roomPwd = root.findViewById(R.id.et_wifi_password);
        cancelBtn = root.findViewById(R.id.btn_cancel);
        cancelBtn.setOnClickListener(this);
        loginBtn = root.findViewById(R.id.btn_login);
        loginBtn.setOnClickListener(this);
        TextInputLayout tilRoomName = root.findViewById(R.id.til_wifi_name);
        TextInputLayout tilRoomPwd = root.findViewById(R.id.til_wifi_password);

        setTitle(R.string.text_dispatch_room_auth);
        tilRoomName.setHint(R.string.text_please_input_room_name);
        roomName.setHint(R.string.text_please_input_room_name);
        tilRoomPwd.setHint(R.string.text_please_input_room_pwd);
        roomPwd.setHint(R.string.text_please_input_room_pwd);
        setContentView(root);
        watcher = new SoftKeyboardStateWatcher(getWindow().getDecorView());
        watcher.addSoftKeyboardStateListener(this);
    }

    private OnViewClickListener onViewClickListener;

    public void setOnViewClickListener(OnViewClickListener onViewClickListener) {
        this.onViewClickListener = onViewClickListener;
    }

    @Override
    public void onClick(View v) {
        if (onViewClickListener != null) onViewClickListener.onViewClick(this,v,getRoomName().getText().toString(),getRoomPwd().getText().toString());
    }

    @Override
    public void onSoftKeyboardOpened(int keyboardHeightInPx) {

    }

    @Override
    public void onSoftKeyboardClosed() {
        Window window = this.getWindow();
        window.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | 0x00004000;
        window.getDecorView().setSystemUiVisibility(uiOptions);
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    }

    public interface OnViewClickListener {
        /**
         * wifi连接弹窗
         * @param v
         */
        void onViewClick(Dialog dialog, View v,String roomName,String roomPwd);
    }

}
