/*
 * Created on 2020-12-8 9:48:45 AM.
 * Copyright © 2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.presenter;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.util.ObjectsCompat;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import com.liuzhenlin.common.utils.ReflectionUtils;
import com.liuzhenlin.videos.view.IView;

/**
 * @author 刘振林
 */
@SuppressWarnings("rawtypes")
public class Presenter<V extends IView> extends ViewModel implements IPresenter<V> {

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

    public static class Provider {

        private static final ViewModelProvider.Factory sViewModelFactory =
                new ViewModelProvider.Factory() {
                    @NonNull
                    @Override
                    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                        try {
                            return ReflectionUtils.getDeclaredConstructor(modelClass).newInstance();
                        } catch (Exception e) {
                            throw new RuntimeException(
                                    "Cannot create an instance of " + modelClass, e);
                        }
                    }
                };

        private final ViewModelProvider mViewModelProvider;

        public Provider(@NonNull ViewModelStoreOwner owner) {
            mViewModelProvider = new ViewModelProvider(owner, sViewModelFactory);
        }

        @NonNull
        public <P extends Presenter<V>, V extends IView> P get(@NonNull Class<P> presenterClass) {
            return mViewModelProvider.get(presenterClass);
        }

        @NonNull
        public <P extends Presenter<V>, V extends IView> P get(
                @NonNull String key, @NonNull Class<P> presenterClass) {
            return mViewModelProvider.get(key, presenterClass);
        }
    }
}
