package com.liuzhenlin.swipeback;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class SwipeBackActivity extends AppCompatActivity implements ISwipeBackActivity {

    private final SwipeBackActivityDelegate<SwipeBackActivity> mDelegate =
            new SwipeBackActivityDelegate<>(this, new PrivateAccess() {
                @Override
                public Object superGetSystemService(@NonNull String name) {
                    return SwipeBackActivity.super.getSystemService(name);
                }

                @Override
                public void superFinish() {
                    SwipeBackActivity.super.finish();
                }

                @SuppressLint("NewApi")
                @Override
                public void superFinishAffinity() {
                    SwipeBackActivity.super.finishAffinity();
                }

                @SuppressLint("NewApi")
                @Override
                public void superFinishAndRemoveTask() {
                    SwipeBackActivity.super.finishAndRemoveTask();
                }
            });

    @Override
    public Object getSystemService(@NonNull String name) {
        return mDelegate.getSystemService(name);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDelegate.onCreate(savedInstanceState);
    }

    @Override
    public final SwipeBackLayout getSwipeBackLayout() {
        return mDelegate.getSwipeBackLayout();
    }

    @Override
    public final boolean isSwipeBackEnabled() {
        return mDelegate.isSwipeBackEnabled();
    }

    @Override
    public final void setSwipeBackEnabled(boolean enabled) {
        mDelegate.setSwipeBackEnabled(enabled);
    }

    @Override
    public boolean canSwipeBackToFinish() {
        return mDelegate.canSwipeBackToFinish();
    }

    /*
     * This default implementation just returns 'null'. Subclasses are encouraged to override this
     * to provide simultaneous scrolling of the previous and the current activities's content views
     * while the current one is involved in a swipe-back.
     */
    @Nullable
    @Override
    public Activity getPreviousActivity() {
        return mDelegate.getPreviousActivity();
    }

    @Override
    public void finish() {
        mDelegate.finish();
    }

    @Override
    public void finishAffinity() {
        mDelegate.finishAffinity();
    }

    @Override
    public void finishAndRemoveTask() {
        mDelegate.finishAndRemoveTask();
    }
}
