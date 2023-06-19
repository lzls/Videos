/*
 * Created on 2021-10-15 12:14:18 AM.
 * Copyright © 2021 刘振林. All rights reserved.
 */

package androidx.appcompat.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
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
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.Toolbar;
import androidx.collection.ArraySet;
import androidx.core.app.ActivityCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.os.LocaleCompat;
import androidx.core.util.Consumer;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.liuzhenlin.common.Configs;
import com.liuzhenlin.common.R;
import com.liuzhenlin.common.compat.ConfigurationCompat;
import com.liuzhenlin.common.utils.ActivityUtils;
import com.liuzhenlin.common.utils.LanguageUtils;
import com.liuzhenlin.common.utils.PictureInPictureHelper;
import com.liuzhenlin.common.utils.ReflectionUtils;
import com.liuzhenlin.common.utils.ThemeUtils;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;

import static com.liuzhenlin.common.Configs.DEBUG_LANGUAGE_SWITCH;
import static com.liuzhenlin.common.Configs.TAG_LANGUAGE_SWITCH;
import static com.liuzhenlin.common.utils.PictureInPictureHelper.SDK_VERSION_SUPPORTS_PIP;

@SuppressLint("RestrictedApi")
public class AppCompatDelegateWrapper extends AppCompatDelegate implements AppCompatDelegateExtensions {

    private final AppCompatDelegate mDelegate;

    private Context mContext;
    private Object mHost;
    private HostCallback mHostCallback;
    private final HostPrivateAccess mHostPrivateAccess;

    /**
     * Flag indicating whether we can return a different context from attachBaseContext().
     * Unfortunately, doing so breaks Robolectric tests, so we skip night mode application there.
     */
    private static final boolean sCanReturnDifferentContext =
            !"robolectric".equals(Build.FINGERPRINT);

    private int mFlags;

    private static final int FLAG_BASE_CONTEXT_ATTACHED = 1;
    // true after the first call to onCreate.
    private static final int FLAG_CREATED = 1 << 1;
    // Set to true on the call of onStop and reset to false in onStart.
    private static final int FLAG_STOPPED = 1 << 2;
    // true after the first (and only) call to onDestroy.
    private static final int FLAG_DESTROYED = 1 << 3;
    private static final int FLAG_DESTROYED_AND_STILL_IN_PIP = 1 << 4;

    private static final int FLAG_ACTIVITY_HANDLES_UI_MODE_CHECKED = 1 << 28;
    private static final int FLAG_ACTIVITY_HANDLES_UI_MODE = 1 << 29;
    private static final int FLAG_ACTIVITY_HANDLES_LANGUAGE_CHECKED = 1 << 30;
    private static final int FLAG_ACTIVITY_HANDLES_LANGUAGE = 1 << 31;

    private int mThemeResId;
    private Configuration mConfig;

    @Nullable
    private PictureInPictureHelper mPipHelper;

    @Nullable
    private PendingTransitionOverrides mTransitionOverrides;

    private static boolean sGenerateConfigDeltaMethodFetched;
    private static Method sGenerateConfigDeltaMethod;

    private static final ArraySet<WeakReference<AppCompatDelegate>> sActivityDelegates;
    private static final Object sActivityDelegatesLock;

    static {
        Class<AppCompatDelegate> clazz = AppCompatDelegate.class;
        sActivityDelegates = ReflectionUtils.getDeclaredFieldValue(clazz, clazz, "sActivityDelegates");
        sActivityDelegatesLock =
                ReflectionUtils.getDeclaredFieldValue(clazz, clazz, "sActivityDelegatesLock");
    }

