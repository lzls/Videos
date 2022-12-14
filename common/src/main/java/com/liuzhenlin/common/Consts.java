/*
 * Created on 2021-3-25 8:37:16 PM.
 * Copyright © 2021 刘振林. All rights reserved.
 */

package com.liuzhenlin.common;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

public class Consts {
    private Consts() {
    }

    public static final long NO_ID = -1L;
    public static final String EMPTY_STRING = "";
    public static final String[] EMPTY_STRING_ARRAY = {};

    public static final String APPLICATION_ID = "com.liuzhenlin.videos";

    public static final int SDK_VERSION = Build.VERSION.SDK_INT;
    public static final int SDK_VERSION_SUPPORTS_WINDOW_INSETS = Build.VERSION_CODES.LOLLIPOP;
    public static final int SDK_VERSION_SUPPORTS_MULTI_WINDOW = Build.VERSION_CODES.N;

    @SuppressLint("InlinedApi")
    public static final int PENDING_INTENT_FLAG_MUTABLE = PendingIntent.FLAG_MUTABLE;
    @SuppressLint("InlinedApi")
    public static final int PENDING_INTENT_FLAG_IMMUTABLE = PendingIntent.FLAG_IMMUTABLE;

    public static final String DIRECTORY_DOCUMENTS =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ?
                    Environment.DIRECTORY_DOCUMENTS : "Documents";

    public static Handler getMainThreadHandler() {
        return NoPreloadHolder.MAIN_THREAD_HANDLER;
    }

    private static final class NoPreloadHolder {
        static final Handler MAIN_THREAD_HANDLER = new Handler(Looper.getMainLooper());
    }
}
