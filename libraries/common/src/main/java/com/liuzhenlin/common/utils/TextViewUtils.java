/*
 * Created on 2017/11/09.
 * Copyright © 2017 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils;

import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.LeadingMarginSpan;
import android.text.style.TypefaceSpan;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author 刘振林
 */
public class TextViewUtils {
    private TextViewUtils() {
    }

    public static int getTextWidth(@NonNull TextView tv) {
//        CharSequence text = tv.getText();
        String text = tv.getText().toString();
        Rect bounds = new Rect();
        TextPaint paint = tv.getPaint();
        paint.getTextBounds(text, 0, text.length(), bounds);
        return bounds.width();
    }

    public static int getTextHeight(@NonNull TextView tv) {
        TextPaint paint = tv.getPaint();
        paint.setTextSize(tv.getTextSize());
        Paint.FontMetrics fm = paint.getFontMetrics();
        return Utils.roundFloat(fm.descent - fm.ascent);
    }

    public static int getLineHeight(@NonNull TextView tv) {
        TextPaint paint = tv.getPaint();
        paint.setTextSize(tv.getTextSize());
        Paint.FontMetrics fm = paint.getFontMetrics();
        return Utils.roundFloat(fm.bottom - fm.top);
    }

    public static int getLineSpacingHeight(@NonNull TextView tv) {
        TextPaint paint = tv.getPaint();
        paint.setTextSize(tv.getTextSize());
        Paint.FontMetrics fm = paint.getFontMetrics();
        return Utils.roundFloat(fm.leading);
    }

    public static final String REGEX_LEADING_OF_LINES_TO_INDENT = "(^\\d+\\. ?).+";

    public static boolean setHangingIndents(@NonNull TextView tv) {
        return setHangingIndents(tv, REGEX_LEADING_OF_LINES_TO_INDENT);
    }

    public static boolean setHangingIndents(
            @NonNull TextView tv, @NonNull String leadingRegexForLinesToIndent) {
        final String text = tv.getText().toString().replaceAll("\r", "");
        if (text.isEmpty()) {
            return false;
        }
//        if (tv.getMeasuredWidth() == 0) {
//            Log.e("TextViewUtils", "Failed to set hanging indents for the TextView " + tv
//                    + " because its width has not been determined yet."
//                    + " Probably you need to call this method after its layout is done.");
//            return false;
//        }

        boolean textChanged = false;

        // 画笔，包含字体信息
        final Paint paint = tv.getPaint();
        final Paint monospacePaint;
        if (paint.getTypeface() != Typeface.MONOSPACE) {
            monospacePaint = new Paint(paint);
            monospacePaint.setTypeface(Typeface.MONOSPACE);
        } else {
            monospacePaint = paint;
        }

        final Matcher leadingMatcherForLinesToIndent =
                Pattern.compile(leadingRegexForLinesToIndent).matcher("");
        final SpannableString spannableText = new SpannableString(text);
        final String[] textLines = text.split("\n");
        int start = 0;
        for (String textLine : textLines) {
            if (leadingMatcherForLinesToIndent.reset(textLine).matches()) {
                String leading = leadingMatcherForLinesToIndent.group(1);
                //noinspection ConstantConditions
                spannableText.setSpan(new TypefaceSpan("monospace"),
                        start, start + leading.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
                int indent = Utils.roundFloat(
                        monospacePaint.measureText(text, start, start + leading.length()));
                spannableText.setSpan(new LeadingMarginSpan.Standard(0, indent),
                        start, start + textLine.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
                textChanged = true;
            }
            start += textLine.length() + 1;
        }

        if (textChanged) {
            tv.setText(spannableText);
        }

        return textChanged;
    }
}
