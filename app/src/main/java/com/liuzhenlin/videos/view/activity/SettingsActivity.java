/*
 * Created on 2022-4-3 7:36:03 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.activity;

import android.app.Activity;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.liuzhenlin.common.utils.ThemeUtils;
import com.liuzhenlin.swipeback.SwipeBackPreferenceFragment;
import com.liuzhenlin.videos.Prefs;
import com.liuzhenlin.videos.R;
import com.liuzhenlin.videos.dao.AppPrefs;
import com.liuzhenlin.videos.web.youtube.WebService;
import com.liuzhenlin.videos.web.youtube.Youtube;

public class SettingsActivity extends StatusBarTransparentActivity implements
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
        Preference.OnPreferenceChangeListener, View.OnClickListener {

    private static final String TITLE_TAG = "settingsActivityTitle";

    private TextView mTitleText;

    @Nullable
    @Override
    public Activity getPreviousActivity() {
        return MainActivity.this$;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        insertTopPaddingToActionBarIfNeeded(findViewById(R.id.actionbar));
        findViewById(R.id.btn_back).setOnClickListener(this);
        mTitleText = findViewById(R.id.text_title);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.container_fragments, new HeaderFragment())
                    .commit();
            setTitle(R.string.settings);
        } else {
            setTitle(savedInstanceState.getCharSequence(TITLE_TAG));
        }
        getSupportFragmentManager().addOnBackStackChangedListener(
                () -> {
                    if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                        setTitle(R.string.settings);
                    }
                });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Save current activity title so we can set it again after a configuration change
        outState.putCharSequence(TITLE_TAG, getTitle());
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        // Instantiate the new Fragment
        final Bundle args = pref.getExtras();
        final Fragment fragment = getSupportFragmentManager().getFragmentFactory().instantiate(
                getClassLoader(),
                pref.getFragment());
        fragment.setArguments(args);
        fragment.setTargetFragment(caller, 0);
        // Replace the existing Fragment with the new Fragment
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(
                        R.anim.anim_open_enter, R.anim.anim_open_exit,
                        R.anim.anim_close_enter, R.anim.anim_close_exit)
                .hide(caller)
                .add(R.id.container_fragments, fragment)
                .addToBackStack(null)
                .commit();
        setTitle(pref.getTitle());
        return true;
    }

    @Override
    protected void onTitleChanged(CharSequence title, int color) {
        super.onTitleChanged(title, color);
        mTitleText.setText(title);
    }

    @Override
    public void onClick(View v) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (v.getId()) {
            case R.id.btn_back:
                if (!getSupportFragmentManager().popBackStackImmediate()) {
                    scrollToFinish();
                }
                break;
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        switch (preference.getKey()) {
            case Prefs.KEY_DARK_MODE:
                int mode;
                switch (newValue.toString()) {
                    case Prefs.DARK_MODE_ON:
                        mode = AppCompatDelegate.MODE_NIGHT_YES;
                        break;
                    case Prefs.DARK_MODE_OFF:
                        mode = AppCompatDelegate.MODE_NIGHT_NO;
                        break;
                    case Prefs.DARK_MODE_FOLLOWS_SYSTEM:
                        mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                        break;
                    default:
                        return false;
                }
                AppPrefs.getSingleton(this).edit().setDefaultNightMode(mode).apply();
                WebService.bind(this, webService -> {
                    try {
                        // Apply default night mode for web process...
                        webService.applyDefaultNightMode(mode);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    // This can cause the current Activity to recreate, so it can only be invoked
                    // after this Activity is bound with the web service or this onBindAction will
                    // probably not be called at all.
                    AppCompatDelegate.setDefaultNightMode(mode);
                });
                return true;
            case Prefs.KEY_UPDATE_CHANNEL:
            case Youtube.Prefs.KEY_PLAYBACK_PAGE_STYLE:
            case Youtube.Prefs.KEY_PIP:
            case Youtube.Prefs.KEY_VIDEO_QUALITY:
            case Youtube.Prefs.KEY_RETAIN_HISTORY_VIDEO_PAGES:
                return true;
        }
        return false;
    }

    public static class HeaderFragment extends PreferenceFragment {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.header_preferences, rootKey);
            setOnChangeListenerForPreferences();
        }

        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            //noinspection ConstantConditions
            return attachViewToSwipeBackLayout(super.onCreateView(inflater, container, savedInstanceState));
        }
    }

    @Keep
    public static class GeneralFragment extends OpaquePreferenceFragment {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.general_preferences, rootKey);
            setOnChangeListenerForPreferences();
        }
    }

    @Keep
    public static class YoutubePlaybackFragment extends OpaquePreferenceFragment {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.youtube_playback_preferences, rootKey);
            setOnChangeListenerForPreferences();
        }
    }

    public static abstract class OpaquePreferenceFragment extends PreferenceFragment {
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            //noinspection ConstantConditions
            view.setBackgroundResource(
                    ThemeUtils.getThemeAttrRes(getContext(), android.R.attr.windowBackground));
            return attachViewToSwipeBackLayout(view);
        }
    }

    public abstract static class PreferenceFragment extends SwipeBackPreferenceFragment {
        void setOnChangeListenerForPreferences() {
            PreferenceScreen ps = getPreferenceScreen();
            for (int i = ps.getPreferenceCount() - 1; i >= 0; i--) {
                ps.getPreference(i).setOnPreferenceChangeListener(
                        (Preference.OnPreferenceChangeListener) getContext() /* SettingsActivity.this */);
            }
        }
    }
}