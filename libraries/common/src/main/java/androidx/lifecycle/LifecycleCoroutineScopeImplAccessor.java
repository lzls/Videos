/*
 * Created on 2025-1-6 9:15:21 PM.
 * Copyright © 2025 刘振林. All rights reserved.
 */

package androidx.lifecycle;

import androidx.annotation.NonNull;

import kotlin.coroutines.CoroutineContext;

/** @noinspection KotlinInternalInJava*/
public class LifecycleCoroutineScopeImplAccessor {
    private LifecycleCoroutineScopeImplAccessor() {
    }

    @NonNull
    public static LifecycleCoroutineScopeImpl newLifecycleCoroutineScopeImpl(
            @NonNull Lifecycle lifecycle, @NonNull CoroutineContext coroutineContext) {
        return new LifecycleCoroutineScopeImpl(lifecycle, coroutineContext);
    }

    public static void register(@NonNull LifecycleCoroutineScopeImpl impl) {
        impl.register();
    }
}
