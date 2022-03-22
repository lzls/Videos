/*
 * Created on 2022-3-22 1:48:36 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.view;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;

public class TranslucentOnPressedButton extends AppCompatButton {

    public TranslucentOnPressedButton(@NonNull Context context) {
        super(context);
    }

    public TranslucentOnPressedButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public TranslucentOnPressedButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void dispatchSetPressed(boolean pressed) {
        setAlpha(pressed ? 0.67f : 1.0f);
    }
}
