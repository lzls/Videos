/*
 * Created on 2023-5-29 8:31:09 PM.
 * Copyright © 2023 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.compat;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class AudioManagerCompat {
    private AudioManagerCompat() {
    }

    private static final String TAG = "AudioManagerCompat";

    // Fixed EXTRA_KEY_EVENT extra not being present when MediaButtonEventReceiver receives events,
    // as a result of the PendingIntent.FLAG_IMMUTABLE flag used to retrieve broadcast Intent in
    // AudioManager#registerMediaButtonEventReceiver(ComponentName) on platform versions S and later.
    public static void registerMediaButtonEventReceiver(
            @NonNull Context context,
            @NonNull AudioManager audioManager, @Nullable ComponentName eventReceiver) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            if (eventReceiver == null) {
                return;
            }
            if (!eventReceiver.getPackageName().equals(context.getPackageName())) {
                Log.e(TAG, "registerMediaButtonEventReceiver() error: " +
                        "receiver and context package names don't match");
                return;
            }
            // construct a PendingIntent for the media button
            Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            // the associated intent will be handled by the component being registered
            mediaButtonIntent.setComponent(eventReceiver);
            PendingIntent pi = PendingIntent.getBroadcast(
                    context,
                    0 /* requestCode, ignored */,
                    mediaButtonIntent,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);
            audioManager.registerMediaButtonEventReceiver(pi);
        } else {
            audioManager.registerMediaButtonEventReceiver(eventReceiver);
        }
    }
}
