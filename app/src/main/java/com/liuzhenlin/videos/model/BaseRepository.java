/*
 * Created on 2024-12-23 9:17:12 PM.
 * Copyright © 2024 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.model;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liuzhenlin.common.utils.CloseableCoroutineScope;
import com.liuzhenlin.common.utils.Coroutines;
import com.liuzhenlin.common.utils.Singleton;

public abstract class BaseRepository<C extends Repository.Callback> implements Repository<C> {

    @NonNull
    protected final Context mContext;
    @Nullable
    protected C mCallback;

    @NonNull
    protected final Singleton<Void, CloseableCoroutineScope> mCoroutineScope =
            new Singleton<Void, CloseableCoroutineScope>() {
                @NonNull
                @Override
                protected CloseableCoroutineScope onCreate(Void... voids) {
                    return Coroutines.ModelScope();
                }
            };

    public BaseRepository(@NonNull Context context) {
        mContext = context.getApplicationContext();
    }

    @Override
    public void setCallback(@Nullable C callback) {
        mCallback = callback;
    }

    @Override
    public void dispose() {
        CloseableCoroutineScope scope = mCoroutineScope.getNoCreate();
        if (scope != null) {
            scope.close();
        }
        mCallback = null;
    }
}
