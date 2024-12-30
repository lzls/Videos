/*
 * Created on 2022-11-30 7:32:59 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.compat;

import android.os.Build;
import android.widget.GridView;

import androidx.annotation.NonNull;

import java.lang.reflect.Field;

public class GridViewCompat {
    private GridViewCompat() {
    }

    private static Field sColumnWidthField;
    private static boolean sColumnWidthFieldFetched;

    private static Field sVerticalSpacingField;
    private static boolean sVerticalSpacingFieldFetched;

    @SuppressWarnings("SameParameterValue")
    private static void ensureColumnWidthFieldFetched(Class<?> gvClass) {
        if (!sColumnWidthFieldFetched) {
            try {
                sColumnWidthField = gvClass.getDeclaredField("mColumnWidth");
                sColumnWidthField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }
            sColumnWidthFieldFetched = true;
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static void ensureVerticalSpacingFieldFetched(Class<?> gvClass) {
        if (!sVerticalSpacingFieldFetched) {
            try {
                sVerticalSpacingField = gvClass.getDeclaredField("mVerticalSpacing");
                sVerticalSpacingField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }
            sVerticalSpacingFieldFetched = true;
        }
    }

    public static int getColumnWidth(@NonNull GridView gridView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            return gridView.getColumnWidth();
        } else {
            ensureColumnWidthFieldFetched(GridView.class);
            if (sColumnWidthField != null) {
                try {
                    return sColumnWidthField.getInt(gridView);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            return 0;
        }
    }

    public static int getVerticalSpacing(@NonNull GridView gridView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            return gridView.getVerticalSpacing();
        } else {
            ensureVerticalSpacingFieldFetched(GridView.class);
            if (sVerticalSpacingField != null) {
                try {
                    return sVerticalSpacingField.getInt(gridView);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            return 0;
        }
    }
}
