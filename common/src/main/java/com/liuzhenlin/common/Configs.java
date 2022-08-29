/*
 * Created on 2021-12-31 11:02:36 PM.
 * Copyright © 2021 刘振林. All rights reserved.
 */

package com.liuzhenlin.common;

public class Configs {
    private Configs() {
    }

    public static final String DEFAULT_CHARSET = "UTF-8";

    @SuppressWarnings({"PointlessBooleanExpression", "ConstantConditions"})
    public static final boolean DEBUG_DAY_NIGHT_SWITCH = BuildConfig.DEBUG && false;
    public static final String TAG_DAY_NIGHT_SWITCH = "DayNightSwitch";
}