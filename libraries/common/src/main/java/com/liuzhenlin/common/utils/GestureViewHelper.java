/*
 * Created on 2024-4-16 2:20:59 PM.
 * Copyright © 2024 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.core.math.MathUtils;
import androidx.core.view.ViewCompat;
import androidx.customview.widget.ViewDragHelper;

import com.bumptech.glide.util.Synthetic;
import com.liuzhenlin.common.compat.ViewCompatibility;

import java.util.Arrays;

/**
 * An auxiliary class that provides support for gestural scaling, scrolling and fling of
 * the View content, without any influence on the size or position of the View itself.
 */
public class GestureViewHelper {

    private static final String TAG = "GestureViewHelper";

    protected final View mView;

    /** Caches the transform matrix of the view content */
    private final Matrix mMatrix;

    /** A float array to receive the values of {@link #mMatrix the matrix} */
    private final float[] mMatrixValues;

    private static final float DEFAULT_RESERVED_EDGE_SIZE = 25; // dp

    /**
     * Smallest edge size of the view content to be shown when it is shrunk and scrolled
     * out of the user-visible area.
     */
    private final float mReservedEdgeSize;

    private static final float DEFAULT_MIN_SCALE = 1f;
    private static final float DEFAULT_MAX_SCALE = 25f;

    private float mMinScale = DEFAULT_MIN_SCALE;
    private float mMaxScale = DEFAULT_MAX_SCALE;

    private final ScaleGestureDetector mScaleGestureDetector;

    private boolean mGestureEnabled;

    @Synthetic boolean mBeingDragged;

    private int mActivePointerId = ViewDragHelper.INVALID_POINTER;

    private float mDownX;
    private float mDownY;

    private final float[] mTouchX;
    private final float[] mTouchY;

    protected final int mTouchSlop;

    protected final float mMinimumFlingVelocity;
    protected final float mMaximumFlingVelocity;

    private VelocityTracker mVelocityTracker;

    private Flinger mFlinger;

    @Synthetic final Callbacks mCallbacks;

    private static final class NoTmpFieldsPreloadHolder {
        static final ThreadLocal<Matrix> sLocalTmpMatrix =
                Utils.newThreadLocalWithValueInitializerAndResetter(Matrix::new, null);

        static final ThreadLocal<float[]> sLocalTmpTwoFloats =
                Utils.newThreadLocalWithValueInitializerAndResetter(() -> new float[2], null);

        /**
         * Currently used to temporarily cache the 4 corner coordinates of the transformed
         * view content, which are relative to the real left vertex of the view
         * ({@link View#getLeft()}, {@link View#getTop()}).
         */
        static final ThreadLocal<RectF> sLocalTmpRectF =
                Utils.newThreadLocalWithValueInitializerAndResetter(RectF::new, null);
    }

    /**
     * Listener to be notified when any touch event related to view scaling happens.
     * <p>
     * Currently the events are:<br/>&emsp;
     *   - Scroll/Fling the view content<br/>&emsp;
     *   - Zoom in/out the view content<br/>
     * The scroll and the scale events can happen together or separately.
     */
    public interface OnGestureListener {

        /**
         * Called when the view content starts to be dragged by the user, either to be scrolled
         * or scaled.
         */
        default void onViewDragBegin(@NonNull View view) {
        }

        /** Called at the beginning of a scaling gesture. */
        default void onViewScaleBegin(@NonNull View view, @NonNull ScaleGestureDetector detector) {
        }

        /**
         * Called by this helper to respond to the scaling events for a gesture in progress.
         *
         * @return whether the scaling event is consumed from your code.
         */
        default boolean onViewScale(@NonNull View view, @NonNull ScaleGestureDetector detector) {
            return false;
        }

        /** Called at the end of a scale gesture. */
        default void onViewScaleEnd(@NonNull View view, @NonNull ScaleGestureDetector detector) {
        }

        /**
         * Called by this helper to respond to the scrolling events for a gesture in progress.
         *
         * @param deltaX distance in pixels along the X axis that has been scrolled since
         *               the last call to onViewScroll
         * @param deltaY distance in pixels along the Y axis that has been scrolled since
         *               the last call to onViewScroll
         * @return whether the scrolling event is consumed from your code.
         */
        default boolean onViewScroll(@NonNull View view, float deltaX, float deltaY) {
            return false;
        }

