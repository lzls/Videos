/*
 * Created on 2021-10-26 2:43:42 PM.
 * Copyright © 2021 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TransitionUtils {
    private TransitionUtils() {
    }

    // TransitionManager
    private static Field sPendingTransitionsField;
    private static Method sGetRunningTransitionsMethod;

    // Transition
    private static Method sForceToEndTransitionMethod;

    private static void ensurePendingTransitionsFieldFetched(Class<TransitionManager> clazz)
            throws NoSuchFieldException {
        if (sPendingTransitionsField == null) {
            sPendingTransitionsField = clazz.getDeclaredField("sPendingTransitions");
            sPendingTransitionsField.setAccessible(true);
        }
    }

    private static void ensureGetRunningTransitionsMethodFetched(Class<TransitionManager> clazz)
            throws NoSuchMethodException {
        if (sGetRunningTransitionsMethod == null) {
            sGetRunningTransitionsMethod = clazz.getDeclaredMethod("getRunningTransitions");
            sGetRunningTransitionsMethod.setAccessible(true);
        }
    }

    private static void ensureForceToEndTransitionMethodFetched()
            throws NoSuchMethodException {
        if (sForceToEndTransitionMethod == null) {
            sForceToEndTransitionMethod =
                    Transition.class.getDeclaredMethod("forceToEnd", ViewGroup.class);
            sForceToEndTransitionMethod.setAccessible(true);
        }
    }

    /**
     * Includes a set of children of the given `parent` ViewGroup (not necessary to be the root of
     * the transition) for the given Transition object to skip the others while it is running on a
     * view hierarchy.
     */
    public static void includeChildrenForTransition(
            @NonNull Transition transition, @NonNull ViewGroup parent, @Nullable View... children) {
        outsider:
        for (int i = 0, childCount = parent.getChildCount(); i < childCount; i++) {
            View child = parent.getChildAt(i);
            if (children != null) {
                for (View child2 : children) {
                    if (child2 == child) continue outsider;
                }
            }
            transition.excludeTarget(child, true);
        }
    }

    /** Checks if there are any transitions (either pending or running) on the given scene root. */
    public static boolean hasTransitions(@NonNull ViewGroup sceneRoot) {
        return hasPendingTransitions(sceneRoot) || hasRunningTransitions(sceneRoot);
    }

    /**
     * Checks if there are any transitions that are not yet running or finished and are being
     * scheduled for the given scene root.
     */
    public static boolean hasPendingTransitions(@NonNull ViewGroup sceneRoot) {
        try {
            List<ViewGroup> pendingTransitions = getPendingTransitions();
            return pendingTransitions != null && pendingTransitions.contains(sceneRoot);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /** Checks if there are any ongoing transitions on the given scene root. */
    public static boolean hasRunningTransitions(@NonNull ViewGroup sceneRoot) {
        try {
            List<Transition> runningTransitions = getRunningTransitionsOnSceneRoot(sceneRoot);
            return runningTransitions != null && !runningTransitions.isEmpty();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private static List<ViewGroup> getPendingTransitions()
            throws NoSuchFieldException, IllegalAccessException {
        Class<TransitionManager> transitionManagerClass = TransitionManager.class;
        ensurePendingTransitionsFieldFetched(transitionManagerClass);
        //noinspection unchecked
        return (List<ViewGroup>) sPendingTransitionsField.get(transitionManagerClass);
    }

    private static List<Transition> getRunningTransitionsOnSceneRoot(ViewGroup sceneRoot)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class<TransitionManager> transitionManagerClass = TransitionManager.class;
        ensureGetRunningTransitionsMethodFetched(transitionManagerClass);
        //noinspection unchecked
        Map<ViewGroup, List<Transition>> transitions = (Map<ViewGroup, List<Transition>>)
                sGetRunningTransitionsMethod.invoke(transitionManagerClass);
        //noinspection ConstantConditions
        return transitions.get(sceneRoot);
    }

    /**
     * Removes the pending transitions being scheduled for the given scene root.
     */
    public static boolean removePendingTransitions(@NonNull ViewGroup sceneRoot) {
        try {
            return getPendingTransitions().remove(sceneRoot);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Forces any transition of the specified scene root to move to its end state,
     * ending all the animators.
     */
    public static void endRunningTransitions(@NonNull ViewGroup sceneRoot) {
        try {
            List<Transition> runningTransitions = getRunningTransitionsOnSceneRoot(sceneRoot);
            if (runningTransitions != null && !runningTransitions.isEmpty()) {
                ensureForceToEndTransitionMethodFetched();
                // Make a copy in case this is called by an onTransitionEnd listener
                List<Transition> copy = new ArrayList<>(runningTransitions);
                for (int i = copy.size() - 1; i >= 0; i--) {
                    sForceToEndTransitionMethod.invoke(copy.get(i), sceneRoot);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
