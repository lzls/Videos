/*
 * Created on 2022-8-29 1:44:49 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.compat;

import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Build;

import androidx.annotation.NonNull;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FragmentManagerCompat {
    private FragmentManagerCompat() {}

    private static Field sDestroyedField;
    private static boolean sDestroyedFieldFetched;

    private static Field sAddedField;
    private static boolean sAddedFieldFetched;

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

    private static void ensureAddedFieldFetched(Class<?> fmClass) {
        if (!sAddedFieldFetched) {
            try {
                sAddedField = fmClass.getDeclaredField("mAdded");
                sAddedField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }
            sAddedFieldFetched = true;
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

    @NonNull
    public static List<Fragment> getFragments(@NonNull FragmentManager fm) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //noinspection deprecation
            return fm.getFragments();
        } else {
            ensureAddedFieldFetched(fm.getClass());
            if (sAddedField != null) {
                try {
                    //noinspection unchecked,deprecation
                    List<Fragment> fragments = (List<Fragment>) sAddedField.get(fm);
                    if (fragments != null && !fragments.isEmpty()) {
                        return new ArrayList<>(fragments);
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return Collections.emptyList();
    }
}
