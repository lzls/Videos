/*
 * Created on 2018/05/14.
 * Copyright © 2018–2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

import com.bumptech.glide.Glide;
import com.liuzhenlin.common.utils.Executors;
import com.liuzhenlin.common.utils.InternetResourceLoadTask;
import com.liuzhenlin.common.utils.SystemBarUtils;
import com.liuzhenlin.common.utils.Utils;
import com.liuzhenlin.floatingmenu.DensityUtils;
import com.liuzhenlin.videos.crashhandler.CrashMailReporter;
import com.liuzhenlin.videos.crashhandler.LogOnCrashHandler;
import com.liuzhenlin.videos.dao.AppPrefs;

/**
 * @author 刘振林
 */
public class App extends Application {

    private static App sApp;

    private int mStatusHeight;

    private volatile int mScreenWidth = -1;
    private volatile int mScreenHeight = -1;

    private volatile int mRealScreenWidth = -1;
    private volatile int mRealScreenHeight = -1;

    private volatile int mVideoThumbWidth = -1;

    private static volatile boolean sNightMode;

//    static {
//        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
//    }

    @Override
    public void onCreate() {
        super.onCreate();
        sApp = this;

        Executors.THREAD_POOL_EXECUTOR.execute(new CrashMailReporter(this)::send);
        Thread.setDefaultUncaughtExceptionHandler(LogOnCrashHandler.INSTANCE.get(this));

        mStatusHeight = SystemBarUtils.getStatusHeight(this);
        registerComponentCallbacks(Glide.get(this));
        InternetResourceLoadTask.setAppContext(this);
        AppCompatDelegate.setDefaultNightMode(AppPrefs.getSingleton(this).getDefaultNightMode());

        // Each directory storing WebView data can be used by only one process in the application
        // when targetSdk >= P.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            String procName = getProcessName();
            String pkgName = getPackageName();
            if (!procName.equals(pkgName)) {
                android.webkit.WebView.setDataDirectorySuffix(procName.replace(pkgName, ""));
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

    public int getStatusHeightInPortrait() {
        return mStatusHeight;
    }

    @SuppressWarnings("SuspiciousNameCombination")
    public int getScreenWidthIgnoreOrientation() {
        if (mScreenWidth == -1) {
            synchronized (this) {
                if (mScreenWidth == -1) {
                    int screenWidth = DensityUtils.getScreenWidth(this);
                    if (getResources().getConfiguration().orientation
                            != Configuration.ORIENTATION_PORTRAIT) {
                        //@formatter:off
                        int screenHeight  = DensityUtils.getScreenHeight(this);
                        if (screenWidth   > screenHeight) {
                            screenWidth  ^= screenHeight;
                            screenHeight ^= screenWidth;
                            screenWidth  ^= screenHeight;
                        }
                        //@formatter:on
                        mScreenHeight = screenHeight;
                    }
                    mScreenWidth = screenWidth;
                }
            }
        }
        return mScreenWidth;
    }

    @SuppressWarnings("SuspiciousNameCombination")
    public int getScreenHeightIgnoreOrientation() {
        if (mScreenHeight == -1) {
            synchronized (this) {
                if (mScreenHeight == -1) {
                    int screenHeight = DensityUtils.getScreenHeight(this);
                    if (getResources().getConfiguration().orientation
                            != Configuration.ORIENTATION_PORTRAIT) {
                        //@formatter:off
                        int screenWidth   = DensityUtils.getScreenWidth(this);
                        if (screenWidth   > screenHeight) {
                            screenWidth  ^= screenHeight;
                            screenHeight ^= screenWidth;
                            screenWidth  ^= screenHeight;
                        }
                        //@formatter:on
                        mScreenWidth = screenWidth;
                    }
                    mScreenHeight = screenHeight;
                }
            }
        }
        return mScreenHeight;
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

    public int getVideoThumbWidth() {
        if (mVideoThumbWidth == -1) {
            synchronized (this) {
                if (mVideoThumbWidth == -1) {
                    mVideoThumbWidth = Utils.roundFloat(getScreenWidthIgnoreOrientation() * 0.2778f);
                }
            }
        }
        return mVideoThumbWidth;
    }

    public static void cacheNightMode(boolean nightMode) {
        sNightMode = nightMode;
    }

    public static boolean isNightMode() {
        return sNightMode;
    }
}
