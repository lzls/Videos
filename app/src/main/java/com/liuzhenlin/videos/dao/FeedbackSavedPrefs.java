/*
 * Created on 2018/04/13.
 * Copyright © 2018–2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.dao;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.util.Synthetic;
import com.liuzhenlin.common.Consts;
import com.liuzhenlin.videos.Files;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import kotlin.text.StringsKt;

/**
 * @author 刘振林
 */
public class FeedbackSavedPrefs {

    @Synthetic final SharedPreferences mSP;
    @Synthetic final ReadWriteLock mLock = new ReentrantReadWriteLock();

    private static final String TEXT = "text";
    private static final String CONTACT_WAY = "contactWay";
    private static final String PICTURE_PATHS = "picturePaths";

    private static final String DELIMITER_PICTURE_PATHS = File.separator + File.separator;
    private static final String[] DELIMITER_ARRAY_PICTURE_PATHS = {DELIMITER_PICTURE_PATHS};

    public FeedbackSavedPrefs(@NonNull Context context) {
        mSP = context.getSharedPreferences(Files.SAVED_FEEDBACK_PREFS, Context.MODE_PRIVATE);
    }

    @NonNull
    public String getText() {
        //noinspection ConstantConditions
        return mSP.getString(TEXT, Consts.EMPTY_STRING);
    }

    @NonNull
    public String getContactWay() {
        //noinspection ConstantConditions
        return mSP.getString(CONTACT_WAY, Consts.EMPTY_STRING);
    }

    @Nullable
    public List<String> getPicturePaths() {
        List<String> paths = null;
        String pathsString = null;
        Lock readLock = mLock.readLock();
        Lock writeLock = mLock.writeLock();

        readLock.lock();
        try {
            pathsString = mSP.getString(PICTURE_PATHS, null);

            // 用于兼容1.6.3以前的旧版本
        } catch (ClassCastException e) {
            Set<String> pathSet = mSP.getStringSet(PICTURE_PATHS, null);
            if (pathSet != null) {
                try {
                    readLock.unlock();
                    writeLock.lock();
                    try {
                        pathSet = mSP.getStringSet(PICTURE_PATHS, null);
                        if (pathSet != null) {
                            pathsString = combinePicturePaths(new ArrayList<>(pathSet));
                            mSP.edit().putString(PICTURE_PATHS, pathsString).apply();
                        }
                    } catch (ClassCastException e2) {
                        return getPicturePaths();
                    }
                } finally {
                    readLock.lock();
                    writeLock.unlock();
                }
            }
        } finally {
            readLock.unlock();
        }

        if (pathsString != null) {
            paths = new ArrayList<>(
                    StringsKt.split(pathsString, DELIMITER_ARRAY_PICTURE_PATHS, false, 0));
        }

        return paths;
    }

    @Synthetic static String combinePicturePaths(List<String> paths) {
        if (paths != null) {
            final int size = paths.size();
            if (size > 0) {
                StringBuilder pathsString = new StringBuilder();

                for (int i = 0; i < size - 1; i++) {
                    pathsString.append(paths.get(i)).append(DELIMITER_PICTURE_PATHS);
                }
                pathsString.append(paths.get(size - 1));

                return pathsString.toString();
            }
        }
        return null;
    }

    @NonNull
    public Editor edit() {
        return new Editor(this);
    }

    public static class Editor {

        private final FeedbackSavedPrefs mPrefs;
        private final SharedPreferences.Editor mEditor;

        Editor(FeedbackSavedPrefs prefs) {
            mPrefs = prefs;
            mEditor = prefs.mSP.edit();
        }

        public Editor setText(@Nullable String text) {
            mEditor.putString(TEXT, text);
            return this;
        }

        public Editor setContactWay(@Nullable String contactWay) {
            mEditor.putString(CONTACT_WAY, contactWay);
            return this;
        }

        public Editor setPicturePaths(@Nullable List<String> paths) {
            mEditor.putString(PICTURE_PATHS, combinePicturePaths(paths));
            return this;
        }

        public Editor clear() {
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
