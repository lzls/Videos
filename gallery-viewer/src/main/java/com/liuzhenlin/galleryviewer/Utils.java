/*
 * Created on 2022-11-24 3:20:09 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.galleryviewer;

import android.os.Build;
import android.os.Handler;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class Utils {
    private Utils() {
    }

    /**
     * Causes the Runnable to execute once the {@code view} is laid out.
     * The runnable will be run on the user interface thread.
     */
    public static void runOnLayoutValid(@NonNull View view, @NonNull Runnable action) {
        Handler uiHandler = view.getHandler();
        if (uiHandler != null && Thread.currentThread() == uiHandler.getLooper().getThread()) {
            if (isLayoutValid(view)) {
                action.run();
            } else {
                //noinspection unchecked
                List<Runnable> actions = (List<Runnable>)
                        view.getTag(R.id.tag_actionsRunOnLayoutValid);
                if (actions == null) {
                    actions = new ArrayList<>(1);
                    view.setTag(R.id.tag_actionsRunOnLayoutValid, actions);
                }

                boolean actionsWasEmpty = actions.isEmpty();
                // Tie any actions to the view weakly referenced below and later poll each from
                // the view in the following ViewTreeObserver listener, so that we will not cause
                // any memory leaks if the caller refers the view directly in some pending actions.
                actions.add(0, action);

                if (actionsWasEmpty) {
                    WeakReference<View> viewRef = new WeakReference<>(view);
                    view.getViewTreeObserver().addOnGlobalLayoutListener(
                            new ViewTreeObserver.OnGlobalLayoutListener() {
                                @Override
                                public void onGlobalLayout() {
                                    View v = viewRef.get();
                                    if (v != null && isLayoutValid(v)) {
                                        v.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                                        //noinspection unchecked
                                        List<Runnable> as = (List<Runnable>)
                                                v.getTag(R.id.tag_actionsRunOnLayoutValid);
                                        if (as != null) {
                                            for (int ai = as.size() - 1; ai >= 0; ai--) {
                                                as.remove(ai).run();
                                            }
                                        }
                                    }
                                }
                            });
                }
            }
        } else {
            view.post(() -> runOnLayoutValid(view, action));
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
