/*
 * Created on 2021-10-15 12:14:18 AM.
 * Copyright © 2021 刘振林. All rights reserved.
 */

package androidx.appcompat.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import com.liuzhenlin.common.utils.PictureInPictureHelper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@SuppressLint("RestrictedApi")
public class AppCompatDelegateProxy extends AppCompatDelegate {

    private final AppCompatDelegateImpl mDelegate;

    /**
     * Flag indicating whether we can return a different context from attachBaseContext().
     * Unfortunately, doing so breaks Robolectric tests, so we skip night mode application there.
     */
    private static final boolean sCanReturnDifferentContext =
            !"robolectric".equals(Build.FINGERPRINT);

    private boolean mBaseContextAttached;
    private boolean mCreated;

    private static Method sIsActivityManifestHandlingUiModeMethod;

    @Nullable
    private PictureInPictureHelper mPipHelper;

    public AppCompatDelegateProxy(@NonNull AppCompatDelegate delegate) {
        mDelegate = (AppCompatDelegateImpl) delegate;
    }

    public void setPipHelper(@Nullable PictureInPictureHelper pipHelper) {
        mPipHelper = pipHelper;
    }

    @Nullable
    public PictureInPictureHelper getPipHelper() {
        return mPipHelper;
    }

    @NonNull
    public PictureInPictureHelper requirePipHelper() {
        if (mPipHelper == null) {
            throw new IllegalStateException("No PictureInPictureHelper has been set for " + this);
        }
        return mPipHelper;
    }

    @Nullable
    @Override
    public ActionBar getSupportActionBar() {
        return mDelegate.getSupportActionBar();
    }

    @Override
    public void setSupportActionBar(@Nullable Toolbar toolbar) {
        mDelegate.setSupportActionBar(toolbar);
    }

    @Override
    public MenuInflater getMenuInflater() {
        return mDelegate.getMenuInflater();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void attachBaseContext(Context context) {
        mBaseContextAttached = true;
        mDelegate.attachBaseContext(context);
    }

    @SuppressLint("MissingSuperCall")
    @NonNull
    public Context attachBaseContext2(@NonNull Context context) {
        mBaseContextAttached = true;
        return mDelegate.attachBaseContext2(context);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // attachBaseContext will only be called from an Activity, so make sure we switch this for
        // Dialogs, etc
        mBaseContextAttached = true;
        mDelegate.onCreate(savedInstanceState);
        mCreated = true;
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        mDelegate.onPostCreate(savedInstanceState);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        mDelegate.onConfigurationChanged(newConfig);
    }

    @Override
    public void onStart() {
        mDelegate.onStart();
    }

    @Override
    public void onStop() {
        mDelegate.onStop();
    }

    @Override
    public void onPostResume() {
        mDelegate.onPostResume();
    }

    @Override
    public void setTheme(int themeResId) {
        mDelegate.setTheme(themeResId);
    }

    @Nullable
    @Override
    public <T extends View> T findViewById(int id) {
        return mDelegate.findViewById(id);
    }

    @Override
    public void setContentView(View v) {
        mDelegate.setContentView(v);
    }

    @Override
    public void setContentView(int resId) {
        mDelegate.setContentView(resId);
    }

    @Override
    public void setContentView(View v, ViewGroup.LayoutParams lp) {
        mDelegate.setContentView(v, lp);
    }

    @Override
    public void addContentView(View v, ViewGroup.LayoutParams lp) {
        mDelegate.addContentView(v, lp);
    }

    @Override
    public void setTitle(@Nullable CharSequence title) {
        mDelegate.setTitle(title);
    }

    @Override
    public void invalidateOptionsMenu() {
        mDelegate.invalidateOptionsMenu();
    }

    @Override
    public void onDestroy() {
        mDelegate.onDestroy();
    }

    @Nullable
    @Override
    public ActionBarDrawerToggle.Delegate getDrawerToggleDelegate() {
        return mDelegate.getDrawerToggleDelegate();
    }

    @Override
    public boolean requestWindowFeature(int featureId) {
        return mDelegate.requestWindowFeature(featureId);
    }

    @Override
    public boolean hasWindowFeature(int featureId) {
        return mDelegate.hasWindowFeature(featureId);
    }

    @Nullable
    @Override
    public ActionMode startSupportActionMode(@NonNull ActionMode.Callback callback) {
        return mDelegate.startSupportActionMode(callback);
    }

    @Override
    public void installViewFactory() {
        mDelegate.installViewFactory();
    }

    @Override
    public View createView(@Nullable View parent, String name, @NonNull Context context, @NonNull AttributeSet attrs) {
        return mDelegate.createView(parent, name, context, attrs);
    }

    @Override
    public void setHandleNativeActionModesEnabled(boolean enabled) {
        mDelegate.setHandleNativeActionModesEnabled(enabled);
    }

    @Override
    public boolean isHandleNativeActionModesEnabled() {
        return mDelegate.isHandleNativeActionModesEnabled();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        mDelegate.onSaveInstanceState(outState);
    }

    @Override
    public boolean applyDayNight() {
        return mDelegate.applyDayNight();
    }

    public void recreateHostWhenDayNightAppliedIfNeeded() {
        if (!mDelegate.mIsDestroyed
                && mBaseContextAttached
                && (sCanReturnDifferentContext || mCreated)
                && mDelegate.mHost instanceof Activity && !((Activity) mDelegate.mHost).isChild()
                && isActivityManifestHandlingUiMode()) {
            ActivityCompat.recreate((Activity) mDelegate.mHost);
        }
    }

    private boolean isActivityManifestHandlingUiMode() {
        try {
            if (sIsActivityManifestHandlingUiModeMethod == null) {
                sIsActivityManifestHandlingUiModeMethod = AppCompatDelegateImpl.class
                        .getDeclaredMethod("isActivityManifestHandlingUiMode");
                sIsActivityManifestHandlingUiModeMethod.setAccessible(true);
            }
            Boolean ret = (Boolean) sIsActivityManifestHandlingUiModeMethod.invoke(mDelegate);
            return ret != null && ret;
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public int getLocalNightMode() {
        return mDelegate.getLocalNightMode();
    }

    @RequiresApi(17)
    @Override
    public void setLocalNightMode(int mode) {
        mDelegate.setLocalNightMode(mode);
    }
}
