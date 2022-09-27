/*
 * Created on 2022-8-29 1:44:49 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.compat;

import android.app.FragmentManager;
import android.os.Build;

import androidx.annotation.NonNull;

import java.lang.reflect.Field;

public class FragmentManagerCompat {
    private FragmentManagerCompat() {}

    private static Field sDestroyedField;
    private static boolean sDestroyedFieldFetched;

    private static void ensureDestroyedFieldFetched(Class<?> fmClass) {
        if (!sDestroyedFieldFetched) {
            try {
                sDestroyedField = fmClass.getDeclaredField("mDestroyed");
                sDestroyedField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }
            sDestroyedFieldFetched = true;
        }
    }

    public static boolean isDestroyed(@NonNull FragmentManager fm) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            //noinspection deprecation
            return fm.isDestroyed();
        } else {
            ensureDestroyedFieldFetched(fm.getClass());
            try {
                return sDestroyedField.getBoolean(fm);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return false;
    }
}
