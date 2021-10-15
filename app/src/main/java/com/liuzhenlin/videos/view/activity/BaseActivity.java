/*
 * Created on 2021-10-14 11:13:13 PM.
 * Copyright © 2021 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.activity;

import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.app.AppCompatDelegateProxy;

import com.liuzhenlin.common.utils.ThemeUtils;
import com.liuzhenlin.swipeback.SwipeBackActivity;
import com.liuzhenlin.swipeback.SwipeBackLayout;
import com.liuzhenlin.videos.App;

public class BaseActivity extends SwipeBackActivity {

    private Configuration mConfig;
    private AppCompatDelegateProxy mDelegate;
    private int mThemeWindowAnimations;
    private static final boolean FINISH_AFTER_CONTENT_OUT_OF_SIGHT = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Install decor first to get the default window animations coming from theme
        getWindow().getDecorView();
        mThemeWindowAnimations = getWindow().getAttributes().windowAnimations;

        super.onCreate(savedInstanceState);
        mConfig = new Configuration(getResources().getConfiguration());

        // Caches the night mode in global as far as possible early, under the promise of
        // the same day/night mode throughout the whole app. The default night mode may not follow
        // the system default, and so we can not just use the app context to see if this app
        // is decorated with the dark theme. This should be called for each activity instead of
        // just the first launched one, in case some of the activities are being recreated...
        App.cacheNightMode(ThemeUtils.isNightMode(this));
    }

    @NonNull
    @Override
    public AppCompatDelegate getDelegate() {
        if (mDelegate == null) {
            mDelegate = new AppCompatDelegateProxy(super.getDelegate());
        }
        return mDelegate;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Be sure to update the Config from Resources here since it may have changed with
        // the updated UI Mode
        newConfig = getResources().getConfiguration();
        int uiModeMask = Configuration.UI_MODE_NIGHT_MASK;
        if ((mConfig.uiMode & uiModeMask) != (newConfig.uiMode & uiModeMask)) {
            mDelegate.recreateHostWhenDayNightAppliedIfNeeded();
        }
        mConfig.setTo(newConfig);
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
}
