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
import androidx.core.util.Consumer;

import com.liuzhenlin.common.Configs;
import com.liuzhenlin.common.utils.PictureInPictureHelper;
import com.liuzhenlin.common.utils.ThemeUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static com.liuzhenlin.common.utils.PictureInPictureHelper.SDK_VERSION_SUPPORTS_PIP;

@SuppressLint("RestrictedApi")
public class AppCompatDelegateWrapper extends AppCompatDelegate implements AppCompatDelegateExtensions {

    private final AppCompatDelegate mDelegate;

    private Object mHost;
    private HostCallback mHostCallback;
    private final HostPrivateAccess mHostPrivateAccess;

    /**
     * Flag indicating whether we can return a different context from attachBaseContext().
     * Unfortunately, doing so breaks Robolectric tests, so we skip night mode application there.
     */
    private static final boolean sCanReturnDifferentContext =
            !"robolectric".equals(Build.FINGERPRINT);

    private boolean mBaseContextAttached;
    private boolean mCreated;
    private boolean mStopped;
    private boolean mDestroyedAndStillInPiP;

    private Configuration mConfig;

    private static Method sIsActivityManifestHandlingUiModeMethod;

    @Nullable
    private PictureInPictureHelper mPipHelper;

    @Nullable
    private PendingTransitionOverrides mTransitionOverrides;

