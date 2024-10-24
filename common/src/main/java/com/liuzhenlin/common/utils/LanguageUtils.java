/*
 * Created on 2023-6-5 10:37:00 AM.
 * Copyright © 2023 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils;

import android.app.LocaleManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatDelegateWrapper;
import androidx.core.os.BuildCompat;
import androidx.core.os.LocaleCompat;

import com.liuzhenlin.common.Configs;
import com.liuzhenlin.common.compat.ConfigurationCompat;
import com.liuzhenlin.common.compat.LocaleListCompat;

import java.util.Locale;

@NonNullApi
public class LanguageUtils {
    private LanguageUtils() {
    }

    public static int sLanguageMode = 0;
    public static final int MODE_LANGUAGE_FOLLOWS_SYSTEM = 0;
    public static final int MODE_LANGUAGE_SIMPLIFIED_CHINESE = 1;
    public static final int MODE_LANGUAGE_ENGLISH = 2;

    public static void setDefaultLanguageMode(Context context, int mode) {
        sLanguageMode = mode;
        setAppLocaleToDefault(context, true);
    }

    public static int getDefaultLanguageMode() {
        return sLanguageMode;
    }

    public static String getDefaultLanguage(Context context) {
        switch (sLanguageMode) {
            case MODE_LANGUAGE_FOLLOWS_SYSTEM:
                Locale sysLocale = getSystemLocale(context);
                if (!Locale.CHINESE.getLanguage().equals(sysLocale.getLanguage())) {
                    return LocaleCompat.toLanguageTag(sysLocale);
                }
                // fall through
            case MODE_LANGUAGE_SIMPLIFIED_CHINESE:
                return "zh-CN";
            case MODE_LANGUAGE_ENGLISH:
                return "en-US";
        }
        return "und";
    }

    public static Locale getDefaultLanguageLocale(Context context) {
        return LocaleCompat.forLanguageTag(getDefaultLanguage(context));
    }

    @OptIn(markerClass = BuildCompat.PrereleaseSdkCheck.class)
    public static Locale getSystemLocale(Context context) {
        if (BuildCompat.isAtLeastT()) {
            LocaleManager localeManager = (LocaleManager)
                    context.getApplicationContext().getSystemService(Context.LOCALE_SERVICE);
            return localeManager.getSystemLocales().get(0);
        }
        return Resources.getSystem().getConfiguration().locale;
    }

    public static void setAppLocaleToDefault(Context context, boolean reloadAppPages) {
        if (reloadAppPages) {
            AppCompatDelegateWrapper.applyLanguageToActiveDelegates();
        }
        Locale appLocale = getDefaultLanguageLocale(context);
        updateResourcesConfigLocale(context.getApplicationContext().getResources(), appLocale);
        LocaleListCompat.setDefault(androidx.core.os.LocaleListCompat.create(appLocale));
    }

    public static void updateResourcesConfigLocale(Resources res, @Nullable Locale locale) {
        Configuration config = res.getConfiguration();
        if (!Configs.LanguageDiff.areLocaleEqual(config.locale, locale)) {
            ConfigurationCompat.setLocale(config, locale);
            res.updateConfiguration(config, res.getDisplayMetrics());
        }
    }
}
