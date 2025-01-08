/*
 * Created on 2022-9-3 7:57:44 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils;

import androidx.annotation.Nullable;

import com.liuzhenlin.common.Consts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@NonNullApi
public class Regex {

    private final Matcher mMatcher;
    private String mMatcherInput = "";

    public Regex(String regex) {
        this(regex, 0);
    }

    public Regex(String regex, int flags) {
        mMatcher = Pattern.compile(regex, flags).matcher(mMatcherInput);
    }

    public String regex() {
        return mMatcher.pattern().pattern();
    }

    public int flags() {
        return mMatcher.pattern().flags();
    }

    public int start() {
        return mMatcher.start();
    }

    public int start(int group) {
        return mMatcher.start(group);
    }

    public int end() {
        return mMatcher.end();
    }

    public int end(int group) {
        return mMatcher.end(group);
    }

    public boolean matches(String input) {
        ensureMatcherInput(input);
        return mMatcher.matches();
    }

    public boolean find(String input) {
        ensureMatcherInput(input);
        return mMatcher.find();
    }

    public boolean find(String input, int start) {
        ensureMatcherInput(input);
        return mMatcher.find(start);
    }

    public boolean lookingAt(String input) {
        ensureMatcherInput(input);
        return mMatcher.lookingAt();
    }

    @Nullable
    public String group() {
        return mMatcher.group();
    }

    @Nullable
    public String group(int group) {
        return mMatcher.group(group);
    }

    public int groupCount() {
        return mMatcher.groupCount();
    }

    public String[] split(String input) {
        return split(input, 0);
    }

    public String[] split(String input, int limit) {
        String[] array = mMatcher.pattern().split(input, limit);
        if (limit == 0) {
            return array;
        }

        List<String> list = new ArrayList<>(Arrays.asList(array));
        for (int i = list.size() - 1; i >= 0; i--) {
            if ("".equals(list.get(i))) {
                list.remove(i);
            }
        }
        return list.toArray(Consts.EMPTY_STRING_ARRAY);
    }

    public String replaceFirst(String input, String replacement) {
        ensureMatcherInput(input);
        return mMatcher.replaceFirst(replacement);
    }

    public String replaceAll(String input, String replacement) {
        ensureMatcherInput(input);
        return mMatcher.replaceAll(replacement);
    }

    private void ensureMatcherInput(String input) {
        if (!mMatcherInput.equals(input)) {
            mMatcher.reset(input);
            mMatcherInput = input;
        }
    }

    @Override
    public String toString() {
        return regex();
    }
}
