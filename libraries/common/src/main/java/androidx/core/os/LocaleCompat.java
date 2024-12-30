/*
 * Created on 2023-6-6 12:54:32 PM.
 * Copyright © 2023 刘振林. All rights reserved.
 */

package androidx.core.os;

import android.annotation.SuppressLint;
import android.os.Build;

import androidx.annotation.NonNull;

import java.util.Locale;

public class LocaleCompat {
    private LocaleCompat() {
    }

    @NonNull
    public static Locale forLanguageTag(@NonNull String languageTag) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return Locale.forLanguageTag(languageTag);
        } else {
            return LocaleListCompat.forLanguageTagCompat(languageTag);
        }
    }

    @SuppressLint("VisibleForTests")
    @NonNull
    public static String toLanguageTag(@NonNull Locale locale) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return locale.toLanguageTag();
        } else {
            StringBuilder languageTag = new StringBuilder();
            LocaleListCompatWrapper.toLanguageTag(languageTag, locale);
            return languageTag.toString();
        }
    }
}
