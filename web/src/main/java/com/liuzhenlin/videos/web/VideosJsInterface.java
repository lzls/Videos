/*
 * Created on 2022-3-25 7:57:23 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.web;

import android.content.Context;
import android.util.Log;
import android.webkit.JavascriptInterface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liuzhenlin.common.utils.Executors;

public abstract class VideosJsInterface {

    public static final int JSE_ERR = 0;
    public static final int JSE_LAST = 1;

    @NonNull
    protected final Context mContext;

    public VideosJsInterface(@NonNull Context context) {
        mContext = context;
    }

    @JavascriptInterface
    public void onEvent(int event, @Nullable String data) {
        Executors.MAIN_EXECUTOR.execute(() -> handleEvent(event, data));
    }

    protected void handleEvent(int event, @Nullable String data) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (event) {
            case JSE_ERR:
                //noinspection ConstantConditions
                Log.e(logTag(), data);
                break;
        }
    }

    @NonNull
    protected abstract String logTag();
}
