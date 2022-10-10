package com.liuzhenlin.swipeback;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.widget.FrameLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.FloatRange;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.customview.widget.ViewDragHelper;
import androidx.fragment.app.Fragment;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class SwipeBackLayout extends FrameLayout {

    @Nullable
    /*synthetic*/ ISwipeBackActivity mActivity;

    @Nullable
    /*synthetic*/ ISwipeBackFragment mFragment;

    /*package*/ ViewDragHelper mDragHelper;

    public static final int EDGE_LEFT = ViewDragHelper.EDGE_LEFT;
    public static final int EDGE_RIGHT = ViewDragHelper.EDGE_RIGHT;
    public static final int EDGE_START = EDGE_RIGHT << 3;
    public static final int EDGE_END = EDGE_RIGHT << 4;

    private static final int ABSOLUTE_EDGE_MASK = EDGE_LEFT | EDGE_RIGHT;
    private static final int EDGE_MASK = ABSOLUTE_EDGE_MASK | EDGE_START | EDGE_END;

    @IntDef(flag = true, value = {EDGE_LEFT, EDGE_RIGHT, EDGE_START, EDGE_END})
    @Retention(RetentionPolicy.SOURCE)
    /*package*/ @interface Edge {
    }

    @IntDef(flag = true, value = {EDGE_LEFT, EDGE_RIGHT})
    @Retention(RetentionPolicy.SOURCE)
    /*package*/ @interface AbsoluteEdge {
    }

    @AbsoluteEdge
    /*synthetic*/ int mDraggedEdge = NO_DRAGGED_EDGE;
    private static final int NO_DRAGGED_EDGE = -1;

    public static final int STATE_IDLE = ViewDragHelper.STATE_IDLE;
    public static final int STATE_DRAGGING = ViewDragHelper.STATE_DRAGGING;
    public static final int STATE_SETTLING = ViewDragHelper.STATE_SETTLING;

    @IntDef({STATE_IDLE, STATE_DRAGGING, STATE_SETTLING})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ScrollState {
    }

    /**
     * @see #getScrollThreshold()
     * @see #setScrollThreshold(float)
     */
    @FloatRange(from = 0.0, to = 1.0, fromInclusive = false, toInclusive = false)
    /*synthetic*/ float mScrollThreshold = DEFAULT_SCROLL_THRESHOLD;
    /** Default threshold of scroll */
    public static final float DEFAULT_SCROLL_THRESHOLD = 1f / 3f;

    /**
     * Minimum velocity that will be detected as a fling, as measured in dips per second. We will
     * close the activity or pop up the fragment from the back stack that holds it when the speed
     * at which user lifts his/her finger over this value.
     */
    private static final int MIN_FLING_VELOCITY = 500; // dp/s

    /** The ratio of the current scrolling distance to the maximum scrollable distance */
    @FloatRange(from = 0.0, to = 1.0)
    /*synthetic*/ float mScrollPercent;

    /*synthetic*/ int mViewFlags = FLAG_ENABLED | FLAG_PREVIOUS_CONTENT_SCROLLABLE;

    /**
     * @see #isGestureEnabled()
     * @see #setGestureEnabled(boolean)
     */
    private static final int FLAG_ENABLED = EDGE_END << 1;

    /**
     * @see #isPreviousContentScrollable()
     * @see #setPreviousContentScrollable(boolean)
     */
    private static final int FLAG_PREVIOUS_CONTENT_SCROLLABLE = EDGE_END << 2;

    private static final int FLAG_WINDOW_IS_TRANSLUCENT = EDGE_END << 3;

    /**
     * Bit for {@link #mViewFlags}: {@code true} when the application is willing to support RTL
     * (right to left). All activities will inherit this value.
     * Set from the {@link android.R.attr#supportsRtl} attribute in the activity's manifest.
     * Default value is false (no support for RTL).
     */
    private static final int FLAG_SUPPORTS_RTL = EDGE_END << 4;

    private static final int FLAG_TRACKING_EDGES_RESOLVED = EDGE_END << 5;
    private static final int FLAG_TRACKING_LEFT_EDGE_SPECIFIED = EDGE_END << 6;
    private static final int FLAG_TRACKING_RIGHT_EDGE_SPECIFIED = EDGE_END << 7;
    private static final int FLAG_TRACKING_START_EDGE_SPECIFIED = EDGE_END << 8;
    private static final int FLAG_TRACKING_END_EDGE_SPECIFIED = EDGE_END << 9;

    private static final int FLAG_EDGE_SHADOWS_RESOLVED = EDGE_END << 10;
    private static final int FLAG_LEFT_EDGE_SHADOW_SPECIFIED = EDGE_END << 11;
    private static final int FLAG_RIGHT_EDGE_SHADOW_SPECIFIED = EDGE_END << 12;
    private static final int FLAG_START_EDGE_SHADOW_SPECIFIED = EDGE_END << 13;
    private static final int FLAG_END_EDGE_SHADOW_SPECIFIED = EDGE_END << 14;

    /**
     * The set of listeners to be sent events through
     *
     * @see SwipeListener
     */
    @Nullable
    /*synthetic*/ List<SwipeListener> mSwipeListeners;

    /** The content view that will be moved by user gestures */
    /*synthetic*/ View mContentView;

    /**
     * The rectangle used to measure the bounds of {@link #mContentView} (relative to current view)
     */
    private final Rect mTempRect = new Rect();

    /** The left position of {@link #mContentView} */
    /*synthetic*/ int mContentLeft;

    /** The shadow to be shown while the left edge of {@link #mContentView} is being dragged */
    @Nullable
    /*synthetic*/ Drawable mShadowLeft;
    /** The shadow to be shown while the right edge of {@link #mContentView} is being dragged */
    @Nullable
    /*synthetic*/ Drawable mShadowRight;

    private Drawable mShadowStart;
    private Drawable mShadowEnd;

    /**
     * @see #getScrimColor()
     * @see #setScrimColor(int)
     */
    @ColorInt
    private int mScrimColor = DEFAULT_SCRIM_COLOR;
    @ColorInt
    public static final int DEFAULT_SCRIM_COLOR = 0x80000000;

    @FloatRange(from = 0.0, to = 1.0)
    /*synthetic*/ float mScrimOpacity;

    private static final int FULL_ALPHA = 255;

    protected final int mTouchSlop;
    private static Field sDraggerTouchSlopField;

    static {
        try {
            sDraggerTouchSlopField = ViewDragHelper.class.getDeclaredField("mTouchSlop");
            sDraggerTouchSlopField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    public SwipeBackLayout(Context context) {
        this(context, null);
    }

    public SwipeBackLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwipeBackLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        ApplicationInfo ai = context.getApplicationInfo();
        if (ai.targetSdkVersion >= Build.VERSION_CODES.JELLY_BEAN_MR1
                && (ai.flags & ApplicationInfo.FLAG_SUPPORTS_RTL) != 0) {
            mViewFlags |= FLAG_SUPPORTS_RTL;
        }
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        getDragHelper().setMinVelocity(MIN_FLING_VELOCITY * getResources().getDisplayMetrics().density);
        setEnabledEdges(EDGE_START);
        setEdgeShadow(R.drawable.shadow_left, EDGE_LEFT);
        setEdgeShadow(R.drawable.shadow_right, EDGE_RIGHT);
    }

    /*package*/ ViewDragHelper getDragHelper() {
        if (mDragHelper == null) {
            mDragHelper = ViewDragHelper.create(this, new ViewDragCallback());
        }
        return mDragHelper;
    }

    /**
     * Attach this layout to the given 'activity'
     */
    public void attachToActivity(ISwipeBackActivity activity) {
        Activity host = (Activity) activity;
        Window window = host.getWindow();
        ViewGroup decor = (ViewGroup) window.getDecorView();

        mActivity = activity;
        if (Utils.isWindowTranslucentOrFloatingTheme(window)) {
            mViewFlags |= FLAG_WINDOW_IS_TRANSLUCENT;
        }
        mContentView = decor.getChildAt(0);
        mContentView.setBackgroundResource(
                Utils.getThemeAttrRes(host, android.R.attr.windowBackground));

        decor.removeViewAt(0);
        addView(mContentView);
        decor.addView(this, 0);
    }

    /**
     * Attach the view created in fragment's
     * {@link Fragment#onCreateView(LayoutInflater, ViewGroup, Bundle)} method to this layout.
     */
    public void attachFragmentView(ISwipeBackFragment fragment, View view) {
        mFragment = fragment;
        mContentView = view;
        addView(view);
    }

    /**
     * @return whether swipe-back gesture is enabled or not
     * @see #setGestureEnabled(boolean)
     */
    public boolean isGestureEnabled() {
        return (mViewFlags & FLAG_ENABLED) != 0;
    }

    /**
     * Enables swipe-back gesture or not
     *
     * @see #isGestureEnabled()
     */
    public void setGestureEnabled(boolean enable) {
        if (enable) {
            mViewFlags |= FLAG_ENABLED;
        } else {
            mViewFlags &= ~FLAG_ENABLED;
        }
    }

    /**
     * @return the edges for which edge tracking is enabled
     * @see #setEnabledEdges(int)
     */
    @Edge
    public int getEnabledEdges() {
        if ((mViewFlags & FLAG_TRACKING_EDGES_RESOLVED) == 0) {
            if ((mViewFlags & FLAG_SUPPORTS_RTL) == 0) {
                resolveTrackingEdges(ViewCompat.LAYOUT_DIRECTION_LTR);
            } else {
                if (((FLAG_TRACKING_START_EDGE_SPECIFIED | FLAG_TRACKING_END_EDGE_SPECIFIED)
                        & mViewFlags) == 0) {
                    int edges = 0;
                    if ((mViewFlags & FLAG_TRACKING_LEFT_EDGE_SPECIFIED) != 0) {
                        edges |= EDGE_LEFT;
                    }
                    if ((mViewFlags & FLAG_TRACKING_RIGHT_EDGE_SPECIFIED) != 0) {
                        edges |= EDGE_RIGHT;
                    }
                    return edges;
                }
                //noinspection StatementWithEmptyBody
                if (!resolveTrackingEdgesIfDirectionResolved()) {
                    // Can not return specific edges which are being watched when layout direction
                    // is not resolved.
                }
            }
        }
        return mViewFlags & EDGE_MASK;
    }

    private boolean resolveTrackingEdgesIfDirectionResolved() {
        if (Utils.isLayoutDirectionResolved(this)) {
            resolveTrackingEdges(ViewCompat.getLayoutDirection(this));
            return true;
        }
        return false;
    }

    private void resolveTrackingEdges(int layoutDirection) {
        if ((mViewFlags & FLAG_TRACKING_EDGES_RESOLVED) != 0) {
            return;
        }

        if ((mViewFlags & FLAG_SUPPORTS_RTL) == 0) {
            if ((mViewFlags & FLAG_TRACKING_LEFT_EDGE_SPECIFIED) != 0
                    || (mViewFlags & FLAG_TRACKING_START_EDGE_SPECIFIED) != 0) {
                mViewFlags |= EDGE_LEFT;
            } else {
                mViewFlags &= ~EDGE_LEFT;
            }
            if ((mViewFlags & FLAG_TRACKING_RIGHT_EDGE_SPECIFIED) != 0
                    || (mViewFlags & FLAG_TRACKING_END_EDGE_SPECIFIED) != 0) {
                mViewFlags |= EDGE_RIGHT;
            } else {
                mViewFlags &= ~EDGE_RIGHT;
            }
        } else {
            final boolean ldrtl = layoutDirection == ViewCompat.LAYOUT_DIRECTION_RTL;
            final int startTrackingEdge = ldrtl ? EDGE_RIGHT : EDGE_LEFT;
            final int endTrackingEdge = ldrtl ? EDGE_LEFT : EDGE_RIGHT;
            if ((mViewFlags & FLAG_TRACKING_START_EDGE_SPECIFIED) != 0) {
                mViewFlags |= startTrackingEdge;
            } else {
                if (ldrtl && (mViewFlags & FLAG_TRACKING_RIGHT_EDGE_SPECIFIED) != 0
                        || !ldrtl && (mViewFlags & FLAG_TRACKING_LEFT_EDGE_SPECIFIED) != 0) {
                    mViewFlags |= startTrackingEdge;
                } else {
                    mViewFlags &= ~startTrackingEdge;
                }
            }
            if ((mViewFlags & FLAG_TRACKING_END_EDGE_SPECIFIED) != 0) {
                mViewFlags |= endTrackingEdge;
            } else {
                if (ldrtl && (mViewFlags & FLAG_TRACKING_LEFT_EDGE_SPECIFIED) != 0
                        || !ldrtl && (mViewFlags & FLAG_TRACKING_RIGHT_EDGE_SPECIFIED) != 0) {
                    mViewFlags |= endTrackingEdge;
                } else {
                    mViewFlags &= ~endTrackingEdge;
                }
            }
        }

        // Can not access mDragHelper directly here because this method might be called during
        // super constructor method invocation, at which point mDragHelper will not have been
        // initialized in this class constructor.
        getDragHelper().setEdgeTrackingEnabled(mViewFlags & ABSOLUTE_EDGE_MASK);

        mViewFlags |= FLAG_TRACKING_EDGES_RESOLVED;
    }

    /**
     * Enable edge tracking for the selected edges of the content view.
     * The callback's {@link ViewDragCallback#onEdgeTouched(int, int)} and
     * {@link ViewDragCallback#onEdgeDragStarted(int, int)} methods will only be invoked
     * for edges for which edge tracking has been enabled.
     *
     * @param edgeFlags Combination of edge flags describing the edges to watch
     * @see #getEnabledEdges()
     */
    public void setEnabledEdges(@Edge int edgeFlags) {
        mViewFlags = (mViewFlags & ~EDGE_MASK) | (edgeFlags & EDGE_MASK);
        if ((edgeFlags & EDGE_LEFT) != 0) {
            mViewFlags |= FLAG_TRACKING_LEFT_EDGE_SPECIFIED;
        } else {
            mViewFlags &= ~FLAG_TRACKING_LEFT_EDGE_SPECIFIED;
        }
        if ((edgeFlags & EDGE_RIGHT) != 0) {
            mViewFlags |= FLAG_TRACKING_RIGHT_EDGE_SPECIFIED;
        } else {
            mViewFlags &= ~FLAG_TRACKING_RIGHT_EDGE_SPECIFIED;
        }
        if ((edgeFlags & EDGE_START) != 0) {
            mViewFlags |= FLAG_TRACKING_START_EDGE_SPECIFIED;
        } else {
            mViewFlags &= ~FLAG_TRACKING_START_EDGE_SPECIFIED;
        }
        if ((edgeFlags & EDGE_END) != 0) {
            mViewFlags |= FLAG_TRACKING_END_EDGE_SPECIFIED;
        } else {
            mViewFlags &= ~FLAG_TRACKING_END_EDGE_SPECIFIED;
        }
        mViewFlags &= ~FLAG_TRACKING_EDGES_RESOLVED;
        resolveTrackingEdgesIfDirectionResolved();
    }

    /**
     * @see ViewDragHelper#getEdgeSize()
     */
    public int getEdgeSize() {
        return mDragHelper.getEdgeSize();
    }

    /**
     * @return The sensitivity of the dragger between 0 and 1.
     *         Smaller values are less sensitive. 1.0f is normal.
     */
    public float getSensitivity() {
        if (sDraggerTouchSlopField != null) {
            try {
                return (float) mTouchSlop / sDraggerTouchSlopField.getInt(mDragHelper);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return 1.0f;
    }

    /**
     * Sets the sensitivity for the dragger.
     *
     * @param sensitivity value between 0 and 1, the final value for touchSlop is
     *                    {@code ViewConfiguration.get(context).getScaledTouchSlop * (1 / sensitivity)}
     */
    public void setSensitivity(float sensitivity) {
        if (sDraggerTouchSlopField != null) {
            sensitivity = Math.max(0f, Math.min(1.0f, sensitivity));
            try {
                sDraggerTouchSlopField.setInt(mDragHelper,
                        Utils.roundFloat(mTouchSlop * (1.0f / sensitivity)));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * @return the threshold of scroll
     * @see #setScrollThreshold(float)
     */
    @FloatRange(from = 0.0, to = 1.0, fromInclusive = false, toInclusive = false)
    public float getScrollThreshold() {
        return mScrollThreshold;
    }

    /**
     * Threshold of scroll, we will close the activity or pop up the fragment from the back stack
     * that holds it when the scroll percentage over this value after the content view released.
     *
     * @see ViewDragCallback#onViewReleased(View, float, float)
     * @see #getScrollThreshold()
     */
    public void setScrollThreshold(
            @FloatRange(from = 0.0, to = 1.0, fromInclusive = false, toInclusive = false)
                    float threshold) {
        if (threshold >= 1 || threshold <= 0) {
            throw new IllegalArgumentException(
                    "Threshold value should be between 0 and 1.0, but your value is " + threshold);
        }
        mScrollThreshold = threshold;
    }

    /**
     * @return the color used to dim the previous content view while the current one is open
     * @see #setScrimColor(int)
     */
    @ColorInt
    public int getScrimColor() {
        return mScrimColor;
    }

    /**
     * Sets a color to use for the scrim that obscures previous content view
     * while the current one is open.
     *
     * @param color Color to use in 0xAARRGGBB format.
     * @see #getScrimColor()
     */
    public void setScrimColor(@ColorInt int color) {
        if (mScrimColor != color) {
            mScrimColor = color;
            if (mScrollPercent > 0 && mDragHelper.getViewDragState() != STATE_SETTLING) {
                // redraw scrim
                invalidate();
            }
        }
    }

    /**
     * Sets a drawable used for edge shadows.
     *
     * @param resId     Resource of drawable to use
     * @param edgeFlags Edge flags describing the edges to set
     * @see #setEdgeShadow(Drawable, int)
     */
    public void setEdgeShadow(@DrawableRes int resId, @Edge int edgeFlags) {
        setEdgeShadow(ContextCompat.getDrawable(getContext(), resId), edgeFlags);
    }

    /**
     * Sets a drawable used for edge shadows.
     *
     * @param shadow    Drawable to use
     * @param edgeFlags Edge flags describing the edges to set
     * @see #setEdgeShadow(int, int)
     */
    public void setEdgeShadow(@Nullable Drawable shadow, @Edge int edgeFlags) {
        if ((edgeFlags & EDGE_LEFT) != 0) {
            mViewFlags |= FLAG_LEFT_EDGE_SHADOW_SPECIFIED;
            mShadowLeft = shadow;
        }
        if ((edgeFlags & EDGE_RIGHT) != 0) {
            mViewFlags |= FLAG_RIGHT_EDGE_SHADOW_SPECIFIED;
            mShadowRight = shadow;
        }
        if ((edgeFlags & EDGE_START) != 0) {
            mViewFlags |= FLAG_START_EDGE_SHADOW_SPECIFIED;
            mShadowStart = shadow;
        }
        if ((edgeFlags & EDGE_END) != 0) {
            mViewFlags |= FLAG_END_EDGE_SHADOW_SPECIFIED;
            mShadowEnd = shadow;
        }
        mViewFlags &= ~FLAG_EDGE_SHADOWS_RESOLVED;
        resolveEdgeShadowsIfDirectionResolved();
    }

    @SuppressWarnings("UnusedReturnValue")
    private boolean resolveEdgeShadowsIfDirectionResolved() {
        if (Utils.isLayoutDirectionResolved(this)) {
            resolveEdgeShadows(ViewCompat.getLayoutDirection(this));
            return true;
        }
        return false;
    }

    private void resolveEdgeShadows(int layoutDirection) {
        if ((mViewFlags & FLAG_EDGE_SHADOWS_RESOLVED) != 0) {
            return;
        }

        if ((mViewFlags & FLAG_SUPPORTS_RTL) == 0) {
            if ((mViewFlags & FLAG_LEFT_EDGE_SHADOW_SPECIFIED) == 0) {
                mShadowLeft = mShadowStart;
            }
            if ((mViewFlags & FLAG_RIGHT_EDGE_SHADOW_SPECIFIED) == 0) {
                mShadowRight = mShadowEnd;
            }
        } else {
            final boolean ldrtl = layoutDirection == ViewCompat.LAYOUT_DIRECTION_RTL;
            if ((mViewFlags & FLAG_START_EDGE_SHADOW_SPECIFIED) != 0) {
                if (ldrtl) {
                    mShadowRight = mShadowStart;
                } else {
                    mShadowLeft = mShadowStart;
                }
            }
            if ((mViewFlags & FLAG_END_EDGE_SHADOW_SPECIFIED) != 0) {
                if (ldrtl) {
                    mShadowLeft = mShadowEnd;
                } else {
                    mShadowRight = mShadowEnd;
                }
            }
        }

        if (mScrollPercent > 0 && mDragHelper.getViewDragState() != STATE_SETTLING) {
            // redraw edge shadows
            invalidate();
        }

        mViewFlags |= FLAG_EDGE_SHADOWS_RESOLVED;
    }

    /**
     * @return whether to scroll the previous view (the content view of the last activity or fragment)
     *         at the same time as the current layout scrolls.
     * @see #setPreviousContentScrollable(boolean)
     */
    public boolean isPreviousContentScrollable() {
        return (mViewFlags & FLAG_PREVIOUS_CONTENT_SCROLLABLE) != 0;
    }

    /**
     * When true, we moves the previous content view as the current layout scrolls.
     *
     * @see #isPreviousContentScrollable()
     */
    public void setPreviousContentScrollable(boolean scrollable) {
        if (scrollable) {
            mViewFlags |= FLAG_PREVIOUS_CONTENT_SCROLLABLE;
        } else {
            mViewFlags &= ~FLAG_PREVIOUS_CONTENT_SCROLLABLE;
        }
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        mViewFlags &= ~(FLAG_TRACKING_EDGES_RESOLVED | FLAG_EDGE_SHADOWS_RESOLVED);
        resolveRtlProperties(layoutDirection);
    }

    private void resolveRtlProperties(int layoutDirection) {
        resolveTrackingEdges(layoutDirection);
        resolveEdgeShadows(layoutDirection);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            // Since onRtlPropertiesChanged() was not defined on JELLY_BEAN and lower version SDKs,
            // call resolveRtlProperties() manually instead.
            resolveRtlProperties(ViewCompat.LAYOUT_DIRECTION_LTR);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (mContentView != null) {
            mContentView.layout(
                    mContentLeft, top, mContentLeft + mContentView.getMeasuredWidth(), bottom);
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        final boolean issued = super.drawChild(canvas, child, drawingTime);

        if (child == mContentView && mScrollPercent > 0
                && mDragHelper.getViewDragState() != STATE_IDLE) {
            drawShadow(canvas, child);
            drawScrim(canvas, child);
        }

        return issued;
    }

    private void drawShadow(Canvas canvas, View child) {
        child.getHitRect(mTempRect);

        switch (mDraggedEdge) {
            case EDGE_LEFT:
                if (mShadowLeft != null) {
                    mShadowLeft.setBounds(
                            mTempRect.left - mShadowLeft.getIntrinsicWidth(), mTempRect.top,
                            mTempRect.left, mTempRect.bottom);
                    mShadowLeft.setAlpha(Utils.roundFloat(mScrimOpacity * (float) FULL_ALPHA));
                    mShadowLeft.draw(canvas);
                }
                break;
            case EDGE_RIGHT:
                if (mShadowRight != null) {
                    mShadowRight.setBounds(mTempRect.right, mTempRect.top,
                            mTempRect.right + mShadowRight.getIntrinsicWidth(), mTempRect.bottom);
                    mShadowRight.setAlpha(Utils.roundFloat(mScrimOpacity * (float) FULL_ALPHA));
                    mShadowRight.draw(canvas);
                }
                break;
        }
    }

    private void drawScrim(Canvas canvas, View child) {
        canvas.save();
        switch (mDraggedEdge) {
            case EDGE_LEFT:
                canvas.clipRect(0, 0, child.getLeft(), getHeight());
                break;
            case EDGE_RIGHT:
                canvas.clipRect(child.getRight(), 0, getRight(), getHeight());
                break;
        }
        canvas.drawColor(Utils.dimColor(mScrimColor, 1 - mScrimOpacity));
        canvas.restore();
    }

    @Override
    public void computeScroll() {
        if (mDragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if ((mViewFlags & FLAG_ENABLED) == 0) {
            return false;
        }
        try {
            if (mDragHelper.shouldInterceptTouchEvent(event)) {
                ViewParent parent = getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                }
                return true;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            //
        }
        return false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if ((mViewFlags & FLAG_ENABLED) == 0) {
            return false;
        }
        mDragHelper.processTouchEvent(event);
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // This view will never draw and the onViewPositionChanged() will not be called anymore...
        // Aborts all motion in progress and snaps to the end of any animation, in case
        // the previous content view will not be laid back to its original position.
        mDragHelper.abort();
    }

    private final class ViewDragCallback extends ViewDragHelper.Callback {
        ViewDragCallback() {
        }

        @Override
        public boolean tryCaptureView(@NonNull View child, int pointerId) {
            if (child != mContentView) {
                return false;
            }

            if (mDragHelper.isEdgeTouched(mViewFlags & ABSOLUTE_EDGE_MASK, pointerId)) {
                if (mDragHelper.isEdgeTouched(EDGE_LEFT, pointerId)) {
                    mDraggedEdge = EDGE_LEFT;
                } else if (mDragHelper.isEdgeTouched(EDGE_RIGHT, pointerId)) {
                    mDraggedEdge = EDGE_RIGHT;
                }
                // Only when the distance in vertical the user's finger traveled is not more than
                // the distance to travel before a drag may begin can we capture it.
                if (mDraggedEdge == EDGE_LEFT || mDraggedEdge == EDGE_RIGHT) {
                    return !mDragHelper.checkTouchSlop(ViewDragHelper.DIRECTION_VERTICAL, pointerId);
                }
            }
            return false;
        }

        @Override
        public void onViewCaptured(@NonNull View capturedChild, int activePointerId) {
            prepareForSlidingContents();
        }

        @Override
        public void onViewPositionChanged(@NonNull View changedView, int left, int top, int dx, int dy) {
            final float oldScrollPercent = mScrollPercent;
            mScrollPercent = Math.min(Math.abs((float) left / (float) mContentView.getWidth()), 1);
            if (mScrollPercent != oldScrollPercent) {
                if (mSwipeListeners != null) {
                    for (int i = mSwipeListeners.size() - 1; i >= 0; i--) {
                        mSwipeListeners.get(i).onScrollPercentChange(mDraggedEdge, mScrollPercent);
                    }
                }

                if ((mViewFlags & FLAG_PREVIOUS_CONTENT_SCROLLABLE) != 0) {
                    movePreviousContent();
                }

                mScrimOpacity = 1 - mScrollPercent;
                mContentLeft = left;
                // redraw scrim and shadow
                invalidate();
            }
        }

        @Override
        public int getViewHorizontalDragRange(@NonNull View child) {
            if ((mViewFlags & (EDGE_LEFT | EDGE_RIGHT)) != 0
                    && (mFragment != null || mActivity != null && mActivity.canSwipeBackToFinish())) {
                return 1;
            }
            return 0;
        }

        @Override
        public int clampViewPositionHorizontal(@NonNull View child, int left, int dx) {
            switch (mDraggedEdge) {
                case EDGE_LEFT:
                    return Math.min(child.getWidth(), Math.max(left, 0));
                case EDGE_RIGHT:
                    return Math.min(0, Math.max(left, -child.getWidth()));
            }
            return left;
        }

        @Override
        public void onViewReleased(@NonNull View releasedChild, float xvel, float yvel) {
            int finalLeft = 0;
            switch (mDraggedEdge) {
                case EDGE_LEFT:
                    if (xvel > 0 || xvel == 0 && mScrollPercent > mScrollThreshold) {
                        finalLeft = releasedChild.getWidth()
                                + (mShadowLeft == null ? 0 : mShadowLeft.getIntrinsicWidth());
                    }
                    break;
                case EDGE_RIGHT:
                    if (xvel < 0 || xvel == 0 && mScrollPercent > mScrollThreshold) {
                        finalLeft = -(releasedChild.getWidth()
                                + (mShadowRight == null ? 0 : mShadowRight.getIntrinsicWidth()));
                    }
                    break;
            }
            mDragHelper.settleCapturedViewAt(finalLeft, 0);
            invalidate();
        }

        @Override
        public void onViewDragStateChanged(int state) {
            if (mSwipeListeners != null) {
                for (int i = mSwipeListeners.size() - 1; i >= 0; i--) {
                    mSwipeListeners.get(i).onScrollStateChange(mDraggedEdge, state);
                }
            }

            if (state == STATE_IDLE) {
                if (mScrollPercent == 1) {
                    // Disable the transition animations of the fragments while the current one
                    // is being popped up.
                    if (mFragment != null) {
                        ISwipeBackFragment prefragment = mFragment.getPreviousFragment();
                        if (prefragment != null)
                            prefragment.setTransitionEnabled(false);

                        mFragment.setTransitionEnabled(false);
                        //noinspection deprecation
                        ((Fragment) mFragment).requireFragmentManager().popBackStackImmediate();
                        mFragment.setTransitionEnabled(true);

                        if (prefragment != null)
                            prefragment.setTransitionEnabled(true);

                    } else if (mActivity != null) {
                        Activity activity = (Activity) mActivity;
                        if (!activity.isFinishing()) {
                            activity.finish();
                        }
                        activity.overridePendingTransition(0, 0);
                    }
                } else {
                    mDraggedEdge = NO_DRAGGED_EDGE;
                    // If this view has been settled at its original position, lay the previous
                    // content view back to ensure it will be shown normally after user presses
                    // the return key to finish current activity.
                    layPreviousContentBack();
                    // Convert the Activity back to opaque if it was previously so.
                    if (mActivity != null) {
                        Activity activity = (Activity) mActivity;
                        if ((mViewFlags & FLAG_WINDOW_IS_TRANSLUCENT) == 0) {
                            Utils.convertActivityToOpaque(activity);
                        }
                    }
                }
            }
        }
    }

    /*synthetic*/ void prepareForSlidingContents() {
        // If the current view is in the fragment, set the view of the previous fragment
        // to be visible before the scroll started.
        if (mFragment != null) {
            Fragment prefragment = (Fragment) mFragment.getPreviousFragment();
            if (prefragment != null && prefragment.getView() != null) {
                prefragment.getView().setVisibility(VISIBLE);
            }
            // Or else convert the background of the activity that contains it
            // to transparency, then the previous activity will be visible.
        } else {
            if ((mViewFlags & FLAG_WINDOW_IS_TRANSLUCENT) == 0) {
                Utils.convertActivityToTranslucent((Activity) mActivity);
            }
        }
    }

    /**
     * @return the previous content view of the activity {@link #mActivity} or
     *         the fragment {@link #mFragment}
     */
    @Nullable
    private View getPreviousContent() {
        if (mFragment != null) {
            ISwipeBackFragment prefragment = mFragment.getPreviousFragment();
            if (prefragment != null)
                return prefragment.getSwipeBackLayout();

        } else if (mActivity != null) {
            Activity preactivity = mActivity.getPreviousActivity();
            if (preactivity != null)
                return ((ViewGroup) preactivity.getWindow().getDecorView()).getChildAt(0);
        }
        return null;
    }

    /**
     * Translate the previous content view {@link #getPreviousContent()}
     */
    /*synthetic*/ void movePreviousContent() {
        View view = getPreviousContent();
        if (view != null) {
            float translationX = 0;
            switch (mDraggedEdge) {
                case EDGE_LEFT:
                    translationX = mScrollThreshold * (mScrollPercent - 1f) * view.getWidth();
                    break;
                case EDGE_RIGHT:
                    translationX = mScrollThreshold * (1f - mScrollPercent) * view.getWidth();
                    break;
            }
            view.setTranslationX(translationX);
        }
    }

    /**
     * Translate the previous content view {@link #getPreviousContent()} back to
     * its original position (0,0)
     */
    /*synthetic*/ void layPreviousContentBack() {
        View view = getPreviousContent();
        if (view != null) {
            view.setTranslationX(0);
        }
    }

    /**
     * Scroll out content view and finish the activity or pop up the fragment from
     * the back stack holding it
     */
    public void scrollToFinishActivityOrPopUpFragment() {
        prepareForSlidingContents();

        int left = 0;
        if ((mViewFlags & EDGE_LEFT) != 0) {
            left = mContentView.getWidth()
                    + (mShadowLeft == null ? 0 : mShadowLeft.getIntrinsicWidth());
            mDraggedEdge = EDGE_LEFT;
        } else if ((mViewFlags & EDGE_RIGHT) != 0) {
            left = -(mContentView.getWidth()
                    + (mShadowRight == null ? 0 : mShadowRight.getIntrinsicWidth()));
            mDraggedEdge = EDGE_RIGHT;
        }

        mDragHelper.smoothSlideViewTo(mContentView, left, 0);
        invalidate();
    }

    /**
     * Adds a callback to be invoked when a swipe event is sent to this view.
     *
     * @param listener the swipe listener {@link SwipeListener} to attach to this view
     */
    public void addSwipeListener(SwipeListener listener) {
        if (mSwipeListeners == null) {
            mSwipeListeners = new ArrayList<>(1);

        } else if (mSwipeListeners.contains(listener)) {
            return;
        }
        mSwipeListeners.add(listener);
    }

    /**
     * Removes a swipe listener from the set of listeners
     *
     * @param listener the swipe listener {@link SwipeListener} already attached to this view
     */
    public void removeSwipeListener(SwipeListener listener) {
        if (mSwipeListeners != null) {
            mSwipeListeners.remove(listener);
        }
    }

    public interface SwipeListener {
        /**
         * Invoked as the scroll state changes
         *
         * @param edge  edge flag describing the edge being dragged
         * @param state constant to describe scroll state
         * @see #STATE_IDLE
         * @see #STATE_DRAGGING
         * @see #STATE_SETTLING
         */
        void onScrollStateChange(@AbsoluteEdge int edge, @ScrollState int state);

        /**
         * Invoked as the scroll percentage changes
         *
         * @param edge    edge flag describing the edge being dragged
         * @param percent scroll percentage of the content view
         */
        void onScrollPercentChange(
                @AbsoluteEdge int edge, @FloatRange(from = 0.0, to = 1.0) float percent);
    }
}