        /**
         * Notified of a fling event when it occurs with the initial on down MotionEvent
         * and the matching up MotionEvent. The calculated velocity is supplied along
         * the x and y axis in pixels per second.
         *
         * @param velocityX The velocity of this fling measured in pixels per second
         *                  along the x axis.
         * @param velocityY The velocity of this fling measured in pixels per second
         *                  along the y axis.
         * @return true if the event is consumed, else false
         */
        default boolean onViewFling(@NonNull View view, float velocityX, float velocityY) {
            return false;
        }

        /**
         * Called when the user stops dragging the content of the view, neither to scale nor
         * to move it more.
         */
        default void onViewDragEnd(@NonNull View view) {
        }
    }

    /**
     * Interface definition for callbacks to be invoked when the scale or scrolled position
     * of the view content changes, as a result of an update to the content transform matrix.
     */
    public interface OnViewTransformedListener {
        /**
         * This is called after a scale happened on the content of the view.
         */
        default void onViewScaled(@NonNull View view, float oldScaleX, float oldScaleY,
                                  float scaleX, float scaleY, float pivotX, float pivotY) {
        }

        /**
         * This is called after a scroll happened on the content of the view.
         */
        default void onViewScrolled(
                @NonNull View view, float oldScrollX, float oldScrollY, float scrollX, float scrollY) {
        }
    }

    /**
     * A Callback is used as a communication channel with the GestureViewHelper back to the
     * view it assists.
     */
    public interface Callback {

        /**
         * Returns whether the view content supports matrix transformations, usually depending on
         * the current running environment, the view ability, etc.
         */
        @SuppressLint("AnnotateVersionCheck")
        default boolean supportsViewTransform() {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
        }

        /** Gets the specified transform matrix associated with the view content. */
        default void getViewTransform(@NonNull View view, @NonNull Matrix outMatrix) {
            //noinspection NewApi
            outMatrix.set(view.getAnimationMatrix());
        }

        /**
         * Applies the specified transform matrix to the content of the view. A null matrix
         * indicates that its transform should be cleared.
         */
        default void setViewTransform(@NonNull View view, @Nullable Matrix transform) {
            //noinspection NewApi
            view.setAnimationMatrix(transform);
        }

        /**
         * Returns whether the transform matrix should be pre-concatenated with the matrix
         * of the view (obtained via {@link View#getMatrix()}) when used to resolve the bounds
         * of the transformed view content.
         */
        default boolean shouldTransformPreConcatViewMatrix() {
            return true;
        }
    }

    private static final class Callbacks implements Callback, OnGestureListener,
            OnViewTransformedListener {

        Callbacks(Callback cb) {
            mCallback = cb;
        }

        private final Callback mCallback;

        private OnGestureListener mGestureListener;

        private OnViewTransformedListener mViewTransformListener;

        void setOnViewTransformedListener(OnViewTransformedListener listener) {
            mViewTransformListener = listener;
        }

        boolean isOnGestureListenerSet() {
            return mGestureListener != null;
        }

        void setOnGestureListener(OnGestureListener listener) {
            mGestureListener = listener;
        }

        @Override
        public boolean supportsViewTransform() {
            return mCallback.supportsViewTransform();
        }

        @Override
        public void getViewTransform(@NonNull View view, @NonNull Matrix outMatrix) {
            mCallback.getViewTransform(view, outMatrix);
        }

        @Override
        public void setViewTransform(@NonNull View view, @Nullable Matrix transform) {
            mCallback.setViewTransform(view, transform);
        }

        @Override
        public boolean shouldTransformPreConcatViewMatrix() {
            return mCallback.shouldTransformPreConcatViewMatrix();
        }

        @Override
        public void onViewDragBegin(@NonNull View view) {
            if (mGestureListener != null) {
                mGestureListener.onViewDragBegin(view);
            }
        }

        @Override
        public void onViewScaleBegin(@NonNull View view, @NonNull ScaleGestureDetector detector) {
            if (mGestureListener != null) {
                mGestureListener.onViewScaleBegin(view, detector);
            }
        }

        @Override
        public boolean onViewScale(@NonNull View view, @NonNull ScaleGestureDetector detector) {
            return mGestureListener != null && mGestureListener.onViewScale(view, detector);
        }

        @Override
        public void onViewScaled(@NonNull View view, float oldScaleX, float oldScaleY,
                                 float scaleX, float scaleY, float pivotX, float pivotY) {
            if (mViewTransformListener != null) {
                mViewTransformListener
                        .onViewScaled(view, oldScaleX, oldScaleY, scaleX, scaleY, pivotX, pivotY);
            }
        }

        @Override
        public void onViewScaleEnd(@NonNull View view, @NonNull ScaleGestureDetector detector) {
            if (mGestureListener != null) {
                mGestureListener.onViewScaleEnd(view, detector);
            }
        }

        @Override
        public boolean onViewScroll(@NonNull View view, float deltaX, float deltaY) {
            return mGestureListener != null && mGestureListener.onViewScroll(view, deltaX, deltaY);
        }

        @Override
        public void onViewScrolled(
                @NonNull View view, float oldScrollX, float oldScrollY, float scrollX, float scrollY) {
            if (mViewTransformListener != null) {
                mViewTransformListener.onViewScrolled(view, oldScrollX, oldScrollY, scrollX, scrollY);
            }
        }

        @Override
        public boolean onViewFling(@NonNull View view, float velocityX, float velocityY) {
            return mGestureListener != null
                    && mGestureListener.onViewFling(view, velocityX, velocityY);
        }

        @Override
        public void onViewDragEnd(@NonNull View view) {
            if (mGestureListener != null) {
                mGestureListener.onViewDragEnd(view);
            }
        }
    }

