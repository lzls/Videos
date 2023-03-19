/*
 * Created on 2018/06/26.
 * Copyright © 2018–2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.dao;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

import com.bumptech.glide.util.Synthetic;
import com.liuzhenlin.common.Configs;
import com.liuzhenlin.common.Consts;
import com.liuzhenlin.common.utils.AESUtils;
import com.liuzhenlin.common.utils.Executors;
import com.liuzhenlin.common.utils.IOUtils;
import com.liuzhenlin.common.utils.Singleton;
import com.liuzhenlin.videos.App;
import com.liuzhenlin.videos.Files;
import com.liuzhenlin.videos.VideosLibrary;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.security.GeneralSecurityException;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author 刘振林
 */
public final class AppPrefs {

    private final Context mContext;
    @Synthetic final SharedPreferences mSP;
    @Synthetic final ReadWriteLock mLock = new ReentrantReadWriteLock();

    private static final String DRAWER_BACKGROUND_PATH = "drawerBackgroundPath";
    private static final String IS_LIGHT_DRAWER_STATUS = "isLightDrawerStatus";
    private static final String IS_LIGHT_DRAWER_LIST_FOREGROUND = "isLightDrawerListForeground";
    private static final String IS_LIGHT_DRAWER_ICONS = "isLightDrawerIcons";
    private static final String KEY_POSTFIX_NIGHT_UI_WITH_NO_DRAWER_BACKGROUND =
            "_nightUIWithNoDrawerBackground";

    private static final String DEFAULT_NIGHT_MODE = "defaultNightMode";

    private static final String GUID = "GUID";

    private static final String DOES_USER_PREFER_RUNNING_APP_IN_RESTRICTED_MODE =
            "doesUserPreferRunningAppInRestrictedMode";
    private static final String IS_LEGACY_EXTERNAL_STORAGE_DATA_MIGRATED =
            "isLegacyExternalStorageDataMigrated";

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

    // Always commits the new path for drawer background image immediately to SP, since
    // we could rely on its current value later from an Editor's setLightDrawerStatus()
    // or setLightDrawerListForeground() method.
    public void setDrawerBackgroundPath(@Nullable String path) {
        mSP.edit().putString(DRAWER_BACKGROUND_PATH, path).apply();
    }

    public boolean isLightDrawerStatus() {
        return getDrawerDayNightPref(IS_LIGHT_DRAWER_STATUS, false);
    }

    public boolean isLightDrawerListForeground() {
        return getDrawerDayNightPref(IS_LIGHT_DRAWER_LIST_FOREGROUND, true);
    }

    public boolean isLightDrawerIcons() {
        return getDrawerDayNightPref(IS_LIGHT_DRAWER_ICONS, true);
    }

    private boolean getDrawerDayNightPref(String key, boolean defaultFollowsNight) {
        Lock readLock = mLock.readLock();
        readLock.lock();
        try {
            boolean nightMode = App.isNightMode();
            if (nightMode && !mSP.contains(DRAWER_BACKGROUND_PATH)) {
                key += KEY_POSTFIX_NIGHT_UI_WITH_NO_DRAWER_BACKGROUND;
            }
            return mSP.getBoolean(key,
                    defaultFollowsNight && nightMode || !defaultFollowsNight && !nightMode);
        } finally {
            readLock.unlock();
        }
    }

