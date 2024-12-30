/*
 * Created on 2020-10-16 9:44:22 AM.
 */

package com.liuzhenlin.common.compat;

import android.os.Build;
import android.os.Looper;
import android.os.MessageQueue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Field;

/**
 * @author 刘振林
 */
public class LooperCompat {

    private LooperCompat() {
    }

    private static volatile Field sQueueField;
    private static volatile boolean sQueueFieldFetched;

    private static void ensureQueueFieldFetched() {
        if (!sQueueFieldFetched) {
            synchronized (LooperCompat.class) {
                if (!sQueueFieldFetched) {
                    try {
                        //noinspection JavaReflectionMemberAccess,DiscouragedPrivateApi
                        sQueueField = Looper.class.getDeclaredField("mQueue");
                        sQueueField.setAccessible(true);
                    } catch (NoSuchFieldException e) {
                        e.printStackTrace();
                    }
                    sQueueFieldFetched = true;
                }
            }
        }
    }

    @Nullable
    public static MessageQueue getQueue(@NonNull Looper looper) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return looper.getQueue();
        } else if (Looper.myLooper() == looper) {
            return Looper.myQueue();
        } else {
            try {
                ensureQueueFieldFetched();
                if (sQueueField != null) {
                    return (MessageQueue) sQueueField.get(looper);
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
