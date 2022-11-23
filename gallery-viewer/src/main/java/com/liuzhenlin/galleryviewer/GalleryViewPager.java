package com.liuzhenlin.galleryviewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.customview.widget.ViewDragHelper;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

/**
 * @author 刘振林
 */
public class GalleryViewPager extends ViewPager {
    private static final String TAG = "GalleryViewPager";

    protected final int mTouchSlop;

    /*synthetic*/ int mActivePointerId = ViewDragHelper.INVALID_POINTER;
    private float mDownX;
    private float mDownY;

    /*synthetic*/ VelocityTracker mVelocityTracker;

    /**
     * The minimum velocity to fling this view when the image in the current page is magnified
     *
     * @see GestureImageView
     */
    private final float mMinimumFlingVelocityOnCurrImageMagnified; // 400 dp/s

    protected final float mMaximumFlingVelocity;

    /** Position of the last selected page */
    /*synthetic*/ int mLastSelectedPageIndex;
    /*synthetic*/ boolean mFirstLayout;
    /*synthetic*/ boolean mImageOverScrollEnabled;

    private final OnPageChangeListener mInternalOnPageChangeListener = new SimpleOnPageChangeListener() {
        @Override
        public void onPageSelected(int position) {
            if (!mFirstLayout && mLastSelectedPageIndex != position && mItemCallback != null) {
                Object lastItem = mItemCallback.getItemAt(mLastSelectedPageIndex);
                if (lastItem instanceof GestureImageView) {
                    ((GestureImageView) lastItem).reinitializeImage();
                }

                if (mImageOverScrollEnabled) {
                    Object currentItem = mItemCallback.getItemAt(position);
                    if (currentItem instanceof GestureImageView) {
                        GestureImageView image = (GestureImageView) currentItem;
                        boolean scrollPageLeft = position > mLastSelectedPageIndex;
                        float dx = scrollPageLeft ?
                                -image.mImageOverTranslation : image.mImageOverTranslation;
                        float baseDuration = GestureImageView.DEFAULT_DURATION_TRANSFORM_IMAGE / 2f;
                        float duration;
                        if (mVelocityTracker != null) {
                            mVelocityTracker.computeCurrentVelocity(1000, mMaximumFlingVelocity);
                            float vx = mVelocityTracker.getXVelocity(mActivePointerId);
                            if (vx > 0 && scrollPageLeft || vx < 0 && !scrollPageLeft) {
                                vx = 0;
                            }
                            duration = baseDuration * (1 + (1 - Math.abs(vx) / mMaximumFlingVelocity));
                        } else {
                            duration = baseDuration * 2;
                        }
                        startImageOverScrollAndSpringBack(image, dx, 0, (int) duration);
                    }
                }
            }
            mLastSelectedPageIndex = position;
        }

        void startImageOverScrollAndSpringBack(
                GestureImageView image, float dx, float dy, int duration) {
            if (isLayoutValid(image)) {
                image.startImageOverScrollAndSpringBack(dx, dy, duration);
            } else {
                image.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (isLayoutValid(image)) {
                            if (mImageOverScrollEnabled) {
                                image.startImageOverScrollAndSpringBack(dx, dy, duration);
                            }
                            image.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                        }
                    }
                });
            }
        }

        boolean isLayoutValid(GestureImageView view) {
            return isLaidOut(view) && !view.isLayoutRequested();
        }

        boolean isLaidOut(GestureImageView view) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                return view.isLaidOut();
            }
            return ViewCompat.isAttachedToWindow(view)
                    && (view.getWidth() != 0 || view.getHeight() != 0);
        }
    };

    public GalleryViewPager(@NonNull Context context) {
        this(context, null);
    }

    public GalleryViewPager(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.GalleryViewPager, 0, 0);
        setImageOverScrollEnabled(
                ta.getBoolean(R.styleable.GalleryViewPager_imageOverScrollEnabled, true));
        ta.recycle();

        final float dp = getResources().getDisplayMetrics().density;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mMinimumFlingVelocityOnCurrImageMagnified = 400f * dp;
        mMaximumFlingVelocity = ViewConfiguration.getMaximumFlingVelocity() * dp;
        addOnPageChangeListener(mInternalOnPageChangeListener);
    }

    @Override
    public void clearOnPageChangeListeners() {
        super.clearOnPageChangeListeners();
        addOnPageChangeListener(mInternalOnPageChangeListener);
    }

    /**
     * Returns whether a child {@link GestureImageView} can start to overscroll
     * as it becomes the selected page.
     */
    public boolean isImageOverScrollEnabled() {
        return mImageOverScrollEnabled;
    }

    /**
     * Sets whether to enable the overscroll feature for a child {@link GestureImageView}
     * to overscroll and spring itself back when it becomes the selected page.
     */
    public void setImageOverScrollEnabled(boolean enabled) {
        mImageOverScrollEnabled = enabled;
    }

    @Override
    public void setAdapter(@Nullable PagerAdapter adapter) {
        super.setAdapter(adapter);
        if (adapter != null) {
            mFirstLayout = true;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mFirstLayout = true;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        mFirstLayout = false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int actionMasked = ev.getAction() & MotionEvent.ACTION_MASK;

        if (actionMasked == MotionEvent.ACTION_DOWN) {
            initOrClearVelocityTracker();
        }
        if (mVelocityTracker != null) {
            mVelocityTracker.addMovement(ev);
        }

        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                onPointerDown(ev);
                break;
            case MotionEvent.ACTION_MOVE:
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    Log.e(TAG, "Error processing scroll; pointer index for id "
                            + mActivePointerId + " not found. Did any MotionEvents get skipped?");
                    return false;
                }

                if (mItemCallback == null) break;
                Object item = mItemCallback.getItemAt(getCurrentItem());
                if (item instanceof GestureImageView) {
                    GestureImageView image = (GestureImageView) item;

                    if (ev.getPointerCount() != 1) {
                        return false;
                    }

                    final float absDx = Math.abs(ev.getX() - mDownX);
                    boolean intercept = absDx > mTouchSlop && absDx > Math.abs(ev.getY() - mDownY);
                    if (!intercept) return false;

                    RectF imgBounds = image.mImageBounds;
                    image.getImageBounds(imgBounds);
                    if (imgBounds.isEmpty()) return true;

                    final int imgAvailableWidth =
                            image.getWidth() - image.getPaddingLeft() - image.getPaddingRight();
                    if (imgBounds.width() > imgAvailableWidth) {
                        mVelocityTracker.computeCurrentVelocity(1000);
                        final float vx = mVelocityTracker.getXVelocity(mActivePointerId);

                        // Account for very small floating-point error (+/- 0.01f)
                        final boolean canScrollPageRight = imgBounds.left >= -0.01f
                                && vx >= mMinimumFlingVelocityOnCurrImageMagnified;
                        final boolean canScrollPageLeft = imgBounds.right <= imgAvailableWidth + 0.01f
                                && vx <= -mMinimumFlingVelocityOnCurrImageMagnified;

                        intercept = canScrollPageLeft || canScrollPageRight;
                    }

                    if (intercept) {
                        ViewParent parent = getParent();
                        if (parent != null) {
                            parent.requestDisallowInterceptTouchEvent(true);
                        }
                    }
                    return intercept;
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mActivePointerId = ViewDragHelper.INVALID_POINTER;
                recycleVelocityTracker();
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final int actionMasked = ev.getActionMasked();

        if (actionMasked == MotionEvent.ACTION_DOWN) {
            initOrClearVelocityTracker();
        }
        if (mVelocityTracker != null) {
            mVelocityTracker.addMovement(ev);
        }

        final boolean handled;
        switch (actionMasked) {
            case MotionEvent.ACTION_POINTER_DOWN:
                onPointerDown(ev);
                handled = superOnTouchEvent(ev);
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                handled = superOnTouchEvent(ev);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                handled = superOnTouchEvent(ev);
                mActivePointerId = ViewDragHelper.INVALID_POINTER;
                recycleVelocityTracker();
                break;
            default:
                handled = superOnTouchEvent(ev);
                break;
        }
        return handled;
    }

    private boolean superOnTouchEvent(MotionEvent ev) {
        return super.onTouchEvent(ev);
    }

    private void onPointerDown(MotionEvent e) {
        final int actionIndex = e.getActionIndex();
        mActivePointerId = e.getPointerId(actionIndex);
        mDownX = e.getX(actionIndex);
        mDownY = e.getY(actionIndex);
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
        }
    }

    private void initOrClearVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        } else {
            mVelocityTracker.clear();
        }
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    /*synthetic*/ ItemCallback mItemCallback;

    public void setItemCallback(@Nullable ItemCallback callback) {
        mItemCallback = callback;
    }

    public interface ItemCallback {
        /**
         * @param position the <strong>adapter position</strong> of the item that you want to get
         * @return the item at the specified position
         */
        Object getItemAt(int position);
    }
}
