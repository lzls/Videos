/*
 * Created on 2022-4-3 11:16:25 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.swipeback;

import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.util.List;

public class SwipeBackFragmentDelegate<F extends Fragment & ISwipeBackFragment>
        implements ISwipeBackFragment {

    private SwipeBackLayout mSwipeBackLayout;
    private static Animation sNoTransition;

    private boolean mTransitionEnabled = true;

    private final F mFragment;
    private final PrivateAccess mPrivateAccess;

    public SwipeBackFragmentDelegate(@NonNull F fragment, @NonNull PrivateAccess privateAccess) {
        mFragment = fragment;
        mPrivateAccess = privateAccess;
    }

    public void onCreate(@Nullable Bundle savedInstanceState) {
        mSwipeBackLayout = (SwipeBackLayout)
                View.inflate(mFragment.getContext(), R.layout.fragment_swipeback, null);
    }

    public void onDestroy() {
        mSwipeBackLayout = null;
    }

    @NonNull
    public SwipeBackLayout attachViewToSwipeBackLayout(@NonNull View view) {
        mSwipeBackLayout.attachFragmentView(mFragment, view);
        return mSwipeBackLayout;
    }

    public Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
        if (mTransitionEnabled) {
            return mPrivateAccess.superOnCreateAnimation(transit, enter, nextAnim);
        } else {
            if (sNoTransition == null)
                sNoTransition = AnimationUtils.loadAnimation(mFragment.getContext(), R.anim.no_anim);
            return sNoTransition;
        }
    }

    public void onHiddenChanged(boolean hidden) {
        mPrivateAccess.superOnHiddenChanged(hidden);
        if (hidden) {
            // On this fragment hidden, we should set the previous fragment to be gone
            // since we may have made it visible during our dragging
            Fragment prefragment = (Fragment) getPreviousFragment();
            if (prefragment != null && prefragment.getView() != null) {
                prefragment.getView().setVisibility(View.GONE);
            }
        }
    }

    @Override
    public SwipeBackLayout getSwipeBackLayout() {
        return mSwipeBackLayout;
    }

    @Override
    public boolean isSwipeBackEnabled() {
        return mSwipeBackLayout.isGestureEnabled();
    }

    @Override
    public void setSwipeBackEnabled(boolean enabled) {
        mSwipeBackLayout.setGestureEnabled(enabled);
    }

    @Override
    public boolean isTransitionEnabled() {
        return mTransitionEnabled;
    }

    @Override
    public void setTransitionEnabled(boolean enabled) {
        mTransitionEnabled = enabled;
    }

    @Nullable
    @Override
    public ISwipeBackFragment getPreviousFragment() {
        //noinspection deprecation
        FragmentManager fm = mFragment.getFragmentManager();
        if (fm == null) return null;

        List<Fragment> fragments = fm.getFragments();
        if (fragments.size() > 1) {
            final int index = fragments.indexOf(mFragment);
            for (int i = index - 1; i >= 0; i--) {
                Fragment fragment = fragments.get(i);
                if (fragment != null && fragment.getView() != null) {
                    return (ISwipeBackFragment) fragment;
                }
            }
        }
        return null;
    }
}
