/*
 * Created on 2024-5-7 8:21:25 PM.
 * Copyright © 2024 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.compat;

import android.os.Build;
import android.os.LocaleList;

import androidx.annotation.NonNull;

import java.util.Locale;

public class LocaleListCompat {
    private LocaleListCompat() {
    }

    public static void setDefault(@NonNull androidx.core.os.LocaleListCompat locales) {
        if (locales.isEmpty()) {
            throw new IllegalArgumentException("locales is empty");
        }
        if (Build.VERSION.SDK_INT >= 24) {
            LocaleList.setDefault(LocaleList.forLanguageTags(locales.toLanguageTags()));
        } else {
            Locale.setDefault(locales.get(0));
        }
    }
}
