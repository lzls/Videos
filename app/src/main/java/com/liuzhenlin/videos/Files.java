/*
 * Created on 2020-10-23 5:47:52 PM.
 * Copyright © 2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos;

import android.content.Context;
import android.os.Build;
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

    public static final String GUID = ".guid__lzls_videos";

    public static final String EXTERNAL_FILES_FOLDER = "videos_lzl";
    static final String LEGACY_SCREENSHOTS_LAST_PATH_SEGMENT = "screenshots";
    static final String LEGACY_SHORT_CLIPS_LAST_PATH_SEGMENT = "clips/ShortVideos";
    public static final String SCREENSHOTS_FOLDER = LEGACY_SCREENSHOTS_LAST_PATH_SEGMENT;
    public static final String SHORT_CLIPS_FOLDER = "ShortClips";

    public static final String PROVIDER_AUTHORITY = Consts.APPLICATION_ID + ".provider";

    @NonNull
    public static File getAppExternalFilesDir(@NonNull String dirType) {
        @SuppressWarnings("deprecation") File base =
                Build.VERSION.SDK_INT > Build.VERSION_CODES.Q
                        ? Environment.getExternalStoragePublicDirectory(dirType)
                        : Environment.getExternalStorageDirectory();
        File dir = new File(base, EXTERNAL_FILES_FOLDER);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    @NonNull
    static File getLegacyAppExternalFilesDir() {
        File dir = new File(Environment.getExternalStorageDirectory(), EXTERNAL_FILES_FOLDER);
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
