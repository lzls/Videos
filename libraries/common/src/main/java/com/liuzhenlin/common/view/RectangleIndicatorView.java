/*
 * Created on 2024-4-17 5:51:06 PM.
 * Copyright © 2024 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.util.ObjectsCompat;
import androidx.core.view.ViewCompat;

import com.liuzhenlin.common.R;
import com.liuzhenlin.common.utils.DensityUtils;

public class RectangleIndicatorView extends View {

    private int mMaxWidth;
    private int mMaxHeight;

    private final Paint mPaint = new Paint();

    private float mOuterRectStrokeWidth;
    private float mInnerRectStrokeWidth;

    private int mOuterRectStrokeColor;
    private int mInnerRectStrokeColor;

    private int mScrimDoubleRectColor;

    private final RectF mOuterRectF = new RectF();
    private final RectF mInnerRectF = new RectF();

    private final Path mScrimDoubleRectPath = new Path();

    private Callback mCallback;

    public interface Callback {
        @Nullable
        Bitmap getDrawingBitmap(int width, int height);

        boolean continueDrawingBitmap();
    }

    public void setCallback(@Nullable Callback callback) {
        mCallback = callback;
    }

    public RectangleIndicatorView(Context context) {
        this(context, null);
    }

    public RectangleIndicatorView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RectangleIndicatorView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float dp = DensityUtils.dp2px(context, 1);
        TypedArray ta = context.obtainStyledAttributes(
                attrs, R.styleable.RectangleIndicatorView, defStyleAttr, 0);
        mMaxWidth = ta.getDimensionPixelSize(R.styleable.RectangleIndicatorView_android_maxWidth, 0);
        mMaxHeight =
                ta.getDimensionPixelSize(R.styleable.RectangleIndicatorView_android_maxHeight, 0);
        mOuterRectStrokeWidth =
                ta.getDimension(R.styleable.RectangleIndicatorView_outerRectStrokeWidth, dp);
        mInnerRectStrokeWidth =
                ta.getDimension(R.styleable.RectangleIndicatorView_innerRectStrokeWidth, dp);
        mOuterRectStrokeColor =
                ta.getColor(R.styleable.RectangleIndicatorView_outerRectStrokeColor,
                        ContextCompat.getColor(context, R.color.lightBlue));
        mInnerRectStrokeColor =
                ta.getColor(R.styleable.RectangleIndicatorView_innerRectStrokeColor,
                        ContextCompat.getColor(context, R.color.pink));
        mScrimDoubleRectColor =
                ta.getColor(R.styleable.RectangleIndicatorView_scrimDoubleRectColor, 0x40_000000);
        ta.recycle();
    }

    public int getMaximumWidth() {
        return mMaxWidth;
    }

    public void setMaximumWidth(int maxWidth) {
        if (mMaxWidth != maxWidth) {
            mMaxWidth = maxWidth;
            requestLayout();
        }
    }

    public int getMaximumHeight() {
        return mMaxHeight;
    }

    public void setMaximumHeight(int maxHeight) {
        if (mMaxHeight != maxHeight) {
            mMaxHeight = maxHeight;
            requestLayout();
        }
    }

    public float getOuterRectStrokeWidth() {
        return mOuterRectStrokeWidth;
    }

    public void setOuterRectStrokeWidth(float width) {
        if (mOuterRectStrokeWidth != width) {
            mOuterRectStrokeWidth = width;
            invalidateIfBothRectAreNotEmpty();
        }
    }

    public float getInnerRectStrokeWidth() {
        return mInnerRectStrokeWidth;
    }

    public void setInnerRectStrokeWidth(float width) {
        if (mInnerRectStrokeWidth != width) {
            mInnerRectStrokeWidth = width;
            invalidateIfBothRectAreNotEmpty();
        }
    }

    public int getOuterRectStrokeColor() {
        return mOuterRectStrokeColor;
    }

    public void setOuterRectStrokeColor(int color) {
        if (mOuterRectStrokeColor != color) {
            mOuterRectStrokeColor = color;
            invalidateIfBothRectAreNotEmpty();
        }
    }

    public int getInnerRectStrokeColor() {
        return mInnerRectStrokeColor;
    }

    public void setInnerRectStrokeColor(int color) {
        if (mInnerRectStrokeColor != color) {
            mInnerRectStrokeColor = color;
            invalidateIfBothRectAreNotEmpty();
        }
    }

    public int getScrimDoubleRectColor() {
        return mScrimDoubleRectColor;
    }

    public void setScrimDoubleRectColor(int color) {
        if (mScrimDoubleRectColor != color) {
            mScrimDoubleRectColor = color;
            invalidateIfBothRectAreNotEmpty();
        }
    }

    private void invalidateIfBothRectAreNotEmpty() {
        if (!mOuterRectF.isEmpty() && !mInnerRectF.isEmpty()) {
            invalidate();
        }
    }

    public void setRectangles(@NonNull RectF outerRectF, @NonNull RectF innerRectF) {
        if (!ObjectsCompat.equals(outerRectF, mOuterRectF)
                || !ObjectsCompat.equals(innerRectF, mInnerRectF)) {
            mOuterRectF.set(outerRectF);
            mInnerRectF.set(innerRectF);
//            if (!outerRectF.contains(innerRectF)) {
//                throw new IllegalArgumentException("innerRectF is not inside or equal to outerRectF");
//            }
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Drawable background = getBackground();
        int width = getDefaultSize(background == null ? 0 : background.getMinimumWidth(),
                widthMeasureSpec);
        int height = getDefaultSize(background == null ? 0 : background.getMinimumHeight(),
                heightMeasureSpec);

        width = Math.max(width, ViewCompat.getMinimumWidth(this));
        height = Math.max(height, ViewCompat.getMinimumHeight(this));
        if (mMaxWidth > 0) {
            width = Math.min(width, mMaxWidth);
        }
        if (mMaxHeight > 0) {
            height = Math.min(height, mMaxHeight);
        }

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        int paddingRight = getPaddingRight();
        int paddingBottom = getPaddingBottom();
        int width = getWidth() - paddingLeft - paddingRight;
        int height = getHeight() - paddingTop - paddingBottom;

        if (mCallback != null) {
            Bitmap thumb = mCallback.getDrawingBitmap(width, height);
            if (thumb != null) {
                mPaint.reset();
                canvas.drawBitmap(thumb, paddingLeft, paddingTop, mPaint);
                thumb.recycle();
            }

            if (mCallback.continueDrawingBitmap()) {
                ViewCompat.postInvalidateOnAnimation(this);
            }
        }

        RectF outerRectF = mOuterRectF;
        RectF innerRectF = mInnerRectF;
        if (!outerRectF.isEmpty() && !innerRectF.isEmpty()) {
            float innerStrokeWidth = mInnerRectStrokeWidth;
            float outerStrokeWidth = mOuterRectStrokeWidth;
            float innerRectWidth = width / outerRectF.width() * innerRectF.width();
            float innerRectHeight = height / outerRectF.height() * innerRectF.height();
            float innerRectLeft =
                    paddingLeft + width / outerRectF.width() * (innerRectF.left - outerRectF.left);
            float innerRectTop =
                    paddingTop + height / outerRectF.height() * (innerRectF.top - outerRectF.top);

            mPaint.setStyle(Paint.Style.STROKE);

            mPaint.setStrokeWidth(outerStrokeWidth);
            mPaint.setColor(mOuterRectStrokeColor);
            canvas.drawRect(
                    paddingLeft + outerStrokeWidth / 2,
                    paddingTop + outerStrokeWidth / 2,
                    paddingLeft + width - outerStrokeWidth / 2,
                    paddingTop + height - outerStrokeWidth / 2,
                    mPaint);

            mPaint.setStrokeWidth(innerStrokeWidth);
            mPaint.setColor(mInnerRectStrokeColor);
            canvas.drawRect(
                    innerRectLeft + outerStrokeWidth + innerStrokeWidth * 0.5f,
                    innerRectTop + outerStrokeWidth + innerStrokeWidth * 0.5f,
                    innerRectLeft + innerRectWidth - outerStrokeWidth - innerStrokeWidth * 0.5f,
                    innerRectTop + innerRectHeight - outerStrokeWidth - innerStrokeWidth * 0.5f,
                    mPaint);

            mPaint.reset();
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(mScrimDoubleRectColor);
            mScrimDoubleRectPath.reset();
            mScrimDoubleRectPath.addRect(
                    paddingLeft + outerStrokeWidth, paddingTop + outerStrokeWidth,
                    paddingLeft + width - outerStrokeWidth, paddingTop + height - outerStrokeWidth,
                    Path.Direction.CW);
            mScrimDoubleRectPath.addRect(
                    innerRectLeft + outerStrokeWidth,
                    innerRectTop + outerStrokeWidth,
                    innerRectLeft + innerRectWidth - outerStrokeWidth,
                    innerRectTop + innerRectHeight - outerStrokeWidth,
                    Path.Direction.CCW);
            canvas.drawPath(mScrimDoubleRectPath, mPaint);
        }
    }
}