    public GestureViewHelper(@NonNull View view, @NonNull Callback cb) {
        mView = view;
        mCallbacks = new Callbacks(cb);
        Context context = view.getContext();
        float dp = context.getResources().getDisplayMetrics().density;

        ViewConfiguration vc = ViewConfiguration.get(context);
        mTouchSlop = vc.getScaledTouchSlop();
        mMinimumFlingVelocity = vc.getScaledMinimumFlingVelocity();
        mMaximumFlingVelocity = vc.getScaledMaximumFlingVelocity();

        if (cb.supportsViewTransform()) {
            mScaleGestureDetector = new ScaleGestureDetector(context, new OnScaleGestureListener());
            mMatrix = new Matrix();
            mMatrixValues = new float[9];
            mTouchX = new float[2];
            mTouchY = new float[2];
        } else {
            mScaleGestureDetector = null;
            mMatrix = null;
            mMatrixValues = null;
            mTouchY = mTouchX = null;
        }
        mReservedEdgeSize = DEFAULT_RESERVED_EDGE_SIZE * dp;
    }

    /** Sets the listener to be notified when any touch event related to view scaling happens. */
    public void setOnGestureListener(@Nullable OnGestureListener listener) {
        mCallbacks.setOnGestureListener(listener);
    }

    /**
     * Sets the listener to be notified of the scale or scrolled position change initiated by
     * an update of the view content matrix.
     */
    public void setOnViewTransformedListener(@Nullable OnViewTransformedListener listener) {
        mCallbacks.setOnViewTransformedListener(listener);
    }

    /** Gets the minimum scale that the visual content can be zoomed out to */
    public float getMinViewScale() {
        return mMinScale;
    }

    /** Sets the minimum scale that the visual content can be zoomed out to */
    public void setMinViewScale(float scale) {
        if (mMinScale != scale) {
            mMinScale = scale;
            resetViewTransform();
        }
    }

    /** Gets the maximum scale that the visual content can be zoomed in to */
    public float getMaxViewScale() {
        return mMaxScale;
    }

    /** Sets the maximum scale that the visual content can be zoomed in to */
    public void setMaxViewScale(float scale) {
        if (mMaxScale != scale) {
            mMaxScale = scale;
            resetViewTransform();
        }
    }

    /** Gets the current scale of the visual content */
    public float getViewScale() {
        ensureViewMatrix();
        mMatrix.getValues(mMatrixValues);
        return mMatrixValues[Matrix.MSCALE_X] * mMatrixValues[Matrix.MSCALE_Y];
    }

    /**
     * Ensures our values of {@link #mMatrix the cached matrix} are equal to the values of the
     * current matrix of the view content since they may have been changed in other class.
     */
    @Synthetic void ensureViewMatrix() {
        if (mCallbacks.supportsViewTransform()) {
            mCallbacks.getViewTransform(mView, mMatrix);
        }
    }

