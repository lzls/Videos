package com.liuzhenlin.common.utils.prefs;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.util.Map;
import java.util.Set;

import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import androidx.collection.ArraySet;

import com.liuzhenlin.common.utils.NonNullApi;

import static com.liuzhenlin.common.utils.prefs.Constants.ACTION_CLEAR;
import static com.liuzhenlin.common.utils.prefs.Constants.ACTION_CONTAINS;
import static com.liuzhenlin.common.utils.prefs.Constants.ACTION_GET_ALL;
import static com.liuzhenlin.common.utils.prefs.Constants.CONTENT_URL_PREFIX;
import static com.liuzhenlin.common.utils.prefs.Constants.CURSOR_COLUMN_KEY;
import static com.liuzhenlin.common.utils.prefs.Constants.CURSOR_COLUMN_TYPE;
import static com.liuzhenlin.common.utils.prefs.Constants.CURSOR_COLUMN_VALUE;
import static com.liuzhenlin.common.utils.prefs.Constants.KEY_VALUE;
import static com.liuzhenlin.common.utils.prefs.Constants.NULL_STRING;
import static com.liuzhenlin.common.utils.prefs.Constants.PATH_SEPARATOR;
import static com.liuzhenlin.common.utils.prefs.Constants.TYPE_BOOLEAN;
import static com.liuzhenlin.common.utils.prefs.Constants.TYPE_FLOAT;
import static com.liuzhenlin.common.utils.prefs.Constants.TYPE_INT;
import static com.liuzhenlin.common.utils.prefs.Constants.TYPE_LONG;
import static com.liuzhenlin.common.utils.prefs.Constants.TYPE_STRING;
import static com.liuzhenlin.common.utils.prefs.Constants.TYPE_STRING_SET;
import static com.liuzhenlin.common.utils.prefs.Constants.TYPE_UNKNOWN;

@NonNullApi
public class PrefsHelper {

    private static final String STRING_ELEMENT_SEPARATOR = ", ";
    private static final String STRING_ELEMENT_SEPARATOR_REPLACEMENT = "__COMMA__";

    private static final String REGEX_ARRAY = "\\[(.*(, )?)+\\]";

    private final Context mContext;
    private final String mUrlPrefix;

    public static PrefsHelper create(Context context, String prefsFilename) {
        return new PrefsHelper(context, prefsFilename);
    }

    private PrefsHelper(Context context, String prefsFilename) {
        mContext = context.getApplicationContext();
        mUrlPrefix = CONTENT_URL_PREFIX + prefsFilename + PATH_SEPARATOR;
    }

    public void put(String key, boolean b) {
        _put(key, b);
    }

    public void put(String key, int i) {
        _put(key, i);
    }

    public void put(String key, long l) {
        _put(key, l);
    }

    public void put(String key, float f) {
        _put(key, f);
    }

    public void put(String key, @Nullable String s) {
        _put(key, s);
    }

    public void put(String key, @Nullable Set<String> strings) {
        _put(key, strings);
    }

    private void _put(String key, @Nullable Object obj) {
        String type;
        ContentValues values = new ContentValues(1);
        if (obj instanceof String) {
            type = TYPE_STRING;
            values.put(KEY_VALUE, (String) obj);
        } else if (obj instanceof Boolean) {
            type = TYPE_BOOLEAN;
            values.put(KEY_VALUE, (Boolean) obj);
        } else if (obj instanceof Integer) {
            type = TYPE_INT;
            values.put(KEY_VALUE, (Integer) obj);
        } else if (obj instanceof Long) {
            type = TYPE_LONG;
            values.put(KEY_VALUE, (Long) obj);
        } else if (obj instanceof Float) {
            type = TYPE_FLOAT;
            values.put(KEY_VALUE, (Float) obj);
        } else if (obj instanceof Set/*<String>*/) {
            type = TYPE_STRING_SET;
            //noinspection unchecked
            Set<String> strings = (Set<String>) obj;
            Set<String> ss = new ArraySet<>();
            for (String string : strings) {
                ss.add(string.replace(STRING_ELEMENT_SEPARATOR, STRING_ELEMENT_SEPARATOR_REPLACEMENT));
            }
            values.put(KEY_VALUE, ss.toString());
        } else {
            type = TYPE_UNKNOWN;
        }
        ContentResolver cr = mContext.getContentResolver();
        Uri uri = Uri.parse(mUrlPrefix + type + PATH_SEPARATOR + key);
        cr.update(uri, values, null, null);
    }

