package com.liuzhenlin.common.utils.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import java.lang.ref.SoftReference;
import java.util.Map;

import androidx.collection.ArrayMap;

import static com.liuzhenlin.common.utils.prefs.Constants.TYPE_BOOLEAN;
import static com.liuzhenlin.common.utils.prefs.Constants.TYPE_FLOAT;
import static com.liuzhenlin.common.utils.prefs.Constants.TYPE_INT;
import static com.liuzhenlin.common.utils.prefs.Constants.TYPE_LONG;
import static com.liuzhenlin.common.utils.prefs.Constants.TYPE_STRING;
import static com.liuzhenlin.common.utils.prefs.Constants.TYPE_STRING_SET;

final class PrefsHelperImpl {

    private PrefsHelperImpl() {
    }

    private static SharedPreferences getPrefs(Context context, String prefsFilename) {
        if (context == null) {
            return null;
        }
        return context.getSharedPreferences(prefsFilename, Context.MODE_PRIVATE);
    }

    private static SoftReference<Map<String, Object>> sCacheMap;

    private static Object getCachedValue(String key) {
        if (sCacheMap != null) {
            Map<String, Object> map = sCacheMap.get();
            if (map != null) {
                return map.get(key);
            }
        }
        return null;
    }

    private static void cacheValue(String key, Object value) {
//        Map<String, Object> map;
//        if (sCacheMap == null) {
//            map = new ArrayMap<>();
//            sCacheMap = new SoftReference<>(map);
//        } else {
//            map = sCacheMap.get();
//            if (map == null) {
//                map = new ArrayMap<>();
//                sCacheMap = new SoftReference<>(map);
//            }
//        }
//        map.put(key, value);
    }

    private static void cleanCachedValues() {
        if (sCacheMap != null) {
            Map<String, Object> map = sCacheMap.get();
            if (map != null) {
                map.clear();
            }
        }
    }

    static <T> void put(Context context, String prefsFilename, String key, T value) {
        SharedPreferences sp = getPrefs(context, prefsFilename);
        if (sp == null) return;

        if (value.equals(getCachedValue(key))) {
            return;
        }
        SharedPreferences.Editor editor = sp.edit();
        if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        }
        editor.apply();
        cacheValue(key, value);
    }

    static String get(Context context, String prefsFilename, String key, String type) {
        Object value = getCachedValue(key);
        if (value == null) {
            value = getFromPrefs(context, prefsFilename, key, type);
            cacheValue(key, value);
        }
        return String.valueOf(value);
    }

    private static Object getFromPrefs(Context context, String prefsFilename, String key, String type) {
        if (type.equalsIgnoreCase(TYPE_STRING)) {
            return getString(context, prefsFilename, key, null);
        } else if (type.equalsIgnoreCase(TYPE_BOOLEAN)) {
            return getBoolean(context, prefsFilename, key, false);
        } else if (type.equalsIgnoreCase(TYPE_INT)) {
            return getInt(context, prefsFilename, key, 0);
        } else if (type.equalsIgnoreCase(TYPE_LONG)) {
            return getLong(context, prefsFilename, key, 0L);
        } else if (type.equalsIgnoreCase(TYPE_FLOAT)) {
            return getFloat(context, prefsFilename, key, 0f);
        } else if (type.equalsIgnoreCase(TYPE_STRING_SET)) {
            return getString(context, prefsFilename, key, null);
        }
        return null;
    }

    static String getString(Context context, String prefsFilename, String name, String defaultValue) {
        SharedPreferences sp = getPrefs(context, prefsFilename);
        if (sp == null) return defaultValue;
        return sp.getString(name, defaultValue);
    }

    static boolean getBoolean(Context context, String prefsFilename, String name, boolean defaultValue) {
        SharedPreferences sp = getPrefs(context, prefsFilename);
        if (sp == null) return defaultValue;
        return sp.getBoolean(name, defaultValue);
    }

    static int getInt(Context context, String prefsFilename, String name, int defaultValue) {
        SharedPreferences sp = getPrefs(context, prefsFilename);
        if (sp == null) return defaultValue;
        return sp.getInt(name, defaultValue);
    }

    static long getLong(Context context, String prefsFilename, String name, long defaultValue) {
        SharedPreferences sp = getPrefs(context, prefsFilename);
        if (sp == null) return defaultValue;
        return sp.getLong(name, defaultValue);
    }

    static float getFloat(Context context, String prefsFilename, String name, float defaultValue) {
        SharedPreferences sp = getPrefs(context, prefsFilename);
        if (sp == null) return defaultValue;
        return sp.getFloat(name, defaultValue);
    }

    static boolean contains(Context context, String prefsFilename, String key) {
        SharedPreferences sp = getPrefs(context, prefsFilename);
        if (sp == null) return false;
        return sp.contains(key);
    }

    static void remove(Context context, String prefsFilename, String key) {
        SharedPreferences sp = getPrefs(context, prefsFilename);
        if (sp == null) return;
        sp.edit().remove(key).apply();
    }

    static void clear(Context context, String prefsFilename) {
        SharedPreferences sp = getPrefs(context, prefsFilename);
        if (sp != null) sp.edit().clear().apply();
        cleanCachedValues();
    }

    static Map<String, ?> getAll(Context context, String prefsFilename) {
        SharedPreferences sp = getPrefs(context, prefsFilename);
        if (sp == null) return null;
        return sp.getAll();
    }
}