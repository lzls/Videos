/*
 * Created on 2021-12-17 3:58:37 PM.
 * Copyright © 2021 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.crashhandler;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import com.liuzhenlin.common.Configs;
import com.liuzhenlin.common.utils.IOUtils;
import com.liuzhenlin.common.utils.Singleton;
import com.liuzhenlin.videos.Files;
import com.liuzhenlin.videos.dao.AppPrefs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;

public class LogOnCrashHandler implements Thread.UncaughtExceptionHandler {

    private final Context mContext;
    private final Thread.UncaughtExceptionHandler mDefaultUncaughtExceptionHandler;

    private LogOnCrashHandler(@NonNull Context context) {
        mContext = context.getApplicationContext();
        mDefaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    public static final Singleton<Context, LogOnCrashHandler> INSTANCE = new Singleton<Context, LogOnCrashHandler>() {
        @SuppressLint("SyntheticAccessor")
        @NonNull
        @Override
        protected LogOnCrashHandler onCreate(Context... ctxes) {
            return new LogOnCrashHandler(ctxes[0]);
        }
    };

    @Override
    public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
        try {
            String log = collectCrashLog(t, e);
            writeCrashLog(log);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        if (mDefaultUncaughtExceptionHandler != null) {
            mDefaultUncaughtExceptionHandler.uncaughtException(t, e);
        } else {
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    private String collectCrashLog(Thread t, Throwable e)
            throws PackageManager.NameNotFoundException, IllegalAccessException {
        StringBuilder sb = new StringBuilder();

        PackageInfo pkgInfo = mContext.getPackageManager()
                .getPackageInfo(mContext.getPackageName(), 0);
        sb.append("versionName=").append(pkgInfo.versionName).append('\n');
        sb.append("versionCode=").append(pkgInfo.versionCode).append('\n');
        sb.append('\n');

        Field[] fields = Build.class.getFields();
        for (Field field : fields) {
            field.setAccessible(true);
            sb.append(field.getName()).append('=').append(field.get(null)).append('\n');
        }
        sb.append('\n');

        sb.append("FATAL EXCEPTION: ").append(t.getName()).append('\n');
        sb.append(Log.getStackTraceString(e));

        return sb.toString();
    }

    private void writeCrashLog(String log) throws IOException {
        @SuppressLint("SimpleDateFormat") File crashLogFile = new File(
                Files.getCrashLogsDir(mContext),
                AppPrefs.getSingleton(mContext).getGUID() + "_" +
                        new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS")
                                .format(System.currentTimeMillis()) + ".txt");
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(crashLogFile);
            out.write(log.getBytes(Configs.DEFAULT_CHARSET));
        } finally {
            IOUtils.closeSilently(out);
        }
    }
}
