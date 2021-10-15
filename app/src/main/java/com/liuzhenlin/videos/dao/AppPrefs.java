/*
 * Created on 2018/06/26.
 * Copyright © 2018–2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.dao;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

import com.liuzhenlin.common.utils.Singleton;
import com.liuzhenlin.videos.App;
import com.liuzhenlin.videos.Files;

/**
 * @author 刘振林
 */
public final class AppPrefs {

    private final Context mContext;
    private final SharedPreferences mSP;

    private static final String DRAWER_BACKGROUND_PATH = "drawerBackgroundPath";
    private static final String IS_LIGHT_DRAWER_STATUS = "isLightDrawerStatus";
    private static final String IS_LIGHT_DRAWER_LIST_FOREGROUND = "isLightDrawerListForeground";
    private static final String KEY_POSTFIX_NIGHT_UI_WITH_NO_DRAWER_BACKGROUND =
            "_nightUIWithNoDrawerBackground";

    private static final String DEFAULT_NIGHT_MODE = "defaultNightMode";

    private static final Singleton<Context, AppPrefs> sAppPrefsSingleton =
            new Singleton<Context, AppPrefs>() {
                @SuppressLint("SyntheticAccessor")
                @NonNull
                @Override
                protected AppPrefs onCreate(Context... ctxs) {
                    return new AppPrefs(ctxs[0]);
                }
            };

    public static AppPrefs getSingleton(@NonNull Context context) {
        return sAppPrefsSingleton.get(context);
    }

    private AppPrefs(Context context) {
        mContext = context.getApplicationContext();
        mSP = mContext.getSharedPreferences(Files.SHARED_PREFS, Context.MODE_PRIVATE);
    }

    @Nullable
    public String getDrawerBackgroundPath() {
        return mSP.getString(DRAWER_BACKGROUND_PATH, null);
    }

    public void setDrawerBackgroundPath(@Nullable String path) {
        mSP.edit().putString(DRAWER_BACKGROUND_PATH, path).apply();
    }

    public boolean isLightDrawerStatus() {
        String key = IS_LIGHT_DRAWER_STATUS;
        final boolean nightMode = App.isNightMode();
        if (nightMode && !mSP.contains(DRAWER_BACKGROUND_PATH)) {
            key += KEY_POSTFIX_NIGHT_UI_WITH_NO_DRAWER_BACKGROUND;
        }
        return mSP.getBoolean(key, !nightMode);
    }

    public void setLightDrawerStatus(boolean light) {
        setLightDrawerStatus(App.isNightMode(), light);
    }

    public void setLightDrawerStatus(boolean nightMode, boolean light) {
        String key = IS_LIGHT_DRAWER_STATUS;
        if (nightMode && !mSP.contains(DRAWER_BACKGROUND_PATH)) {
            key += KEY_POSTFIX_NIGHT_UI_WITH_NO_DRAWER_BACKGROUND;
        }
        mSP.edit().putBoolean(key, light).apply();
    }

    public boolean isLightDrawerListForeground() {
        String key = IS_LIGHT_DRAWER_LIST_FOREGROUND;
        final boolean nightMode = App.isNightMode();
        if (nightMode && !mSP.contains(DRAWER_BACKGROUND_PATH)) {
            key += KEY_POSTFIX_NIGHT_UI_WITH_NO_DRAWER_BACKGROUND;
        }
        return mSP.getBoolean(key, nightMode);
    }

    public void setLightDrawerListForeground(boolean light) {
        setLightDrawerListForeground(App.isNightMode(), light);
    }

    public void setLightDrawerListForeground(boolean nightMode, boolean light) {
        String key = IS_LIGHT_DRAWER_LIST_FOREGROUND;
        if (nightMode && !mSP.contains(DRAWER_BACKGROUND_PATH)) {
            key += KEY_POSTFIX_NIGHT_UI_WITH_NO_DRAWER_BACKGROUND;
        }
        mSP.edit().putBoolean(key, light).apply();
    }

    public void setDefaultNightMode(int mode) {
        mSP.edit().putInt(DEFAULT_NIGHT_MODE, mode).apply();
    }

    public int getDefaultNightMode() {
        return mSP.getInt(DEFAULT_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
}