    /**
     * Applies the specified matrix to the content of the view if it supports transformations.
     * A null matrix indicates that its transform should be cleared.
     */
    private void setViewMatrix(@Nullable Matrix matrix) {
        if (mCallbacks.supportsViewTransform()) {
            mCallbacks.setViewTransform(mView, matrix);
        }
    }

    /** Resets any gestural transformations applied to the view content */
    public void resetViewTransform() {
        int count = 0;
        while (resetViewTransformInternal()) {
            if (++count >= 10) {
                Log.w(TAG, "Number of calls to resetViewTransformInternal() from "
                        + "resetViewTransform() reached the threshold (10).");
                return;
            }
        }
        Log.d(TAG, "Invocation count of resetViewTransformInternal(): " + count);
    }

    boolean resetViewTransformInternal() {
        // Kill any existing fling/scroll first
        if (mFlinger != null) {
            mFlinger.stop();
        }

        // getViewScale() refreshes the cached matrix by the way
        float scale = getViewScale();
        float scaleFactor = (float) Math.sqrt(MathUtils.clamp(1, mMinScale, mMaxScale) / scale);
        float scalePivotX = mView.getWidth() / 2f;
        float scalePivotY = mView.getHeight() / 2f;
        Matrix tmpMatrix = NoTmpFieldsPreloadHolder.sLocalTmpMatrix.get();
        RectF bounds = NoTmpFieldsPreloadHolder.sLocalTmpRectF.get();
        float[] scrollDelta = NoTmpFieldsPreloadHolder.sLocalTmpTwoFloats.get();

        // Scales the tmpMatrix based on the current matrix value of the view content and then
        // calculates the x and y distance the content should scroll by when it is rescaled to
        // the default.
        tmpMatrix.set(mMatrix);
        tmpMatrix.postScale(scaleFactor, scaleFactor, scalePivotX, scalePivotY);
        Arrays.fill(scrollDelta, 0);
        clampViewScrollPositions(tmpMatrix, scrollDelta, true);

        trackMotionScale(scaleFactor, scalePivotX, scalePivotY);
        scrollByInternal(bounds, scrollDelta[0], scrollDelta[1]);

        // Transform needs to be reset again due to the scroll tolerance is greater than
        // square root of 0.1 (0.316) pixel.
        return scrollDelta[0] * scrollDelta[0] + scrollDelta[1] * scrollDelta[1] > 0.1;
    }

    private RectF resolveViewBounds(Matrix matrix) {
        RectF bounds = NoTmpFieldsPreloadHolder.sLocalTmpRectF.get();
        bounds.set(0, 0, mView.getWidth(), mView.getHeight());
        if (mCallbacks.shouldTransformPreConcatViewMatrix()) {
            mView.getMatrix().mapRect(bounds);
            matrix.mapRect(bounds);
        } else {
            matrix.mapRect(bounds);
            mView.getMatrix().mapRect(bounds);
        }
        return bounds;
    }

    /**
     * Gets the bounds of the transformed view content by providing a rectangle into which its
     * 4 corners are put. The coordinates of the corners are relative to the real left vertex
     * of the view, which can be known via the {@link View#getLeft()} and the {@link View#getTop()}
     * methods.
     */
    public void getViewBounds(@NonNull RectF outRect) {
        ensureViewMatrix();
        outRect.set(0, 0, mView.getWidth(), mView.getHeight());
        if (mCallbacks.shouldTransformPreConcatViewMatrix()) {
            mView.getMatrix().mapRect(outRect);
            mMatrix.mapRect(outRect);
        } else {
            mMatrix.mapRect(outRect);
            mView.getMatrix().mapRect(outRect);
        }
    }

    /** Returns whether the user can scale and translate the view content */
    public boolean isGestureEnabled() {
        return mGestureEnabled;
    }

    /** Sets whether the user can scale and translate the view content */
    public void setGestureEnabled(boolean enabled) {
        mGestureEnabled = enabled;
    }

