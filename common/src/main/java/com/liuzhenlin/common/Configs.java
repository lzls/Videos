/*
 * Created on 2021-12-31 11:02:36 PM.
 * Copyright © 2021 刘振林. All rights reserved.
 */

package com.liuzhenlin.common;

import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.LocaleCompat;
import androidx.core.text.TextUtilsCompat;

import java.util.Locale;

public class Configs {
    private Configs() {
    }

    public static final String DEFAULT_CHARSET = "UTF-8";

    @SuppressWarnings({"PointlessBooleanExpression", "ConstantConditions"})
    public static final boolean DEBUG_DAY_NIGHT_SWITCH = BuildConfig.DEBUG && false;
    public static final String TAG_DAY_NIGHT_SWITCH = "DayNightSwitch";

    @SuppressWarnings({"PointlessBooleanExpression", "ConstantConditions"})
    public static final boolean DEBUG_LANGUAGE_SWITCH = BuildConfig.DEBUG && false;
    public static final String TAG_LANGUAGE_SWITCH = "LanguageSwitch";

    public static final int VIDEO_VIEW_BACKGROUND_COLOR = Color.BLACK;

    public static final int[] SWIPE_REFRESH_WIDGET_COLOR_SCHEME =
            {R.color.pink, R.color.lightBlue, R.color.purple};

    public enum ScreenWidthDpLevel {
        SMALL(0),
        MEDIUM(350),
        LARGE(600);

        public final int smallestWidthDp;

        ScreenWidthDpLevel(int smallestWidthDp) {
            this.smallestWidthDp = smallestWidthDp;
        }

        @NonNull
        public static ScreenWidthDpLevel of(int widthDp) {
            if (widthDp > LARGE.smallestWidthDp) {
                return LARGE;
            }
            if (widthDp > MEDIUM.smallestWidthDp) {
                return MEDIUM;
            }
            return SMALL;
        }
    }

    public static class LanguageDiff {
        private LanguageDiff() {
        }

        public static boolean areLocaleEqual(@Nullable Locale locale1, @Nullable Locale locale2) {
            if (locale1 == locale2)
                return true;
            if (locale1 == null || locale2 == null)
                return false;
            return locale1.getLanguage().equals(locale2.getLanguage())
                    && TextUtilsCompat.getLayoutDirectionFromLocale(locale1)
                            == TextUtilsCompat.getLayoutDirectionFromLocale(locale2);
        }

        public static boolean areLanguageEqual(@Nullable String language1, @Nullable String language2) {
            //noinspection StringEquality
            if (language1 == language2)
                return true;
            if (language1 == null || language2 == null)
                return false;
            return areLocaleEqual(LocaleCompat.forLanguageTag(language1),
                    LocaleCompat.forLanguageTag(language2));
        }
    }
}
