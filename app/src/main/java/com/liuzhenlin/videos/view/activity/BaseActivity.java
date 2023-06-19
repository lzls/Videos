/*
 * Created on 2021-10-14 11:13:13 PM.
 * Copyright © 2021 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.activity;

import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegateWrapper;

import com.liuzhenlin.common.Configs.ScreenWidthDpLevel;
import com.liuzhenlin.common.Consts;
import com.liuzhenlin.common.utils.OSHelper;
import com.liuzhenlin.common.utils.PictureInPictureHelper;
import com.liuzhenlin.common.utils.SystemBarUtils;
import com.liuzhenlin.common.utils.ThemeUtils;
import com.liuzhenlin.swipeback.SwipeBackActivity;
import com.liuzhenlin.swipeback.SwipeBackLayout;

public class BaseActivity extends SwipeBackActivity {

    private AppCompatDelegateWrapper mDelegate;

    private int mThemeWindowAnimations;

    private int mScreenWidthDp;

    private static final boolean FINISH_AFTER_CONTENT_OUT_OF_SIGHT = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        mThemeWindowAnimations =
                ThemeUtils.getThemeAttrRes(this, android.R.attr.windowAnimationStyle);
        super.onCreate(savedInstanceState);
        mScreenWidthDp = getResources().getConfiguration().screenWidthDp;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int screenWidthDp = mScreenWidthDp;
        if (screenWidthDp != 0) {
            mScreenWidthDp = newConfig.screenWidthDp;
            ScreenWidthDpLevel oldLevel = ScreenWidthDpLevel.of(screenWidthDp);
            ScreenWidthDpLevel level = ScreenWidthDpLevel.of(newConfig.screenWidthDp);
            if (level != oldLevel) {
                onScreenWidthDpLevelChanged(oldLevel, level);
            }
        }
    }

    protected void onScreenWidthDpLevelChanged(
            @NonNull ScreenWidthDpLevel oldLevel, @NonNull ScreenWidthDpLevel level) {
    }

    @NonNull
    @Override
    public AppCompatDelegateWrapper getDelegate() {
        if (mDelegate == null) {
            mDelegate = new AppCompatDelegateWrapper(super.getDelegate(), super::finish);
        }
        return mDelegate;
    }

    /**
     * Disables the swipe-back feature and removes the window animation overrides.
     * <p>
     * NOTE: <stronge>MUST</stronge> only be called after {@code super.onCreate()} has been called.
     */
    public void setAsNonSwipeBackActivity() {
        SwipeBackLayout swipeBackLayout = getSwipeBackLayout();
        swipeBackLayout.setGestureEnabled(false);
        swipeBackLayout.setEnabledEdges(0);

        getWindow().setWindowAnimations(mThemeWindowAnimations);
    }

    public void setLightStatus(boolean light) {
        Window window = getWindow();
        if (!SystemBarUtils.setLightStatusCompat(window, light)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            SystemBarUtils.setTranslucentStatus(window, light);
        }
    }

    /**
     * Scroll out content view and finish activity.
     */
    public void scrollToFinish() {
        if (FINISH_AFTER_CONTENT_OUT_OF_SIGHT
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !isInMultiWindowMode())) {
            getSwipeBackLayout().scrollToFinishActivityOrPopUpFragment();
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        if (FINISH_AFTER_CONTENT_OUT_OF_SIGHT) {
            if (canSwipeBackToFinish()) {
                scrollToFinish();
                return;
            }
        }
        super.onBackPressed();
    }

    @Override
    public void finish() {
        getDelegate().finish();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        getDelegate().onPictureInPictureModeChanged(isInPictureInPictureMode);
    }

    @Override
    public boolean isInPictureInPictureMode() {
        PictureInPictureHelper pipHelper = getDelegate().getPipHelper();
        if (pipHelper != null) {
            return pipHelper.doesSdkVersionSupportPiP() && super.isInPictureInPictureMode();
        }
        return Build.VERSION.SDK_INT >= PictureInPictureHelper.SDK_VERSION_SUPPORTS_PIP
                && super.isInPictureInPictureMode();
    }

    @Override
    public boolean isInMultiWindowMode() {
        return Consts.SDK_VERSION >= Consts.SDK_VERSION_SUPPORTS_MULTI_WINDOW
                && super.isInMultiWindowMode();
    }
}
