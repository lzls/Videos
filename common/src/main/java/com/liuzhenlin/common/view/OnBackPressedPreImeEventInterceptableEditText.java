/*
 * Created on 2021-10-28 7:41:33 PM.
 * Copyright © 2021 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.FocusCompatEditText;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liuzhenlin.common.listener.OnBackPressedPreImeListener;

public class OnBackPressedPreImeEventInterceptableEditText extends FocusCompatEditText
        implements OnBackPressedPreImeEventInterceptableView {

    private OnBackPressedPreImeListener mOnBackPressedPreImeListener;

    public OnBackPressedPreImeEventInterceptableEditText(@NonNull Context context) {
        super(context);
    }

    public OnBackPressedPreImeEventInterceptableEditText(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public OnBackPressedPreImeEventInterceptableEditText(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setOnBackPressedPreImeListener(@Nullable OnBackPressedPreImeListener listener) {
        mOnBackPressedPreImeListener = listener;
    }

    @Override
    public boolean onBackPressedPreIme() {
        return mOnBackPressedPreImeListener != null
                && mOnBackPressedPreImeListener.onBackPressedPreIme();
    }

    @Override
    public boolean onKeyPreIme(int keyCode, @NonNull KeyEvent event) {
        return OnBackPressedPreImeEventInterceptableView.super.onKeyPreIme(keyCode, event)
                || super.onKeyPreIme(keyCode, event);
    }
}
