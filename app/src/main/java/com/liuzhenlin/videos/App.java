/*
 * Created on 2018/05/14.
 * Copyright © 2018–2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

import com.bumptech.glide.Glide;
import com.liuzhenlin.common.Configs;
import com.liuzhenlin.common.compat.ApplicationCompat;
import com.liuzhenlin.common.listener.OnSystemUiNightModeChangedListener;
import com.liuzhenlin.common.utils.DensityUtils;
import com.liuzhenlin.common.utils.Executors;
import com.liuzhenlin.common.utils.InternetResourceLoadTask;
import com.liuzhenlin.common.utils.ListenerSet;
import com.liuzhenlin.common.utils.ThemeUtils;
import com.liuzhenlin.videos.crashhandler.CrashMailReporter;
import com.liuzhenlin.videos.crashhandler.LogOnCrashHandler;
import com.liuzhenlin.videos.dao.AppPrefs;
import com.liuzhenlin.videos.web.youtube.YoutubePlaybackService;

import pub.devrel.easypermissions.EasyPermissions;

/**
 * @author 刘振林
 */
public class App extends Application {

    private static App sApp;

    private volatile int mRealScreenWidth = -1;
    private volatile int mRealScreenHeight = -1;

    private static volatile boolean sNightMode;

    private volatile boolean mSystemUiNightMode;

    public static final String[] STORAGE_PERMISSION =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ? new String[]{
                            Manifest.permission.READ_MEDIA_AUDIO,
                            Manifest.permission.READ_MEDIA_VIDEO,
                            Manifest.permission.READ_MEDIA_IMAGES}
                    : new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};

//    static {
//        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
//    }

    @Override
    public void onCreate() {
        super.onCreate();
        sApp = this;

        Executors.THREAD_POOL_EXECUTOR.execute(new CrashMailReporter(this)::send);
        Thread.setDefaultUncaughtExceptionHandler(LogOnCrashHandler.INSTANCE.get(this));

        mSystemUiNightMode = ThemeUtils.isNightMode(this);

        registerComponentCallbacks(Glide.get(this));
        InternetResourceLoadTask.setAppContext(this);
        AppCompatDelegate.setDefaultNightMode(AppPrefs.getSingleton(this).getDefaultNightMode());

        String procName = ApplicationCompat.getProcessName(this);
        if (procName != null) {
            // Each directory storing WebView data can be used by only one process in the application
            // when targetSdk >= P.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                String pkgName = getPackageName();
                if (!procName.equals(pkgName)) {
                    android.webkit.WebView.setDataDirectorySuffix(procName.replace(pkgName, ""));
                }
            }

            switch (procName) {
                case Consts.PROCESS_NAME_MAIN:
                    if (Configs.DEBUG_DAY_NIGHT_SWITCH) {
                        addOnSystemUiNightModeChangedListener(night -> Log.d(
                                Configs.TAG_DAY_NIGHT_SWITCH,
                                "System UI night mode is " + (night ? "on" : "off")));
                    }
                    break;
                case Consts.PROCESS_NAME_WEB:
                    addOnSystemUiNightModeChangedListener(night ->
                            YoutubePlaybackService.peekIfNonnullThenDo(service -> {
                                if (Configs.DEBUG_DAY_NIGHT_SWITCH) {
                                    Log.d(Configs.TAG_DAY_NIGHT_SWITCH,
                                            "Refresh YouTube playback control notification.");
                                }
                                service.refreshNotification();
                            }));
                    break;
            }
        }
    }

    @NonNull
    public static App getInstance(@NonNull Context context) {
        return sApp == null ? (App) context.getApplicationContext() : sApp;
    }

    @Nullable
    public static App getInstanceUnsafe() {
        return sApp;
    }

    @SuppressLint("NewApi")
    public boolean hasAllFilesAccess() {
        boolean sdkBeforeR = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q;
        boolean hasStoragePermission = hasStoragePermission();
        return sdkBeforeR && hasStoragePermission
                || !sdkBeforeR && hasStoragePermission && Environment.isExternalStorageLegacy()
                || !sdkBeforeR && Environment.isExternalStorageManager();
    }

    public boolean hasStoragePermission() {
        return EasyPermissions.hasPermissions(this, STORAGE_PERMISSION);
    }

    @SuppressWarnings("SuspiciousNameCombination")
    public int getRealScreenWidthIgnoreOrientation() {
        if (mRealScreenWidth == -1) {
            synchronized (this) {
                if (mRealScreenWidth == -1) {
                    int screenWidth = DensityUtils.getRealScreenWidth(this);
                    if (getResources().getConfiguration().orientation
                            != Configuration.ORIENTATION_PORTRAIT) {
                        //@formatter:off
                        int screenHeight  = DensityUtils.getRealScreenHeight(this);
                        if (screenWidth   > screenHeight) {
                            screenWidth  ^= screenHeight;
                            screenHeight ^= screenWidth;
                            screenWidth  ^= screenHeight;
                        }
                        //@formatter:on
                        mRealScreenHeight = screenHeight;
                    }
                    mRealScreenWidth = screenWidth;
                }
            }
        }
        return mRealScreenWidth;
    }

    @SuppressWarnings("SuspiciousNameCombination")
    public int getRealScreenHeightIgnoreOrientation() {
        if (mRealScreenHeight == -1) {
            synchronized (this) {
                if (mRealScreenHeight == -1) {
                    int screenHeight = DensityUtils.getRealScreenHeight(this);
                    if (getResources().getConfiguration().orientation
                            != Configuration.ORIENTATION_PORTRAIT) {
                        //@formatter:off
                        int screenWidth   = DensityUtils.getRealScreenWidth(this);
                        if (screenWidth   > screenHeight) {
                            screenWidth  ^= screenHeight;
                            screenHeight ^= screenWidth;
                            screenWidth  ^= screenHeight;
                        }
                        //@formatter:on
                        mRealScreenWidth = screenWidth;
                    }
                    mRealScreenHeight = screenHeight;
                }
            }
        }
        return mRealScreenHeight;
    }

    public static void cacheNightMode(boolean nightMode) {
        sNightMode = nightMode;
    }

    public static boolean isNightMode() {
        return sNightMode;
    }

    public boolean isSystemUiNightMode() {
        return mSystemUiNightMode;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean night = (newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        if (mSystemUiNightMode != night) {
            mSystemUiNightMode = night;
            synchronized (mOnSystemUiNightModeChangedListeners) {
                mOnSystemUiNightModeChangedListeners.forEach(
                        listener -> listener.onSystemUiNightModeChanged(night));
            }
        }
    }

    private final ListenerSet<OnSystemUiNightModeChangedListener> mOnSystemUiNightModeChangedListeners
            = new ListenerSet<>();

    public void addOnSystemUiNightModeChangedListener(OnSystemUiNightModeChangedListener listener) {
        synchronized (mOnSystemUiNightModeChangedListeners) {
            mOnSystemUiNightModeChangedListeners.add(listener);
        }
    }

    public void removeOnSystemUiNightModeChangeListener(OnSystemUiNightModeChangedListener listener) {
        synchronized (mOnSystemUiNightModeChangedListeners) {
            mOnSystemUiNightModeChangedListeners.remove(listener);
        }
    }
}
