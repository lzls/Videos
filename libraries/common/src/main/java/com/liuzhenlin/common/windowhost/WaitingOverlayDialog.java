/*
 * Created on 2023-3-18 11:07:35 PM.
 * Copyright © 2023 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.windowhost;

import android.content.Context;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDialog;

import com.liuzhenlin.common.R;

public class WaitingOverlayDialog extends AppCompatDialog {

    public WaitingOverlayDialog(Context context) {
        this(context, 0);
    }

    public WaitingOverlayDialog(Context context, int theme) {
        super(context, theme);
        init();
    }

    protected WaitingOverlayDialog(Context context, boolean cancelable, OnCancelListener cancelListener) {
        super(context, cancelable, cancelListener);
        init();
    }

    private void init() {
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        setContentView(R.layout.layout_waiting_overlay);
        setCancelable(false);
        setCanceledOnTouchOutside(false);
    }

    public void setMessage(CharSequence message) {
        //noinspection ConstantConditions
        this.<TextView>findViewById(R.id.text_progress).setText(message);
    }

    public CharSequence getMessage() {
        //noinspection ConstantConditions
        return this.<TextView>findViewById(R.id.text_progress).getText();
    }
}
