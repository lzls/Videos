/*
 * Created on 2021-9-11 11:47:00 PM.
 * Copyright © 2021 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import com.liuzhenlin.common.observer.ScreenNotchSwitchObserver;

import java.util.ArrayList;
import java.util.List;

public class DisplayCutoutManager {

    private static final String TAG = "DisplayCutoutManager";

    private boolean mIsNotchSupport;
    private boolean mIsNotchSupportOnEMUI;
    private boolean mIsNotchSupportOnMIUI;
    @Synthetic boolean mIsNotchHidden;
    private int mNotchHeight;
    private ScreenNotchSwitchObserver mNotchSwitchObserver;
    @Synthetic List<OnNotchSwitchListener> mNotchSwitchListeners;

    private final Window mParentWindow;
    private final Window mWindow;
    private final View.OnAttachStateChangeListener mOnWindowAttachStateChangeListener =
            new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    init();
                    observeNotchSwitch(v.getContext(), v.getHandler());
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    unobserveNotchSwitch();
                }
            };

    private boolean mInitialized;
    private boolean mDisposed;

    /** Constructs a DisplayCutoutManager with a {@link Window} instance. */
    public DisplayCutoutManager(@NonNull Window window) {
        this(null, window);
    }

    /**
     * Constructs a DisplayCutoutManager using a {@link Window} instance together with a parent one.
     * <p>The parent window <strong>MUST</strong> has its decor view attached to it if supplied or
     * IllegalStateException will be thrown. The parent window is used to initialize this class at
     * instantiation time and only the child {@code window} will work for
     * {@link #setLayoutInDisplayCutout(boolean)}.
     */
    public DisplayCutoutManager(@Nullable Window parentWindow, @NonNull Window window) {
        mParentWindow = parentWindow;
        mWindow = window;
        View decorView = parentWindowFirstIfSupplied().getDecorView();
        if (ViewCompat.isAttachedToWindow(decorView)) {
            mOnWindowAttachStateChangeListener.onViewAttachedToWindow(decorView);
        } else if (parentWindow != null) {
            throwWhenWindowNeverAttached();
        }
        if (parentWindow != null) {
            window.getDecorView().addOnAttachStateChangeListener(mOnWindowAttachStateChangeListener);
        } else {
            decorView.addOnAttachStateChangeListener(mOnWindowAttachStateChangeListener);
        }
    }

    private Window parentWindowFirstIfSupplied() {
        return mParentWindow != null ? mParentWindow : mWindow;
    }

    @Synthetic void init() {
        if (mInitialized) {
            return;
        }
        mInitialized = true;

        Window window = parentWindowFirstIfSupplied();
        View decorView = window.getDecorView();
        Context context = window.getContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            DisplayCutout dc = decorView.getRootWindowInsets().getDisplayCutout();
            if (dc != null) {
                mIsNotchSupport = true;
                if (OSHelper.isEMUI()) {
                    mIsNotchSupportOnEMUI = true;
                    mIsNotchHidden = DisplayCutoutUtils.isNotchHiddenForEMUI(context);
                } else if (OSHelper.isMIUI()) {
                    mIsNotchSupportOnMIUI = true;
                    mIsNotchHidden = DisplayCutoutUtils.isNotchHiddenForMIUI(context);
                }
                mNotchHeight = dc.getSafeInsetTop();
            }
        } else if (OSHelper.isEMUI()) {
            if (DisplayCutoutUtils.hasNotchInScreenForEMUI(context)) {
                mIsNotchSupport = mIsNotchSupportOnEMUI = true;
                mNotchHeight = DisplayCutoutUtils.getNotchSizeForEMUI(context)[1];
                mIsNotchHidden = DisplayCutoutUtils.isNotchHiddenForEMUI(context);
            }
        } else if (OSHelper.isColorOS()) {
            if (DisplayCutoutUtils.hasNotchInScreenForColorOS(context)) {
                mIsNotchSupport = true;
                mNotchHeight = DisplayCutoutUtils.getNotchSizeForColorOS()[1];
            }
        } else if (OSHelper.isFuntouchOS()) {
            if (DisplayCutoutUtils.hasNotchInScreenForFuntouchOS(context)) {
                mIsNotchSupport = true;
                mNotchHeight = DisplayCutoutUtils.getNotchHeightForFuntouchOS(context);
            }
        } else if (OSHelper.isMIUI()) {
            if (DisplayCutoutUtils.hasNotchInScreenForMIUI()) {
                mIsNotchSupport = mIsNotchSupportOnMIUI = true;
                mNotchHeight = DisplayCutoutUtils.getNotchHeightForMIUI(context);
                mIsNotchHidden = DisplayCutoutUtils.isNotchHiddenForMIUI(context);
            }
        }
    }

    /**
     * Disposes the display cutout manager, removes the window attach state listener
     * and stop observing the notch switch.
     */
    public void dispose() {
        mDisposed = true;
        mWindow.getDecorView().removeOnAttachStateChangeListener(mOnWindowAttachStateChangeListener);
        unobserveNotchSwitch();
    }

    private void throwIfWindowNeverAttached() {
        if (!mInitialized) {
            throwWhenWindowNeverAttached();
        }
    }

    private void throwWhenWindowNeverAttached() {
        Window window = parentWindowFirstIfSupplied();
        throw new IllegalStateException("The window [" + window + "] has never been attached");
    }

    /**
     * Sets whether the window is allowed to extend into the display cutout area on the short edge
     * of the screen.
     *
     * @throws IllegalStateException If the window to set to extend into display cutout area
     *                               or the parent window if supplied has never been attached
     *                               for this class to extract the related screen notch info.
     */
    public void setLayoutInDisplayCutout(boolean in) {
        if (mDisposed) {
            Log.w(TAG, "Attempt to set whether the Window is allowed to extend"
                    + " into display cutout area on the short edge of the screen"
                    + " but the DisplayCutoutManager has already been disposed.");
            return;
        }

        throwIfWindowNeverAttached();
        if (mIsNotchSupport && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            DisplayCutoutUtils.setLayoutInDisplayCutoutSinceP(mWindow, in);
        } else if (mIsNotchSupportOnEMUI) {
            DisplayCutoutUtils.setLayoutInDisplayCutoutForEMUI(mWindow, in);
        } else if (mIsNotchSupportOnMIUI) {
            DisplayCutoutUtils.setLayoutInDisplayCutoutForMIUI(mWindow, in);
        }
    }

    /** If the display cutout exists */
    public boolean isNotchSupport() {
        return mIsNotchSupport;
    }

    /** If the display cutout exists on an EMUI device */
    public boolean isNotchSupportOnEMUI() {
        return mIsNotchSupportOnEMUI;
    }

    /** If the display cutout exists on a MIUI device */
    public boolean isNotchSupportOnMIUI() {
        return mIsNotchSupportOnMIUI;
    }

    /** Whether the switch to hide screen notch in vision is turned on for EMUI or MIUI. */
    public boolean isNotchHidden() {
        return mIsNotchHidden;
    }

    /** Gets the height of the screen notch in pixels */
    public int getNotchHeight() {
        return mNotchHeight;
    }

    /**
     * Adds an {@link OnNotchSwitchListener} for receiving the subsequent visibility changes of
     * the screen notch on EMUI or MIUI.
     */
    public void addOnNotchSwitchListener(@NonNull OnNotchSwitchListener listener) {
        if (mDisposed) {
            Log.w(TAG, "Attempt to add an OnNotchSwitchListener to DisplayCutoutManager which"
                    + " however has already been disposed.");
            return;
        }

        if (mNotchSwitchListeners == null) {
            mNotchSwitchListeners = new ArrayList<>(1);
        } else if (!mNotchSwitchListeners.contains(listener)) {
            return;
        }
        mNotchSwitchListeners.add(listener);
    }

    /**
     * Removes an {@link OnNotchSwitchListener} from the set of listeners listening the visibility
     * changes of the screen notch on EMUI or MIUI.
     */
    public void removeOnNotchSwitchListener(@Nullable OnNotchSwitchListener listener) {
        if (mNotchSwitchListeners != null) {
            mNotchSwitchListeners.remove(listener);
        }
    }

    @Synthetic void observeNotchSwitch(Context context, Handler handler) {
        if (mNotchSwitchObserver == null && (mIsNotchSupportOnEMUI || mIsNotchSupportOnMIUI)) {
            mNotchSwitchObserver = new ScreenNotchSwitchObserver(
                    handler, context, mIsNotchSupportOnEMUI, mIsNotchSupportOnMIUI) {
                @Override
                public void onNotchChange(boolean selfChange, boolean hidden) {
                    if (hidden != mIsNotchHidden) {
                        mIsNotchHidden = hidden;
                        if (mNotchSwitchListeners != null) {
                            for (int i = mNotchSwitchListeners.size() - 1; i >= 0; i--) {
                                mNotchSwitchListeners.get(i).onNotchChange(hidden);
                            }
                        }
                    }
                }
            };
            mNotchSwitchObserver.startObserver();
        }
    }

    @Synthetic void unobserveNotchSwitch() {
        if (mNotchSwitchObserver != null) {
            mNotchSwitchObserver.stopObserver();
            mNotchSwitchObserver = null;
        }
    }

    public interface OnNotchSwitchListener {
        void onNotchChange(boolean hidden);
    }
}
