/*
 * Created on 2021-10-15 12:14:18 AM.
 * Copyright © 2021 刘振林. All rights reserved.
 */

package androidx.appcompat.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

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

    private boolean mActivityManifestDefinedSupportsPipChecked;
    private boolean mActivityManifestDefinedSupportsPiP;

    public AppCompatDelegateProxy(@NonNull AppCompatDelegate delegate) {
        mDelegate = (AppCompatDelegateImpl) delegate;
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

    public boolean doesActivityManifestDefinedSupportPiP() {
        if (!mActivityManifestDefinedSupportsPipChecked && mDelegate.mHost instanceof Activity) {
            final PackageManager pm = mDelegate.mContext.getPackageManager();
            if (pm == null) {
                // If we don't have a PackageManager, return false. Don't set
                // the checked flag though so we still check again later
                return false;
            }
            try {
                int flags = 0;
                // On newer versions of the OS we need to pass direct boot
                // flags so that getActivityInfo doesn't crash under strict
                // mode checks
                if (Build.VERSION.SDK_INT >= 29) {
                    flags = PackageManager.MATCH_DIRECT_BOOT_AUTO
                            | PackageManager.MATCH_DIRECT_BOOT_AWARE
                            | PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
                } else if (Build.VERSION.SDK_INT >= 24) {
                    flags = PackageManager.MATCH_DIRECT_BOOT_AWARE
                            | PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
                }
                final ActivityInfo info = pm.getActivityInfo(
                        new ComponentName(mDelegate.mContext, mDelegate.mHost.getClass()), flags);
                mActivityManifestDefinedSupportsPiP =
                        (info.flags & 0x400000 /* FLAG_SUPPORTS_PICTURE_IN_PICTURE */) != 0;
            } catch (PackageManager.NameNotFoundException e) {
                // This shouldn't happen but let's not crash because of it, we'll just log and
                // return false (since most apps won't be handling it)
                Log.d(TAG, "Exception while getting ActivityInfo", e);
                mActivityManifestDefinedSupportsPiP = false;
            }
        }
        // Flip the checked flag so we don't check again
        mActivityManifestDefinedSupportsPipChecked = true;

        return mActivityManifestDefinedSupportsPiP;
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
