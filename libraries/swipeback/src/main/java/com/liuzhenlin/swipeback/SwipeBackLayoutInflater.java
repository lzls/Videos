/*
 * Created on 2022-11-12 5:40:37 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.swipeback;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.Nullable;

import com.android.internal.policy.PlatformLayoutInflater;

import org.xmlpull.v1.XmlPullParser;

/*package*/ class SwipeBackLayoutInflater extends PlatformLayoutInflater {

    /*package*/ SwipeBackLayout mSwipeBackLayout;
    private final Context mContext;

    /*package*/ SwipeBackLayoutInflater(LayoutInflater inflater, Context context) {
        super(inflater, context);
        mContext = context;
    }

    @Override
    public LayoutInflater cloneInContext(Context newContext) {
        // Do not wrap the platform one for a new Context since the PhoneWindow directly uses
        // the Activity's LayoutInflater to generate its root layout so that we only
        // replace the mContentRoot of DecorView with our SwipeBackLayout when it is the View
        // created for the base Window of the Activity.
        return super.cloneInContext(newContext);
    }

    @Override
    public View inflate(int resource, @Nullable ViewGroup root, boolean attachToRoot) {
        View view = super.inflate(resource, root, attachToRoot);
        return wrapWithSwipeBackLayoutIfInflatedViewIsActivityContentRoot(root, view, attachToRoot);
    }

    @Override
    public View inflate(XmlPullParser parser, @Nullable ViewGroup root, boolean attachToRoot) {
        View view = super.inflate(parser, root, attachToRoot);
        return wrapWithSwipeBackLayoutIfInflatedViewIsActivityContentRoot(root, view, attachToRoot);
    }

    private View wrapWithSwipeBackLayoutIfInflatedViewIsActivityContentRoot(
            ViewGroup root, View view, boolean attachToRoot) {
        boolean attachedToRoot = root != null && attachToRoot;
        View inflated = attachedToRoot ? root.getChildAt(root.getChildCount() - 1) : view;
        if (mSwipeBackLayout == null
                && inflated.findViewById(Window.ID_ANDROID_CONTENT) != null) {
            View swipebackLayoutContainer = inflate(R.layout.activity_swipeback, root, false);
            mSwipeBackLayout = swipebackLayoutContainer.findViewById(R.id.swipebackLayout);
            if (attachedToRoot) {
                root.removeView(inflated);
                mSwipeBackLayout.attachActivityContentRoot((ISwipeBackActivity) mContext, inflated);
                root.addView(swipebackLayoutContainer);
                view = root;
            } else {
                mSwipeBackLayout.attachActivityContentRoot((ISwipeBackActivity) mContext, inflated);
                view = swipebackLayoutContainer;
            }
            inflated.setFitsSystemWindows(false);
        }
        return view;
    }
}