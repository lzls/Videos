/*
 * Created on 2022-11-25 11:04:59 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.compat;

import android.animation.ValueAnimator;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewAccessor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.ObjectsCompat;
import androidx.core.view.ViewCompat;

import com.liuzhenlin.common.R;
import com.liuzhenlin.common.utils.HandlerActionQueue;
import com.liuzhenlin.common.utils.ReflectionUtils;
import com.liuzhenlin.common.utils.SynchronizedPlatformSparseArray;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class ViewCompatibility {
    private ViewCompatibility() {
    }

    private static final boolean VIEW_RUNQUEUE_WILL_NEVER_CAUSE_LEAKS =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;

    private static volatile Field sViewKeyedTagsField;
    private static volatile boolean sViewKeyedTagsFieldFetched;

    private static void ensureViewKeyedTagsFieldFetched() {
        if (!sViewKeyedTagsFieldFetched) {
            synchronized (ViewCompatibility.class) {
                if (!sViewKeyedTagsFieldFetched) {
                    try {
                        //noinspection DiscouragedPrivateApi,JavaReflectionMemberAccess
                        sViewKeyedTagsField = View.class.getDeclaredField("mKeyedTags");
                        if (!(ReflectionUtils.removeFieldModifiers(sViewKeyedTagsField,
                                        Modifier.PRIVATE | Modifier.PROTECTED)
                                && ReflectionUtils.addFieldModifiers(sViewKeyedTagsField,
                                        Modifier.PUBLIC | Modifier.VOLATILE))) {
                            sViewKeyedTagsField.setAccessible(true);
                        }
                    } catch (NoSuchFieldException e) {
                        e.printStackTrace();
                    }
                    sViewKeyedTagsFieldFetched = true;
                }
            }
        }
    }

    public static boolean post(@NonNull View view, @NonNull Runnable action) {
        if (VIEW_RUNQUEUE_WILL_NEVER_CAUSE_LEAKS) {
            return view.post(action);
        } else {
            Handler uiHandler = view.getHandler();
            if (uiHandler != null) {
                return uiHandler.post(action);
            }

            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (view) {
                RunQueueHolder runQueueHolder = getTag(view, R.id.tag_viewRunQueueHolder);
                if (runQueueHolder == null) {
                    runQueueHolder = new RunQueueHolder(view);
                    setTag(view, R.id.tag_viewRunQueueHolder, runQueueHolder);
                    view.addOnAttachStateChangeListener(runQueueHolder);
                }
                runQueueHolder.getRunQueue().post(action);
                return true;
            }
        }
    }

    public static boolean postDelayed(@NonNull View view, @NonNull Runnable action, long delayMillis) {
        if (VIEW_RUNQUEUE_WILL_NEVER_CAUSE_LEAKS) {
            return view.postDelayed(action, delayMillis);
        } else {
            Handler uiHandler = view.getHandler();
            if (uiHandler != null) {
                return uiHandler.postDelayed(action, delayMillis);
            }

            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (view) {
                RunQueueHolder runQueueHolder = getTag(view, R.id.tag_viewRunQueueHolder);
                if (runQueueHolder == null) {
                    runQueueHolder = new RunQueueHolder(view);
                    setTag(view, R.id.tag_viewRunQueueHolder, runQueueHolder);
                    view.addOnAttachStateChangeListener(runQueueHolder);
                }
                runQueueHolder.getRunQueue().postDelayed(action, delayMillis);
                return true;
            }
        }
    }

    public static void postOnAnimation(@NonNull View view, @NonNull Runnable action) {
        if (VIEW_RUNQUEUE_WILL_NEVER_CAUSE_LEAKS) {
            view.postOnAnimation(action);
        } else {
            if (ViewCompat.isAttachedToWindow(view)
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                view.postOnAnimation(action);
            } else {
                postDelayed(view, action, ValueAnimator.getFrameDelay());
            }
        }
    }

    public static void postOnAnimationDelayed(
            @NonNull View view, @NonNull Runnable action, long delayMillis) {
        if (VIEW_RUNQUEUE_WILL_NEVER_CAUSE_LEAKS) {
            view.postOnAnimationDelayed(action, delayMillis);
        } else {
            if (ViewCompat.isAttachedToWindow(view)
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                view.postOnAnimationDelayed(action, delayMillis);
            } else {
                postDelayed(view, action, ValueAnimator.getFrameDelay() + delayMillis);
            }
        }
    }

    public static boolean removeCallbacks(@NonNull View view, @Nullable Runnable action) {
        if (VIEW_RUNQUEUE_WILL_NEVER_CAUSE_LEAKS) {
            return view.removeCallbacks(action);
        } else {
            view.removeCallbacks(action);
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (view) {
                RunQueueHolder runQueueHolder = getTag(view, R.id.tag_viewRunQueueHolder);
                if (runQueueHolder != null) {
                    if (runQueueHolder.mRunQueue != null) {
                        runQueueHolder.mRunQueue.removeCallbacks(action);
                    }
                }
            }
            return true;
        }
    }

    public static void scheduleDrawable(
            @NonNull View view, @NonNull Drawable who, @Nullable Runnable what, long when) {
        if (VIEW_RUNQUEUE_WILL_NEVER_CAUSE_LEAKS) {
            view.scheduleDrawable(who, what, when);
        } else {
            if (what != null && ViewAccessor.verifyDrawable(view, who)) {
                if (ViewCompat.isAttachedToWindow(view)) {
                    view.scheduleDrawable(who, what, when);
                } else {
                    postDelayed(view, what, when - SystemClock.uptimeMillis());
                }
            }
        }
    }

    public static void unscheduleDrawable(
            @NonNull View view, @NonNull Drawable who, @Nullable Runnable what) {
        view.unscheduleDrawable(who, what);
        if (!VIEW_RUNQUEUE_WILL_NEVER_CAUSE_LEAKS
                && what != null && ViewAccessor.verifyDrawable(view, who)) {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (view) {
                RunQueueHolder runQueueHolder = getTag(view, R.id.tag_viewRunQueueHolder);
                if (runQueueHolder != null) {
                    if (runQueueHolder.mRunQueue != null) {
                        runQueueHolder.mRunQueue.removeCallbacks(what);
                    }
                }
            }
        }
    }

    public static <T> T getTag(@NonNull View view, int key) {
        if (VIEW_RUNQUEUE_WILL_NEVER_CAUSE_LEAKS) {
            //noinspection unchecked
            return (T) view.getTag(key);
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (view) {
            //noinspection unchecked
            return (T) view.getTag(key);
        }
    }

    public static void setTag(@NonNull View view, int key, Object tag) {
        if (VIEW_RUNQUEUE_WILL_NEVER_CAUSE_LEAKS) {
            view.setTag(key, tag);
        } else {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (view) {
                initializeViewKeyedTagsArrayIfNeededLocked(view);
                view.setTag(key, tag);
            }
        }
    }

    private static void initializeViewKeyedTagsArrayIfNeededLocked(View view) {
        ensureViewKeyedTagsFieldFetched();
        if (sViewKeyedTagsField != null) {
            try {
                //noinspection unchecked
                SparseArray<Object> keyedTags = (SparseArray<Object>) sViewKeyedTagsField.get(view);
                SparseArray<Object> clonedKeyedTags;
                SparseArray<Object> newKeyedTags = null;
                //noinspection unchecked
                do {
                    if (keyedTags instanceof SynchronizedPlatformSparseArray)
                        return;

                    clonedKeyedTags = keyedTags != null ? keyedTags.clone() : null;
                    if (clonedKeyedTags != null) {
                        int clonedKeyedTagsSize = clonedKeyedTags.size();
                        if (newKeyedTags == null) {
                            newKeyedTags = new SynchronizedPlatformSparseArray<>(
                                    view, clonedKeyedTagsSize + 2);
                        } else {
                            newKeyedTags.clear();
                        }
                        for (int i = 0; i < clonedKeyedTagsSize; i++) {
                            newKeyedTags.put(clonedKeyedTags.keyAt(i), clonedKeyedTags.valueAt(i));
                        }
                    } else {
                        if (newKeyedTags == null) {
                            newKeyedTags = new SynchronizedPlatformSparseArray<>(view, 2);
                        } else {
                            newKeyedTags.clear();
                        }
                    }
                } while (!areKeyedTagsEqual(
                        clonedKeyedTags,
                        keyedTags = (SparseArray<Object>) sViewKeyedTagsField.get(view)));
                sViewKeyedTagsField.set(view, newKeyedTags);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private static boolean areKeyedTagsEqual(SparseArray<Object> t1, SparseArray<Object> t2) {
        int size = t1 == null ? 0 : t1.size();

        if (size != (t2 == null ? 0 : t2.size())) return false;

        for (int index = 0; index < size; index++) {
            int key = t1.keyAt(index);
            if (!ObjectsCompat.equals(t1.valueAt(index), t2.get(key))) {
                return false;
            }
        }

        return true;
    }

    private static final class RunQueueHolder implements View.OnAttachStateChangeListener {

        final Object mGuard;
        volatile HandlerActionQueue mRunQueue;

        RunQueueHolder(Object guard) {
            mGuard = guard;
        }

        @Override
        public void onViewAttachedToWindow(View v) {
            synchronized (mGuard) {
                if (mRunQueue != null) {
                    mRunQueue.executeActions(v.getHandler());
                    mRunQueue = null;
                }
            }
        }

        @Override
        public void onViewDetachedFromWindow(View v) {
        }

        HandlerActionQueue getRunQueue() {
            if (mRunQueue == null) {
                synchronized (mGuard) {
                    if (mRunQueue == null) {
                        mRunQueue = new HandlerActionQueue();
                    }
                }
            }
            return mRunQueue;
        }
    }

    private static Method sViewIsLayoutDirectionResolvedMethod;
    private static boolean sViewIsLayoutDirectionResolvedMethodFetched;

    /**
     * @return true if the view's layout direction has been resolved.
     */
    public static boolean isLayoutDirectionResolved(@NonNull View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return view.isLayoutDirectionResolved();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            ensureViewIsLayoutDirectionResolvedMethodFetched();
            if (sViewIsLayoutDirectionResolvedMethod != null) {
                try {
                    Boolean ret = (Boolean) sViewIsLayoutDirectionResolvedMethod.invoke(view);
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

    private static void ensureViewIsLayoutDirectionResolvedMethodFetched() {
        if (!sViewIsLayoutDirectionResolvedMethodFetched) {
            try {
                sViewIsLayoutDirectionResolvedMethod =
                        View.class.getDeclaredMethod("isLayoutDirectionResolved");
                sViewIsLayoutDirectionResolvedMethod.setAccessible(true);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
            sViewIsLayoutDirectionResolvedMethodFetched = true;
        }
    }

    /**
     * @return {@code true} if the view is laid-out and not about to do another layout.
     */
    public static boolean isLayoutValid(@NonNull View view) {
        return isLaidOut(view) && !view.isLayoutRequested();
    }

    /**
     * @return {@code true} if the view has been through at least one layout since it
     * was last attached to or detached from a window.
     */
    public static boolean isLaidOut(@NonNull View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return view.isLaidOut();
        }
        return ViewCompat.isAttachedToWindow(view)
                && (view.getWidth() != 0 || view.getHeight() != 0);
    }
}
