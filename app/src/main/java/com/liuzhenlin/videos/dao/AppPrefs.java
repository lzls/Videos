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

import com.liuzhenlin.common.utils.Singleton;
import com.liuzhenlin.common.utils.ThemeUtils;
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
        // FIXME: we can use the app context here for convenience only under the premise of
        //  the day/night mode is throughout the whole app, otherwise a themed context is required
        final boolean nightMode = ThemeUtils.isNightMode(mContext);
        if (nightMode && !mSP.contains(DRAWER_BACKGROUND_PATH)) {
            key += KEY_POSTFIX_NIGHT_UI_WITH_NO_DRAWER_BACKGROUND;
        }
        return mSP.getBoolean(key, !nightMode);
    }

    public void setLightDrawerStatus(boolean light) {
        // FIXME: we can use the app context here for convenience only under the premise of
        //  the day/night mode is throughout the whole app, otherwise a themed context is required
        setLightDrawerStatus(ThemeUtils.isNightMode(mContext), light);
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
        // FIXME: we can use the app context here for convenience only under the premise of
        //  the day/night mode is throughout the whole app, otherwise a themed context is required
        final boolean nightMode = ThemeUtils.isNightMode(mContext);
        if (nightMode && !mSP.contains(DRAWER_BACKGROUND_PATH)) {
            key += KEY_POSTFIX_NIGHT_UI_WITH_NO_DRAWER_BACKGROUND;
        }
        return mSP.getBoolean(key, nightMode);
    }

    public void setLightDrawerListForeground(boolean light) {
        // FIXME: we can use the app context here for convenience only under the premise of
        //  the day/night mode is throughout the whole app, otherwise a themed context is required
        setLightDrawerListForeground(ThemeUtils.isNightMode(mContext), light);
    }

    public void setLightDrawerListForeground(boolean nightMode, boolean light) {
        String key = IS_LIGHT_DRAWER_LIST_FOREGROUND;
        if (nightMode && !mSP.contains(DRAWER_BACKGROUND_PATH)) {
            key += KEY_POSTFIX_NIGHT_UI_WITH_NO_DRAWER_BACKGROUND;
        }
        mSP.edit().putBoolean(key, light).apply();
    }
}
