/*
 * Created on 2019/5/11 11:50 PM.
 * Copyright © 2019 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.fragment;

/**
 * @author 刘振林
 */
public class Payloads {

    public static final int PAYLOAD_CHANGE_ITEM_LPS_AND_BG = 1;
    public static final int PAYLOAD_CHANGE_CHECKBOX_VISIBILITY = 1 << 1;
    public static final int PAYLOAD_REFRESH_CHECKBOX = 1 << 2;
    public static final int PAYLOAD_REFRESH_CHECKBOX_WITH_ANIMATOR = 1 << 3;
    public static final int PAYLOAD_REFRESH_VIDEO_THUMB = 1 << 4;
    public static final int PAYLOAD_REFRESH_ITEM_NAME = 1 << 5;
    public static final int PAYLOAD_REFRESH_VIDEO_PROGRESS_DURATION = 1 << 6;

    public static final int PAYLOAD_LAST = PAYLOAD_REFRESH_VIDEO_PROGRESS_DURATION;

    private Payloads() {
    }
}