    @Nullable
    public String getString(String key, @Nullable String defaultValue) {
        ContentResolver cr = mContext.getContentResolver();
        Uri uri = Uri.parse(mUrlPrefix + TYPE_STRING + PATH_SEPARATOR + key);
        String value = cr.getType(uri);
        if (value == null || value.equals(NULL_STRING)) {
            return defaultValue;
        }
        return value;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        ContentResolver cr = mContext.getContentResolver();
        Uri uri = Uri.parse(mUrlPrefix + TYPE_BOOLEAN + PATH_SEPARATOR + key);
        String value = cr.getType(uri);
        if (value == null || value.equals(NULL_STRING)) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    public int getInt(String key, int defaultValue) {
        ContentResolver cr = mContext.getContentResolver();
        Uri uri = Uri.parse(mUrlPrefix + TYPE_INT + PATH_SEPARATOR + key);
        String value = cr.getType(uri);
        if (value == null || value.equals(NULL_STRING)) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    public long getLong(String key, long defaultValue) {
        ContentResolver cr = mContext.getContentResolver();
        Uri uri = Uri.parse(mUrlPrefix + TYPE_LONG + PATH_SEPARATOR + key);
        String value = cr.getType(uri);
        if (value == null || value.equals(NULL_STRING)) {
            return defaultValue;
        }
        return Long.parseLong(value);
    }

    public float getFloat(String key, float defaultValue) {
        ContentResolver cr = mContext.getContentResolver();
        Uri uri = Uri.parse(mUrlPrefix + TYPE_FLOAT + PATH_SEPARATOR + key);
        String value = cr.getType(uri);
        if (value == null || value.equals(NULL_STRING)) {
            return defaultValue;
        }
        return Float.parseFloat(value);
    }

    @Nullable
    public Set<String> getStringSet(String key, @Nullable Set<String> defaultValue) {
        ContentResolver cr = mContext.getContentResolver();
        Uri uri = Uri.parse(mUrlPrefix + TYPE_STRING_SET + PATH_SEPARATOR + key);
        String value = cr.getType(uri);
        if (value == null || value.equals(NULL_STRING)) {
            return defaultValue;
        }
        if (!value.matches(REGEX_ARRAY)) {
            return defaultValue;
        }
        String[] ss = value.substring(1, value.length() - 1).split(STRING_ELEMENT_SEPARATOR);
        Set<String> result = new ArraySet<>();
        for (String s : ss) {
            result.add(s.replace(STRING_ELEMENT_SEPARATOR_REPLACEMENT, STRING_ELEMENT_SEPARATOR));
        }
        return result;
    }

    public boolean contains(String key) {
        ContentResolver cr = mContext.getContentResolver();
        Uri uri = Uri.parse(mUrlPrefix + ACTION_CONTAINS + PATH_SEPARATOR + key);
        String result = cr.getType(uri);
        if (result == null || result.equals(NULL_STRING)) {
            return false;
        } else {
            return Boolean.parseBoolean(result);
        }
    }

    public void remove(String key) {
        ContentResolver cr = mContext.getContentResolver();
        Uri uri = Uri.parse(mUrlPrefix + key);
        cr.delete(uri, null, null);
    }

    public void clear() {
        ContentResolver cr = mContext.getContentResolver();
        Uri uri = Uri.parse(mUrlPrefix + ACTION_CLEAR);
        cr.delete(uri, null, null);
    }

    public Map<String, ?> getAll() {
        ContentResolver cr = mContext.getContentResolver();
        Uri uri = Uri.parse(mUrlPrefix + ACTION_GET_ALL);
        Cursor cursor = cr.query(uri, null, null, null, null);
        Map<String, Object> result = new ArrayMap<>();
        if (cursor != null && cursor.moveToFirst()) {
            int keyIndex = cursor.getColumnIndex(CURSOR_COLUMN_KEY);
            int typeIndex = cursor.getColumnIndex(CURSOR_COLUMN_TYPE);
            int valueIndex = cursor.getColumnIndex(CURSOR_COLUMN_VALUE);
            do {
                String key = cursor.getString(keyIndex);
                String type = cursor.getString(typeIndex);
                @Nullable Object value = null;
                if (type.equalsIgnoreCase(TYPE_STRING)) {
                    value = cursor.getString(valueIndex);
                    if (((String) value).contains(STRING_ELEMENT_SEPARATOR_REPLACEMENT)) {
                        String str = (String) value;
                        if (str.matches(REGEX_ARRAY)) {
                            String substr = str.substring(1, str.length() - 1);
                            String[] ss = substr.split(STRING_ELEMENT_SEPARATOR);
                            Set<String> strings = new ArraySet<>();
                            for (String s : ss) {
                                strings.add(
                                        s.replace(STRING_ELEMENT_SEPARATOR_REPLACEMENT,
                                                STRING_ELEMENT_SEPARATOR));
                            }
                            value = strings;
                        }
                    }
                } else if (type.equalsIgnoreCase(TYPE_BOOLEAN)) {
                    value = cursor.getString(valueIndex);
                } else if (type.equalsIgnoreCase(TYPE_INT)) {
                    value = cursor.getInt(valueIndex);
                } else if (type.equalsIgnoreCase(TYPE_LONG)) {
                    value = cursor.getLong(valueIndex);
                } else if (type.equalsIgnoreCase(TYPE_FLOAT)) {
                    value = cursor.getFloat(valueIndex);
                } else if (type.equalsIgnoreCase(TYPE_STRING_SET)) {
                    value = cursor.getString(valueIndex);
                }
                //noinspection ConstantConditions
                result.put(key, value);
            } while (cursor.moveToNext());
            cursor.close();
        }
        return result;
    }
}