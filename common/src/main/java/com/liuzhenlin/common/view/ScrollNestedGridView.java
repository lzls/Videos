/*
 * Created on 2022-11-30 5:28:04 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Adapter;
import android.widget.GridView;

/** Special {@link GridView} that can be easily nested in an
    {@link android.widget.HorizontalScrollView HorizontalScrollView} */
public class ScrollNestedGridView extends GridView {

    private int mRequestedHorizontalSpacing;
    private int mRequestedColumnWidth;
    private int mRequestedNumColumns;

    public ScrollNestedGridView(Context context) {
        super(context);
    }

    public ScrollNestedGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ScrollNestedGridView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setHorizontalSpacing(int horizontalSpacing) {
        mRequestedHorizontalSpacing = horizontalSpacing;
        super.setHorizontalSpacing(horizontalSpacing);
    }

    @Override
    public void setNumColumns(int numColumns) {
        mRequestedNumColumns = numColumns;
        super.setNumColumns(numColumns);
    }

    @Override
    public void setColumnWidth(int columnWidth) {
        mRequestedColumnWidth = columnWidth;
        super.setColumnWidth(columnWidth);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        Adapter adapter = getAdapter();
        int numColumns = Math.min(
                mRequestedNumColumns, adapter != null ? adapter.getCount() : AUTO_FIT);
        int columnWidth = mRequestedColumnWidth;
        // HorizontalScrollView replaces the width measure spec mode with MeasureSpec.UNSPECIFIED
        // to see how wide its child would like to be. However, GridView in this case will only
        // claim a very small width size for itself (horizontal paddings of the list + width of
        // the vertical scroll bar), such that items inside it will not have enough space to be
        // laid out. Thus we need remeasure it properly when width measurement mode is UNSPECIFIED
        // and if both columnWidth and numColumns have been set with positive values.
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED
                && columnWidth > 0 && numColumns > 0) {
            int width;
            int requestedWidth = getLayoutParams().width;
            switch (requestedWidth) {
                case LayoutParams.WRAP_CONTENT:
                    int stretchMode = getStretchMode();
                    int listPaddingHorizontal = getListPaddingLeft() + getListPaddingRight();
                    int horizontalSpacing = mRequestedHorizontalSpacing;
                    int childAvailableWidth =
                            columnWidth * numColumns + horizontalSpacing * (numColumns - 1);
                    width = childAvailableWidth + listPaddingHorizontal;
                    // Temporarily disable item/spacing stretching, just for fear that GridView
                    // calculates out a negative spaceLeftOver in the determineColumns() method,
                    // causing unexpected item overlaps.
                    setStretchModeInLayout(NO_STRETCH);
                    super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                            heightMeasureSpec);
                    setStretchModeInLayout(stretchMode);
                    return; // bail out
                case LayoutParams.MATCH_PARENT:
                    width = MeasureSpec.getSize(widthMeasureSpec);
                    break;
                default:
                    width = requestedWidth;
                    break;
            }
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    heightMeasureSpec);
        }
    }

    private boolean mSettingStretchModeInLayout;

    /**
     * Similar to {@link #setStretchMode(int)}, but without requesting a new layout, used during
     * layout pass.
     */
    protected void setStretchModeInLayout(/*@StretchMode*/ int stretchMode) {
        mSettingStretchModeInLayout = true;
        super.setStretchMode(stretchMode);
        mSettingStretchModeInLayout = false;
    }

    @Override
    public int getChildCount() {
        return mSettingStretchModeInLayout ? 0 : super.getChildCount();
    }
}
