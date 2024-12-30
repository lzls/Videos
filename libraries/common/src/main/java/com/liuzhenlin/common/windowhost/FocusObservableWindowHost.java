/*
 * Created on 2021-10-28 11:09:32 PM.
 * Copyright © 2021 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.windowhost;

import androidx.annotation.Nullable;

import com.liuzhenlin.common.listener.OnWindowFocusChangedListener;

public interface FocusObservableWindowHost {

    boolean hasWindowFocus();

    void addOnWindowFocusChangedListener(@Nullable OnWindowFocusChangedListener listener);

    void removeOnWindowFocusChangedListener(@Nullable OnWindowFocusChangedListener listener);
}
