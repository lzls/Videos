/*
 * Created on 2022-4-3 11:31:24 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.swipeback;

import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceFragmentCompat;

public abstract class SwipeBackPreferenceFragment extends PreferenceFragmentCompat
        implements ISwipeBackFragment {

    private final SwipeBackFragmentDelegate<SwipeBackPreferenceFragment> mDelegate =
            new SwipeBackFragmentDelegate<>(this, new ISwipeBackFragment.PrivateAccess() {
                @Override
                public Animation superOnCreateAnimation(int transit, boolean enter, int nextAnim) {
                    return SwipeBackPreferenceFragment.super.onCreateAnimation(transit, enter, nextAnim);
                }

                @Override
                public void superOnHiddenChanged(boolean hidden) {
                    SwipeBackPreferenceFragment.super.onHiddenChanged(hidden);
                }
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDelegate.onCreate(savedInstanceState);
    }

    protected final SwipeBackLayout attachViewToSwipeBackLayout(@NonNull View view) {
        return mDelegate.attachViewToSwipeBackLayout(view);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDelegate.onDestroy();
    }

    @Override
    public Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
        return mDelegate.onCreateAnimation(transit, enter, nextAnim);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        mDelegate.onHiddenChanged(hidden);
    }

    @Override
    public final SwipeBackLayout getSwipeBackLayout() {
        return mDelegate.getSwipeBackLayout();
    }

    @Override
    public final boolean isSwipeBackEnabled() {
        return mDelegate.isSwipeBackEnabled();
    }

    @Override
    public final void setSwipeBackEnabled(boolean enabled) {
        mDelegate.setSwipeBackEnabled(enabled);
    }

    @Override
    public final boolean isTransitionEnabled() {
        return mDelegate.isTransitionEnabled();
    }

    @Override
    public final void setTransitionEnabled(boolean enabled) {
        mDelegate.setTransitionEnabled(enabled);
    }

    @Nullable
    @Override
    public ISwipeBackFragment getPreviousFragment() {
        return mDelegate.getPreviousFragment();
    }
}
