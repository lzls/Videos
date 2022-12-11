/*
 * Created on 2022-12-11 12:36:06 AM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.compat;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.annotation.Nullable;

public class DrawableCompat {
    private DrawableCompat() {
    }

    @Nullable
    public static ConstantState getConstantState(@Nullable Drawable drawable) {
        return drawable == null ? null : new ConstantState(drawable.getConstantState());
    }

    public static class ConstantState extends Drawable.ConstantState {
        final Drawable.ConstantState mDrawableState;

        ConstantState(Drawable.ConstantState constantState) {
            mDrawableState = constantState;
        }

        @Nullable
        @Override
        public Drawable newDrawable() {
            return mDrawableState == null ? null : mDrawableState.newDrawable();
        }

        @Nullable
        @Override
        public Drawable newDrawable(@Nullable Resources res) {
            return mDrawableState == null ? null : mDrawableState.newDrawable(res);
        }

        @Nullable
        @Override
        public Drawable newDrawable(@Nullable Resources res, @Nullable Resources.Theme theme) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                return mDrawableState == null ? null : mDrawableState.newDrawable(res, theme);
            }
            return newDrawable(res);
        }

        @Override
        public boolean canApplyTheme() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                return mDrawableState != null && mDrawableState.canApplyTheme();
            }
            return false;
        }

        @Override
        public int getChangingConfigurations() {
            return mDrawableState == null ? 0 : mDrawableState.getChangingConfigurations();
        }
    }
}