    public AppCompatDelegateWrapper(
            @NonNull AppCompatDelegate delegate, @NonNull HostPrivateAccess hostPrivateAccess) {
        mDelegate = delegate;
        mHostPrivateAccess = hostPrivateAccess;
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

    public void setPendingTransitionOverrides(@Nullable PendingTransitionOverrides overrides) {
        mTransitionOverrides = overrides;
    }

    public void setHostCallback(@Nullable HostCallback callback) {
        mHostCallback = callback;
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
        if (mDelegate instanceof AppCompatDelegateImpl) {
            AppCompatDelegateImpl baseDelegate = (AppCompatDelegateImpl) mDelegate;
            boolean hostIsActivity = baseDelegate.mHost instanceof Activity;
            mHost = baseDelegate.mHost;

            if (mTransitionOverrides != null && hostIsActivity) {
                ((Activity) baseDelegate.mHost).overridePendingTransition(
                        mTransitionOverrides.getOpenEnterTransition(),
                        mTransitionOverrides.getOpenExitTransition());
            }

            mConfig = new Configuration(baseDelegate.mContext.getResources().getConfiguration());

            //// Logic for app module...
            // Caches the night mode in global as far as possible early, under the promise of
            // the same day/night mode throughout the whole app. The default night mode may not follow
            // the system default, and so we can not just use the app context to see if this app
            // is decorated with the dark theme. This should be called for each activity instead of
            // just the first launched one, in case some of the activities are being recreated...
            if (hostIsActivity) {
                try {
                    Class<?> appClass = Class.forName("com.liuzhenlin.videos.App");
                    if (appClass != null) {
                        appClass.getMethod("cacheNightMode", boolean.class)
                                .invoke(appClass, ThemeUtils.isNightMode((Context) baseDelegate.mHost));
                    }
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                }
            }
        } else if (mDelegate instanceof AppCompatDelegateWrapper) {
            AppCompatDelegateWrapper delegate = (AppCompatDelegateWrapper) mDelegate;
            mHost = delegate.mHost;
        }
        // Try replacing the wrapped delegate cached in active Activity delegates with this,
        // which will ensure our applyDayNight() method can be called from
        // AppCompatDelegate.setDefaultNightMode().
        if (mHost instanceof Activity) {
            removeActivityDelegate(mDelegate);
            addActiveDelegate(this);
        }
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        mDelegate.onPostCreate(savedInstanceState);
        if (doesSdkVersionSupportPiP()) {
            doIfDelegateIsTheBase(baseDelegate -> {
                if (baseDelegate.mHost instanceof Activity) {
                    Activity activity = (Activity) baseDelegate.mHost;
                    // Fix onPictureInPictureModeChanged not called when the activity is recreated
                    // due to any configuration we do not handle changed, which can lead to
                    // state inconsistencies and even crashes.
                    //noinspection NewApi
                    if (activity.isInPictureInPictureMode()) {
                        activity.onPictureInPictureModeChanged(true);
                    }
                }
            });
        }
    }

    private boolean doesSdkVersionSupportPiP() {
        return mPipHelper != null && mPipHelper.doesSdkVersionSupportPiP()
                || mPipHelper == null && Build.VERSION.SDK_INT >= SDK_VERSION_SUPPORTS_PIP;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        mDelegate.onConfigurationChanged(newConfig);
        doIfDelegateIsTheBase(baseDelegate -> {
            // Be sure to update the Config from Resources here since it may have changed with
            // the updated UI Mode
            Configuration config = baseDelegate.mContext.getResources().getConfiguration();
            int uiModeMask = Configuration.UI_MODE_NIGHT_MASK;
            int oldUiNightMode = mConfig.uiMode & uiModeMask;
            int uiNightMode = config.uiMode & uiModeMask;
            if (oldUiNightMode != uiNightMode) {
                if (Configs.DEBUG_DAY_NIGHT_SWITCH) {
                    Log.d(Configs.TAG_DAY_NIGHT_SWITCH,
                            "UI night mode of " + baseDelegate.mHost + " changes"
                                    + " from " + uiNightModeToString(oldUiNightMode)
                                    + " to " + uiNightModeToString(uiNightMode));
                }
                recreateHostWhenDayNightAppliedToResourcesConfigIfNeeded(
                        baseDelegate, uiNightMode == Configuration.UI_MODE_NIGHT_YES);
            }
            mConfig.setTo(config);
        });
    }

    private String uiNightModeToString(int uiNightMode) {
        switch (uiNightMode) {
            case Configuration.UI_MODE_NIGHT_YES:
                return "night";
            case Configuration.UI_MODE_NIGHT_NO:
            default:
                return "day";
        }
    }

    private void recreateHostWhenDayNightAppliedToResourcesConfigIfNeeded(
            AppCompatDelegateImpl baseDelegate, boolean night) {
        if (!baseDelegate.mDestroyed
                && mBaseContextAttached
                && (sCanReturnDifferentContext || mCreated)
                && baseDelegate.mHost instanceof Activity && !((Activity) baseDelegate.mHost).isChild()
                && isActivityManifestHandlingUiMode()) {
            if (mHostCallback == null || !mHostCallback.onDayNightAppliedToResourcesConfig(night)) {
                if (Configs.DEBUG_DAY_NIGHT_SWITCH) {
                    Log.d(Configs.TAG_DAY_NIGHT_SWITCH, "Recreate " + baseDelegate.mHost);
                }
                ActivityCompat.recreate((Activity) baseDelegate.mHost);
            }
        }
    }

    private boolean isActivityManifestHandlingUiMode() {
        try {
            if (sIsActivityManifestHandlingUiModeMethod == null) {
                sIsActivityManifestHandlingUiModeMethod =
                        AppCompatDelegateImpl.class
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
    public void onStart() {
        mDelegate.onStart();
        mStopped = false;
    }

    @Override
    public void onStop() {
        mDelegate.onStop();
        mStopped = true;
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
        if (mHost instanceof Activity) {
            removeActivityDelegate(this);
        }
        if (doesSdkVersionSupportPiP()) {
            doIfDelegateIsTheBase(baseDelegate -> {
                if (baseDelegate.mHost instanceof Activity) {
                    Activity activity = (Activity) baseDelegate.mHost;
                    // Fix onPictureInPictureModeChanged not called when the activity is going to be
                    // recreated due to any configuration we do not handle changed, which can lead to
                    // the previous instance to leak for mReceiver is still registered on it, etc.
                    //noinspection NewApi
                    if (activity.isInPictureInPictureMode()) {
                        mDestroyedAndStillInPiP = true;
                        activity.onPictureInPictureModeChanged(false);
                    }
                }
            });
        }
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
    public View createView(
            @Nullable View parent, String name, @NonNull Context context, @NonNull AttributeSet attrs) {
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

    @Override
    public int getLocalNightMode() {
        return mDelegate.getLocalNightMode();
    }

    @RequiresApi(17)
    @Override
    public void setLocalNightMode(int mode) {
        mDelegate.setLocalNightMode(mode);
    }

    @Override
    public void finish() {
        doIfDelegateIsTheBase(baseDelegate -> {
            if (baseDelegate.mHost instanceof Activity) {
                Activity activity = (Activity) baseDelegate.mHost;

                PictureInPictureHelper pipHelper = getPipHelper();
                if (pipHelper != null && pipHelper.supportsPictureInPictureMode()) {
                    // finish() does not remove the activity in PIP mode from the recents stack.
                    // Only finishAndRemoveTask() does this.
                    //noinspection NewApi
                    activity.finishAndRemoveTask();
                } else {
                    mHostPrivateAccess.superFinishActivity();
                }

                if (mTransitionOverrides != null) {
                    activity.overridePendingTransition(
                            mTransitionOverrides.getCloseEnterTransition(),
                            mTransitionOverrides.getCloseExitTransition());
                }
            }
        });
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        doIfDelegateIsTheBase(baseDelegate -> {
            if (baseDelegate.mHost instanceof Activity) {
                Activity activity = (Activity) baseDelegate.mHost;
                if (!isInPictureInPictureMode) {
                    if (mStopped && !mDestroyedAndStillInPiP) {
                        // We have closed the picture-in-picture window by clicking the 'close' button.
                        // Remove the pip activity task too, so that it will not be kept
                        // in the recents list.
                        activity.finish();
                    }
                    // If the above condition doesn't hold, this activity is destroyed or may be in
                    // the recreation process...
                }
            }
        });
    }

    private void doIfDelegateIsTheBase(Consumer<AppCompatDelegateImpl> consumer) {
        if (mDelegate instanceof AppCompatDelegateImpl) {
            consumer.accept((AppCompatDelegateImpl) mDelegate);
        }
    }

    public interface HostPrivateAccess {
        void superFinishActivity();
    }

    public interface HostCallback {
        boolean onDayNightAppliedToResourcesConfig(boolean night);
    }
}
