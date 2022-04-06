package com.liuzhenlin.common.utils.prefs;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;
import java.util.Set;

import static com.liuzhenlin.common.utils.prefs.Constants.ACTION_CLEAR;
import static com.liuzhenlin.common.utils.prefs.Constants.ACTION_CONTAINS;
import static com.liuzhenlin.common.utils.prefs.Constants.ACTION_GET_ALL;
import static com.liuzhenlin.common.utils.prefs.Constants.CURSOR_COLUMN_KEY;
import static com.liuzhenlin.common.utils.prefs.Constants.CURSOR_COLUMN_TYPE;
import static com.liuzhenlin.common.utils.prefs.Constants.CURSOR_COLUMN_VALUE;
import static com.liuzhenlin.common.utils.prefs.Constants.KEY_VALUE;
import static com.liuzhenlin.common.utils.prefs.Constants.PATH_SEPARATOR;
import static com.liuzhenlin.common.utils.prefs.Constants.TYPE_BOOLEAN;
import static com.liuzhenlin.common.utils.prefs.Constants.TYPE_FLOAT;
import static com.liuzhenlin.common.utils.prefs.Constants.TYPE_INT;
import static com.liuzhenlin.common.utils.prefs.Constants.TYPE_LONG;
import static com.liuzhenlin.common.utils.prefs.Constants.TYPE_STRING;

public class PrefsContentProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(
            @NonNull Uri uri, @Nullable String[] projection,
            @Nullable String selection, @Nullable String[] selectionArgs,
            @Nullable String sortOrder) {
        String[] path = uri.getPath().split(PATH_SEPARATOR);
        String action = path[2];
        if (action.equals(ACTION_GET_ALL)) {
            Map<String, ?> all = PrefsHelperImpl.getAll(getContext(), path[1]);
            if (all != null && !all.isEmpty()) {
                MatrixCursor cursor = new MatrixCursor(
                        new String[]{CURSOR_COLUMN_KEY, CURSOR_COLUMN_TYPE, CURSOR_COLUMN_VALUE});
                Set<String> keys = all.keySet();
                for (String key : keys) {
                    Object[] cols = new Object[3];
                    cols[0] = key;
                    cols[2] = all.get(key);
                    if (cols[2] instanceof String) {
                        cols[1] = TYPE_STRING;
                    } else if (cols[2] instanceof Boolean) {
                        cols[1] = TYPE_BOOLEAN;
                    } else if (cols[2] instanceof Integer) {
                        cols[1] = TYPE_INT;
                    } else if (cols[2] instanceof Long) {
                        cols[1] = TYPE_LONG;
                    } else if (cols[2] instanceof Float) {
                        cols[1] = TYPE_FLOAT;
                    }
                    cursor.addRow(cols);
                }
                return cursor;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        String[] path = uri.getPath().split(PATH_SEPARATOR);
        String prefsFilename = path[1];
        String arg2 = path[2];
        String key = path[3];
        if (arg2.equals(ACTION_CONTAINS)) {
            return String.valueOf(PrefsHelperImpl.contains(getContext(), prefsFilename, key));
        }
        return PrefsHelperImpl.get(getContext(), prefsFilename, key, arg2);
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        update(uri, values, null, null);
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        String[] path = uri.getPath().split(PATH_SEPARATOR);
        String prefsFilename = path[1];
        String arg2 = path[2];
        if (arg2.equals(ACTION_CLEAR)) {
            PrefsHelperImpl.clear(getContext(), prefsFilename);
        } else {
            PrefsHelperImpl.remove(getContext(), prefsFilename, arg2);
        }
        return 0;
    }

    @Override
    public int update(
            @NonNull Uri uri, @Nullable ContentValues values,
            @Nullable String selection, @Nullable String[] selectionArgs) {
        if (values != null) {
            String[] segments = uri.getPath().split(PATH_SEPARATOR);
            String prefsFilename = segments[1];
            String type = segments[2];
            String key = segments[3];
            Object value = values.get(KEY_VALUE);
            if (value == null) {
                PrefsHelperImpl.remove(getContext(), prefsFilename, key);
            } else {
                PrefsHelperImpl.put(getContext(), prefsFilename, key, value);
            }
        }
        return 0;
    }
}