    /**
     * Check if this event as provided to the view's onInterceptTouchEvent() should
     * cause the view to intercept the touch event stream.
     */
    public boolean shouldInterceptTouchEvent(@NonNull MotionEvent event) {
        if (skipOrPreprocessTouchEvent(event)) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                onPointerDown(event);
                break;

            case MotionEvent.ACTION_MOVE:
                if (!onPointerMove(event)) {
                    return false;
                }
                return tryCaptureViewForDrag();

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(event);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                clearTouch();
                break;
        }
        return false;
    }

    private boolean skipOrPreprocessTouchEvent(MotionEvent ev) {
        if (!mCallbacks.supportsViewTransform()) {
            return true; // To skip touch events
        }

        final int action = ev.getAction();
        final int actionMasked = action & MotionEvent.ACTION_MASK;

        if (!mGestureEnabled) {
            if (mBeingDragged) {
                ev.setAction(MotionEvent.ACTION_CANCEL);
                mScaleGestureDetector.onTouchEvent(ev);
                ev.setAction(action);
                mCallbacks.onViewDragEnd(mView);
            }
            clearTouch();
            return true;
        }

        if (actionMasked == MotionEvent.ACTION_DOWN) {
            if (mFlinger != null) {
                // Kill any existing fling/scroll
                mFlinger.stop();
            }
            // Make sure the touch caches are in the initial state when a new gesture starts.
            resetTouch();
        }

        if (mVelocityTracker == null)
            mVelocityTracker = VelocityTracker.obtain();
        mVelocityTracker.addMovement(ev);

        mScaleGestureDetector.onTouchEvent(ev);

        return false;
    }

    /**
     * Analyzes the given motion event and if applicable triggers the appropriate callbacks on the
     * {@link OnGestureListener} supplied and makes the view content scale or/and translate.
     * The view's onTouchEvent implementation should call this.
     */
    public boolean handleTouchEvent(@NonNull MotionEvent event) {
        if (skipOrPreprocessTouchEvent(event)) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                onPointerDown(event);
                break;

            case MotionEvent.ACTION_MOVE:
                if (!onPointerMove(event)) {
                    return false;
                }

                final boolean multiTouch = event.getPointerCount() >= 2;
                if (multiTouch) {
                    if (mBeingDragged) {
                        float dx = mTouchX[mTouchX.length - 1] - mTouchX[mTouchX.length - 2];
                        float dy = mTouchY[mTouchY.length - 1] - mTouchY[mTouchY.length - 2];

                        if (mCallbacks.onViewScroll(mView, dx, dy)) {
                            ensureViewMatrix();
                            break;
                        }

                        trackMotionScroll(dx, dy);
                    } else {
                        tryCaptureViewForDrag();
                    }
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(event);
                break;

            case MotionEvent.ACTION_UP:
                if (mBeingDragged) {
                    mVelocityTracker.computeCurrentVelocity(1000, mMaximumFlingVelocity);
                    float velocityX = mVelocityTracker.getXVelocity(mActivePointerId);
                    float velocityY = mVelocityTracker.getYVelocity(mActivePointerId);
                    if (Math.abs(velocityX) >= mMinimumFlingVelocity
                            || Math.abs(velocityY) >= mMinimumFlingVelocity) {
                        if (!mCallbacks.onViewFling(mView, velocityX, velocityY)) {
                            if (mFlinger == null) {
                                mFlinger = new Flinger();
                            }
                            mFlinger.startFling(velocityX, velocityY);
                        }
                    }
                }
            case MotionEvent.ACTION_CANCEL:
                try {
                    if (mBeingDragged) {
                        mCallbacks.onViewDragEnd(mView);
                        return true;
                    }
                } finally {
                    clearTouch();
                }
                break;
        }

        return mBeingDragged;
    }

    private boolean tryCaptureViewForDrag() {
        if (!mBeingDragged) {
            float dx = mTouchX[mTouchX.length - 1] - mDownX;
            float dy = mTouchY[mTouchY.length - 1] - mDownY;
            if (dx * dx + dy * dy > mTouchSlop * mTouchSlop) {
                mBeingDragged = true;
                requestViewParentDisallowInterceptTouchEvent();
                if (mCallbacks.isOnGestureListenerSet()) {
                    mCallbacks.onViewDragBegin(mView);
                    ensureViewMatrix();
                }
            }
        }
        return mBeingDragged;
    }

    private void onPointerDown(MotionEvent e) {
        final int actionIndex = e.getActionIndex();
        mActivePointerId = e.getPointerId(actionIndex);
        mDownX = e.getX(actionIndex);
        mDownY = e.getY(actionIndex);
        markCurrTouchPoint(mDownX, mDownY);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean onPointerMove(MotionEvent e) {
        final int pointerIndex = e.findPointerIndex(mActivePointerId);
        if (pointerIndex < 0) {
            Log.e(TAG, "Error processing scroll; pointer index for id "
                    + mActivePointerId + " not found. Did any MotionEvents get skipped?");
            return false;
        }
        markCurrTouchPoint(e.getX(pointerIndex), e.getY(pointerIndex));
        return true;
    }

    private void onSecondaryPointerUp(MotionEvent e) {
        final int pointerIndex = e.getActionIndex();
        final int pointerId = e.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up.
            // Choose a new active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mActivePointerId = e.getPointerId(newPointerIndex);
            mDownX = e.getX(newPointerIndex);
            mDownY = e.getY(newPointerIndex);
            markCurrTouchPoint(mDownX, mDownY);
        }
    }

    private void markCurrTouchPoint(float x, float y) {
        System.arraycopy(mTouchX, 1, mTouchX, 0, mTouchX.length - 1);
        mTouchX[mTouchX.length - 1] = x;
        System.arraycopy(mTouchY, 1, mTouchY, 0, mTouchY.length - 1);
        mTouchY[mTouchY.length - 1] = y;
    }

    protected void requestViewParentDisallowInterceptTouchEvent() {
        ViewParent parent = mView.getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
        }
    }

    private void clearTouch() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
        resetTouch();
    }

    private void resetTouch() {
        mBeingDragged = false;
        mActivePointerId = ViewDragHelper.INVALID_POINTER;
        if (mVelocityTracker != null) {
            mVelocityTracker.clear();
        }
    }

    /**
     * Scrolls the view content by {@code dx} distance along the x axis and {@code dy} distance
     * along the y axis. The content may not scroll at all if the user-visible area in the scroll
     * direction already reached its corner bounds. Usually, you may need this in either
     * the {@link OnGestureListener#onViewScroll(View, float, float)}
     * or {@link OnGestureListener#onViewFling(View, float, float)} listener callback method.
     *
     * @return true if any of the view content scrolled.
     */
    public boolean scrollViewBy(float dx, float dy) {
        if (mFlinger != null) {
            mFlinger.stop();
        }
        ensureViewMatrix();
        return trackMotionScroll(dx, dy);
    }

    /**
     * Tracks a motion scroll. In reality, this is used to do just about any
     * movement to the content (touch scroll, fling scroll).
     * <p>
     * <strong>NOTE:</strong> This does not perform any validity checks on the cached matrix object.
     * Be sure to use with caution.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    public boolean trackMotionScroll(float dx, float dy) {
        RectF bounds = NoTmpFieldsPreloadHolder.sLocalTmpRectF.get();
        float[] scrollDelta = NoTmpFieldsPreloadHolder.sLocalTmpTwoFloats.get();
        scrollDelta[0] = dx;
        scrollDelta[1] = dy;
        clampViewScrollPositions(mMatrix, scrollDelta, false);
        dx = scrollDelta[0];
        dy = scrollDelta[1];
        // Translate the view content by the clamped dx & dy displacements
        return scrollByInternal(bounds, dx, dy);
    }

    /** Do real scroll of the content */
    private boolean scrollByInternal(RectF bounds, float dx, float dy) {
        if (dx != 0 || dy != 0) {
            mMatrix.postTranslate(dx, dy);
            setViewMatrix(mMatrix);

            ViewGroup parent = (ViewGroup) mView.getParent();
            if (parent != null) {
                int visibleLeft = -mView.getLeft() + parent.getPaddingLeft();
                int visibleTop = -mView.getTop() + parent.getPaddingTop();
                float oldScrollX = visibleLeft - bounds.left;
                float oldScrollY = visibleTop - bounds.top;
                mCallbacks.onViewScrolled(
                        mView, oldScrollX, oldScrollY, oldScrollX + dx, oldScrollY + dy);
            }

            return true;
        }
        return false;
    }

    private void clampViewScrollPositions(Matrix matrix, float[] inOutDelta, boolean centerView) {
        ViewGroup parent = (ViewGroup) mView.getParent();
        if (parent == null)
            return;

        int parentPaddingLeft = parent.getPaddingLeft();
        int parentPaddingTop = parent.getPaddingTop();
        int parentPaddingHorizontal = parentPaddingLeft + parent.getPaddingRight();
        int parentPaddingVertical = parentPaddingTop + parent.getPaddingBottom();

        int visibleLeft = -mView.getLeft() + parentPaddingLeft;
        int visibleTop = -mView.getTop() + parentPaddingTop;
        int visibleRight = visibleLeft + parent.getWidth() - parentPaddingHorizontal;
        int visibleBottom = visibleTop + parent.getHeight() - parentPaddingVertical;
        int visibleWidth = visibleRight - visibleLeft;
        int visibleHeight = visibleBottom - visibleTop;

        RectF bounds = resolveViewBounds(matrix);
        if (centerView) {
            inOutDelta[0] = visibleLeft + (visibleWidth + bounds.width()) / 2f - bounds.right;
        } else if (Utils.roundFloat(bounds.width()) >= visibleWidth) {
            if (bounds.left + inOutDelta[0] > visibleLeft) {
                inOutDelta[0] = visibleLeft - bounds.left;
            } else if (bounds.right + inOutDelta[0] < visibleRight) {
                inOutDelta[0] = visibleRight - bounds.right;
            }
        } else {
            if (bounds.right + inOutDelta[0] < visibleLeft + mReservedEdgeSize) {
                inOutDelta[0] = visibleLeft + mReservedEdgeSize - bounds.right;
            } else if (bounds.left + inOutDelta[0] > visibleRight - mReservedEdgeSize) {
                inOutDelta[0] = visibleRight - mReservedEdgeSize - bounds.left;
            }
        }
        if (centerView) {
            inOutDelta[1] = visibleTop + (visibleHeight + bounds.height()) / 2f - bounds.bottom;
        } else if (Utils.roundFloat(bounds.height()) >= visibleHeight) {
            if (bounds.top + inOutDelta[1] > visibleTop) {
                inOutDelta[1] = visibleTop - bounds.top;
            } else if (bounds.bottom + inOutDelta[1] < visibleBottom) {
                inOutDelta[1] = visibleBottom - bounds.bottom;
            }
        } else {
            if (bounds.bottom + inOutDelta[1] < visibleTop + mReservedEdgeSize) {
                inOutDelta[1] = visibleTop + mReservedEdgeSize - bounds.bottom;
            } else if (bounds.top + inOutDelta[1] > visibleBottom - mReservedEdgeSize) {
                inOutDelta[1] = visibleBottom - mReservedEdgeSize - bounds.top;
            }
        }
    }

    /**
     * Scales the view content {@code scaleFactor} times both in horizontal and vertical around
     * the ({@code pivotX}, {@code pivotY}) point within its scalable rage
     * ({@link #getMinViewScale()} .. {@link #getMaxViewScale()}). Usually, you may need this in
     * the {@link OnGestureListener#onViewScale(View, ScaleGestureDetector)} listener callback
     * method.
     *
     * @return true if the view scale changed.
     */
    public boolean scaleView(float scaleFactor, float pivotX, float pivotY) {
        if (mFlinger != null) {
            mFlinger.stop();
        }
        ensureViewMatrix();
        return trackMotionScale(scaleFactor, pivotX, pivotY);
    }

    /**
     * Tracks a motion scale.
     * <p>
     * <strong>NOTE:</strong> This does not perform any validity checks on the cached matrix object.
     * Be sure to use with caution.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    public boolean trackMotionScale(float scaleFactor, float pivotX, float pivotY) {
        mMatrix.getValues(mMatrixValues);
        final float scaleX = mMatrixValues[Matrix.MSCALE_X];
        final float scaleY = mMatrixValues[Matrix.MSCALE_Y];

        /*
         * Adjust the xScaleFactor and yScaleFactor to make them within the scalable range of
         * the view content.
         */
        final float minScale = (float) Math.sqrt(mMinScale);
        final float maxScale = (float) Math.sqrt(mMaxScale);
        float xScaleFactor = scaleFactor;
        float yScaleFactor = scaleFactor;
        // xScaleFactor
        if (xScaleFactor * scaleX > maxScale)
            xScaleFactor = maxScale / scaleX;
        else if (xScaleFactor * scaleX < minScale)
            xScaleFactor = minScale / scaleX;
        // yScaleFactor
        if (yScaleFactor * scaleY > maxScale)
            yScaleFactor = maxScale / scaleY;
        else if (yScaleFactor * scaleY < minScale)
            yScaleFactor = minScale / scaleY;

        if (xScaleFactor != 1 || yScaleFactor != 1) {
            mMatrix.postScale(xScaleFactor, yScaleFactor, pivotX, pivotY);
            setViewMatrix(mMatrix);
            mCallbacks.onViewScaled(mView, scaleX, scaleY, scaleX * xScaleFactor,
                    scaleY * yScaleFactor, pivotX, pivotY);
            return true;
        }
        return false;
    }

    /**
     * Responsible for fling behavior. Use {@link Flinger#startFling(float, float)}
     * to initiate a fling. Each frame of the fling is handled in {@link #run()}.
     * A Flinger will keep re-posting itself until the fling is done.
     */
    private final class Flinger implements Runnable {

        /** Tracks the decay of a fling scroll */
        private final OverScroller mScroller;

        /** X value reported by mScroller on the previous fling */
        private float mLastFlingX;
        /** Y value reported by mScroller on the previous fling */
        private float mLastFlingY;

        /**
         * Number of consecutive frames of a fling that will not cause any scrolling of
         * the view content till the threshold (10) at which we will cancel the animation.
         */
        private int mNoScrollCount;

        public Flinger() {
            mScroller = new OverScroller(mView.getContext());
        }

        public void startFling(float initialVelocityX, float initialVelocityY) {
            if (initialVelocityX == 0 && initialVelocityY == 0)
                return;

            // Remove any pending flings
            ViewCompatibility.removeCallbacks(mView, this);

            mLastFlingX = 0;
            mLastFlingY = 0;
            mScroller.fling(0, 0, initialVelocityX, initialVelocityY,
                    Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
            ViewCompatibility.post(mView, this);
        }

        public void stop() {
            ViewCompatibility.removeCallbacks(mView, this);
            mScroller.forceFinished(true);
        }

        @Override
        public void run() {
            if (!ViewCompat.isAttachedToWindow(mView)) {
                mScroller.forceFinished(true);
                return;
            }

            if (mScroller.computeScrollOffset()) {
                float x = mScroller.getCurrX();
                float y = mScroller.getCurrY();
                float deltaX = x - mLastFlingX;
                float deltaY = y - mLastFlingY;

                // Pretend that each frame of a fling scroll is a touch scroll
                boolean scrolledAny = trackMotionScroll(deltaX, deltaY);
                if (scrolledAny) {
                    mNoScrollCount = 0;
                }

                if (scrolledAny || ++mNoScrollCount <= 10) {
                    mLastFlingX = x;
                    mLastFlingY = y;
                    ViewCompatibility.postOnAnimation(mView, this);
                } else {
                    mScroller.forceFinished(true);
                }
            }
        }
    }

    private final class OnScaleGestureListener implements ScaleGestureDetector.OnScaleGestureListener {

        OnScaleGestureListener() {
        }

        @Override
        public boolean onScale(@NonNull ScaleGestureDetector detector) {
            if (mCallbacks.onViewScale(mView, detector)) {
                ensureViewMatrix();
                return true;
            }

            trackMotionScale(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
            return true;
        }

        @Override
        public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
            final boolean alreadyBeingDragged = mBeingDragged;
            final boolean onGestureListenerSet = mCallbacks.isOnGestureListenerSet();
            if (!alreadyBeingDragged) {
                mBeingDragged = true;
                requestViewParentDisallowInterceptTouchEvent();
            }
            if (onGestureListenerSet) {
                if (!alreadyBeingDragged)
                    mCallbacks.onViewDragBegin(mView);
                mCallbacks.onViewScaleBegin(mView, detector);
            }
            if (!alreadyBeingDragged || onGestureListenerSet) {
                ensureViewMatrix();
            }
            return true;
        }

        @Override
        public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
            if (mCallbacks.isOnGestureListenerSet()) {
                mCallbacks.onViewScaleEnd(mView, detector);
                ensureViewMatrix();
            }
        }
    }
}
