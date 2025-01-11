/*
 * Created on 2025-1-4 7:31:41 PM.
 * Copyright © 2025 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils;

import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.util.Consumer;
import androidx.core.util.Supplier;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.Deferred;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.Job;

@JavaOnly
@NonNullApi
public class JCoroutine {
    private JCoroutine() {
    }

    private static final String TAG = "JCoroutine";

    public static final CoroutineDispatcher CpuDispatcher =
            Coroutines.getCPU(Dispatchers.INSTANCE);
    public static final CoroutineDispatcher SingleDispatcher =
            Coroutines.getSingle(Dispatchers.INSTANCE);

    private static final Continuation<?> EmptyContinuation =
            createContinuation(EmptyCoroutineContext.INSTANCE, null);

    public static <T> Continuation<T> emptyContinuation() {
        //noinspection unchecked
        return (Continuation<T>) EmptyContinuation;
    }

    public static <T> Continuation<T> createContinuation(
            CoroutineContext context, @Nullable Consumer<T> resumeWith) {
        return new Continuation<T>() {
            @Override
            public CoroutineContext getContext() {
                return context;
            }

            @Override
            public void resumeWith(Object o) {
                if (resumeWith != null) {
                    withContext(getContext(), () -> {
                        //noinspection unchecked
                        resumeWith.accept((T) o);
                    });
                }
            }
        };
    }

    public static Job launch(CoroutineScope scope, Runnable block) {
        return launch(scope, EmptyCoroutineContext.INSTANCE, block);
    }

    public static Job launch(
            CoroutineScope scope, CoroutineContext context, Runnable block) {
        return launch(scope, context, CoroutineStart.DEFAULT, block);
    }

    public static Job launch(
            CoroutineScope scope, CoroutineContext context, CoroutineStart start, Runnable block) {
        return BuildersKt.launch(scope, context, start, (coroutineScope, continuation) -> {
            block.run();
            return null;
        });
    }

    public static <T> Deferred<T> async(CoroutineScope scope, Supplier<T> block) {
        return async(scope, EmptyCoroutineContext.INSTANCE, block);
    }

    public static <T> Deferred<T> async(
            CoroutineScope scope, CoroutineContext context, Supplier<T> block) {
        return async(scope, context, CoroutineStart.DEFAULT, block);
    }

    public static <T> Deferred<T> async(
            CoroutineScope scope, CoroutineContext context, CoroutineStart start, Supplier<T> block) {
        return BuildersKt.async(
                scope, context, start, (coroutineScope, continuation) -> block.get());
    }

    public static void withContext(CoroutineContext context, Runnable block) {
        Supplier<Void> supplier = () -> {
            block.run();
            //noinspection DataFlowIssue
            return null;
        };
        withContext(context, supplier, emptyContinuation());
    }

    public static <T> void withContext(
            CoroutineContext context, Supplier<T> block, Continuation<T> complemention) {
        Supplier<Void> supplier = () -> {
            BuildersKt.withContext(
                    context, (coroutineScope, continuation) -> block.get(), complemention);
            //noinspection DataFlowIssue
            return null;
        };
        runBlocking(context, supplier);
    }

    /** @noinspection DataFlowIssue*/
    @Nullable
    public static <T> T runBlocking(CoroutineContext context, Supplier<T> block) {
        return runBlocking(context, block, null);
    }

    /** @noinspection NullableProblems*/
    public static <T> T runBlocking(
            CoroutineContext context, Supplier<T> block, T defaultValueIfInterrupted) {
        try {
            return BuildersKt.runBlocking(context, ((coroutineScope, continuation) -> block.get()));
        } catch (InterruptedException e) {
            Log.w(TAG, e);
        }
        return defaultValueIfInterrupted;
    }
}
