/*
 * Created on 2023-6-6 8:52:49 PM.
 * Copyright © 2023 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.compat;


import android.content.res.Configuration;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

public class ConfigurationCompat {
    private ConfigurationCompat() {
    }

    @NonNull
    public static LocaleListCompat getLocales(@NonNull Configuration configuration) {
        return androidx.core.os.ConfigurationCompat.getLocales(configuration);
    }

    public static void setLocale(@NonNull Configuration configuration, @Nullable Locale locale) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            configuration.setLocale(locale);
        } else {
            configuration.locale = locale;
        }
    }
}
