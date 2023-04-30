/*
 * Created on 2023-3-27 10:32:48 AM.
 * Copyright © 2023 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicReference;

public abstract class LateinitProperty<T> {

    private final AtomicReference<T> mProperty = new AtomicReference<>(null);

    public final void reset() {
        set(null);
    }

    public final void set(@Nullable T value) {
        mProperty.set(value);
    }

    @NonNull
    public abstract T initialize();

    @NonNull
    public final T get() {
        T property = mProperty.get();
        while (property == null) {
            property = initialize();
            if (!mProperty.compareAndSet(null, property)) {
                property = mProperty.get();
            }
        }
        return property;
    }

    @Nullable
    public final T getNoInitialize() {
        return mProperty.get();
    }
}