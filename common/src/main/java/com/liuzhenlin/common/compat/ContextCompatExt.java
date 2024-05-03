/*
 * Created on 2024-5-3 6:41:58 AM.
 * Copyright © 2024 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.compat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;

public class ContextCompatExt {
    private ContextCompatExt() {
    }

    @SuppressLint("InlinedApi")
    public static final int RECEIVER_EXPORTED = Context.RECEIVER_EXPORTED;
    @SuppressLint("InlinedApi")
    public static final int RECEIVER_NOT_EXPORTED = Context.RECEIVER_NOT_EXPORTED;

    public static void registerReceiver(
            @NonNull Context context, @Nullable BroadcastReceiver receiver,
            @NonNull IntentFilter filter, int flags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.registerReceiver(receiver, filter, flags);
        } else {
            //noinspection UnspecifiedRegisterReceiverFlag
            context.registerReceiver(receiver, filter);
        }
    }
}
