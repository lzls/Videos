/*
 * Created on 2022-4-11 1:57:51 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos;

public class Prefs {
    private Prefs() {}

    public static final String KEY_UPDATE_CHANNEL = "update_channel";
    public static final String UPDATE_CHANNEL_STABLE = "stable";
    public static final String UPDATE_CHANNEL_BETA = "beta";
    public static final String UPDATE_CHANNEL_DEV = "dev";

    public static final String KEY_DARK_MODE = "dark_mode";
    public static final String DARK_MODE_ON = "on";
    public static final String DARK_MODE_OFF = "off";
    public static final String DARK_MODE_FOLLOWS_SYSTEM = "followsSystem";
}
