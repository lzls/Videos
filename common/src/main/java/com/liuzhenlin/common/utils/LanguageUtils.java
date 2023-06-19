/*
 * Created on 2023-6-5 10:37:00 AM.
 * Copyright © 2023 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegateWrapper;
import androidx.core.os.LocaleCompat;

import com.liuzhenlin.common.Configs;
import com.liuzhenlin.common.compat.ConfigurationCompat;

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
        AppCompatDelegateWrapper.applyLanguageToActiveDelegates();
        updateResourcesConfigLocale(context.getApplicationContext(), getDefaultLanguageLocale());
    }

    public static int getDefaultLanguageMode() {
        return sLanguageMode;
    }

    public static String getDefaultLanguage() {
        switch (sLanguageMode) {
            case MODE_LANGUAGE_FOLLOWS_SYSTEM:
                return LocaleCompat.toLanguageTag(getSystemLocal());
            case MODE_LANGUAGE_SIMPLIFIED_CHINESE:
                return "zh-CN";
            case MODE_LANGUAGE_ENGLISH:
                return "en-US";
        }
        return "und";
    }

    public static Locale getDefaultLanguageLocale() {
        return LocaleCompat.forLanguageTag(getDefaultLanguage());
    }

    public static Locale getSystemLocal() {
        return ConfigurationCompat.getLocales(Resources.getSystem().getConfiguration()).get(0);
    }

    public static void updateResourcesConfigLocale(Context context, @Nullable Locale locale) {
        Resources res = context.getResources();
        Configuration config = res.getConfiguration();
        if (!Configs.LanguageDiff.areLocaleEqual(config.locale, locale)) {
            ConfigurationCompat.setLocale(config, locale);
            res.updateConfiguration(config, res.getDisplayMetrics());
        }
    }
}
