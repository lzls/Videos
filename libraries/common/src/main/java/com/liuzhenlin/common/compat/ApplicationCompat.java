/*
 * Created on 2022-7-9 12:47:10 AM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.compat;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public class ApplicationCompat {
    private ApplicationCompat() {}

    @Nullable
    public static String getProcessName(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName();
        } else {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> appProcesses = am.getRunningAppProcesses();
            if (appProcesses != null && appProcesses.size() > 0) {
                int pid = Process.myPid();
                for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
                    if (appProcess.pid == pid) {
                        return appProcess.processName;
                    }
                }
            }
        }
        return null;
    }
}
