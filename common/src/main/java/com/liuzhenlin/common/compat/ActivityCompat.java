/*
 * Created on 2022-12-11 12:48:51 AM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.compat;

import android.app.Activity;
import android.os.Build;
import android.view.Window;

import androidx.annotation.NonNull;

import java.lang.reflect.Field;

public class ActivityCompat {
    private ActivityCompat() {
    }

    private static Field sWindowDestroyedField;
    private static boolean sWindowDestroyedFieldFetched;

    private static void ensureWindowDestroyedFieldFetchedMethod() {
        if (!sWindowDestroyedFieldFetched) {
            try {
                //noinspection DiscouragedPrivateApi,JavaReflectionMemberAccess
                sWindowDestroyedField = Window.class.getDeclaredField("mDestroyed");
                sWindowDestroyedField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }
            sWindowDestroyedFieldFetched = true;
        }
    }

    public static boolean isDestroyed(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return activity.isDestroyed();
        } else {
            ensureWindowDestroyedFieldFetchedMethod();
            if (sWindowDestroyedField != null) {
                try {
                    return sWindowDestroyedField.getBoolean(activity.getWindow());
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }
}
