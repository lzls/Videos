/*
 * Created on 2018/06/26.
 * Copyright © 2018 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils;

import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.FloatRange;

/**
 * @author 刘振林
 */
public class ColorUtils {
    private ColorUtils() {
    }

    public static boolean isLightColor(@ColorInt int color) {
        return getColorGrayLevel(color) >= 192;
    }

    public static float getColorGrayLevel(@ColorInt int color) {
        final int r = Color.red(color);
        final int g = Color.green(color);
        final int b = Color.blue(color);
        return r * 0.299f + g * 0.578f + b * 0.114f;
    }

    public static int getGradientColor(
            @ColorInt int startColor, @ColorInt int endColor,
            @FloatRange(from = 0.0, to = 1.0) float percent) {
        final int startA = Color.alpha(startColor);
        final int startR = Color.red(startColor);
        final int startG = Color.green(startColor);
        final int startB = Color.blue(startColor);

        final int endA = Color.alpha(endColor);
        final int endR = Color.red(endColor);
        final int endG = Color.green(endColor);
        final int endB = Color.blue(endColor);

        final int currentA = Utils.roundFloat(startA * (1f - percent) + endA * percent);
        final int currentR = Utils.roundFloat(startR * (1f - percent) + endR * percent);
        final int currentG = Utils.roundFloat(startG * (1f - percent) + endG * percent);
        final int currentB = Utils.roundFloat(startB * (1f - percent) + endB * percent);
        return Color.argb(currentA, currentR, currentG, currentB);
    }

    public static int dimColor(@ColorInt int color, @FloatRange(from = 0.0f, to = 1.0f) float amount) {
        return Utils.roundFloat(((color & 0xff000000) >>> 24) * (1 - amount)) << 24
                | color & 0x00ffffff;
    }
}
