/*
 * Created on 2021-10-28 7:41:33 PM.
 * Copyright © 2021 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputEditText;
import com.liuzhenlin.common.listener.OnBackPressedPreImeListener;
import com.liuzhenlin.common.utils.UiUtils;

public class OnBackPressedPreImeEventInterceptableTextInputEditText extends TextInputEditText
        implements OnBackPressedPreImeEventInterceptableView {

    private OnBackPressedPreImeListener mOnBackPressedPreImeListener;

    public OnBackPressedPreImeEventInterceptableTextInputEditText(@NonNull Context context) {
        super(context);
    }

    public OnBackPressedPreImeEventInterceptableTextInputEditText(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public OnBackPressedPreImeEventInterceptableTextInputEditText(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
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

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        UiUtils.fixZeroSizedViewCannotKeepFocusedInLayout(this);
    }
}
