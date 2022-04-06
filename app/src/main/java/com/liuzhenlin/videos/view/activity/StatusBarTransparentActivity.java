/*
 * Created on 2022-4-4 12:37:32 AM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.activity;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liuzhenlin.common.utils.SystemBarUtils;
import com.liuzhenlin.videos.App;

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

    protected void insertTopPaddingToActionBarIfNeeded(@NonNull View actionbar) {
        if (isLayoutUnderStatusBar()) {
            final int statusHeight = App.getInstance(this).getStatusHeightInPortrait();
            switch (actionbar.getLayoutParams().height) {
                case ViewGroup.LayoutParams.WRAP_CONTENT:
                case ViewGroup.LayoutParams.MATCH_PARENT:
                    break;
                default:
                    actionbar.getLayoutParams().height += statusHeight;
            }
            actionbar.setPadding(
                    actionbar.getPaddingLeft(),
                    actionbar.getPaddingTop() + statusHeight,
                    actionbar.getPaddingRight(),
                    actionbar.getPaddingBottom());
        }
    }

    protected boolean isLayoutUnderStatusBar() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }
}
