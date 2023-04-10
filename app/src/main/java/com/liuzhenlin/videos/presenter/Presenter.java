/*
 * Created on 2020-12-8 9:48:45 AM.
 * Copyright © 2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.presenter;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.util.ObjectsCompat;

import com.liuzhenlin.videos.view.IView;

/**
 * @author 刘振林
 */
@SuppressWarnings("rawtypes")
public class Presenter<V extends IView> implements IPresenter<V> {

    protected V mView;
    protected Context mThemedContext;
    protected Context mContext; // The application Context

    @Override
    public void attachToView(@NonNull V view) {
        if (mView != null) {
            throw new IllegalStateException(
                    "This Presenter is already attached to a View [" + mView.toString() + "]");
        }
        mView = view;
        if (view instanceof Activity) {
            mThemedContext = (Activity) view;
        } else if (view instanceof androidx.fragment.app.Fragment) {
            mThemedContext = ((androidx.fragment.app.Fragment) view).getActivity();
        } else if (view instanceof android.app.Fragment) {
            mThemedContext = ((android.app.Fragment) view).getActivity();
        }
        mContext = ObjectsCompat.requireNonNull(mThemedContext).getApplicationContext();
    }

    @Override
    public void detachFromView(@NonNull V view) {
        if (mView != view) {
            return;
        }
        mView = null;
        mThemedContext = null;
    }

    @Override
    public void onViewCreated(@NonNull V view) {
    }

    @Override
    public void onViewStart(@NonNull V view) {
    }

    @Override
    public void onViewResume(@NonNull V view) {
    }

    @Override
    public void onViewPaused(@NonNull V view) {
    }

    @Override
    public void onViewStopped(@NonNull V view) {
    }

    @Override
    public void onViewDestroyed(@NonNull V view) {
    }
}
