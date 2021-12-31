/*
 * Created on 2020-10-23 5:47:52 PM.
 * Copyright © 2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos;

import android.content.Context;
import android.os.Environment;

import androidx.annotation.NonNull;

import com.liuzhenlin.common.Consts;
import com.liuzhenlin.common.utils.FileUtils;

import java.io.File;

/**
 * @author 刘振林
 */
public class Files {
    private Files() {
    }

    public static final String DB = "Videos.db";
    public static final String SHARED_PREFS = "Videos.sp";
    public static final String SAVED_FEEDBACK_PREFS = "SavedFeedback.sp";

    public static final String EXTERNAL_FILES_FOLDER = "videos_lzl";
    public static final String PROVIDER_AUTHORITY = Consts.APPLICATION_ID + ".provider";

    @NonNull
    public static File getAppExternalFilesDir() {
        File dir = new File(Environment.getExternalStorageDirectory(), Files.EXTERNAL_FILES_FOLDER);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    @NonNull
    public static File getJsonsCacheDir(@NonNull Context context) {
        File dir = new File(FileUtils.getAppCacheDir(context), "data/json");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    @NonNull
    public static File getCrashLogsDir(@NonNull Context context) {
        File dir = new File(context.getFilesDir(), "crash/logs");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }
}
