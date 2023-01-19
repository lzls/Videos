/*
 * Created on 2020-9-28 6:23:14 PM.
 * Copyright © 2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.style.TextAppearanceSpan;

import androidx.annotation.AnimRes;
import androidx.annotation.AnyRes;
import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.content.ContextCompat;

import com.liuzhenlin.common.R;

/**
 * @author 刘振林
 */
public class ThemeUtils {
    private ThemeUtils() {
    }

    private static volatile int[] sTextAppearanceStyleable;
    private static volatile int sTextAppearanceStyleableTextColorIndex;
    private static volatile boolean sTextAppearanceStyleableFetched;
    private static volatile boolean sTextAppearanceStyleableTextColorIndexFetched;

    @SuppressLint("PrivateApi")
    private static void ensureTextAppearanceStyleableFetched(Context context, ClassLoader loader) {
        if (!sTextAppearanceStyleableFetched) {
            synchronized (ThemeUtils.class) {
                if (!sTextAppearanceStyleableFetched) {
                    try {
                        if (loader == null) {
                            loader = context.getClassLoader();
                        }
                        Class<?> styleable = loader.loadClass("com.android.internal.R$styleable");
                        sTextAppearanceStyleable =
                                (int[]) styleable.getField("TextAppearance").get(styleable);
                    } catch (Exception e) {
                        e.printStackTrace();
                        sTextAppearanceStyleable = null;
                    }
                    sTextAppearanceStyleableFetched = true;
                }
            }
        }
    }

    @SuppressLint("PrivateApi")
    private static void ensureTextAppearanceStyleableTextColorIndexFetched(
            Context context, ClassLoader loader) {
        if (!sTextAppearanceStyleableTextColorIndexFetched) {
            synchronized (ThemeUtils.class) {
                if (!sTextAppearanceStyleableTextColorIndexFetched) {
                    try {
                        if (loader == null) {
                            loader = context.getClassLoader();
                        }
                        Class<?> styleable = loader.loadClass("com.android.internal.R$styleable");
                        sTextAppearanceStyleableTextColorIndex =
                                styleable.getField("TextAppearance_textColor").getInt(styleable);
                    } catch (Exception e) {
                        e.printStackTrace();
                        sTextAppearanceStyleableTextColorIndex = 0;
                    }
                    sTextAppearanceStyleableTextColorIndexFetched = true;
                }
            }
        }
    }

    /** Returns the default text color of a given text appearance. */
    public static int getTextAppearanceDefaultTextColor(
            @NonNull Context context, @StyleRes int appearance) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q
                || context.getApplicationInfo().targetSdkVersion <= Build.VERSION_CODES.Q) {
            ClassLoader loader = context.getClassLoader();
            ensureTextAppearanceStyleableFetched(context, loader);
            if (sTextAppearanceStyleable != null) {
                ensureTextAppearanceStyleableTextColorIndexFetched(context, loader);
                if (sTextAppearanceStyleableTextColorIndex != 0) {
                    TypedArray ta =
                            context.obtainStyledAttributes(appearance, sTextAppearanceStyleable);
                    try {
                        ColorStateList csl =
                                ta.getColorStateList(sTextAppearanceStyleableTextColorIndex);
                        if (csl != null) {
                            return csl.getDefaultColor();
                        }
                    } finally {
                        ta.recycle();
                    }
                }
            }
        }
        return new TextAppearanceSpan(context, appearance).getTextColor().getDefaultColor();
    }

    /** Retrieves the platform default value for {@link android.R.attr.activityOpenEnterAnimation} */
    @SuppressWarnings("JavadocReference")
    @AnimRes
    public static int getDefaultActivityOpenEnterAnim() {
        return Resources.getSystem().getIdentifier("activity_open_enter", "anim", "android");
    }

    /** Retrieves the platform default value for {@link android.R.attr.activityOpenExitAnimation} */
    @SuppressWarnings("JavadocReference")
    @AnimRes
    public static int getDefaultActivityOpenExitAnim() {
        return Resources.getSystem().getIdentifier("activity_open_exit", "anim", "android");
    }

    /** Retrieves the platform default value for {@link android.R.attr.activityCloseEnterAnimation} */
    @SuppressWarnings("JavadocReference")
    @AnimRes
    public static int getDefaultActivityCloseEnterAnim() {
        return Resources.getSystem().getIdentifier("activity_close_enter", "anim", "android");
    }

    /** Retrieves the platform default value for {@link android.R.attr.activityCloseExitAnimation} */
    @SuppressWarnings("JavadocReference")
    @AnimRes
    public static int getDefaultActivityCloseExitAnim() {
        return Resources.getSystem().getIdentifier("activity_close_exit", "anim", "android");
    }

    /**
     * Returns a drawable to use as the list item dividers
     *
     * @param context the themed context object
     * @param light   true to get the divider with light color, otherwise to get the dark one
     */
    public static Drawable getListDivider(@NonNull Context context, boolean light) {
        context = wrapContextIfNeeded(context, light);
        return ContextCompat.getDrawable(
                context, getThemeAttrRes(context, android.R.attr.listDivider));
    }

    /**
     * Returns a drawable to use as the single choice indicators for selected list items
     *
     * @param context the themed context object
     * @param light   true to get the indicator with light color, otherwise to get the dark one
     */
    public static Drawable getListChoiceIndicatorSingle(@NonNull Context context, boolean light) {
        context = wrapContextIfNeeded(context, light);
        return ContextCompat.getDrawable(
                context, getThemeAttrRes(context, android.R.attr.listChoiceIndicatorSingle));
    }

    private static Context wrapContextIfNeeded(Context context, boolean light) {
        final boolean nightMode = isNightMode(context);
        if (light) {
            if (!nightMode) {
                context = new ContextThemeWrapper(
                        context, R.style.ThemeOverlay_MaterialComponents_Dark);
            }
        } else if (nightMode) {
            context = new ContextThemeWrapper(context, R.style.ThemeOverlay_MaterialComponents_Light);
        }
        return context;
    }

    /** Resolves the resource ID from the provided theme attribute. */
    @AnyRes
    public static int getThemeAttrRes(@NonNull Context context, @AttrRes int attr) {
        TypedArray ta = context.getTheme().obtainStyledAttributes(new int[]{attr});
        try {
            return ta.getResourceId(0, 0);
        } finally {
            ta.recycle();
        }
    }

    /**
     * Returns whether the current theme is in dark mode, enabling {@code night} qualified resources.
     */
    public static boolean isNightMode(@NonNull Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }
}
