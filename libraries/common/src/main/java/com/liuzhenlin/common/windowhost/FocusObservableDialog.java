/*
 * Created on 2021-10-27 6:29:18 PM.
 * Copyright © 2021 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.windowhost;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialog;

import com.liuzhenlin.common.listener.OnWindowFocusChangedListener;
import com.liuzhenlin.common.utils.ListenerSet;

public class FocusObservableDialog extends AppCompatDialog implements FocusObservableWindowHost {

    private boolean mHasFocus;
    private ListenerSet<OnWindowFocusChangedListener> mListeners;

    public FocusObservableDialog(Context context) {
        super(context);
    }

    public FocusObservableDialog(Context context, int theme) {
        super(context, theme);
    }

    protected FocusObservableDialog(
            Context context, boolean cancelable, OnCancelListener cancelListener) {
        super(context, cancelable, cancelListener);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        mHasFocus = hasFocus;
        if (mListeners != null) {
            mListeners.forEach(l -> l.onWindowFocusChanged(hasFocus));
        }
    }

    @Override
    public boolean hasWindowFocus() {
        return mHasFocus;
    }

    @Override
    public void addOnWindowFocusChangedListener(@Nullable OnWindowFocusChangedListener listener) {
        if (listener != null) {
            if (mListeners == null) {
                mListeners = new ListenerSet<>();
            }
            mListeners.add(listener);
        }
    }

    @Override
    public void removeOnWindowFocusChangedListener(@Nullable OnWindowFocusChangedListener listener) {
        if (listener != null && mListeners != null) {
            mListeners.remove(listener);
        }
    }
}
