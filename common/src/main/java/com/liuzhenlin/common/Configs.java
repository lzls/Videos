/*
 * Created on 2021-12-31 11:02:36 PM.
 * Copyright © 2021 刘振林. All rights reserved.
 */

package com.liuzhenlin.common;

import android.graphics.Color;

import androidx.annotation.NonNull;

public class Configs {
    private Configs() {
    }

    public static final String DEFAULT_CHARSET = "UTF-8";

    @SuppressWarnings({"PointlessBooleanExpression", "ConstantConditions"})
    public static final boolean DEBUG_DAY_NIGHT_SWITCH = BuildConfig.DEBUG && false;
    public static final String TAG_DAY_NIGHT_SWITCH = "DayNightSwitch";

    public static final int VIDEO_VIEW_BACKGROUND_COLOR = Color.BLACK;

    public static final int[] SWIPE_REFRESH_WIDGET_COLOR_SCHEME =
            {R.color.pink, R.color.lightBlue, R.color.purple};

    public enum ScreenWidthDpLevel {
        SMALL(0),
        MEDIUM(350),
        LARGE(600);

        public final int smallestWidthDp;

        ScreenWidthDpLevel(int smallestWidthDp) {
            this.smallestWidthDp = smallestWidthDp;
        }

        @NonNull
        public static ScreenWidthDpLevel of(int widthDp) {
            if (widthDp > LARGE.smallestWidthDp) {
                return LARGE;
            }
            if (widthDp > MEDIUM.smallestWidthDp) {
                return MEDIUM;
            }
            return SMALL;
        }
    }
}
