/*
 * Created on 2018/04/17.
 * Copyright © 2018–2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.galleryviewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.util.Predicate;
import androidx.customview.widget.ViewDragHelper;

import com.liuzhenlin.common.compat.ViewCompatibility;

/**
 * @author <a href="mailto:2233788867@qq.com">刘振林</a>
 */
public class GestureImageView extends AppCompatImageView {
    private static final String TAG = "GestureImageView";

    private final GestureDetector mGestureDetector;
    private final ScaleGestureDetector mScaleGestureDetector;

    /** The matrix of the image in this view */
    /*synthetic*/ final Matrix mImageMatrix = new Matrix();

    /** A float array to receive the values of the matrix {@link #mImageMatrix} */
    /*synthetic*/ final float[] mImageMatrixValues = new float[9];

    /** Used to temporarily cache the 4 corner coordinates of the image */
    /*package*/ final RectF mImageBounds = new RectF();

    // Avoid allocations...
    /*package*/ final Matrix mTmpMatrix = new Matrix();
    /*package*/ final PointF mTmpPointF = new PointF();

    /** @see #getFitCenterImageScale() */
    /*synthetic*/ float mFitCenterImageScale;
    /** @see #getFitWidthImageScale() */
    private float mFitWidthImageScale;

    /** @see #getImageMinScale() */
    /*synthetic*/ float mImageMinScale;
    /** @see #getImageMaxScale() */
    /*synthetic*/ float mImageMaxScale;

    /** Image scale that the image will be zoomed in to when double tapped by the user. */
    /*synthetic*/ float mDoubleTapMagnifiedImageScale;

    /**
     * A threshold, the minimum ratio of the ratio of the view width to the picture width to
     * the ratio of the view height to the picture height, that a picture to be considered as
     * a long picture must exceed.
     */
    protected static final float PIIIC_THRESHOLD = 1.1f;

    /**
     * A multiplier of the maximum scale for the image {@link #mImageMaxScale}, means that
     * the image can be temporarily over-scaled to a scale
     * {@value #IMAGE_OVERSCALE_TIMES_ON_MAXIMIZED} times the maximum one by the user.
     */
    @SuppressWarnings("JavaDoc")
    protected static final float IMAGE_OVERSCALE_TIMES_ON_MAXIMIZED = 1.5f;

    /*synthetic*/ int mPrivateFlags;

    /**
     * A flag indicates that the user can scale or translate the image with zoom in and out
     * or drag and drop gestures.
     */
    private static final int PFLAG_IMAGE_GESTURES_ENABLED = 1;

    /**
     * A flag indicates that the user can translate the image
     * with single finger drag and drop gestures though the image has not been magnified.
     * <p>
     * <strong>NOTE:</strong> Only when {@link #mPrivateFlags} has been marked with
     * {@link #PFLAG_IMAGE_GESTURES_ENABLED} will it take work.
     */
    private static final int PFLAG_MOVE_UNMAGNIFIED_IMAGE_VIA_SINGLE_FINGER_ALLOWED = 1 << 1;

    /**
     * A flag indicates that the image is being dragged by user
     */
    private static final int PFLAG_IMAGE_BEING_DRAGGED = 1 << 2;

    /**
     * Indicates that we have performed a long click during the user's current touches
     */
    private static final int PFLAG_HAS_PERFORMED_LONG_CLICK = 1 << 3;

    /**
     * Indicates that the performed long click has been consumed
     */
    private static final int PFLAG_LONG_CLICK_CONSUMED = 1 << 4;

    /** Square of the distance to travel before drag may begin */
    private float mTouchSlopSquare;

    /** Last known pointer id for touch events */
    private int mActivePointerId = ViewDragHelper.INVALID_POINTER;

    private float mDownX;
    private float mDownY;

    private final float[] mTouchX = new float[2];
    private final float[] mTouchY = new float[2];

    private VelocityTracker mVelocityTracker;

    /** The minimum velocity for the user gesture to be detected as fling. */
    protected final float mMinimumFlingVelocity; // 200 dp/s
    /** The maximum velocity that a fling gesture can produce. */
    protected final float mMaximumFlingVelocity; // 8000 dp/s

    /**
     * The ratio of the offset (relative to the current position of the image) that the image
     * will be translated by to the current fling velocity.
     */
    protected static final float RATIO_FLING_OFFSET_TO_VELOCITY = 1f / 10f;

    /**
     * The distance by which this magnified image will be over-translated when we fling it
     * to some end, as measured in pixels.
     */
    /*package*/ final float mImageOverTranslation; // 25dp

    protected static final Interpolator sDecelerateInterpolator = new DecelerateInterpolator();

    /** Frequently used duration for the image transformation animator */
    public static final int DEFAULT_DURATION_TRANSFORM_IMAGE = 256; // ms

