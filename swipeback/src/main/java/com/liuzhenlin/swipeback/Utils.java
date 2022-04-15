package com.liuzhenlin.swipeback;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.view.View;
import android.view.Window;

import androidx.annotation.AnyRes;
import androidx.annotation.AttrRes;
import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Utils {

    private Utils() {
    }

    public static int dimColor(int color, @FloatRange(from = 0.0f, to = 1.0f) float amount) {
        return roundFloat(((color & 0xff000000) >>> 24) * (1 - amount)) << 24
                | color & 0x00ffffff;
    }

    /** Lightweight choice to {@link Math#round(float)} */
    public static int roundFloat(float value) {
        return (int) (value > 0 ? value + 0.5f : value - 0.5f);
    }

    /** Lightweight choice to {@link Math#round(double)} */
    public static long roundDouble(double value) {
        return (long) (value > 0 ? value + 0.5 : value - 0.5);
    }

    /**
     * Convert a translucent themed Activity {@link android.R.attr#windowIsTranslucent} to
     * a fullscreen opaque Activity.
     * <p>
     * Call this whenever the background of a translucent Activity has changed to become opaque.
     * Doing so will allow the {@link android.view.Surface} of the Activity behind to be released.
     * <p>
     * This call has no effect on non-translucent activities or on activities
     * with the {@link android.R.attr#windowIsFloating} attribute.
     *
     * @see #convertActivityToTranslucent(Activity)
     */
    public static void convertActivityToOpaque(Activity activity) {
        try {
            //noinspection JavaReflectionMemberAccess
            Activity.class
                    .getMethod("convertFromTranslucent")
                    .invoke(activity);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Convert a translucent themed Activity {@link android.R.attr#windowIsTranslucent} back from
     * opaque to translucent following a call to {@link #convertActivityToOpaque(Activity)}.
     * <p>
     * Calling this allows the Activity behind this one to be seen again...
     * <p>
     * This call has no effect on non-translucent activities or on activities with the
     * {@link android.R.attr#windowIsFloating} attribute.
     *
     * @see #convertActivityToTranslucentBeforeL(Activity)
     * @see #convertActivityToTranslucentSinceL(Activity)
     * @see #convertActivityToOpaque(Activity)
     */
    public static void convertActivityToTranslucent(Activity activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                convertActivityToTranslucentSinceL(activity);
            } else {
                convertActivityToTranslucentBeforeL(activity);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Calling the method {@link #convertActivityToTranslucent(Activity)} on platforms
     * before Android 5.0
     */
    private static void convertActivityToTranslucentBeforeL(Activity activity) throws Exception {
        Class<?> translucentConversionListener = null;
        for (Class<?> clazz : Activity.class.getClasses()) {
            if ("TranslucentConversionListener".equals(clazz.getSimpleName())) {
                translucentConversionListener = clazz;
                break;
            }
        }
        if (translucentConversionListener != null) {
            //noinspection JavaReflectionMemberAccess,JavaReflectionInvocation
            Activity.class
                    .getMethod("convertToTranslucent", translucentConversionListener)
                    .invoke(activity, (Object) null);
        }
    }

    /**
     * Calling the method {@link #convertActivityToTranslucent(Activity)} on platforms
     * since Android 5.0
     *
     * @since Android 5.0
     */
    @SuppressWarnings("JavaReflectionMemberAccess")
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private static void convertActivityToTranslucentSinceL(Activity activity) throws Exception {
        Class<?> translucentConversionListener = null;
        for (Class<?> clazz : Activity.class.getClasses()) {
            if ("TranslucentConversionListener".equals(clazz.getSimpleName())) {
                translucentConversionListener = clazz;
                break;
            }
        }
        if (translucentConversionListener != null) {
            @SuppressLint("DiscouragedPrivateApi")
            Method getActivityOptions = Activity.class.getDeclaredMethod("getActivityOptions");
            getActivityOptions.setAccessible(true);

            //noinspection JavaReflectionInvocation
            Activity.class
                    .getMethod("convertToTranslucent",
                            translucentConversionListener, ActivityOptions.class)
                    .invoke(activity,
                            null, getActivityOptions.invoke(activity));
        }
    }

    /**
     * Determines whether the current Window is translucent or floating by default, according to
     * the {@link android.R.attr#windowIsFloating} and {@link android.R.attr#windowIsTranslucent}
     * attributes set in its theme.
     */
    public static boolean isWindowTranslucentOrFloatingTheme(Window window) {
        try {
            @SuppressLint("PrivateApi")
            Class<?> styleableClass =
                    window.getContext().getClassLoader()
                            .loadClass("com.android.internal.R$styleable");
            return getWindowStyleBoolean(window, styleableClass, "Window_windowIsTranslucent", false)
                    || getWindowStyleBoolean(window, styleableClass, "Window_windowIsFloating", false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private static boolean getWindowStyleBoolean(
            Window window,
            Class<?> styleableClass,
            String attrIndexName,
            @SuppressWarnings("SameParameterValue") boolean defValue) throws Exception {
        return window
                .getWindowStyle()
                .getBoolean(styleableClass.getField(attrIndexName).getInt(styleableClass), defValue);
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
     * @return true if the view's layout direction has been resolved.
     */
    public static boolean isLayoutDirectionResolved(@NonNull View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return view.isLayoutDirectionResolved();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            ensureIsLayoutDirectionResolvedMethodFetched();
            if (sIsLayoutDirectionResolvedMethod != null) {
                try {
                    Boolean ret = (Boolean) sIsLayoutDirectionResolvedMethod.invoke(view);
                    return ret != null && ret;
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
            return false;
        }
        // No support for RTL in SDKs below 17
        return true;
    }

    private static void ensureIsLayoutDirectionResolvedMethodFetched() {
        if (!sIsLayoutDirectionResolvedMethodFetched) {
            try {
                sIsLayoutDirectionResolvedMethod =
                        View.class.getDeclaredMethod("isLayoutDirectionResolved");
                sIsLayoutDirectionResolvedMethod.setAccessible(true);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
            sIsLayoutDirectionResolvedMethodFetched = true;
        }
    }

    private static Method sIsLayoutDirectionResolvedMethod;
    private static boolean sIsLayoutDirectionResolvedMethodFetched;
}
