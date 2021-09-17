/*
 * Created on 2017/11/12.
 * Copyright © 2017 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.liuzhenlin.common.R;

import java.lang.reflect.Field;

/**
 * @author 刘振林
 */
public class UiUtils {
    private UiUtils() {
    }

    public static void setWindowAlpha(
            @NonNull Window window, @FloatRange(from = 0.0, to = 1.0) float alpha) {
        WindowManager.LayoutParams wmlp = window.getAttributes();
        wmlp.alpha = alpha;
        window.setAttributes(wmlp);
    }

    public static void requestViewMargins(@NonNull View view, int left, int top, int right, int bottom) {
        if (view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            mlp.setMargins(left, top, right, bottom);
            view.setLayoutParams(mlp);
        }
    }

    public static void setViewMargins(@NonNull View view, int left, int top, int right, int bottom) {
        if (view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            mlp.setMargins(left, top, right, bottom);
        }
    }

    public static void requestRuleForRelativeLayoutChild(@NonNull View view, int verb, int subject) {
        if (view.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) view.getLayoutParams();
            lp.addRule(verb, subject);
            view.setLayoutParams(lp);
        }
    }

    public static void setRuleForRelativeLayoutChild(@NonNull View view, int verb, int subject) {
        if (view.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) view.getLayoutParams();
            lp.addRule(verb, subject);
        }
    }

    public static void showSoftInput(@NonNull View view) {
        InputMethodManager imm = (InputMethodManager)
                view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && (view.hasFocus() || view.requestFocus())) {
            imm.showSoftInput(view, 0);
        }
    }

    public static void hideSoftInput(@NonNull Window window) {
        View focus = window.getCurrentFocus();
        if (focus != null) {
            hideSoftInput(focus);
        }
    }

    public static void hideSoftInput(@NonNull View focus) {
        if (focus.hasFocus()) {
            InputMethodManager imm = (InputMethodManager)
                    focus.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && imm.hideSoftInputFromWindow(focus.getWindowToken(), 0)) {
                focus.clearFocus();
            }
        }
    }

    public static boolean isSoftInputShown(@NonNull Window window) {
        return isSoftInputShownInternal(window.getDecorView().getRootView());
    }

    public static boolean isSoftInputShown(@NonNull View view) {
        return ViewCompat.isAttachedToWindow(view)
                && isSoftInputShownInternal(view.getRootView());
    }

    private static boolean isSoftInputShownInternal(@NonNull View rootView) {
        Rect r = new Rect();
        rootView.getWindowVisibleDisplayFrame(r);

        final int heightDiff = rootView.getBottom() - r.bottom;
        final float assumedKeyboardHeight = 50f * rootView.getResources().getDisplayMetrics().density;
        return heightDiff >= assumedKeyboardHeight;
    }

    public static void setTabItemsEnabled(@NonNull TabLayout tabLayout, boolean enabled) {
        LinearLayout tabStrip = (LinearLayout) tabLayout.getChildAt(0);
        final int selection = tabLayout.getSelectedTabPosition();
        for (int i = tabStrip.getChildCount() - 1; i >= 0; i--) {
            if (i != selection) {
                View tabView = tabStrip.getChildAt(i);
                if (tabView != null) {
                    tabView.setEnabled(enabled);
                }
            }
        }
    }

    public static void showUserCancelableSnackbar(
            @NonNull View view, @StringRes int resId, @Snackbar.Duration int duration) {
        showUserCancelableSnackbar(view, resId, false, duration);
    }

    public static void showUserCancelableSnackbar(
            @NonNull View view,
            @StringRes int resId,
            boolean shownTextSelectable,
            @Snackbar.Duration int duration) {
        showUserCancelableSnackbar(view, view.getResources().getText(resId), shownTextSelectable, duration);
    }

    public static void showUserCancelableSnackbar(
            @NonNull View view, @NonNull CharSequence text, @Snackbar.Duration int duration) {
        showUserCancelableSnackbar(view, text, false, duration);
    }

    // Statically caching the Snackbar to be shown here is 1) in order to fix when a Snackbar is
    // already being displayed, creating a new Snackbar inside a method and calling its show() will
    // cause the displaying Snackbar to be hidden and that the Snackbar to be shown may have no
    // opportunity.
    // The specific reason is: SnackbarManager is a singleton class. Snackbar.show() calls the show()
    // method of SnackbarManager on the way, and uses a weak reference to refer to the managerCallback
    // in Snackbar for the SnackbarRecord instance created in the show() method, that is, to
    // indirectly and weakly hold Snackbar. When we call Snackbar.make().show(), if there is already
    // a Snackbar currently being displayed, it will be cancelled first. This process is a series
    // of handler.sendMessage() steps, and finally when the onDismissed() callback method of
    // SnackbarManager is reached, the next SnackBar will be displayed. Since we usually directly
    // write Snackbar.make().show() in a method, we will not hold any reference to the Snakebar
    // object after the method ends, it will only be held by a weak reference then. At this time, the
    // Snackbar referred from the nextSnackbar obtained in the onDismissed() callback method of the
    // SnackbarManager may have been recycled by the gc... So it won’t show up. That’s pretty nasty.
    //
    // 2) By the way, this can also play a role in reusing the Snackbar when it is already showing
    //    and if we want.
    @Synthetic static Snackbar sCurrentSnackbar;
    private static final boolean REUSE_SNACKBAR = false;

    public static void showUserCancelableSnackbar(
            @NonNull View view,
            @NonNull CharSequence text,
            boolean shownTextSelectable,
            @Snackbar.Duration int duration) {
        Snackbar snackbar;
        if (sCurrentSnackbar == null || !REUSE_SNACKBAR) {
            snackbar = Snackbar.make(view, text, duration);
            sCurrentSnackbar = snackbar;

            TextView snackbarText = snackbar.getView().findViewById(R.id.snackbar_text);
            snackbarText.setMaxLines(Integer.MAX_VALUE);
            snackbarText.setTextIsSelectable(shownTextSelectable);

            snackbar.setAction(R.string.undo, v -> snackbar.dismiss());
            snackbar.addCallback(new Snackbar.Callback() {
                @Override
                public void onDismissed(Snackbar transientBottomBar, int event) {
                    super.onDismissed(transientBottomBar, event);
                    if (sCurrentSnackbar == transientBottomBar) {
                        sCurrentSnackbar = null;
                    }
                }
            });
        } else {
            snackbar = sCurrentSnackbar;
            snackbar.setText(text);
            snackbar.setDuration(duration);
        }
        snackbar.show();
    }

    @Nullable
    public static TextView getAlertDialogTitle(@NonNull android.app.AlertDialog dialog) {
        try {
            //noinspection JavaReflectionMemberAccess
            Field mAlert = android.app.AlertDialog.class.getDeclaredField("mAlert");
            mAlert.setAccessible(true);

            Object alertController = mAlert.get(dialog);
            //noinspection ConstantConditions
            Field mTitleView = alertController.getClass().getDeclaredField("mTitleView");
            mTitleView.setAccessible(true);

            return (TextView) mTitleView.get(alertController);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Nullable
    public static TextView getAlertDialogTitle(@NonNull androidx.appcompat.app.AlertDialog dialog) {
        try {
            Field mAlert = androidx.appcompat.app.AlertDialog.class.getDeclaredField("mAlert");
            mAlert.setAccessible(true);

            Object alertController = mAlert.get(dialog);
            //noinspection ConstantConditions
            Field mTitleView = alertController.getClass().getDeclaredField("mTitleView");
            mTitleView.setAccessible(true);

            return (TextView) mTitleView.get(alertController);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