    public static void applyLanguageToActiveDelegates() {
        synchronized (sActivityDelegatesLock) {
            for (WeakReference<AppCompatDelegate> activeDelegate : sActivityDelegates) {
                final AppCompatDelegate delegate = activeDelegate.get();
                if (delegate instanceof AppCompatDelegateWrapper) {
                    if (DEBUG_LANGUAGE_SWITCH) {
                        Log.d(TAG_LANGUAGE_SWITCH,
                                "applyLanguageToActiveDelegates. Applying to " + delegate);
                    }
                    ((AppCompatDelegateWrapper) delegate).applyLanguage();
                }
            }
        }
    }

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
        mFlags |= FLAG_BASE_CONTEXT_ATTACHED;
        mDelegate.attachBaseContext(context);
    }

    @SuppressLint("MissingSuperCall")
    @NonNull
    public Context attachBaseContext2(@NonNull Context context) {
        mFlags |= FLAG_BASE_CONTEXT_ATTACHED;
        Context[] baseContext = {context};
        doIfDelegateIsTheBase(baseDelegate -> {
            // This is a tricky method. Here are some things to avoid:
            // 1. Don't modify the configuration of the Application context. All changes should remain
            //    local to the Activity to avoid conflicting with other Activities and internal logic.
            // 2. Don't use createConfigurationContext() with Robolectric because Robolectric relies
            //    on method overrides.
            // 3. Don't use createConfigurationContext() unless you're able to retain the base context's
            //    theme stack. Not the last theme applied -- the entire stack of applied themes.
            // 4. Don't use applyOverrideConfiguration() unless you're able to retain the base context's
            //    configuration overrides (as distinct from the entire configuration).

            final Locale locale = LanguageUtils.getDefaultLanguageLocale();

            // If the base context is a ContextThemeWrapper (thus not an Application context)
            // and nobody's touched its Resources yet, we can shortcut and directly apply our
            // override configuration.
            if (Build.VERSION.SDK_INT >= 17 && context instanceof android.view.ContextThemeWrapper) {
                Configuration config = createOverrideConfigurationForLanguage(context, locale, null);
                if (DEBUG_LANGUAGE_SWITCH) {
                    Log.d(TAG_LANGUAGE_SWITCH,
                            String.format("Attempting to apply config to base context: %s", config));
                }

                try {
                    ((android.view.ContextThemeWrapper) context).applyOverrideConfiguration(config);
                    return;
                } catch (IllegalStateException e) {
                    if (DEBUG_LANGUAGE_SWITCH) {
                        Log.d(TAG_LANGUAGE_SWITCH, "Failed to apply configuration to base context", e);
                    }
                }
            }

            // Again, but using the AppCompat version of ContextThemeWrapper.
            if (context instanceof ContextThemeWrapper) {
                Configuration config = createOverrideConfigurationForLanguage(context, locale, null);
                if (DEBUG_LANGUAGE_SWITCH) {
                    Log.d(TAG_LANGUAGE_SWITCH,
                            String.format("Attempting to apply config to base context: %s", config));
                }

                try {
                    ((ContextThemeWrapper) context).applyOverrideConfiguration(config);
                    return;
                } catch (IllegalStateException e) {
                    if (DEBUG_LANGUAGE_SWITCH) {
                        Log.d(TAG_LANGUAGE_SWITCH, "Failed to apply configuration to base context", e);
                    }
                }
            }

            // We can't apply the configuration directly to the existing base context, so we need to
            // wrap it. We can't create a new configuration context since the app may rely on method
            // overrides or a specific theme -- neither of which are preserved when creating a
            // configuration context. Instead, we'll make a best-effort at wrapping the context and
            // rebasing the original theme.
            if (!sCanReturnDifferentContext) {
                return;
            }

            Configuration configOverlay = null;

            if (Build.VERSION.SDK_INT >= 17) {
                // There is a bug in createConfigurationContext where it applies overrides to the
                // canonical configuration, e.g. ActivityThread.mCurrentConfig, rather than the base
                // configuration, e.g. Activity.getResources().getConfiguration(). We can lean on this
                // bug to obtain a reference configuration and reconstruct any custom configuration
                // that may have been applied by the app, thereby avoiding the bug later on.
                Configuration overrideConfig = new Configuration();
                // We have to modify a value to receive a new Configuration, so use one that developers
                // can't override.
                overrideConfig.uiMode = -1;
                // Workaround for incorrect default fontScale on earlier SDKs.
                overrideConfig.fontScale = 0f;
                Configuration referenceConfig =
                        context.createConfigurationContext(overrideConfig)
                                .getResources().getConfiguration();
                // Revert the uiMode change so that the diff doesn't include uiMode.
                Configuration baseConfig = context.getResources().getConfiguration();
                referenceConfig.uiMode = baseConfig.uiMode;

                // Extract any customizations as an overlay.
                if (!referenceConfig.equals(baseConfig)) {
                    configOverlay = generateConfigDelta(referenceConfig, baseConfig);
                    if (DEBUG_LANGUAGE_SWITCH) {
                        Log.d(TAG_LANGUAGE_SWITCH,
                                "Application config (" + referenceConfig + ") does not match "
                                        + "base config (" + baseConfig + "), using base overlay: "
                                        + configOverlay);
                    }
                }
            }

            final Configuration config =
                    createOverrideConfigurationForLanguage(context, locale, configOverlay);
            if (DEBUG_LANGUAGE_SWITCH) {
                Log.d(TAG_LANGUAGE_SWITCH,
                        String.format("Applying language using ContextThemeWrapper and "
                                + "applyOverrideConfiguration(). Config: %s", config));
            }

            // Next, we'll wrap the base context to ensure any method overrides or themes are left
            // intact. Since ThemeOverlay.AppCompat theme is empty, we'll get the base context's theme.
            final ContextThemeWrapper wrappedContext =
                    new ContextThemeWrapper(context, R.style.Theme_AppCompat_Empty);
            baseContext[0] = wrappedContext;
            wrappedContext.applyOverrideConfiguration(config);

            // Check whether the base context has an explicit theme or is able to obtain one
            // from its outer context. If it throws an NPE because we're at an invalid point in app
            // initialization, we don't need to worry about rebasing under the new configuration.
            boolean needsThemeRebase;
            try {
                needsThemeRebase = context.getTheme() != null;
            } catch (NullPointerException e) {
                needsThemeRebase = false;
            }

            if (needsThemeRebase) {
                // Attempt to rebase the old theme within the new configuration. This will only
                // work on SDK 23 and up, but it's unlikely that we're keeping the base theme
                // anyway so maybe nobody will notice. Note that calling getTheme() will clone
                // the base context's theme into the wrapped context's theme.
                ResourcesCompat.ThemeCompat.rebase(wrappedContext.getTheme());
            }
        });
        return mDelegate.attachBaseContext2(baseContext[0]);
    }

    private Configuration createOverrideConfigurationForLanguage(
            @NonNull Context context, @Nullable Locale loc, @Nullable Configuration configOverlay) {
        // If we're here then we can try and apply an override configuration on the Context.
        final Configuration overrideConf = new Configuration();
        overrideConf.fontScale = 0;
        if (configOverlay != null) {
            overrideConf.setTo(configOverlay);
        }
        ConfigurationCompat.setLocale(overrideConf, loc);
        return overrideConf;
    }

    private Configuration generateConfigDelta(
            @NonNull Configuration base, @Nullable Configuration change) {
        if (!sGenerateConfigDeltaMethodFetched) {
            sGenerateConfigDeltaMethod =
                    ReflectionUtils.getDeclaredMethod(AppCompatDelegateImpl.class,
                            "generateConfigDelta", Configuration.class, Configuration.class);
            sGenerateConfigDeltaMethodFetched = true;
        }
        if (sGenerateConfigDeltaMethod != null) {
            try {
                return (Configuration) sGenerateConfigDeltaMethod.invoke(mDelegate, base, change);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // attachBaseContext will only be called from an Activity, so make sure we switch this for
        // Dialogs, etc
        mFlags |= FLAG_BASE_CONTEXT_ATTACHED;
        mDelegate.onCreate(savedInstanceState);

        if (mDelegate instanceof AppCompatDelegateImpl) {
            AppCompatDelegateImpl baseDelegate = (AppCompatDelegateImpl) mDelegate;
            boolean hostIsActivity = baseDelegate.mHost instanceof Activity;
            mHost = baseDelegate.mHost;
            mContext = baseDelegate.mContext;

            // Our implicit call to applyLanguage() should not recreate until after the Activity is
            // created
            applyLanguage(false);

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
            mContext = delegate.mContext;
        }

        mFlags |= FLAG_CREATED;
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
            int uiModeMask = Configuration.UI_MODE_NIGHT_MASK;
            int oldUiNightMode = mConfig.uiMode & uiModeMask;
            Locale oldLocale = mConfig.locale;

            // Configuration might have been updated from mDelegate.onConfigurationChanged()
            // due to Day/Night mode change. Set it to the new, or we would recurse back to
            // this method from applyLanguage.
            Configuration config = baseDelegate.mContext.getResources().getConfiguration();
            mConfig.setTo(config);
            applyLanguage(false);

            // Cache current configuration, since applyLanguage inspects the last-seen configuration,
            // while mContext.getResources().getConfiguration() may be return a more updated one.
            // Also for below to see whether the Language & the DayNight configs are changed.
            config = baseDelegate.mContext.getResources().getConfiguration();
            mConfig.setTo(config);

            boolean languageChanged = !Configs.LanguageDiff.areLocaleEqual(oldLocale, config.locale);
            if (languageChanged) {
                if (DEBUG_LANGUAGE_SWITCH) {
                    Log.d(TAG_LANGUAGE_SWITCH,
                            "Language of " + baseDelegate.mHost + " changes"
                                    + " from " + LocaleCompat.toLanguageTag(oldLocale)
                                    + " to " + LocaleCompat.toLanguageTag(config.locale));
                }
                if (recreateHostWhenLanguageAppliedToResourcesConfigIfNeeded(config.locale)) {
                    return;
                }
            }

            int uiNightMode = config.uiMode & uiModeMask;
            if (oldUiNightMode != uiNightMode) {
                if (Configs.DEBUG_DAY_NIGHT_SWITCH) {
                    Log.d(Configs.TAG_DAY_NIGHT_SWITCH,
                            "UI night mode of " + baseDelegate.mHost + " changes"
                                    + " from " + uiNightModeToString(oldUiNightMode)
                                    + " to " + uiNightModeToString(uiNightMode));
                }
                recreateHostWhenDayNightAppliedToResourcesConfigIfNeeded(
                        uiNightMode == Configuration.UI_MODE_NIGHT_YES);
            }
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

    private boolean recreateHostWhenDayNightAppliedToResourcesConfigIfNeeded(boolean night) {
        if ((mFlags & (FLAG_BASE_CONTEXT_ATTACHED | FLAG_DESTROYED)) == FLAG_BASE_CONTEXT_ATTACHED
                && (sCanReturnDifferentContext || (mFlags & FLAG_CREATED) != 0)
                && mHost instanceof Activity && !((Activity) mHost).isChild()
                && isActivityManifestHandlingUiMode()) {
            if (mHostCallback == null || !mHostCallback.onDayNightAppliedToResourcesConfig(night)) {
                if (Configs.DEBUG_DAY_NIGHT_SWITCH) {
                    Log.d(Configs.TAG_DAY_NIGHT_SWITCH, "Recreate " + mHost);
                }
                ActivityCompat.recreate((Activity) mHost);
                return true;
            }
        }
        return false;
    }

    private boolean recreateHostWhenLanguageAppliedToResourcesConfigIfNeeded(Locale language) {
        if ((mFlags & (FLAG_BASE_CONTEXT_ATTACHED | FLAG_DESTROYED)) == FLAG_BASE_CONTEXT_ATTACHED
                && (sCanReturnDifferentContext || (mFlags & FLAG_CREATED) != 0)
                && mHost instanceof Activity && !((Activity) mHost).isChild()
                && isActivityManifestHandlingLocale()) {
            if (mHostCallback == null
                    || !mHostCallback.onLanguageAppliedToResourcesConfig(language)) {
                if (DEBUG_LANGUAGE_SWITCH) {
                    Log.d(TAG_LANGUAGE_SWITCH, "Recreate " + mHost);
                }
                ActivityCompat.recreate((Activity) mHost);
                return true;
            }
        }
        return false;
    }

    private boolean isActivityManifestHandlingUiMode() {
        if ((mFlags & FLAG_ACTIVITY_HANDLES_UI_MODE_CHECKED) == 0 && mHost instanceof Activity) {
            Activity host = (Activity) mHost;
            if (ActivityUtils.isActivityManifestHandingConfigs(host, ActivityInfo.CONFIG_UI_MODE)) {
                mFlags |= FLAG_ACTIVITY_HANDLES_UI_MODE;
            }
        }
        // Flip the checked flag so we don't check again
        mFlags |= FLAG_ACTIVITY_HANDLES_UI_MODE_CHECKED;
        return (mFlags & FLAG_ACTIVITY_HANDLES_UI_MODE) != 0;
    }

    private boolean isActivityManifestHandlingLocale() {
        if ((mFlags & FLAG_ACTIVITY_HANDLES_LANGUAGE_CHECKED) == 0 && mHost instanceof Activity) {
            Activity host = (Activity) mHost;
            if (ActivityUtils.isActivityManifestHandingConfigs(host, ActivityInfo.CONFIG_LOCALE)) {
                mFlags |= FLAG_ACTIVITY_HANDLES_LANGUAGE;
            }
        }
        // Flip the checked flag so we don't check again
        mFlags |= FLAG_ACTIVITY_HANDLES_LANGUAGE_CHECKED;
        return (mFlags & FLAG_ACTIVITY_HANDLES_LANGUAGE) != 0;
    }

    @Override
    public void onStart() {
        mDelegate.onStart();
        mFlags &= ~FLAG_STOPPED;
    }

    @Override
    public void onStop() {
        mDelegate.onStop();
        mFlags |= FLAG_STOPPED;
    }

    @Override
    public void onPostResume() {
        mDelegate.onPostResume();
    }

    @Override
    public void setTheme(int themeResId) {
        mThemeResId = themeResId;
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
        mFlags |= FLAG_DESTROYED;
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
                        mFlags |= FLAG_DESTROYED_AND_STILL_IN_PIP;
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

    public boolean applyLanguage() {
        return applyLanguage(true);
    }

    private boolean applyLanguage(boolean allowRecreation) {
        if ((mFlags & FLAG_DESTROYED) != 0) {
            if (DEBUG_LANGUAGE_SWITCH) {
                Log.d(TAG_LANGUAGE_SWITCH, "applyLanguage. Skipping because host is destroyed");
            }
            // If we're destroyed, ignore the call
            return false;
        }

        boolean[] handled = {false};
        doIfDelegateIsTheBase(baseDelegate -> {
            Locale locale = LanguageUtils.getDefaultLanguageLocale();
            handled[0] = updateForLanguage(locale, allowRecreation);
        });
        return handled[0];
    }

    private boolean updateForLanguage(Locale locale, boolean allowRecreation) {
        boolean handled = false;

        final boolean activityHandlingLocale = isActivityManifestHandlingLocale();
        final Configuration currentConfig =
                mConfig == null ? mContext.getResources().getConfiguration() : mConfig;
        final Locale currentLocale = currentConfig.locale;
        final String currentLanguage = LocaleCompat.toLanguageTag(currentLocale);
        final String newLanguage = LocaleCompat.toLanguageTag(locale);
        final boolean languageChanged = !Configs.LanguageDiff.areLocaleEqual(currentLocale, locale);

        if (DEBUG_LANGUAGE_SWITCH) {
            String msg = String.format(
                    "updateForLanguage [allowRecreation:%s, currentLanguage:%s, "
                            + "newLanguage:%s, activityHandlingLocale:%s, baseContextAttached:%s, "
                            + "created:%s, canReturnDifferentContext:%s, host:%s]",
                    allowRecreation, currentLanguage, newLanguage, activityHandlingLocale,
                    (mFlags & FLAG_BASE_CONTEXT_ATTACHED) != 0, (mFlags & FLAG_CREATED) != 0,
                    sCanReturnDifferentContext, mHost);
            Log.d(TAG_LANGUAGE_SWITCH, msg);
        }

        if (languageChanged
                && allowRecreation
                && !activityHandlingLocale
                && (mFlags & FLAG_BASE_CONTEXT_ATTACHED) != 0
                && (sCanReturnDifferentContext || (mFlags & FLAG_CREATED) != 0)
                && mHost instanceof Activity
                && !((Activity) mHost).isChild()) {
            // If we're an attached, standalone Activity, we can recreate() to apply using
            // the attachBaseContext() + createConfigurationContext() code path.
            // Else, we need to use updateConfiguration() before we're 'created' (below)
            if (DEBUG_LANGUAGE_SWITCH) {
                Log.d(TAG_LANGUAGE_SWITCH,
                        "updateForLanguage attempting to recreate Activity: " + mHost);
            }
            ActivityCompat.recreate((Activity) mHost);
            handled = true;
        } else if (DEBUG_LANGUAGE_SWITCH) {
            Log.d(TAG_LANGUAGE_SWITCH, "updateForLanguage not recreating Activity: " + mHost);
        }

        if (!handled && languageChanged) {
            // Else we need to use the updateConfiguration path
            if (DEBUG_LANGUAGE_SWITCH) {
                Log.d(TAG_LANGUAGE_SWITCH,
                        "updateForLanguage. Updating resources config on host: " + mHost);
            }
            updateResourcesConfigurationForLanguage(locale, activityHandlingLocale, null);
            handled = true;
        }

        if (DEBUG_LANGUAGE_SWITCH && !handled) {
            Log.d(TAG_LANGUAGE_SWITCH,
                    "updateForLanguage. Skipping. Language: " + newLanguage + " for host:" + mHost);
        }

        // Notify the host of the language. We only notify if we handled the change,
        // or the Activity is set to handle locale changes
        if (handled && mHostCallback != null) {
            mHostCallback.onLanguageChanged(/* oldLocale */ currentLocale, locale);
        }

        return handled;
    }

    private void updateResourcesConfigurationForLanguage(
            Locale locale, boolean callOnConfigChange, Configuration configOverlay) {
        // If the Activity is not set to handle locale config changes we will
        // update the Resources with a new Configuration with an updated Locale
        final Resources res = mContext.getResources();
        final Configuration conf = new Configuration(res.getConfiguration());
        if (configOverlay != null) {
            conf.updateFrom(configOverlay);
        }
        ConfigurationCompat.setLocale(conf, locale);
        res.updateConfiguration(conf, null);

        // We may need to flush the Resources' drawable cache due to framework bugs.
        if (Build.VERSION.SDK_INT < 26) {
            ResourcesFlusher.flush(res);
        }

        if (mThemeResId != 0) {
            // We need to re-apply the theme so that it reflected the new configuration
            mContext.setTheme(mThemeResId);

            if (Build.VERSION.SDK_INT >= 23) {
                // On M+ setTheme only applies if the themeResId actually changes,
                // since we have no way to publicly check what the Theme's current
                // themeResId is, we just manually apply it anyway. Most of the time
                // this is what we need anyway (since the themeResId does not often change)
                mContext.getTheme().applyStyle(mThemeResId, true);
            }
        }

        if (callOnConfigChange && mHost instanceof Activity) {
            final Activity activity = (Activity) mHost;
            if (activity instanceof LifecycleOwner) {
                // If the Activity is a LifecyleOwner, check that it is after onCreate() and
                // before onDestroy(), which includes STOPPED.
                Lifecycle lifecycle = ((LifecycleOwner) activity).getLifecycle();
                if (lifecycle.getCurrentState().isAtLeast(Lifecycle.State.CREATED)) {
                    activity.onConfigurationChanged(conf);
                }
            } else {
                // Otherwise, we'll fallback to our internal created and destroyed flags.
                if ((mFlags & (FLAG_CREATED | FLAG_DESTROYED)) == FLAG_CREATED) {
                    activity.onConfigurationChanged(conf);
                }
            }
        }
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
                    if ((mFlags & (FLAG_STOPPED | FLAG_DESTROYED_AND_STILL_IN_PIP)) == FLAG_STOPPED) {
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
        default boolean onDayNightAppliedToResourcesConfig(boolean night) {
            return false;
        }

        default boolean onLanguageAppliedToResourcesConfig(@NonNull Locale language) {
            return false;
        }

        default void onLanguageChanged(@NonNull Locale oldLanguage, @NonNull Locale newLanguage) {
        }
    }
}
