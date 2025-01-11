/*
 * Created on 2025-1-6 8:52:55 PM.
 * Copyright © 2025 刘振林. All rights reserved.
 */

package androidx.lifecycle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ViewModelAccessor {
    private ViewModelAccessor() {
    }

    @Nullable
    public static <T> T getTag(@NonNull ViewModel viewModel, @NonNull String key) {
        return viewModel.getTag(key);
    }

    @NonNull
    public static <T> T setTagIfAbsent(
            @NonNull ViewModel viewModel, @NonNull String key, @NonNull T newValue) {
        return viewModel.setTagIfAbsent(key, newValue);
    }
}