    private ImageTransformer mImageTransformer;

    public GestureImageView(Context context) {
        this(context, null);
    }

    public GestureImageView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GestureImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray ta = context.obtainStyledAttributes(
                attrs, R.styleable.GestureImageView, defStyleAttr, 0);
        setImageGesturesEnabled(ta.getBoolean(R.styleable
                .GestureImageView_imageGesturesEnabled, true));
        setMoveUnmagnifiedImageViaSingleFingerAllowed(ta.getBoolean(R.styleable
                .GestureImageView_moveUnmagnifiedImageViaSingleFingerAllowed, false));
        setTouchSensitivity(ta.getFloat(R.styleable.GestureImageView_touchSensitivity, 1.0f));
        ta.recycle();

        OnImageGestureListener listener = new OnImageGestureListener();
        mGestureDetector = new GestureDetector(context, listener);
        mScaleGestureDetector = new ScaleGestureDetector(context, listener);

        final float dp = getResources().getDisplayMetrics().density;
        mMaximumFlingVelocity = ViewConfiguration.getMaximumFlingVelocity() * dp;
        mMinimumFlingVelocity = 200f * dp;
        mImageOverTranslation = 25f * dp;
    }

    private ImageTransformer getImageTransformer() {
        if (mImageTransformer == null) {
            mImageTransformer = new ImageTransformer(getContext());
        }
        return mImageTransformer;
    }

    /**
     * @return the scale for the image to fit entirely inside this view, i.e., at least
     *         one axis (X or Y) will fit exactly. The result is centered inside this view,
     *         just as the {@link ScaleType#FIT_CENTER} ScaleType does.
     */
    public float getFitCenterImageScale() {
        return mFitCenterImageScale;
    }

    /**
     * @return the scale for the image to fit the width of this view exactly
     */
    public float getFitWidthImageScale() {
        return mFitWidthImageScale;
    }

    /**
     * @return the minimum scale that the image can be zoomed out to
     */
    public float getImageMinScale() {
        return mImageMinScale;
    }

    /**
     * @return the maximum scale that the image can be zoomed in to.
     */
    public float getImageMaxScale() {
        return mImageMaxScale;
    }

    public boolean isImageGesturesEnabled() {
        return (mPrivateFlags & PFLAG_IMAGE_GESTURES_ENABLED) != 0;
    }

    public void setImageGesturesEnabled(boolean enabled) {
        if (enabled) {
            setScaleType(ScaleType.MATRIX);
            mPrivateFlags |= PFLAG_IMAGE_GESTURES_ENABLED;
        } else {
            mPrivateFlags &= ~PFLAG_IMAGE_GESTURES_ENABLED;
        }
    }

    public boolean isMoveUnmagnifiedImageViaSingleFingerAllowed() {
        return (mPrivateFlags & PFLAG_MOVE_UNMAGNIFIED_IMAGE_VIA_SINGLE_FINGER_ALLOWED) != 0;
    }

    public void setMoveUnmagnifiedImageViaSingleFingerAllowed(boolean allowed) {
        if (allowed)
            mPrivateFlags |= PFLAG_MOVE_UNMAGNIFIED_IMAGE_VIA_SINGLE_FINGER_ALLOWED;
        else
            mPrivateFlags &= ~PFLAG_MOVE_UNMAGNIFIED_IMAGE_VIA_SINGLE_FINGER_ALLOWED;
    }

    /**
     * Sets the sensitivity used for detecting the start of a swipe.
     *
     * @param sensitivity Multiplier for how sensitive we should be about detecting the start of
     *                    a drag. Smaller values are less sensitive. 1.0f is normal.
     */
    public void setTouchSensitivity(float sensitivity) {
        final float dp = getResources().getDisplayMetrics().density;
        final float touchSlop = ViewConfiguration.getTouchSlop() * dp / sensitivity;
        mTouchSlopSquare = touchSlop * touchSlop;
    }

    /**
     * Gets the sensitivity used for detecting the start of a swipe.
     * Larger values are more sensitive. 1.0f is normal.
     */
    public float getTouchSensitivity() {
        final float dp = getResources().getDisplayMetrics().density;
        final float normalTouchSlop = ViewConfiguration.getTouchSlop() * dp;
        return (float) (Math.sqrt(mTouchSlopSquare) / normalTouchSlop);
    }

    /**
     * @return Square of the distance in dips a touch can wander before we think the user is scrolling.
     */
    protected float getTouchSlopSquare() {
        return mTouchSlopSquare;
    }

    @Override
    public void setScaleType(ScaleType scaleType) {
        if ((mPrivateFlags & PFLAG_IMAGE_GESTURES_ENABLED) == 0) {
            super.setScaleType(scaleType);
        }
    }

    /**
     * Scales the image to fit current view and properly positions it inside this view.
     */
    private void initializeImage() {
        Drawable d = getDrawable();
        if (d == null) return;

        // Gets the available width and height for the image
        final int width = getDrawingWidth();
        final int height = getDrawingHeight();
        // Gets the width and height of the image
        final int imgWidth = d.getIntrinsicWidth();
        final int imgHeight = d.getIntrinsicHeight();

        mFitWidthImageScale = (float) width / imgWidth;
        mFitCenterImageScale = Math.min(mFitWidthImageScale, (float) height / imgHeight);
        mImageMinScale = mFitCenterImageScale / 5f;
        mImageMaxScale = mFitCenterImageScale * 5f;

        final boolean piiic = mFitWidthImageScale >= mFitCenterImageScale * PIIIC_THRESHOLD;
        if (piiic) {
            mDoubleTapMagnifiedImageScale = mFitWidthImageScale;
            // Make sure the mImageMaxScale is not less than 3 times that of mDoubleTapMagnifiedImageScale,
            // preferring to have the maximum scale for the image 5 times larger than mFitCenterImageScale.
            if (mImageMaxScale < mDoubleTapMagnifiedImageScale * 3f) {
                mImageMaxScale = mDoubleTapMagnifiedImageScale * 3f;
            }
        } else {
            mDoubleTapMagnifiedImageScale = mImageMaxScale / 2f;
        }

        // We need to ensure below will work normally if an other image has been set for this view,
        // so just reset the current matrix to its initial state.
        mImageMatrix.reset();
        if (piiic) {
            // Scales the image to fit exactly the width of the view with the top edge showed to the user
            mImageMatrix.postScale(mFitWidthImageScale, mFitWidthImageScale, 0, 0);
        } else {
            // Translates the image to the center of the current view
            mImageMatrix.postTranslate((width - imgWidth) / 2f, (height - imgHeight) / 2f);
            // Proportionally scales the image to make its width equal its available width
            // or/and height equal its available height.
            mImageMatrix.postScale(mFitWidthImageScale, mFitWidthImageScale, width / 2f, height / 2f);
        }
        setImageMatrix(mImageMatrix);
    }

    /**
     * Resets the image's scale and translation to the initial values that controlled how
     * the image showed to the user.
     */
    public void reinitializeImage() {
        cancelImageTransformations();
        initializeImage();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (BuildConfig.DEBUG) {
            //@formatter:off
            Log.i(TAG, "Size of GestureImageView changes: "
                    + "oldw= " + oldw + "   " + "oldh= " + oldh + "   "
                    + "w= "    + w    + "   " + "h= "    + h);
            //@formatter:on
        }
        if (oldw == 0 && oldh == 0 /* This view is just added to the view hierarchy */) {
            initializeImage();
        } else {
            reinitializeImage();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelImageTransformations();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Only when the image gestures are enabled and the current view has been set an image
        // can we process the touch events.
        if ((mPrivateFlags & PFLAG_IMAGE_GESTURES_ENABLED) == 0 || getDrawable() == null) {
            clearTouch();
            return super.onTouchEvent(event);
        }

        final int actionMasked = event.getActionMasked();
        if (actionMasked == MotionEvent.ACTION_DOWN) {
            // Make sure the touch caches are in the initial state when a new gesture starts.
            resetTouch();
        }

        if (mVelocityTracker == null)
            mVelocityTracker = VelocityTracker.obtain();
        mVelocityTracker.addMovement(event);

        if (mGestureDetector.onTouchEvent(event)) // Monitor single tap and double tap events
            return true;
        mScaleGestureDetector.onTouchEvent(event); // Monitor the scale gestures

        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                onPointerDown(event);
                break;

            case MotionEvent.ACTION_MOVE:
                if (!onPointerMove(event)) {
                    return false;
                }

                if ((mPrivateFlags & PFLAG_IMAGE_BEING_DRAGGED) == 0) {
                    final float absDx = Math.abs(mTouchX[mTouchX.length - 1] - mDownX);
                    final float absDy = Math.abs(mTouchY[mTouchY.length - 1] - mDownY);
                    if (absDx * absDx + absDy * absDy > mTouchSlopSquare) {
                        mPrivateFlags |= PFLAG_IMAGE_BEING_DRAGGED;
                        requestParentDisallowInterceptTouchEvent();
                        cancelImageTransformations();
                        ensureImageMatrix();
                    }
                } else {
                    // If we are allowed to move the image via single finger when it hasn't been
                    // zoomed in, then we can make it translated, or else it will not be moved
                    // unless we are touching it using multiple fingers.
                    if ((mPrivateFlags & PFLAG_MOVE_UNMAGNIFIED_IMAGE_VIA_SINGLE_FINGER_ALLOWED) == 0
                            && event.getPointerCount() == 1) {
                        mImageMatrix.getValues(mImageMatrixValues);
                        final float scaleX = mImageMatrixValues[Matrix.MSCALE_X];
                        final float scaleY = mImageMatrixValues[Matrix.MSCALE_Y];
                        if (scaleX <= mFitCenterImageScale && scaleY <= mFitCenterImageScale) break;
                    }

                    final int width = getDrawingWidth();
                    final int height = getDrawingHeight();

                    resolveImageBounds(mImageMatrix);
                    final float imgWidth = mImageBounds.width();
                    final float imgHeight = mImageBounds.height();

                    float dx = mTouchX[mTouchX.length - 1] - mTouchX[mTouchX.length - 2];
                    float dy = mTouchY[mTouchY.length - 1] - mTouchY[mTouchY.length - 2];
                    // Disallow an enlarged image to scroll in a case where the user prefers it
                    // to be not moved in an undesired direction as the user is fast scrolling
                    // the screen, which may eventually trigger a fling gesture.
                    if (imgWidth > width || imgHeight > height) {
                        if (imgWidth >= width) {
                            if (mImageBounds.left + dx > 0) {
                                dx = -mImageBounds.left;
                            } else if (mImageBounds.right + dx < width) {
                                dx = width - mImageBounds.right;
                            }
                        } else {
                            dx = (width + imgWidth) / 2f - mImageBounds.right;
                        }
                        if (imgHeight >= height) {
                            if (mImageBounds.top + dy > 0) {
                                dy = -mImageBounds.top;
                            } else if (mImageBounds.bottom + dy < height) {
                                dy = height - mImageBounds.bottom;
                            }
                        } else {
                            dy = (height + imgHeight) / 2f - mImageBounds.bottom;
                        }
                    }
                    if (dx != 0 || dy != 0) {
                        mImageMatrix.postTranslate(dx, dy);
                        setImageMatrix(mImageMatrix);
                    }
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(event);
                break;

            case MotionEvent.ACTION_UP:
                if ((mPrivateFlags & PFLAG_IMAGE_BEING_DRAGGED) == 0
                        && (mPrivateFlags & PFLAG_HAS_PERFORMED_LONG_CLICK) != 0
                        && (mPrivateFlags & PFLAG_LONG_CLICK_CONSUMED) == 0) {
                    performClick();
                }
            case MotionEvent.ACTION_CANCEL:
                try {
                    if ((mPrivateFlags & PFLAG_IMAGE_BEING_DRAGGED) == 0) {
                        break;
                    }

                    final int width = getDrawingWidth();
                    final int height = getDrawingHeight();

                    resolveImageBounds(mImageMatrix);
                    mImageMatrix.getValues(mImageMatrixValues);
                    final float scaleX = mImageMatrixValues[Matrix.MSCALE_X];
                    final float scaleY = mImageMatrixValues[Matrix.MSCALE_Y];
                    final float translationX = mImageMatrixValues[Matrix.MTRANS_X];
                    final float translationY = mImageMatrixValues[Matrix.MTRANS_Y];

                    // If the current scale of the image is larger than the maximum scale
                    // it can be scaled to, zoom it out to that scale.
                    if (scaleX > mImageMaxScale || scaleY > mImageMaxScale) {
                        mTmpMatrix.set(mImageMatrix);
                        mTmpMatrix.postScale(
                                mImageMaxScale / scaleX, mImageMaxScale / scaleY,
                                width / 2f, height / 2f);
                        computeImageTranslationByOnScaled(mTmpMatrix, mTmpPointF);
                        mTmpPointF.offset(translationX, translationY);

                        startImageScaleAndTranslate(
                                scaleX, scaleY, mImageMaxScale, mImageMaxScale,
                                width / 2f, height / 2f,
                                translationX, translationY, mTmpPointF.x, mTmpPointF.y,
                                DEFAULT_DURATION_TRANSFORM_IMAGE);
                        break;

                        // If the current scale of the image is smaller than the scale that makes
                        // it appear center inside this view (just as the FIT_CENTER ScaleType does),
                        // then we need to zoom it in to that scale.
                    } else if (scaleX < mFitCenterImageScale || scaleY < mFitCenterImageScale) {
                        mTmpMatrix.set(mImageMatrix);
                        mTmpMatrix.postScale(
                                mFitCenterImageScale / scaleX, mFitCenterImageScale / scaleY,
                                mImageBounds.left, mImageBounds.top);
                        computeImageTranslationByOnScaled(mTmpMatrix, mTmpPointF);
                        mTmpPointF.offset(translationX, translationY);

                        startImageScaleAndTranslate(
                                scaleX, scaleY, mFitCenterImageScale, mFitCenterImageScale,
                                mImageBounds.left, mImageBounds.top,
                                translationX, translationY, mTmpPointF.x, mTmpPointF.y,
                                DEFAULT_DURATION_TRANSFORM_IMAGE);
                        break;
                    }

                    // No scaling is needed below
                    mVelocityTracker.computeCurrentVelocity(1000, mMaximumFlingVelocity);
                    final float vx = mVelocityTracker.getXVelocity(mActivePointerId);
                    final float vy = mVelocityTracker.getYVelocity(mActivePointerId);
                    // If one of the velocities is not less than our minimum fling velocity,
                    // treat it as fling as user raises up his/her last finger that is
                    // touching the screen.
                    if ((Math.abs(vx) >= mMinimumFlingVelocity
                            || Math.abs(vy) >= mMinimumFlingVelocity)) {
                        cancelImageTransformations();
                        getImageTransformer().fling(translationX, translationY, vx, vy,
                                width, height, mImageBounds.width(), mImageBounds.height());
                        break;
                    }
                    // Not else!
                    // Here regard it as a normal scroll
                    computeImageTranslationByOnScaled(mImageMatrix, mTmpPointF);
                    mTmpPointF.offset(translationX, translationY);
                    startImageScaleAndTranslate(
                            scaleX, scaleY, scaleX, scaleY, 0, 0,
                            translationX, translationY, mTmpPointF.x, mTmpPointF.y,
                            DEFAULT_DURATION_TRANSFORM_IMAGE);
                    break;
                } finally {
                    clearTouch();
                }
        }
        return true;
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

    /*synthetic*/ void requestParentDisallowInterceptTouchEvent() {
        ViewParent parent = getParent();
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
        mActivePointerId = ViewDragHelper.INVALID_POINTER;
        mPrivateFlags &= ~(PFLAG_IMAGE_BEING_DRAGGED
                | PFLAG_HAS_PERFORMED_LONG_CLICK | PFLAG_LONG_CLICK_CONSUMED);
        if (mVelocityTracker != null) {
            mVelocityTracker.clear();
        }
    }

    protected class OnImageGestureListener extends GestureDetector.SimpleOnGestureListener
            implements ScaleGestureDetector.OnScaleGestureListener {

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            performClick();
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            mPrivateFlags |= PFLAG_HAS_PERFORMED_LONG_CLICK;
            final boolean consumed;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                consumed = performLongClick(e.getX(), e.getY());
            } else {
                consumed = performLongClick();
            }
            if (consumed) {
                mPrivateFlags |= PFLAG_LONG_CLICK_CONSUMED;
            }
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            ensureImageMatrix();

            mImageMatrix.getValues(mImageMatrixValues);
            final float scaleX = mImageMatrixValues[Matrix.MSCALE_X];
            final float scaleY = mImageMatrixValues[Matrix.MSCALE_Y];
            final float translationX = mImageMatrixValues[Matrix.MTRANS_X];
            final float translationY = mImageMatrixValues[Matrix.MTRANS_Y];

            final float toScaleX, toScaleY;
            // Take very small floating-point error into account ( + 0.01)
            final float fitCenterScale = mFitCenterImageScale + 0.01f;
            // If the image has been enlarged, zoom it out to the scale that makes it fit this view's
            // center on the user's double tapping.
            if (scaleX > fitCenterScale || scaleY > fitCenterScale)
                toScaleX = toScaleY = mFitCenterImageScale;
            else // else make it zoomed in
                toScaleX = toScaleY = mDoubleTapMagnifiedImageScale;

            final float pivotX = e.getX();
            final float pivotY = e.getY();

            mTmpMatrix.set(mImageMatrix);
            mTmpMatrix.postScale(toScaleX / scaleX, toScaleY / scaleY, pivotX, pivotY);
            computeImageTranslationByOnScaled(mTmpMatrix, mTmpPointF);
            mTmpPointF.offset(translationX, translationY);

            startImageScaleAndTranslate(
                    scaleX, scaleY, toScaleX, toScaleY, pivotX, pivotY,
                    translationX, translationY, mTmpPointF.x, mTmpPointF.y,
                    DEFAULT_DURATION_TRANSFORM_IMAGE);
            return true;
        }

        @Override
        public boolean onDoubleTapEvent(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            if ((mPrivateFlags & PFLAG_IMAGE_BEING_DRAGGED) == 0) {
                mPrivateFlags |= PFLAG_IMAGE_BEING_DRAGGED;
                requestParentDisallowInterceptTouchEvent();
                cancelImageTransformations();
                // Make sure of our matrix for fear that the values of the current matrix
                // of the image might have been changed.
                ensureImageMatrix();
            }
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            mImageMatrix.getValues(mImageMatrixValues);
            final float scaleX = mImageMatrixValues[Matrix.MSCALE_X];
            final float scaleY = mImageMatrixValues[Matrix.MSCALE_Y];

            final float scale = detector.getScaleFactor();
            final float toScaleX, toScaleY;
            /*
             * Adjust the toScaleX and toScaleY to make them within the range of the scales
             * that the image can be scaled to.
             */
            final float maxScale = mImageMaxScale * IMAGE_OVERSCALE_TIMES_ON_MAXIMIZED;
            // toScaleX
            if (scale * scaleX > maxScale) toScaleX = maxScale / scaleX;
            else if (scale * scaleX < mImageMinScale) toScaleX = mImageMinScale / scaleX;
            else toScaleX = scale;
            // toScaleY
            if (scale * scaleY > maxScale) toScaleY = maxScale / scaleY;
            else if (scale * scaleY < mImageMinScale) toScaleY = mImageMinScale / scaleY;
            else toScaleY = scale;

            mImageMatrix.postScale(toScaleX, toScaleY, detector.getFocusX(), detector.getFocusY());
            setImageMatrix(mImageMatrix);
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
        }
    }

    /**
     * Computes the displacement the image will need to be translated by after it is zoomed.
     *
     * @param matrix The matrix that will be finally set for this view
     * @param out    A PointF to receive the horizontal and the vertical displacements
     */
    public void computeImageTranslationByOnScaled(@NonNull Matrix matrix, @NonNull PointF out) {
        final int width = getDrawingWidth();
        final int height = getDrawingHeight();

        float dx = 0, dy = 0;
        resolveImageBounds(matrix);
        // If the width of the image is greater than or equal to the width of the view,
        // let it fill the current view horizontally,
        if (mImageBounds.width() >= width) {
            if (mImageBounds.left > 0) {
                dx = -mImageBounds.left;
            } else if (mImageBounds.right < width) {
                dx = width - mImageBounds.right;
            }
            // else let it be horizontally centered.
        } else {
            dx = (width + mImageBounds.width()) / 2f - mImageBounds.right;
        }
        // If the height of the image is greater than or equal to the height of the view,
        // let it fill the current view vertically,
        if (mImageBounds.height() >= height) {
            if (mImageBounds.top > 0) {
                dy = -mImageBounds.top;
            } else if (mImageBounds.bottom < height) {
                dy = height - mImageBounds.bottom;
            }
            // else let it be vertically centered.
        } else {
            dy = (height + mImageBounds.height()) / 2f - mImageBounds.bottom;
        }

        out.set(dx, dy);
    }

    /**
     * Call this when you want to make the image spring back into a valid coordinate range
     * following an overscroll.
     *
     * @param dx           Total displacement in the horizontal direction of the image at the end
     *                     of springback.
     * @param dy           Total displacement in the vertical direction of the image at the end
     *                     of springback.
     * @param transitionDx Displacement of the image along the x axis in all when overscroll
     *                     is finished and springback is about to happen.
     * @param transitionDy Displacement of the image along the y axis in all when overscroll
     *                     is finished and springback is about to happen.
     * @deprecated Use {@link #startImageOverScrollAndSpringBack(float, float, int)} instead.
     * The {@code dx} and {@code dy} currently are ignored.
     */
    @Deprecated()
    public void startImageOverScrollAndSpringBack(
            float dx, float dy, float transitionDx, float transitionDy) {
        startImageOverScrollAndSpringBack(transitionDx, transitionDy,
                DEFAULT_DURATION_TRANSFORM_IMAGE);
    }

    /**
     * Call this when you want to make the image spring back into a valid coordinate range
     * following an overscroll.
     *
     * @param dx       Total displacement of the image along the x axis when overscroll is finished
     *                 and springback is about to happen.
     * @param dy       Total displacement of the image along the y axis when overscroll is finished
     *                 and springback is about to happen.
     * @param duration specify how long the overscroll will last for, in milliseconds.
     */
    public void startImageOverScrollAndSpringBack(float dx, float dy, int duration) {
        if (getDrawable() == null || dx == 0 && dy == 0) {
            return;
        }

        cancelImageTransformations();
        ensureImageMatrix();

        final int width = getDrawingWidth();
        final int height = getDrawingHeight();

        resolveImageBounds(mImageMatrix);
        final float imgWidth = mImageBounds.width();
        final float imgHeight = mImageBounds.height();

        mImageMatrix.getValues(mImageMatrixValues);
        final float startX = mImageMatrixValues[Matrix.MTRANS_X];
        final float startY = mImageMatrixValues[Matrix.MTRANS_Y];
        final float overscrollX = startX + dx;
        final float overscrollY = startY + dy;

        getImageTransformer().startOverScrollAndSpringBack(startX, startY, overscrollX, overscrollY,
                width, height, imgWidth, imgHeight, duration);
    }

    /**
     * @deprecated Use {@link #startImageScaleAndTranslate(float, float, float, float,
     * float, float, float, float, float, float, int)} instead.
     */
    @Deprecated
    public void transformImage(
            float fromScaleX, float fromScaleY, float toScaleX, float toScaleY,
            float pivotX, float pivotY, float fromX, float fromY, float toX, float toY,
            int duration) {
        startImageScaleAndTranslate(fromScaleX, fromScaleY, toScaleX, toScaleY,
                pivotX, pivotY, fromX, fromY, toX, toY, duration);
    }

    /**
     * Smoothly scales and translates the image through animator.
     *
     * @param fromScaleX the current horizontal scale of the image
     * @param fromScaleY the current vertical scale of the image
     * @param toScaleX   the horizontal scale that the image will be scaled to
     * @param toScaleY   the vertical scale that the image will be scaled to
     * @param pivotX     the x coordinate of the pivot point of the scale transformation
     * @param pivotY     the y coordinate of the pivot point of the scale transformation
     * @param fromX      the current translation x of the image
     * @param fromY      the current translation y of the image
     * @param toX        the final translation x for the image to translate to
     * @param toY        the final translation y for the image to translate to
     * @param duration   the length of the animation, in milliseconds. This value cannot be negative.
     */
    public void startImageScaleAndTranslate(
            float fromScaleX, float fromScaleY, float toScaleX, float toScaleY,
            float pivotX, float pivotY, float fromX, float fromY, float toX, float toY,
            int duration) {
        if (getDrawable() == null
                || fromScaleX == toScaleX && fromScaleY == toScaleY && fromX == toX && fromY == toY) {
            return;
        }
        cancelImageTransformations();
        ensureImageMatrix();
        getImageTransformer().startScaleAndTranslate(fromScaleX, fromScaleY, toScaleX, toScaleY,
                pivotX, pivotY, fromX, fromY, toX, toY, duration);
    }

    /**
     * Start translating image based on a fling gesture. The distance traveled will
     * depend on the initial velocity of the fling.
     */
    public void flingImage(float velocityX, float velocityY) {
        if (getDrawable() == null
                || velocityX < mMinimumFlingVelocity && velocityY < mMinimumFlingVelocity) {
            return;
        }

        cancelImageTransformations();
        ensureImageMatrix();

        final int width = getDrawingWidth();
        final int height = getDrawingHeight();

        resolveImageBounds(mImageMatrix);
        final float imgWidth = mImageBounds.width();
        final float imgHeight = mImageBounds.height();

        mImageMatrix.getValues(mImageMatrixValues);
        final float startX = mImageMatrixValues[Matrix.MTRANS_X];
        final float startY = mImageMatrixValues[Matrix.MTRANS_Y];

        getImageTransformer().fling(startX, startY, velocityX, velocityY,
                width, height, imgWidth, imgHeight);
    }

    private final class ImageTransformer implements Runnable {

        private final GestureImageView mView = GestureImageView.this;

        private final OverScroller mTranslator;
        private final OverScroller mScaler;

        private float mLastTransX, mLastTransY;
        private float mLastScaleX, mLastScaleY;
        private float mScalingPivotX, mScalingPivotY;

        private Predicate<Void> mSpringBackJob;

        private boolean mRunning;

        ImageTransformer(Context context) {
            mTranslator = new OverScroller(context, sDecelerateInterpolator);
            mScaler = new OverScroller(context, sDecelerateInterpolator);
        }

        @Override
        public void run() {
            boolean continueRunning = false;

            if (mScaler.computeScrollOffset()) {
                mImageMatrix.postScale(
                        mScaler.getCurrX() / mLastScaleX, mScaler.getCurrY() / mLastScaleY,
                        mScalingPivotX, mScalingPivotY);

                mLastScaleX = mScaler.getCurrX();
                mLastScaleY = mScaler.getCurrY();

                continueRunning = true;
            }
            if (mTranslator.computeScrollOffset()) {
                mImageMatrix.postTranslate(
                        (mTranslator.getCurrX() - mLastTransX)
                                * (continueRunning ? mScaler.getCurrX() / mScaler.getFinalX() : 1f),
                        (mTranslator.getCurrY() - mLastTransY)
                                * (continueRunning ? mScaler.getCurrY() / mScaler.getFinalY() : 1f));

                mLastTransX = mTranslator.getCurrX();
                mLastTransY = mTranslator.getCurrY();

                continueRunning = true;
            } else {
                Predicate<Void> springBackJob = mSpringBackJob;
                if (springBackJob != null) {
                    mSpringBackJob = null;
                    if (springBackJob.test(null)) {
                        run();
                        return;
                    }
                }
            }

            mRunning = continueRunning;
            if (continueRunning) {
                setImageMatrix(mImageMatrix);
                postOnAnimation();
            }
        }

        void startOverScrollAndSpringBack(
                float startX, float startY, float overscrollX, float overscrollY,
                float viewWidth, float viewHeight, float imgWidth, float imgHeight,
                int duration) {
            mLastTransX = startX;
            mLastTransY = startY;
            mTranslator.startScroll(startX, startY,
                    overscrollX - startX, overscrollY - startY, duration);

            mSpringBackJob = unused -> {
                float minX, maxX;
                float minY, maxY;
                if (imgWidth < viewWidth) {
                    minX = (viewWidth - imgWidth) / 2;
                    maxX = minX;
                } else {
                    minX = -(imgWidth - viewWidth);
                    maxX = 0;
                }
                if (imgHeight < viewHeight) {
                    minY = (viewHeight - imgHeight) / 2;
                    maxY = minY;
                } else {
                    minY = -(imgHeight - viewHeight);
                    maxY = 0;
                }
                return mTranslator.springBack(
                        mTranslator.getCurrX(), mTranslator.getCurrY(), minX, maxX, minY, maxY);
            };

            postOnAnimation();
        }

        void startScaleAndTranslate(
                float fromScaleX, float fromScaleY, float toScaleX, float toScaleY,
                float pivotX, float pivotY, float fromX, float fromY, float toX, float toY,
                int duration) {
            mScalingPivotX = pivotX;
            mScalingPivotY = pivotY;
            mLastScaleX = fromScaleX;
            mLastScaleY = fromScaleY;
            mScaler.startScroll(fromScaleX, fromScaleY,
                    toScaleX - fromScaleX, toScaleY - fromScaleY, duration);

            mLastTransX = fromX;
            mLastTransY = fromY;
            mTranslator.startScroll(fromX, fromY, toX - fromX, toY - fromY, duration);

            postOnAnimation();
        }

        void fling(
                float startX, float startY, float velocityX, float velocityY,
                float viewWidth, float viewHeight, float imgWidth, float imgHeight) {
            float minX, maxX;
            float minY, maxY;
            if (imgWidth < viewWidth) {
                minX = (viewWidth - imgWidth) / 2;
                maxX = minX;
            } else {
                minX = -(imgWidth - viewWidth);
                maxX = 0;
            }
            if (imgHeight < viewHeight) {
                minY = (viewHeight - imgHeight) / 2;
                maxY = minY;
            } else {
                minY = -(imgHeight - viewHeight);
                maxY = 0;
            }

            mLastTransX = startX;
            mLastTransY = startY;
            mTranslator.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY,
                    minX == maxX ? 0 : mImageOverTranslation,
                    minY == maxY ? 0 : mImageOverTranslation);
            postOnAnimation();
        }

        private void postOnAnimation() {
            mRunning = true;
            ViewCompatibility.postOnAnimation(mView, this::run);
        }

        void cancel() {
            if (mRunning) {
                mRunning = false;
                mSpringBackJob = null;
                mTranslator.forceFinished(true);
                mScaler.forceFinished(true);
                ViewCompatibility.removeCallbacks(mView, this::run);
            }
        }

        void end() {
            if (mRunning) {
                mRunning = false;
                if (mSpringBackJob != null) {
                    mSpringBackJob.test(null);
                    mSpringBackJob = null;
                }
                mTranslator.abortAnimation();
                mScaler.abortAnimation();
                run();
                ViewCompatibility.removeCallbacks(mView, this::run);
            }
        }
    }

    /**
     * Cancels the running animator and the pending animation that will bounce this image back
     * after it is over-translated.
     */
    public void cancelImageTransformations() {
        if (mImageTransformer != null) {
            mImageTransformer.cancel();
        }
    }

    /**
     * Ensures our values of the matrix {@link #mImageMatrix} are equal to the values of
     * the current matrix of the image since they may have been changed in other class.
     */
    /*synthetic*/ void ensureImageMatrix() {
        Matrix matrix = getImageMatrix();
        if (!mImageMatrix.equals(matrix))
            mImageMatrix.set(matrix);
    }

    /**
     * Resolves the image bounds by providing a matrix.
     *
     * @param matrix the matrix used to measure this image
     */
    /*package*/ void resolveImageBounds(Matrix matrix) {
        Drawable d = getDrawable();
        if (d == null) {
            mImageBounds.setEmpty();
        } else {
            mImageBounds.set(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
            matrix.mapRect(mImageBounds);
        }
    }

    /**
     * Gets the bounds of this image by providing a rectangle into which its 4 corners are put
     */
    public void getImageBounds(@NonNull RectF out) {
        Drawable d = getDrawable();
        if (d == null) {
            out.setEmpty();
        } else {
            out.set(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
            getImageMatrix().mapRect(out);
        }
    }

    /** Return the width of the visible drawing bounds for this view. */
    protected int getDrawingWidth() {
        return getWidth() - getPaddingLeft() - getPaddingRight();
    }

    /** Return the height of the visible drawing bounds for this view. */
    protected int getDrawingHeight() {
        return getHeight() - getPaddingTop() - getPaddingBottom();
    }
}
