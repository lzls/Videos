/*
 * Created on 2017/11/12.
 * Copyright © 2017 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
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
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.liuzhenlin.common.Consts;
import com.liuzhenlin.common.R;
import com.liuzhenlin.common.view.OnBackPressedPreImeEventInterceptableView;
import com.liuzhenlin.common.windowhost.FocusObservableWindowHost;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static androidx.core.view.WindowInsetsCompat.Type.statusBars;

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

    public static void setViewVisibilityAndVerify(View view, int visibility) {
        view.setVisibility(visibility);
        switch (visibility) {
            case View.VISIBLE:
                Utils.postOnLayoutValid(view, () -> {
                    ViewGroup.LayoutParams lp = view.getLayoutParams();
                    if (view.getVisibility() == View.VISIBLE
                            && (lp.width != 0 || lp.height != 0)
                            && view.getWidth() == 0 && view.getHeight() == 0) {
                        view.requestLayout();
                    }
                });
                break;
            case View.GONE:
                // TODO: verify the view will be invisible and not take any layout space from its
                //   parent, we can not just check the view size here since changing the visibility
                //   of a view to gone will not change its width and height properties.
                break;
        }
    }

    public static void fixZeroSizedViewCannotKeepFocusedInLayout(@NonNull View view) {
        final boolean zeroWidth = view.getWidth() == 0;
        final boolean zeroHeight = view.getHeight() == 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                && view.getContext().getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.P
                && (zeroWidth || zeroHeight)) {
            // A zero sized view can not keep focused during layout when targetSdk >= P, and
            // this can result in a bug if the view size has not yet been really determined.
            final int minAxisValue = Integer.MIN_VALUE;
            if (zeroWidth) {
                view.setLeft(minAxisValue);
                view.setRight(minAxisValue + 1);
            }
            if (zeroHeight) {
                view.setTop(minAxisValue);
                view.setBottom(minAxisValue + 1);
            }
        }
    }

    private static Method sClearFocusInternalMethod;
    private static boolean sIsClearFocusInternalMethodFetched;

    public static void clearFocusNoRefocusInTouch(@NonNull View view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            // Unsupported on platform versions 16 and 17...
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                && view.getContext().getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.P) {
            view.clearFocus();
            return;
        }

        try {
            if (!sIsClearFocusInternalMethodFetched) {
                sIsClearFocusInternalMethodFetched = true;
                //noinspection SoonBlockedPrivateApi,JavaReflectionMemberAccess
                sClearFocusInternalMethod =
                        View.class.getDeclaredMethod(
                                "clearFocusInternal", View.class, boolean.class, boolean.class);
                sClearFocusInternalMethod.setAccessible(true);
            }
            if (sClearFocusInternalMethod != null) {
                sClearFocusInternalMethod.invoke(view, view, true, !view.isInTouchMode());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void showSoftInputForEditingViewsAccordingly(
            @NonNull FocusObservableWindowHost editingViewsWindowHost,
            @NonNull OnBackPressedPreImeEventInterceptableView... editingViews) {
        final boolean[] hasFocusedView = {false};
        for (OnBackPressedPreImeEventInterceptableView editingView : editingViews) {
            View editView = (View) editingView;
            editView.setFocusableInTouchMode(true);
            if (editView.hasFocus()) {
                hasFocusedView[0] = true;
            }

            ShowSoftInputJob showSoftInputJob =
                    new ShowSoftInputJob(editingViewsWindowHost, editView);
            if (editingViewsWindowHost.hasWindowFocus() && editView.hasFocus()) {
                showSoftInputJob.schedule();
            }
            editingViewsWindowHost.addOnWindowFocusChangedListener(hasFocus -> {
                if (hasFocus && editView.hasFocus()) {
                    showSoftInputJob.schedule();
                } else {
                    showSoftInputJob.cancel();
//                    hideSoftInput(editView, false);
                }
            });
            editView.setOnFocusChangeListener((v, hasFocus) -> {
                hasFocusedView[0] = hasFocus;
                if (hasFocus) {
                    if (editingViewsWindowHost.hasWindowFocus()) {
                        showSoftInputJob.schedule();
                    }
                } else {
                    showSoftInputJob.cancel();
                    v.post(() -> {
                        if (!hasFocusedView[0]) {
                            hideSoftInput(v, false);
                        }
                    });
                }
            });
            editingView.setOnBackPressedPreImeListener(() -> {
                showSoftInputJob.cancel();
                return hideSoftInput(editView, true);
            });
        }
    }

    private static final class ShowSoftInputJob implements Runnable {

        final FocusObservableWindowHost host;
        final View view;

        int retryTimes;
        static final int MAX_SHOW_SOFT_INPUT_RETRY_TIMES = 10;

        boolean pending = false;

        ShowSoftInputJob(FocusObservableWindowHost host, View view) {
            this.host = host;
            this.view = view;
        }

        @Override
        public void run() {
            if (retryTimes++ <= MAX_SHOW_SOFT_INPUT_RETRY_TIMES
                    && host.hasWindowFocus() && view.hasFocus()
                    && !showSoftInput(view, false)) {
                view.post(this);
            }
            pending = false;
        }

        void schedule() {
            retryTimes = 0;
            if (!pending) {
                pending = true;
                view.post(this);
            }
        }

        void cancel() {
            if (pending) {
                view.removeCallbacks(this);
                pending = false;
            }
        }
    }

    public static boolean showSoftInput(@NonNull View view, boolean takeFocus) {
        InputMethodManager imm = (InputMethodManager)
                view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            if (imm.showSoftInput(view, 0)) {
                if (takeFocus && !view.hasFocus()) {
                    view.requestFocus();
                }
                return true;
            }
        }
        return false;
    }

    public static boolean hideSoftInput(@NonNull Window window, boolean clearTargetViewFocus) {
        View focus = window.getCurrentFocus();
        if (focus != null) {
            return hideSoftInput(focus, clearTargetViewFocus);
        }
        return false;
    }

    public static boolean hideSoftInput(@NonNull View view, boolean clearFocusIfViewIsTheTarget) {
        InputMethodManager imm = (InputMethodManager)
                view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            final boolean viewIsInputMethodTarget = imm.isActive(view);
            final boolean softInputHidden = imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            if (softInputHidden
                    && clearFocusIfViewIsTheTarget && viewIsInputMethodTarget
                    && view.isFocused()) {
                view.clearFocus();
            }
            return softInputHidden;
        }
        return false;
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

    /**
     * @return {@code true} if the view is laid-out and not about to do another layout.
     */
    public static boolean isLayoutValid(@NonNull View view) {
        return ViewCompat.isLaidOut(view) && !view.isLayoutRequested();
    }

    public static boolean isLandscapeMode(@NonNull Context context) {
        return context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }

    public static int getStableWindowInsetsTop(@NonNull Window window) {
        return getStableWindowInsetsTop(window.getDecorView());
    }

    public static int getStableWindowInsetsTop(@NonNull View rootView) {
        if (!ViewCompat.isAttachedToWindow(rootView)) {
            throw new IllegalStateException(
                    "root view [" + rootView + "] is not attached to a window");
        }

        WindowInsetsCompat windowInsets = ViewCompat.getRootWindowInsets(rootView);
        if (windowInsets != null) {
            return windowInsets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())
                    .top;
        }
        return SystemBarUtils.getStatusHeight(rootView.getContext());
    }

    public static void insertTopMarginToActionBarIfLayoutUnderStatus(@NonNull View actionbar) {
        if (Consts.SDK_VERSION >= Consts.SDK_VERSION_SUPPORTS_WINDOW_INSETS) {
            ViewCompat.setOnApplyWindowInsetsListener(actionbar, (v, insets) -> {
                InsetsApplier applier = (InsetsApplier)
                        v.getTag(R.id.tag_marginInsetsApplier);
                if (applier == null) {
                    applier = new InsetsApplier(v, insets) {
                        @Override
                        void onApplyInsets(WindowInsetsCompat insets) {
                            insertTopMarginToActionBarIfLayoutUnderStatus(
                                    v, insets.getInsetsIgnoringVisibility(statusBars()).top);
                        }
                    };
                    v.setTag(R.id.tag_marginInsetsApplier, applier);
                }
                // Insets are usually dispatched before View.AttachInfo#mWindowTop is assigned by
                // the top of the current Window in ViewRootImpl#performTraversals(), at which time
                // we may calculate out wrong coordinates of the view on screen, so check to see
                // if this is the case where a new layout is approaching and we therefore need
                // postpone applying them.
                if (isLayoutValid(actionbar)) {
                    applier.onApplyInsets(insets);
                    applier.remove();
                } else {
                    applier.insets = insets;
                    applier.post();
                }
                return insets;
            });
        } else {
            Utils.postOnLayoutValid(actionbar, () -> {
                int statusHeight = SystemBarUtils.getStatusHeight(actionbar.getContext());
                insertTopMarginToActionBarIfLayoutUnderStatus(actionbar, statusHeight);
            });
        }
    }

    @Synthetic static void insertTopMarginToActionBarIfLayoutUnderStatus(View actionbar, int statusHeight) {
        ViewGroup.LayoutParams lp = actionbar.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            Integer oldInsetTop = (Integer) actionbar.getTag(R.id.tag_marginInsetTop);
            if (oldInsetTop == null) {
                oldInsetTop = 0;
            }
            int insetTop = isLayoutUnderStatusBar(actionbar) ? statusHeight : 0;
            if (insetTop != oldInsetTop) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                mlp.topMargin = mlp.topMargin - oldInsetTop + insetTop;
                actionbar.setTag(R.id.tag_marginInsetTop, insetTop);
                actionbar.setLayoutParams(mlp);
            }
        }
    }

    public static void insertTopPaddingToActionBarIfLayoutUnderStatus(@NonNull View actionbar) {
        if (Consts.SDK_VERSION >= Consts.SDK_VERSION_SUPPORTS_WINDOW_INSETS) {
            ViewCompat.setOnApplyWindowInsetsListener(actionbar, (v, insets) -> {
                InsetsApplier applier = (InsetsApplier) v.getTag(R.id.tag_paddingInsetsApplier);
                if (applier == null) {
                    applier = new InsetsApplier(v, insets) {
                        @Override
                        void onApplyInsets(WindowInsetsCompat insets) {
                            insertTopPaddingToActionBarIfLayoutUnderStatus(
                                    v, insets.getInsetsIgnoringVisibility(statusBars()).top);
                        }
                    };
                    v.setTag(R.id.tag_paddingInsetsApplier, applier);
                }
                // Insets are usually dispatched before View.AttachInfo#mWindowTop is assigned by
                // the top of the current Window in ViewRootImpl#performTraversals(), at which time
                // we may calculate out wrong coordinates of the view on screen, so check to see
                // if this is the case where a new layout is approaching and we therefore need
                // postpone applying them.
                if (isLayoutValid(actionbar)) {
                    applier.onApplyInsets(insets);
                    applier.remove();
                } else {
                    applier.insets = insets;
                    applier.post();
                }
                return insets;
            });
        } else {
            Utils.postOnLayoutValid(actionbar, () -> {
                int statusHeight = SystemBarUtils.getStatusHeight(actionbar.getContext());
                insertTopPaddingToActionBarIfLayoutUnderStatus(actionbar, statusHeight);
            });
        }
    }

    @Synthetic static void insertTopPaddingToActionBarIfLayoutUnderStatus(View actionbar, int statusHeight) {
        Integer oldInsetTop = (Integer) actionbar.getTag(R.id.tag_paddingInsetTop);
        if (oldInsetTop == null) {
            oldInsetTop = 0;
        }
        int insetTop = isLayoutUnderStatusBar(actionbar) ? statusHeight : 0;
        if (insetTop != oldInsetTop) {
            ViewGroup.LayoutParams lp = actionbar.getLayoutParams();
            switch (lp.height) {
                case ViewGroup.LayoutParams.WRAP_CONTENT:
                case ViewGroup.LayoutParams.MATCH_PARENT:
                    break;
                default:
                    lp.height = lp.height - oldInsetTop + insetTop;
            }
            actionbar.setTag(R.id.tag_paddingInsetTop, insetTop);
            actionbar.setPadding(
                    actionbar.getPaddingLeft(),
                    actionbar.getPaddingTop() - oldInsetTop + insetTop,
                    actionbar.getPaddingRight(),
                    actionbar.getPaddingBottom());
        }
    }

    // XXX: make this generic enough to be public
    private static boolean isLayoutUnderStatusBar(View actionbar) {
        int[] location = new int[2];
        actionbar.getLocationOnScreen(location);
        Integer marginInsetTop = (Integer) actionbar.getTag(R.id.tag_marginInsetTop);
        if (marginInsetTop == null) {
            marginInsetTop = 0;
        }
        return location[1] - marginInsetTop < 10;
    }

    private static abstract class InsetsApplier implements ViewTreeObserver.OnGlobalLayoutListener {
        WindowInsetsCompat insets;
        final View view;
        boolean scheduled;

        InsetsApplier(View view, WindowInsetsCompat insets) {
            this.view = view;
            this.insets = insets;
        }

        @Override
        public final void onGlobalLayout() {
            remove();
            onApplyInsets(insets);
        }

        abstract void onApplyInsets(WindowInsetsCompat insets);

        final void post() {
            if (!scheduled) {
                scheduled = true;
                view.getViewTreeObserver().addOnGlobalLayoutListener(this);
            }
        }

        final void remove() {
            if (scheduled) {
                scheduled = false;
                view.getViewTreeObserver().removeGlobalOnLayoutListener(this);
            }
        }
    }
}
