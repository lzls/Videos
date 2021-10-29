/*
 * Created on 2021-10-28 10:42:05 PM.
 * Copyright © 2021 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.view;

import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liuzhenlin.common.listener.OnBackPressedPreImeListener;

public interface OnBackPressedPreImeEventInterceptableView {

    void setOnBackPressedPreImeListener(@Nullable OnBackPressedPreImeListener listener);

    @Nullable
    KeyEvent.DispatcherState getKeyDispatcherState();

    boolean onBackPressedPreIme();

    default boolean onKeyPreIme(int keyCode, @NonNull KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            switch (event.getAction()) {
                case KeyEvent.ACTION_DOWN:
                    if (event.getRepeatCount() == 0) {
                        KeyEvent.DispatcherState state = getKeyDispatcherState();
                        if (state != null) {
                            state.startTracking(event, this);
                        }
                        return true;
                    }
                    break;
                case KeyEvent.ACTION_UP:
                    KeyEvent.DispatcherState state = getKeyDispatcherState();
                    if (state != null) {
                        state.handleUpEvent(event);
                    }
                    if (event.isTracking() && !event.isCanceled()) {
                        return onBackPressedPreIme();
                    }
                    break;
            }
        }
        return false;
    }
}
