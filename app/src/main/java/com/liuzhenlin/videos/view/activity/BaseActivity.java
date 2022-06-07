/*
 * Created on 2021-10-14 11:13:13 PM.
 * Copyright © 2021 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.activity;

import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegateWrapper;

import com.liuzhenlin.common.utils.PictureInPictureHelper;
import com.liuzhenlin.swipeback.SwipeBackActivity;
import com.liuzhenlin.swipeback.SwipeBackLayout;

public class BaseActivity extends SwipeBackActivity {

    private AppCompatDelegateWrapper mDelegate;

    private int mThemeWindowAnimations;

    private static final boolean FINISH_AFTER_CONTENT_OUT_OF_SIGHT = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Install decor first to get the default window animations coming from theme
        getWindow().getDecorView();
        mThemeWindowAnimations = getWindow().getAttributes().windowAnimations;
        super.onCreate(savedInstanceState);
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
}
