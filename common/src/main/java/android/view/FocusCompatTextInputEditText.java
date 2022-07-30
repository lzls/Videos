/*
 * Created on 2021-11-17 12:18:08 AM.
 * Copyright © 2021 刘振林. All rights reserved.
 */

package android.view;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputEditText;
import com.liuzhenlin.common.utils.UiUtils;

/**
 * This derivant is similar to the material one but it prevents root view from requesting focus
 * in the middle of focus being cleared when it is in touch mode and can remain focused during
 * layout even if it has no size.
 */
public class FocusCompatTextInputEditText extends TextInputEditText {

    public FocusCompatTextInputEditText(@NonNull Context context) {
        super(context);
    }

    public FocusCompatTextInputEditText(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public FocusCompatTextInputEditText(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    // Override method for APIs 16 and 17
    @Keep
    void ensureInputFocusOnFirstFocusable() {
        rootViewRequestFocus();
    }

    // Override method for APIs 18+ but will not be called on platform versions higher than 20.
    @Keep
    boolean rootViewRequestFocus() {
        if (!isInTouchMode()) {
            View root = getRootView();
            return root != null && root.requestFocus();
        }
        return false;
    }

    @Override
    public void clearFocus() {
        if (Build.VERSION.SDK_INT >= 21 && Build.VERSION.SDK_INT < 28) {
            UiUtils.clearFocusNoRefocusInTouch(this);
        } else {
            super.clearFocus();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        UiUtils.fixZeroSizedViewCannotKeepFocusedInLayout(this);
    }
}
