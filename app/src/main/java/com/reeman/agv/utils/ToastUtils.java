package com.reeman.agv.utils;

import android.content.Context;
import android.os.Build;
import android.widget.Toast;

public class ToastUtils {
    private static Toast sToast;
    private static Context sContext;

    public static void init(Context context) {
        sContext = context;
    }

    public static void showShortToast(String content) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            if (sToast != null)sToast.cancel();
            sToast = Toast.makeText(sContext,content,Toast.LENGTH_SHORT);
            sToast.show();
        }else {
            if (sToast == null){
                sToast = new Toast(sContext);
            }
            sToast.cancel();
            sToast.setText(content);
            sToast.setDuration(Toast.LENGTH_SHORT);
            sToast.show();
        }
    }

    public static void showLongToast(String content) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            if (sToast != null)sToast.cancel();
            sToast = Toast.makeText(sContext,content,Toast.LENGTH_LONG);
            sToast.show();
        }else {
            if (sToast == null){
                sToast = new Toast(sContext);
            }
            sToast.cancel();
            sToast.setText(content);
            sToast.setDuration(Toast.LENGTH_LONG);
            sToast.show();
        }
    }
}
