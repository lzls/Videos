/*
 * Created on 2022-4-4 12:37:32 AM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.activity;

import android.os.Build;
import android.os.Bundle;
import android.view.Window;

import androidx.annotation.Nullable;

import com.liuzhenlin.common.utils.SystemBarUtils;

public class StatusBarTransparentActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Window window = getWindow();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                SystemBarUtils.setTransparentStatus(window);
            } else {
                SystemBarUtils.setTranslucentStatus(window, true);
            }
        }
    }
}
