/*
 * Created on 2022-11-30 5:28:04 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.view;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.Adapter;
import android.widget.GridView;

import androidx.collection.CircularIntArray;
import androidx.collection.SimpleArrayMap;

import com.liuzhenlin.common.compat.GridViewCompat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Special {@link GridView} that can be easily nested in any scrolling containers, like platform
 * {@link android.widget.HorizontalScrollView HorizontalScrollView} and
 * {@link android.widget.ScrollView ScrollView}, and be correctly measured and displayed.
 */
public class ScrollNestedGridView extends GridView {

    private static final String TAG = "ScrollNestedGridView";

    protected int mRequestedHorizontalSpacing;
    protected int mRequestedColumnWidth;
    protected int mRequestedNumColumns;

    private final CircularIntArray mCompletelyVisibleRowHeights = new CircularIntArray(1 /* min required */);
    private final SimpleArrayMap<View, Integer> mChildrenHeights = new SimpleArrayMap<>(0);
    private boolean mLayoutingChildren;

    private static Method sObtainViewMethod;
    private static Field sIsScrapField;

    private static Field sRecyclerField;
    private static Method sRecycleBinAddScrapViewMethod;
    private static Method sRecycleBinShouldRecycleViewTypeMethod;

    static {
        try {
            Class<AbsListView> absListViewClass = AbsListView.class;

            //noinspection JavaReflectionMemberAccess
            Method obtainViewMethod =
                    absListViewClass.getDeclaredMethod("obtainView", int.class, boolean[].class);
            obtainViewMethod.setAccessible(true);
            //noinspection SoonBlockedPrivateApi,JavaReflectionMemberAccess
            Field isScrapField = absListViewClass.getDeclaredField("mIsScrap");
            isScrapField.setAccessible(true);
            sObtainViewMethod = obtainViewMethod;
            sIsScrapField = isScrapField;

            //noinspection SoonBlockedPrivateApi,JavaReflectionMemberAccess
            Field recyclerField = absListViewClass.getDeclaredField("mRecycler");
            recyclerField.setAccessible(true);
            Class<?> recycleBinClass = null;
            for (Class<?> clazz : absListViewClass.getDeclaredClasses()) {
                if ("RecycleBin".equals(clazz.getSimpleName())) {
                    recycleBinClass = clazz;
                    break;
                }
            }
            if (recycleBinClass != null) {
                Method recycleBinAddScrapMethod =
                        recycleBinClass.getDeclaredMethod("addScrapView", View.class, int.class);
                recycleBinAddScrapMethod.setAccessible(true);
                Method recycleBinShouldRecycleViewTypeMethod =
                        recycleBinClass.getDeclaredMethod("shouldRecycleViewType", int.class);
                recycleBinShouldRecycleViewTypeMethod.setAccessible(true);
                sRecyclerField = recyclerField;
                sRecycleBinAddScrapViewMethod = recycleBinAddScrapMethod;
                sRecycleBinShouldRecycleViewTypeMethod = recycleBinShouldRecycleViewTypeMethod;
            }
        } catch (Exception e) {
            Log.w(TAG, e);
        }
    }

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
        final int itemCount = adapter != null ? adapter.getCount() : 0;
        int numColumns = Math.min(mRequestedNumColumns, itemCount);
        int columnWidth = mRequestedColumnWidth;

        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        // HorizontalScrollView replaces the width measure spec mode with MeasureSpec.UNSPECIFIED
        // to see how wide its child would like to be. However, GridView in this case will only
        // claim a very small width size for itself (horizontal paddings of the list + width of
        // the vertical scroll bar), such that items inside it will not have enough space to be
        // laid out. Thus we need remeasure it properly when width measurement mode is UNSPECIFIED
        // and if both columnWidth and numColumns have been set with positive values.
        if (widthMode == MeasureSpec.UNSPECIFIED && columnWidth > 0 && numColumns > 0) {
            int width;
            final int requestedWidth = getLayoutParams().width;
            switch (requestedWidth) {
                case LayoutParams.WRAP_CONTENT:
                    final int stretchMode = getStretchMode();
                    final int listPaddingHorizontal = getListPaddingLeft() + getListPaddingRight();
                    final int horizontalSpacing = mRequestedHorizontalSpacing;
                    final int childAvailableWidth =
                            columnWidth * numColumns + horizontalSpacing * (numColumns - 1);
                    width = childAvailableWidth + listPaddingHorizontal;
                    // Temporarily disable item/spacing stretching, just for fear that GridView
                    // calculates out a negative spaceLeftOver in the determineColumns() method,
                    // causing unexpected item overlaps.
                    setStretchModeInLayout(NO_STRETCH);
                    super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                            heightMeasureSpec);
                    setStretchModeInLayout(stretchMode);
                    break;
                case LayoutParams.MATCH_PARENT:
                    width = widthSize;
                    super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                            heightMeasureSpec);
                    break;
                default:
                    width = requestedWidth;
                    super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                            heightMeasureSpec);
                    break;
            }
        }

        if (heightMode == MeasureSpec.EXACTLY)
            return;

        numColumns = getNumColumns();
        columnWidth = GridViewCompat.getColumnWidth(this);

        // GridView only measured the first item from its adapter, then the height of each row was
        // assumed to be that item's height, which could cause its content to not be displayed
        // completely when the total height of the rows < the available height of the grid (
        // width size in widthMeasureSpec that came from the parent) and user will need to scroll
        // it up and down.
        final int itemVerticalSpacing = GridViewCompat.getVerticalSpacing(this);
        int height = getListPaddingTop() + getListPaddingBottom();
        int rowHeight = 0;
        for (int i = 0; i < itemCount; i++) {
            View itemView = obtainView(i);
            if (itemView == null) {
                return; // This can happen if reflection access to obtainView(View, int) failed
            }

            ViewGroup.LayoutParams itemLP = (ViewGroup.LayoutParams) itemView.getLayoutParams();
            if (!checkLayoutParams(itemLP)) {
                itemLP = (ViewGroup.LayoutParams) generateDefaultLayoutParams();
                itemView.setLayoutParams(itemLP);
            }
            itemView.measure(
                    getChildMeasureSpec(
                            MeasureSpec.makeMeasureSpec(columnWidth, MeasureSpec.EXACTLY),
                            /* padding= */ 0, itemLP.width),
                    getChildMeasureSpec(
                            MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.UNSPECIFIED),
                            /* padding= */ 0, itemLP.height));
            // Choose the height of the highest item in a row as the row height
            rowHeight = Math.max(rowHeight, itemView.getMeasuredHeight());

            // Recycle the obtained view if the adapter wants it recycled
            if (shouldRecycleViewType(adapter.getItemViewType(i))) {
                recycleView(itemView, i);
            }

            if ((i + 1) % numColumns == 0) {
                if (i != numColumns - 1) { // Not the first row. Plus vertical item spacing.
                    height += itemVerticalSpacing;
                }
                height += rowHeight; // Add up with the row height
                rowHeight = 0;
                if (heightMode == MeasureSpec.AT_MOST && height >= heightSize) {
                    // height measure spec mode is AT_MOST, our view height should not exceed
                    // the parent given height size
                    height = heightSize;
                    break;
                }
            }
        }
        // Count of items in the last row may be smaller than numColumns
        if (rowHeight != 0) {
            height += (itemCount < numColumns ? 0 : itemVerticalSpacing) + rowHeight;
            if (heightMode == MeasureSpec.AT_MOST) {
                // height measure spec mode is AT_MOST, our view height should not exceed
                // the parent given height size
                height = Math.min(height, heightSize);
            }
        }
        if (getMeasuredHeight() != height) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void layoutChildren() {
        superLayoutChildren();
        makeEachRowChildrenMatchRowHeight();
    }

    private void superLayoutChildren() {
        mLayoutingChildren = true;
        super.layoutChildren();
        mLayoutingChildren = false;
    }

    /* Unify the height of each child to the height of the highest item within each row.
     * We do this because the heights of the itemViews may not be an uniform height due to
     * wrap_content height size defined for the item layout, which can cause vertical item spacing
     * to be omitted, and the problem of misalignment of each item in a same row to appear when
     * the grid scrolls up or down. */
    private void makeEachRowChildrenMatchRowHeight() {
        final int childCount = getChildCount();
        final int numColumns = getNumColumns();

        Adapter adapter = getAdapter();
        if (childCount == 0 || adapter == null || adapter.getCount() <= numColumns)
            return;

        final int firstVisibleItemPosition = getFirstVisiblePosition();
        int firstCompletelyVisibleRowFirstChildIndex = -1;
        int rowHeight = 0;
        View child;

        /* Record height of LayoutParams for each item in each row that is displaying numColumns
           count of items. */
        for (int i = 0; i < childCount; i++) {
            child = getChildAt(i);
            if (firstCompletelyVisibleRowFirstChildIndex < 0) {
                int childPosition = firstVisibleItemPosition + i;
                if (childPosition % numColumns != 0) {
                    continue;
                }
                firstCompletelyVisibleRowFirstChildIndex = i;
            }
            rowHeight = Math.max(rowHeight, child.getHeight());
            if ((i - firstCompletelyVisibleRowFirstChildIndex + 1) % numColumns == 0) {
                mCompletelyVisibleRowHeights.addLast(rowHeight);
                rowHeight = 0;
            }
        }
        if (rowHeight > 0) {
            mCompletelyVisibleRowHeights.addLast(rowHeight);
            rowHeight = 0;
        }

        /* Set height of LayoutParams for each item to the height of each row */
        ViewGroup.LayoutParams childLP;
        if (firstCompletelyVisibleRowFirstChildIndex >= 0) {
            int row = 0;
            int col;
            rowHeight = mCompletelyVisibleRowHeights.get(row);
            for (int i = firstCompletelyVisibleRowFirstChildIndex; i < childCount; i++) {
                child = getChildAt(i);
                if (child.getHeight() != rowHeight) {
                    childLP = child.getLayoutParams();
                    mChildrenHeights.put(child, childLP.height);
                    childLP.height = rowHeight;
                    child.forceLayout();
                }
                if (i == childCount - 1) {
                    break; // We reached the last displaying item
                }
                if ((col = (i - firstCompletelyVisibleRowFirstChildIndex) % numColumns)
                        == numColumns - 1) {
                    ++row;
                    rowHeight = mCompletelyVisibleRowHeights.get(row);
                }
            }
        }
        final int partiallyVisibleRowChildCount =
                firstCompletelyVisibleRowFirstChildIndex >= 0 ?
                        firstCompletelyVisibleRowFirstChildIndex + 1 : childCount;
        int partiallyVisibleRowHeight = 0;
        for (int i = partiallyVisibleRowChildCount - 1; i >= 0; i--) {
            child = getChildAt(i);
            partiallyVisibleRowHeight = Math.max(partiallyVisibleRowHeight, child.getHeight());
        }
        for (int i = partiallyVisibleRowChildCount - 1; i >= 0; i--) {
            child = getChildAt(i);
            if (child.getHeight() != partiallyVisibleRowHeight) {
                childLP = child.getLayoutParams();
                mChildrenHeights.put(child, childLP.height);
                childLP.height = partiallyVisibleRowHeight;
                child.forceLayout();
            }
        }

        if (mChildrenHeights.size() > 0) {
            // Adjust the size of each child and reposition them based on the current heights
            // set for their LayoutParams (the each row height)
            superLayoutChildren();
            // Restore heights of all child LayoutParams when the children are laid out.
            for (int i = 0; i < mChildrenHeights.size(); i++) {
                mChildrenHeights.keyAt(i).getLayoutParams().height = mChildrenHeights.valueAt(i);
            }
            mChildrenHeights.clear();
        }
        mCompletelyVisibleRowHeights.clear();
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (!mLayoutingChildren) {
            makeEachRowChildrenMatchRowHeight();
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

    /** @see AbsListView#obtainView(int, int[]) */
    @SuppressWarnings("JavadocReference")
    protected View obtainView(int position) {
        if (sObtainViewMethod != null && sIsScrapField != null) {
            try {
                return (View) sObtainViewMethod.invoke(this, position, sIsScrapField.get(this));
            } catch (Exception e) {
                Log.w(TAG, e);
            }
        }
        return null;
    }

    /** @see AbsListView.RecycleBin#addScrapView(View, int) */
    @SuppressWarnings("JavadocReference")
    protected void recycleView(View view, int position) {
        if (sRecyclerField != null && sRecycleBinAddScrapViewMethod != null) {
            try {
                sRecycleBinAddScrapViewMethod.invoke(sRecyclerField.get(this), view, position);
            } catch (Exception e) {
                Log.w(TAG, e);
            }
        }
    }

    /** @see AbsListView.RecycleBin#shouldRecycleViewType(int) */
    @SuppressWarnings("JavadocReference")
    protected boolean shouldRecycleViewType(int viewType) {
        if (sRecyclerField != null && sRecycleBinShouldRecycleViewTypeMethod != null) {
            try {
                Boolean ret = (Boolean)
                        sRecycleBinShouldRecycleViewTypeMethod
                                .invoke(sRecyclerField.get(this), viewType);
                return ret != null && ret;
            } catch (Exception e) {
                Log.w(TAG, e);
            }
        }
        return viewType >= 0;
    }
}