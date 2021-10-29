/*
 * Created on 2021-10-28 7:15:28 PM.
 * Copyright © 2021 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import java.util.ArrayList;
import java.util.List;

public class ListenerSet<L> {

    private List<L> mListeners;

    public boolean add(@Nullable L listener) {
        if (listener != null) {
            if (mListeners == null) {
                mListeners = new ArrayList<>();
            }
            if (!mListeners.contains(listener)) {
                return mListeners.add(listener);
            }
        }
        return false;
    }

    public boolean remove(@Nullable L listener) {
        if (listener != null) {
            if (mListeners != null) {
                return mListeners.remove(listener);
            }
        }
        return false;
    }

    public void clear() {
        if (mListeners != null) {
            mListeners.clear();
        }
    }

    public void forEach(@NonNull Consumer<L> consumer) {
        if (mListeners != null) {
            for (int i = mListeners.size() - 1; i >= 0; i--) {
                L l = mListeners.get(i);
                consumer.accept(l);
            }
        }
    }
}
