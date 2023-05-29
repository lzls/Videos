/*
 * Created on 2019/12/29 11:30 PM.
 * Copyright © 2019 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.TypedArray;
import android.os.Build;
import android.text.TextUtils;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author 刘振林
 */
@SuppressWarnings("rawtypes")
public class ActivityUtils {
    private ActivityUtils() {
    }

    /**
     * 获取应用处于前台且为活跃状态（未被暂停）的Activity实例
     */
    @Nullable
    public static Activity getActiveActivity() {
        try {
            Map activities = getActivities();
            if (activities == null) {
                return null;
            }

            // public static final class ActivityClientRecord {...}
            Class activityRecordClass = null;
            // /*package*/ boolean paused;
            Field pausedField = null;
            for (Object activityRecord : activities.values()) {
                if (activityRecordClass == null) {
                    activityRecordClass = activityRecord.getClass();

                    pausedField = activityRecordClass.getDeclaredField("paused");
                    pausedField.setAccessible(true);
                }
                if (!pausedField.getBoolean(activityRecord)) {
                    // /*package*/ Activity activity;
                    Field activityField = activityRecordClass.getDeclaredField("activity");
                    activityField.setAccessible(true);
                    return (Activity) activityField.get(activityRecord);
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取一个指定类名的Activity实例
     */
    @Nullable
    public static Activity getActivityForName(@Nullable String activityClsName) {
        if (TextUtils.isEmpty(activityClsName)) return null;

        try {
            Map activities = getActivities();
            if (activities == null) {
                return null;
            }

            // /*package*/ Activity activity;
            Field activityField = null;
            for (Object activityRecord : activities.values()) {
                if (activityField == null) {
                    // public static final class ActivityClientRecord {...}
                    Class activityRecordClass = activityRecord.getClass();

                    activityField = activityRecordClass.getDeclaredField("activity");
                    activityField.setAccessible(true);
                }
                Activity activity = (Activity) activityField.get(activityRecord);
                //noinspection ConstantConditions
                if (activity.getClass().getName().equals(activityClsName)) {
                    return activity;
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取指定类名的所有Activity实例
     */
    @Nullable
    public static List<Activity> getActivitiesForName(@Nullable String activityClsName) {
        if (TextUtils.isEmpty(activityClsName)) return null;

        try {
            Map activities = getActivities();
            if (activities == null) {
                return null;
            }

            List<Activity> result = null;
            // /*package*/ Activity activity;
            Field activityField = null;
            for (Object activityRecord : activities.values()) {
                if (activityField == null) {
                    // public static final class ActivityClientRecord {...}
                    Class activityRecordClass = activityRecord.getClass();

                    activityField = activityRecordClass.getDeclaredField("activity");
                    activityField.setAccessible(true);
                }
                Activity activity = (Activity) activityField.get(activityRecord);
                //noinspection ConstantConditions
                if (activity.getClass().getName().equals(activityClsName)) {
                    if (result == null) {
                        result = new ArrayList<>(1);
                    }
                    result.add(activity);
                }
            }
            return result;
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Map/*<IBinder, ActivityClientRecord>*/ getActivities() {
        try {
            // public final class ActivityThread...
            @SuppressLint("PrivateApi")
            Class activityThreadClass = Class.forName("android.app.ActivityThread");

            // public static ActivityThread currentActivityThread() {...}
            @SuppressWarnings("unchecked")
            Object currentActivityThread =
                    activityThreadClass
                            .getMethod("currentActivityThread")
                            .invoke(activityThreadClass);

            // /*package*/ final ArrayMap<IBinder, ActivityClientRecord> mActivities
            Field activitiesField = activityThreadClass.getDeclaredField("mActivities");
            activitiesField.setAccessible(true);

            return (Map) activitiesField.get(currentActivityThread);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    /** 获取当前屏幕方向 */
    @SuppressLint("SwitchIntDef")
    public static int getCurrentOrientation(@NonNull Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        switch (wm.getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_90:
                return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
            case Surface.ROTATION_180:
                return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
            case Surface.ROTATION_270:
                return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            default:
                return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        }
    }

    /** 锁定屏幕方向 */
    public static void setOrientationLocked(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            if (Utils.getAppTargetSdkVersion(activity) > Build.VERSION_CODES.O
                    && Build.VERSION.SDK_INT == Build.VERSION_CODES.O) {
                try {
                    Class<ActivityInfo> activityInfoClass = ActivityInfo.class;
                    //noinspection JavaReflectionMemberAccess
                    Method isTranslucentOrFloating =
                            activityInfoClass.getMethod("isTranslucentOrFloating", TypedArray.class);
                    Boolean ret = (Boolean)
                            isTranslucentOrFloating.invoke(
                                    activityInfoClass, activity.getWindow().getWindowStyle());
                    if (ret != null && ret) {
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
                        return;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        } else {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.setTranslucent(false);
        } else {
            try {
                //noinspection JavaReflectionMemberAccess
                Activity.class
                        .getMethod("convertFromTranslucent")
                        .invoke(activity);
            } catch (Exception e) {
                e.printStackTrace();
            }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.setTranslucent(true);
        } else {
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
}
