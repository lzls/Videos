package com.liuzhenlin.common.utils.prefs;

import com.liuzhenlin.common.Consts;

public final class Constants {
    private Constants() {
    }

    public static final String PATH_SEPARATOR = "/";
    public static final String CONTENT_PROTOCOL = "content://";
    public static final String AUTHORITY = Consts.APPLICATION_ID + ".prefs.provider";
    public static final String CONTENT_URL_PREFIX = CONTENT_PROTOCOL + AUTHORITY + PATH_SEPARATOR;

    /*package*/ static final String TYPE_BOOLEAN = "boolean";
    /*package*/ static final String TYPE_INT = "int";
    /*package*/ static final String TYPE_LONG = "long";
    /*package*/ static final String TYPE_FLOAT = "float";
    /*package*/ static final String TYPE_STRING = "string";
    /*package*/ static final String TYPE_STRING_SET = "string_set";
    /*package*/ static final String TYPE_UNKNOWN = "unknown";

    public static final String KEY_VALUE = "value";

    public static final String NULL_STRING = "null";

    /*package*/ static final String ACTION_CONTAINS = "contains";
    /*package*/ static final String ACTION_CLEAR = "clear";
    /*package*/ static final String ACTION_GET_ALL = "get_all";

    /*package*/ static final String CURSOR_COLUMN_KEY = "cursor_key";
    /*package*/ static final String CURSOR_COLUMN_TYPE = "cursor_type";
    /*package*/ static final String CURSOR_COLUMN_VALUE = "cursor_value";
}
