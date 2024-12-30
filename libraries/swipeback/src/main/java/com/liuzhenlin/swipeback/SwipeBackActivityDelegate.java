/*
 * Created on 2022-11-12 5:51:25 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.swipeback;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.liuzhenlin.common.compat.FragmentManagerCompat;

public class SwipeBackActivityDelegate<A extends Activity & ISwipeBackActivity>
        implements ISwipeBackActivity {

    private final A mActivity;
    private final PrivateAccess mPrivateAccess;

    private SwipeBackLayoutInflater mLayoutInflater;

    public SwipeBackActivityDelegate(@NonNull A activity, @NonNull PrivateAccess privateAccess) {
        mActivity = activity;
        mPrivateAccess = privateAccess;
    }

    public Object getSystemService(@NonNull String name) {
        Object obj = mPrivateAccess.superGetSystemService(name);
        if (Context.LAYOUT_INFLATER_SERVICE.equals(name)) {
            if (mLayoutInflater == null) {
                LayoutInflater original = (LayoutInflater) obj;
                mLayoutInflater = new SwipeBackLayoutInflater(original, mActivity);
            }
            return mLayoutInflater;
        }
        return obj;
    }

    public void onCreate(@Nullable Bundle savedInstanceState) {
        // Attach our SwipeBackLayout to the window decor as early as possible
        mActivity.getWindow().getDecorView();
    }

    @Override
    public SwipeBackLayout getSwipeBackLayout() {
        if (mLayoutInflater != null) {
            return mLayoutInflater.mSwipeBackLayout;
        }
        return null;
    }

    public SwipeBackLayout requireSwipeBackLayout() {
        SwipeBackLayout swipeBackLayout = getSwipeBackLayout();
        if (swipeBackLayout == null) {
            throw new IllegalStateException("Unexpected called this before the decor is installed");
        }
        return swipeBackLayout;
    }

    @Override
    public boolean isSwipeBackEnabled() {
        return requireSwipeBackLayout().isGestureEnabled();
    }

    @Override
    public void setSwipeBackEnabled(boolean enabled) {
        requireSwipeBackLayout().setGestureEnabled(enabled);
    }

    @Override
    public boolean canSwipeBackToFinish() {
        if (mActivity instanceof FragmentActivity) {
            return ((FragmentActivity) mActivity)
                    .getSupportFragmentManager().getFragments().size() <= 1;
        }
        return FragmentManagerCompat.getFragments(mActivity.getFragmentManager()).size() <= 1;
    }

    @Nullable
    @Override
    public Activity getPreviousActivity() {
        return null;
    }

    @Override
    public void setWillNotDrawWindowBackgroundInContentViewArea(boolean willNotDraw) {
        requireSwipeBackLayout().setWillNotDrawWindowBackgroundInContentViewArea(willNotDraw);
    }

    public void finish() {
        abortUserSwipeBack();
        mPrivateAccess.superFinish();
    }

    public void finishAffinity() {
        abortUserSwipeBack();
        mPrivateAccess.superFinishAffinity();
    }

    public void finishAndRemoveTask() {
        abortUserSwipeBack();
        mPrivateAccess.superFinishAndRemoveTask();
    }

    /**
     * Aborts all motion in progress and snaps to the end of any animation, in case
     * the previous content view will not be laid back to its original position
     * when one of the flavors of {@link #finish} is called to close the activity.
     * If aborted, the window may be converted back to opaque again so that the window animations
     * will work normally on the current outgoing activity and the next incoming one.
     */
    private void abortUserSwipeBack() {
        SwipeBackLayout swipeBackLayout = getSwipeBackLayout();
        if (swipeBackLayout != null) {
            swipeBackLayout.mDragHelper.abort();
        }
    }
}