    public int getDefaultNightMode() {
        return mSP.getInt(DEFAULT_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @NonNull
    public String getGUID() {
        Lock readLock = mLock.readLock();
        Lock writeLock = mLock.writeLock();
        try {
            readLock.lock();
            if (!mSP.contains(GUID)) {
                // Must release read lock before acquiring write lock
                readLock.unlock();
                writeLock.lock();
                try {
                    // Recheck state because another thread might have acquired write lock
                    // and changed the state before we did.
                    if (!mSP.contains(GUID)) {
                        String guid = null;
                        File guidFile = new File(
                                Files.getAppExternalFilesDir(Consts.DIRECTORY_DOCUMENTS), Files.GUID);
                        if (guidFile.exists()) {
                            try {
                                String data =
                                        IOUtils.decodeStringFromStream(new FileInputStream(guidFile));
                                if (data != null) {
                                    VideosLibrary.throwIfNotAvailable();
                                    guid = AESUtils.decrypt(mContext, data);
                                }
                            } catch (IOException | GeneralSecurityException e) {
                                e.printStackTrace();
                            }
                        }
                        if (TextUtils.isEmpty(guid)) {
                            guid = UUID.randomUUID().toString();
                            String finalGuid = guid;
                            Executors.THREAD_POOL_EXECUTOR.execute(() -> {
                                Writer writer = null;
                                Exception ex = null;
                                try {
                                    writer = new OutputStreamWriter(
                                            new FileOutputStream(guidFile), Configs.DEFAULT_CHARSET);
                                    VideosLibrary.throwIfNotAvailable();
                                    writer.write(AESUtils.encrypt(mContext, finalGuid));
                                } catch (GeneralSecurityException | IOException e) {
                                    ex = e;
                                    e.printStackTrace();
                                } finally {
                                    IOUtils.closeSilently(writer);
                                    if (ex == null) {
                                        guidFile.setReadOnly();
                                    } else {
                                        guidFile.delete();
                                    }
                                }
                            });
                        }
                        mSP.edit().putString(GUID, guid).apply();
                    }
                } finally {
                    // Downgrade by acquiring read lock before releasing write lock
                    readLock.lock();
                    writeLock.unlock(); // Unlock write, still hold read
                }
            }
            return mSP.getString(GUID, Build.UNKNOWN);
        } finally {
            readLock.unlock();
        }
    }

    public boolean doesUserPreferRunningAppInRestrictedMode() {
        return mSP.getBoolean(DOES_USER_PREFER_RUNNING_APP_IN_RESTRICTED_MODE, false);
    }

    public boolean isLegacyExternalStorageDataMigrated() {
        return mSP.getBoolean(IS_LEGACY_EXTERNAL_STORAGE_DATA_MIGRATED, false);
    }

    @NonNull
    public Editor edit() {
        return new Editor(this);
    }

    public static final class Editor {

        private final AppPrefs mPrefs;
        private final SharedPreferences.Editor mEditor;

        Editor(AppPrefs prefs) {
            mPrefs = prefs;
            mEditor = prefs.mSP.edit();
        }

        public AppPrefs.Editor setLightDrawerStatus(boolean light) {
            setLightDrawerStatus(App.isNightMode(), light);
            return this;
        }

        public AppPrefs.Editor setLightDrawerStatus(boolean nightMode, boolean light) {
            setDrawerDayNightPref(IS_LIGHT_DRAWER_STATUS, light, nightMode);
            return this;
        }

        public AppPrefs.Editor setLightDrawerListForeground(boolean light) {
            setLightDrawerListForeground(App.isNightMode(), light);
            return this;
        }

        public AppPrefs.Editor setLightDrawerListForeground(boolean nightMode, boolean light) {
            setDrawerDayNightPref(IS_LIGHT_DRAWER_LIST_FOREGROUND, light, nightMode);
            return this;
        }

        public AppPrefs.Editor setLightDrawerIcons(boolean light) {
            setLightDrawerIcons(App.isNightMode(), light);
            return this;
        }

        public AppPrefs.Editor setLightDrawerIcons(boolean nightMode, boolean light) {
            setDrawerDayNightPref(IS_LIGHT_DRAWER_ICONS, light, nightMode);
            return this;
        }

        private void setDrawerDayNightPref(String key, boolean value, boolean nightMode) {
            Lock readLock = mPrefs.mLock.readLock();
            readLock.lock();
            try {
                if (nightMode && !mPrefs.mSP.contains(DRAWER_BACKGROUND_PATH)) {
                    key += KEY_POSTFIX_NIGHT_UI_WITH_NO_DRAWER_BACKGROUND;
                }
                mEditor.putBoolean(key, value);
            } finally {
                readLock.unlock();
            }
        }

        public AppPrefs.Editor setDefaultNightMode(int mode) {
            mEditor.putInt(DEFAULT_NIGHT_MODE, mode);
            return this;
        }

        public AppPrefs.Editor setUserPreferRunningAppInRestrictedMode(boolean bool) {
            mEditor.putBoolean(DOES_USER_PREFER_RUNNING_APP_IN_RESTRICTED_MODE, bool);
            return this;
        }

        public AppPrefs.Editor setLegacyExternalStorageDataMigrated(boolean migrated) {
            mEditor.putBoolean(IS_LEGACY_EXTERNAL_STORAGE_DATA_MIGRATED, migrated);
            return this;
        }

        public AppPrefs.Editor clear() {
            mEditor.clear();
            return this;
        }

        public void apply() {
            Lock writeLock = mPrefs.mLock.writeLock();
            writeLock.lock();
            try {
                mEditor.apply();
            } finally {
                writeLock.unlock();
            }
        }
    }
}
