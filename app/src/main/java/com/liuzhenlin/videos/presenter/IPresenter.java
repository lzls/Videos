/*
 * Created on 2020-12-8 9:29:19 AM.
 * Copyright © 2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.presenter;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liuzhenlin.videos.view.IView;

/**
 * @author 刘振林
 */
@SuppressWarnings("rawtypes")
public interface IPresenter<V extends IView> {

    void attachToView(@NonNull V view);
    void detachFromView(@NonNull V view);

    default void onViewCreated(@NonNull V view, @Nullable Bundle savedInstanceState) {
        onViewCreated(view);
    }
    void onViewCreated(@NonNull V view);
    void onViewStart(@NonNull V view);
    void onViewResume(@NonNull V view);
    void onViewPaused(@NonNull V view);
    void onViewStopped(@NonNull V view);
    void onViewDestroyed(@NonNull V view);
}
